package com.tradingdesk.risk;

import com.tradingdesk.model.AlertSeverity;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.OrderType;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.RiskAlert;
import com.tradingdesk.model.Side;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRiskManagerTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private static final RiskConfiguration CONFIG =
            new RiskConfiguration(1_000L, 100_000.0, 5_000.0, 0.05, 500L);

    private static Order order(long quantity) {
        return Order.pending(AAPL, Side.BUY, quantity, 150.0, Instant.now());
    }

    private static Set<String> rules(List<RiskAlert> alerts) {
        return alerts.stream().map(RiskAlert::rule).collect(Collectors.toSet());
    }

    private static RiskContext context(Order order, PortfolioSnapshot portfolio,
                                       double marketPrice, double projected) {
        return RiskContext.of(order, portfolio, marketPrice, projected);
    }

    @Test
    void acceptsOrderWithinAllLimits() {
        DefaultRiskManager manager = new DefaultRiskManager(CONFIG);
        assertTrue(manager.evaluate(context(order(100), null, 150.0, 15_000.0)).isEmpty());
    }

    @Test
    void rejectsOversizedQuantity() {
        DefaultRiskManager manager = new DefaultRiskManager(CONFIG);
        assertTrue(rules(manager.evaluate(context(order(5_000), null, 150.0, 10_000.0)))
                .contains(RiskRule.MAX_ORDER_QUANTITY.name()));
    }

    @Test
    void rejectsPositionValueAboveLimit() {
        DefaultRiskManager manager = new DefaultRiskManager(CONFIG);
        assertTrue(rules(manager.evaluate(context(order(10), null, 150.0, 200_000.0)))
                .contains(RiskRule.MAX_POSITION_SIZE.name()));
    }

    @Test
    void rejectsWhenDailyLossExceeded() {
        DefaultRiskManager manager = new DefaultRiskManager(CONFIG);
        PortfolioSnapshot snapshot = new PortfolioSnapshot(0, 0, -6_000.0, 0, Instant.now());
        assertEquals(Set.of(RiskRule.DAILY_MAX_LOSS.name()),
                rules(manager.evaluate(context(order(10), snapshot, 150.0, 1_500.0))));
    }

    @Test
    void flagsPriceDeviationBeyondLimit() {
        DefaultRiskManager manager = new DefaultRiskManager(CONFIG);
        Order far = Order.pending(AAPL, Side.BUY, 10, 200.0, Instant.now());
        assertEquals(Set.of(RiskRule.PRICE_DEVIATION.name()),
                rules(manager.evaluate(context(far, null, 150.0, 1_500.0))));
    }

    @Test
    void duplicateOrderWithinWindowIsRejectedWithReason() {
        DefaultRiskManager manager = new DefaultRiskManager(CONFIG);
        assertTrue(manager.evaluate(context(order(10), null, 150.0, 1_500.0)).isEmpty());

        List<RiskAlert> duplicate = manager.evaluate(context(order(10), null, 150.0, 1_500.0));
        assertEquals(Set.of(RiskRule.DUPLICATE_ORDER.name()), rules(duplicate));
        assertTrue(duplicate.get(0).message().contains("identical order"));
        assertEquals(AlertSeverity.CRITICAL, duplicate.get(0).severity());
    }

    @Test
    void duplicateWindowExpires() throws InterruptedException {
        DefaultRiskManager manager = new DefaultRiskManager(
                new RiskConfiguration(1_000L, 100_000.0, 5_000.0, 0.05, 40L));
        manager.evaluate(context(order(10), null, 150.0, 1_500.0));
        Thread.sleep(80);
        assertTrue(manager.evaluate(context(order(10), null, 150.0, 1_500.0)).isEmpty());
    }

    @Test
    void limitOrderWithValidPriceIsAccepted() {
        DefaultRiskManager manager = new DefaultRiskManager(CONFIG);
        Order limit = new Order(UUID.randomUUID(), AAPL, Side.BUY, OrderType.LIMIT,
                10, 150.5, OrderStatus.PENDING, null, Instant.now(), Instant.now());
        assertTrue(manager.evaluate(context(limit, null, 150.0, 1_505.0)).isEmpty());
    }
}
