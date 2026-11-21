package com.tradingdesk.exchange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * A deterministic in-memory exchange used by <strong>all</strong> automated tests.
 *
 * <p>It behaves like a real adapter — it authenticates, holds balances, enforces safety limits and
 * refuses orders unless live mode is permitted — but it never touches a network or real funds. That
 * makes the whole exchange path testable without credentials.
 *
 * <p>Failure injection ({@link #failNextRequest()}, {@link #setRateLimited(boolean)}) lets tests
 * prove that API failures and rate limits are handled rather than assumed away.
 */
public class MockExchange implements ExchangeClient {

    private static final Logger LOGGER = Logger.getLogger(MockExchange.class.getName());

    private final TradingModeGuard guard;
    private final LiveSafetyLimits limits;
    private final ConcurrentMap<String, ExchangeBalance> balances = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ExchangeOrder> orders = new ConcurrentHashMap<>();
    private final List<ExchangeOrder> trades = new ArrayList<>();

    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private volatile boolean failNextRequest;
    private volatile boolean rateLimited;
    private volatile double realisedLossToday;
    private volatile double lastOrderPrice;

    public MockExchange(TradingModeGuard guard, LiveSafetyLimits limits) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.limits = Objects.requireNonNull(limits, "limits");
        balances.put("USDT", new ExchangeBalance("USDT", 10_000.0, 0.0));
        balances.put("BTC", new ExchangeBalance("BTC", 0.0, 0.0));
    }

    // ------------------------------------------------------------------ test controls

    /** Makes the next call fail, to exercise error handling. */
    public void failNextRequest() {
        this.failNextRequest = true;
    }

    /** Simulates the venue refusing requests due to rate limiting. */
    public void setRateLimited(boolean rateLimited) {
        this.rateLimited = rateLimited;
    }

    public void setRealisedLossToday(double loss) {
        this.realisedLossToday = loss;
    }

    public void credit(String asset, double amount) {
        balances.merge(asset, new ExchangeBalance(asset, amount, 0.0),
                (a, b) -> new ExchangeBalance(asset, a.free() + b.free(), a.locked()));
    }

    // ------------------------------------------------------------------ ExchangeClient

    @Override
    public String venueName() {
        return "mock";
    }

    @Override
    public boolean isSandbox() {
        return true;
    }

    @Override
    public boolean authenticate() {
        if (failNextRequest) {
            failNextRequest = false;
            LOGGER.info("MockExchange: injected authentication failure");
            authenticated.set(false);
            return false;
        }
        if (rateLimited) {
            authenticated.set(false);
            return false;
        }
        if (!guard.hasCredentials()) {
            authenticated.set(false);
            return false;
        }
        authenticated.set(true);
        return true;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated.get();
    }

    @Override
    public List<ExchangeBalance> balances() {
        requireHealthy();
        return List.copyOf(balances.values());
    }

    @Override
    public List<ExchangePosition> positions() {
        requireHealthy();
        // Derive positions from filled orders so the mock stays consistent with its own history.
        Map<String, double[]> aggregated = new LinkedHashMap<>();
        synchronized (trades) {
            for (ExchangeOrder order : trades) {
                if (!order.isFilled()) {
                    continue;
                }
                double signed = "BUY".equalsIgnoreCase(order.side())
                        ? order.quantity()
                        : -order.quantity();
                double[] current = aggregated.computeIfAbsent(order.symbol(),
                        key -> new double[]{0.0, order.price()});
                current[0] += signed;
            }
        }
        List<ExchangePosition> result = new ArrayList<>();
        aggregated.forEach((symbol, values) -> {
            if (values[0] != 0.0) {
                result.add(new ExchangePosition(symbol, values[0], values[1], Instant.now()));
            }
        });
        return List.copyOf(result);
    }

    @Override
    public ExchangeOrder placeOrder(ExchangeOrder request) {
        Objects.requireNonNull(request, "request");
        requireHealthy();

        // The single most important line in this class: no order without live permission.
        requireLiveAllowed(guard);

        List<String> problems = limits.violations(request, realisedLossToday,
                positions().size(), isDuplicate(request));
        if (!problems.isEmpty()) {
            ExchangeOrder rejected = new ExchangeOrder(request.clientOrderId(), null,
                    request.symbol(), request.side(), request.type(), request.quantity(),
                    request.price(), "REJECTED", 0.0, Instant.now());
            LOGGER.warning("MockExchange rejected order: " + problems);
            orders.put(rejected.clientOrderId(), rejected);
            return rejected;
        }

        ExchangeOrder filled = new ExchangeOrder(request.clientOrderId(),
                "mock-" + orders.size(), request.symbol(), request.side(), request.type(),
                request.quantity(), request.price(), "FILLED", request.quantity(), Instant.now());
        orders.put(filled.clientOrderId(), filled);
        synchronized (trades) {
            trades.add(filled);
        }
        adjustBalances(filled);
        lastOrderPrice = request.price();
        return filled;
    }

    private void adjustBalances(ExchangeOrder order) {
        // Simple two-asset model: buying BTC spends USDT and acquires the base asset; selling does
        // the reverse. Amounts are applied as signed deltas to the existing totals.
        String base = order.symbol().length() > 4
                ? order.symbol().substring(0, order.symbol().length() - 4)
                : order.symbol();
        double cash = order.quantity() * order.price();
        boolean buying = "BUY".equalsIgnoreCase(order.side());

        adjust("USDT", buying ? -cash : cash);
        adjust(base, buying ? order.quantity() : -order.quantity());
    }

    private void adjust(String asset, double delta) {
        balances.compute(asset, (key, existing) -> {
            double current = existing == null ? 0.0 : existing.free();
            double updated = Math.max(0.0, current + delta);
            return new ExchangeBalance(asset, updated, existing == null ? 0.0 : existing.locked());
        });
    }

    private boolean isDuplicate(ExchangeOrder request) {
        if (!limits.duplicateOrderPrevention()) {
            return false;
        }
        synchronized (trades) {
            return trades.stream().anyMatch(t -> t.symbol().equals(request.symbol())
                    && t.side().equalsIgnoreCase(request.side())
                    && t.quantity() == request.quantity()
                    && Math.abs(t.price() - request.price()) < 1e-9);
        }
    }

    @Override
    public boolean cancelOrder(String symbol, String exchangeOrderId) {
        requireHealthy();
        Optional<ExchangeOrder> existing = orders.values().stream()
                .filter(o -> exchangeOrderId.equals(o.exchangeOrderId()))
                .findFirst();
        if (existing.isEmpty() || existing.get().isTerminal()) {
            return false;
        }
        ExchangeOrder original = existing.get();
        orders.put(original.clientOrderId(), new ExchangeOrder(original.clientOrderId(),
                original.exchangeOrderId(), original.symbol(), original.side(), original.type(),
                original.quantity(), original.price(), "CANCELLED", 0.0, original.createdAt()));
        return true;
    }

    @Override
    public Optional<ExchangeOrder> orderStatus(String symbol, String exchangeOrderId) {
        requireHealthy();
        return orders.values().stream()
                .filter(o -> exchangeOrderId.equals(o.exchangeOrderId()))
                .findFirst();
    }

    @Override
    public List<ExchangeOrder> tradeHistory(String symbol, int limit) {
        requireHealthy();
        synchronized (trades) {
            List<ExchangeOrder> filtered = trades.stream()
                    .filter(o -> o.symbol().equals(symbol))
                    .toList();
            int from = Math.max(0, filtered.size() - Math.max(0, limit));
            return List.copyOf(filtered.subList(from, filtered.size()));
        }
    }

    /** Fails loudly when the venue is simulating an outage or a rate limit. */
    private void requireHealthy() {
        if (failNextRequest) {
            failNextRequest = false;
            throw new IllegalStateException("injected request failure");
        }
        if (rateLimited) {
            throw new IllegalStateException("rate limited by exchange");
        }
    }
}
