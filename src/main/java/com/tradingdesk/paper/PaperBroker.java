package com.tradingdesk.paper;

import com.tradingdesk.model.Execution;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.OrderType;
import com.tradingdesk.model.RiskAlert;
import com.tradingdesk.model.Side;
import com.tradingdesk.portfolio.PortfolioManager;
import com.tradingdesk.risk.RiskContext;
import com.tradingdesk.risk.RiskManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The paper trading broker: the single place where a paper order is validated, risk-checked, opened,
 * matched against market data and settled into the portfolio.
 *
 * <p>It reuses the existing {@link PortfolioManager} and {@link RiskManager} rather than
 * reimplementing position or risk logic. The legacy automated-signal path
 * ({@code DefaultOrderManagementSystem}) is untouched and still works; both share the same
 * portfolio so balances cannot diverge.
 *
 * <p>All state lives in concurrent collections and every mutation of an order goes through
 * {@link WorkingOrder}'s immutable transitions, so worker threads can match orders while the UI
 * reads them safely.
 */
public class PaperBroker {

    /** Outcome of submitting an order. */
    public record Submission(WorkingOrder order, List<RiskAlert> violations) {
        public Submission {
            violations = List.copyOf(violations);
        }

        public boolean rejected() {
            return !violations.isEmpty();
        }
    }

    private final PaperAccount account;
    private final PortfolioManager portfolio;
    private final RiskManager riskManager;
    private final ExecutionEngine executionEngine;
    private final ConcurrentMap<UUID, WorkingOrder> orders = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Double> bestPriceByOrder = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<WorkingOrder>> listeners = new CopyOnWriteArrayList<>();

    public PaperBroker(PaperAccount account,
                       PortfolioManager portfolio,
                       RiskManager riskManager,
                       ExecutionEngine executionEngine) {
        this.account = Objects.requireNonNull(account, "account");
        this.portfolio = Objects.requireNonNull(portfolio, "portfolio");
        this.riskManager = Objects.requireNonNull(riskManager, "riskManager");
        this.executionEngine = Objects.requireNonNull(executionEngine, "executionEngine");
    }

    public PaperAccount account() {
        return account;
    }

    public void addListener(Consumer<WorkingOrder> listener) {
        listeners.add(listener);
    }

    public PortfolioManager portfolio() {
        return portfolio;
    }

    // ------------------------------------------------------------------ submission

    /**
     * Validates, risk-checks and opens an order. Returns a rejected order (with reasons) when
     * validation or risk fails; nothing is placed on the book in that case.
     */
    public Submission submit(OrderRequest request, double marketPrice) {
        Objects.requireNonNull(request, "request");
        Instant now = Instant.now();

        if (!Double.isFinite(marketPrice) || marketPrice <= 0.0) {
            return reject(request, now, List.of("Invalid market price: " + marketPrice));
        }

        WorkingOrder order = WorkingOrder.open(request, now);

        // 1. Balance / buying-power validation happens before risk so the reason is specific.
        double referencePrice = referencePrice(request, marketPrice);
        double notional = referencePrice * request.quantity();
        double estimatedFee = notional * (executionEngine.costs().commissionBps() / 10_000.0);
        if (request.side() == Side.BUY && !account.canAfford(notional, estimatedFee)) {
            return reject(request, now, List.of(String.format(
                    "Insufficient balance: need %.2f, available %.2f",
                    notional + estimatedFee, account.availableBalance())));
        }

        // 2. Existing risk rules, unchanged.
        Map<String, Double> prices = Map.of(request.instrument().symbol(), marketPrice);
        double projected = portfolio.projectedExposure(
                request.instrument(), request.side(), request.quantity(), marketPrice, prices);
        RiskContext context = RiskContext.of(
                toLegacyOrder(order, marketPrice), portfolio.snapshot(prices), marketPrice, projected);
        List<RiskAlert> violations = riskManager.evaluate(context);
        if (!violations.isEmpty()) {
            WorkingOrder denied = order.reject(
                    truncate(violations.stream().map(RiskAlert::message)
                            .collect(Collectors.joining("; ")), 250), now);
            orders.put(denied.id(), denied);
            notify(denied);
            return new Submission(denied, violations);
        }

        WorkingOrder opened = order.markOpen(now);
        orders.put(opened.id(), opened);
        notify(opened);
        return new Submission(opened, List.of());
    }

    private Submission reject(OrderRequest request, Instant now, List<String> reasons) {
        WorkingOrder order = WorkingOrder.open(request, now).reject(
                truncate(String.join("; ", reasons), 250), now);
        orders.put(order.id(), order);
        notify(order);
        return new Submission(order, List.of());
    }

    /**
     * Adapts a paper order to the legacy {@link Order} shape so the existing risk rules can be
     * reused verbatim without changing their behaviour or signatures.
     */
    private static Order toLegacyOrder(WorkingOrder order, double marketPrice) {
        OrderRequest request = order.request();
        OrderType legacyType = request.kind().requiresLimitPrice() ? OrderType.LIMIT : OrderType.MARKET;
        double price = legacyType == OrderType.LIMIT ? request.limitPrice() : marketPrice;
        return new Order(order.id(), request.instrument(), request.side(), legacyType,
                request.quantity(), price, OrderStatus.PENDING, null,
                order.createdAt(), order.createdAt());
    }

