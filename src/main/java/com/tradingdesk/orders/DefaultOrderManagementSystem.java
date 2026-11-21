package com.tradingdesk.orders;

import com.tradingdesk.model.Execution;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.RiskAlert;
import com.tradingdesk.model.Side;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;
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
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultOrderManagementSystem implements OrderManagementSystem {

    private final RiskManager riskManager;
    private final PortfolioManager portfolioManager;
    private final long orderQuantity;
    private final ConcurrentMap<UUID, Order> orders = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, List<Execution>> executions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<OrderResult>> listeners = new CopyOnWriteArrayList<>();
    private final LongAdder submitted = new LongAdder();
    private final LongAdder approved = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder executed = new LongAdder();
    private final LongAdder cancelled = new LongAdder();

    public DefaultOrderManagementSystem(RiskManager riskManager,
                                        PortfolioManager portfolioManager,
                                        long orderQuantity) {
        this.riskManager = Objects.requireNonNull(riskManager, "riskManager");
        this.portfolioManager = Objects.requireNonNull(portfolioManager, "portfolioManager");
        if (orderQuantity <= 0) {
            throw new IllegalArgumentException("orderQuantity must be positive");
        }
        this.orderQuantity = orderQuantity;
    }

    public void addListener(Consumer<OrderResult> listener) {
        listeners.add(listener);
    }

    @Override
    public OrderResult submit(TradeSignal signal, double marketPrice) {
        Objects.requireNonNull(signal, "signal");
        if (signal.type() == SignalType.HOLD) {
            throw new IllegalArgumentException("HOLD signals do not create orders");
        }
        if (marketPrice <= 0.0) {
            throw new IllegalArgumentException("marketPrice must be positive");
        }

        Side side = signal.type() == SignalType.BUY ? Side.BUY : Side.SELL;
        Instant now = Instant.now();
        Order order = Order.pending(signal.instrument(), side, orderQuantity, marketPrice, now);
        orders.put(order.id(), order);
        submitted.increment();

        Map<String, Double> prices = Map.of(signal.instrument().symbol(), marketPrice);
        double projected = portfolioManager.projectedExposure(
                signal.instrument(), side, orderQuantity, marketPrice, prices);
        RiskContext context = RiskContext.of(order, portfolioManager.snapshot(prices), marketPrice, projected);
        List<RiskAlert> violations = riskManager.evaluate(context);

        if (!violations.isEmpty()) {
            rejected.increment();
            String reason = violations.stream()
                    .map(RiskAlert::message)
                    .collect(Collectors.joining("; "));
            Order denied = order.transitionTo(OrderStatus.REJECTED, truncate(reason, 250), Instant.now());
            orders.put(denied.id(), denied);
            return publish(new OrderResult(denied, Optional.empty(), violations));
        }

        Order approvedOrder = order.transitionTo(OrderStatus.APPROVED, null, Instant.now());
        orders.put(approvedOrder.id(), approvedOrder);
        approved.increment();

        Execution execution = new Execution(approvedOrder.id(), marketPrice, orderQuantity, Instant.now());
        portfolioManager.applyFill(signal.instrument(), side, orderQuantity, marketPrice);
        executions.computeIfAbsent(approvedOrder.id(), id -> new CopyOnWriteArrayList<>()).add(execution);
        executed.increment();

        Order filled = approvedOrder.transitionTo(OrderStatus.EXECUTED, null, execution.executedAt());
        orders.put(filled.id(), filled);
        return publish(new OrderResult(filled, Optional.of(execution), List.of()));
    }

    private OrderResult publish(OrderResult result) {
        for (Consumer<OrderResult> listener : listeners) {
            listener.accept(result);
        }
        return result;
    }

    @Override
    public Optional<Order> find(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public List<Order> openOrders() {
        return orders.values().stream()
                .filter(order -> order.status().isOpen())
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
    }

    @Override
    public List<Order> history(int limit) {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public List<Execution> executionsFor(UUID orderId) {
        return List.copyOf(executions.getOrDefault(orderId, List.of()));
    }

    @Override
    public boolean cancel(UUID orderId) {
        Order current = orders.get(orderId);
        if (current == null || current.status().isTerminal()) {
            return false;
        }
        orders.put(orderId, current.transitionTo(OrderStatus.CANCELLED, null, Instant.now()));
        cancelled.increment();
        return true;
    }

    @Override
    public OrderStatistics statistics() {
        return new OrderStatistics(submitted.sum(), approved.sum(), rejected.sum(),
                executed.sum(), cancelled.sum(), openOrders().size());
    }

    @Override
    public void reset() {
        orders.clear();
        executions.clear();
        submitted.reset();
        approved.reset();
        rejected.reset();
        executed.reset();
        cancelled.reset();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