    private static double referencePrice(OrderRequest request, double marketPrice) {
        if (request.kind().requiresLimitPrice() && request.limitPrice() > 0.0) {
            return request.limitPrice();
        }
        if (request.kind().requiresStopPrice() && request.stopPrice() > 0.0) {
            return request.stopPrice();
        }
        return marketPrice;
    }

    // ------------------------------------------------------------------ matching

    /**
     * Matches every open order against a market snapshot. Called from the market data consumer, so
     * it must be fast and must not block.
     */
    public List<WorkingOrder> onMarketEvent(MarketEvent event) {
        String symbol = event.instrument().symbol();
        List<WorkingOrder> updated = new ArrayList<>();

        for (WorkingOrder order : orders.values()) {
            if (!order.instrument().symbol().equals(symbol) || !order.status().isOpen()) {
                continue;
            }
            Optional<OrderFill> fill = executionEngine.match(order, event, Instant.now());
            if (fill.isEmpty()) {
                continue;
            }
            WorkingOrder filled = order.withFill(fill.get());
            settle(filled, fill.get(), event);
            orders.put(filled.id(), filled);
            notify(filled);
            updated.add(filled);
        }
        return List.copyOf(updated);
    }

    /** Applies a fill to the account and the portfolio, and books realized P&L when reducing. */
    private void settle(WorkingOrder order, OrderFill fill, MarketEvent event) {
        Side side = order.side();
        account.applyFill(fill, side, fill.filledAt());

        // Realized P&L: the portfolio already computes it when a fill closes an opposite position.
        double realizedBefore = portfolio.realizedProfitLoss();
        portfolio.applyFill(order.instrument(), side, fill.quantity(), fill.price());
        double realizedAfter = portfolio.realizedProfitLoss();
        if (realizedAfter != realizedBefore) {
            account.applyRealizedPnl(realizedAfter - realizedBefore,
                    order.instrument().symbol() + " closed portion", fill.filledAt());
        }

        // Reserve margin for a short opened by a sell that leaves a negative position.
        if (side == Side.SELL) {
            long net = portfolio.position(order.instrument()).quantity();
            if (net < 0) {
                double required = Math.abs(net) * event.lastPrice();
                double alreadyPosted = bestPriceByOrder.getOrDefault(order.id(), 0.0);
                double additional = required - alreadyPosted;
                if (additional > 0 && account.availableBalance() >= additional) {
                    account.postMargin(additional);
                    bestPriceByOrder.put(order.id(), required);
                }
            }
        } else {
            // A buy that reduces a short releases collateral.
            long net = portfolio.position(order.instrument()).quantity();
            if (net >= 0 && bestPriceByOrder.containsKey(order.id())) {
                double posted = bestPriceByOrder.remove(order.id());
                account.releaseMargin(posted);
            }
        }
    }

    // ------------------------------------------------------------------ management

    public Optional<WorkingOrder> find(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<WorkingOrder> openOrders() {
        return orders.values().stream()
                .filter(o -> o.status().isOpen())
                .sorted(Comparator.comparing(WorkingOrder::createdAt).reversed())
                .toList();
    }

    public List<WorkingOrder> history(int limit) {
        return orders.values().stream()
                .sorted(Comparator.comparing(WorkingOrder::createdAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    /** Cancels an open order. Returns false when the order is unknown or already terminal. */
    public boolean cancel(UUID orderId) {
        WorkingOrder current = orders.get(orderId);
        if (current == null || current.status().isTerminal()) {
            return false;
        }
        WorkingOrder cancelled = current.cancelled(Instant.now());
        orders.put(orderId, cancelled);
        notify(cancelled);
        return true;
    }

    /** Modifies an open, unfilled order. Returns the updated order, or empty if not modifiable. */
    public Optional<WorkingOrder> modify(UUID orderId, OrderRequest replacement) {
        WorkingOrder current = orders.get(orderId);
        if (current == null || current.status().isTerminal()) {
            return Optional.empty();
        }
        if (current.filledQuantity() > 0) {
            return Optional.empty();
        }
        WorkingOrder modified = current.modify(replacement, Instant.now());
        orders.put(orderId, modified);
        notify(modified);
        return Optional.of(modified);
    }

    /** Converts completed paper fills into legacy executions for the existing persistence layer. */
    public List<Execution> executionsFor(UUID orderId) {
        WorkingOrder order = orders.get(orderId);
        if (order == null) {
            return List.of();
        }
        return order.fills().stream()
                .map(f -> new Execution(order.id(), f.price(), f.quantity(), f.filledAt()))
                .toList();
    }

    public void reset() {
        orders.clear();
        bestPriceByOrder.clear();
    }

    public Instrument instrumentOf(UUID orderId) {
        WorkingOrder order = orders.get(orderId);
        return order == null ? null : order.instrument();
    }

    private void notify(WorkingOrder order) {
        for (Consumer<WorkingOrder> listener : listeners) {
            listener.accept(order);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
