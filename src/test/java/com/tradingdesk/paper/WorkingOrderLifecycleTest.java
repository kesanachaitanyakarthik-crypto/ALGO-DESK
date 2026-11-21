package com.tradingdesk.paper;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.Side;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkingOrderLifecycleTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static WorkingOrder openLimit(long quantity, double price) {
        return WorkingOrder.open(
                OrderRequest.limit(AAPL, Side.BUY, quantity, price), NOW);
    }

    @Test
    void newOrderStartsInNewStatus() {
        WorkingOrder order = openLimit(100, 190.0);
        assertEquals(OrderStatus.NEW, order.status());
        assertEquals(0, order.filledQuantity());
        assertEquals(100, order.remainingQuantity());
        assertFalse(order.isFilled());
    }

    @Test
    void legalProgressionReachesFilled() {
        WorkingOrder order = openLimit(100, 190.0).markOpen(NOW);
        assertEquals(OrderStatus.OPEN, order.status());

        WorkingOrder partial = order.withFill(new OrderFill(40, 190.0, 0.0, 0.0, NOW));
        assertEquals(OrderStatus.PARTIALLY_FILLED, partial.status());
        assertEquals(40, partial.filledQuantity());
        assertEquals(60, partial.remainingQuantity());

        WorkingOrder full = partial.withFill(new OrderFill(60, 191.0, 0.0, 0.0, NOW));
        assertEquals(OrderStatus.FILLED, full.status());
        assertTrue(full.isFilled());
        assertEquals(0, full.remainingQuantity());
    }

    @Test
    void averageFillPriceIsWeightedAcrossFills() {
        WorkingOrder order = openLimit(100, 190.0).markOpen(NOW)
                .withFill(new OrderFill(40, 100.0, 0.0, 0.0, NOW))
                .withFill(new OrderFill(60, 200.0, 0.0, 0.0, NOW));

        assertEquals(160.0, order.averagePrice(), 1e-9);
    }

    @Test
    void feesAccumulateAcrossFills() {
        WorkingOrder order = openLimit(100, 190.0).markOpen(NOW)
                .withFill(new OrderFill(50, 100.0, 1.5, 0.0, NOW))
                .withFill(new OrderFill(50, 100.0, 2.5, 0.0, NOW));

        assertEquals(4.0, order.feesPaid(), 1e-9);
    }

    @Test
    void overFillingIsRejected() {
        WorkingOrder order = openLimit(100, 190.0).markOpen(NOW);
        assertThrows(IllegalArgumentException.class,
                () -> order.withFill(new OrderFill(101, 190.0, 0.0, 0.0, NOW)));
    }

    @Test
    void illegalTransitionIsRejected() {
        WorkingOrder filled = openLimit(10, 190.0).markOpen(NOW)
                .withFill(new OrderFill(10, 190.0, 0.0, 0.0, NOW));
        assertEquals(OrderStatus.FILLED, filled.status());

        // A completed order must never be reopened.
        assertThrows(IllegalStateException.class, () -> filled.markOpen(NOW));
        assertThrows(IllegalStateException.class, () -> filled.withFill(
                new OrderFill(1, 190.0, 0.0, 0.0, NOW)));
    }

    @Test
    void cancelledOrderCannotBeFilled() {
        WorkingOrder cancelled = openLimit(10, 190.0).markOpen(NOW).cancelled(NOW);
        assertEquals(OrderStatus.CANCELLED, cancelled.status());
        assertThrows(IllegalStateException.class, () -> cancelled.withFill(
                new OrderFill(1, 190.0, 0.0, 0.0, NOW)));
    }

    @Test
    void rejectedOrderRequiresReason() {
        WorkingOrder order = openLimit(10, 190.0);
        assertThrows(IllegalArgumentException.class, () -> order.reject("", NOW),
                "an empty rejection reason must be refused");
    }

    @Test
    void modifyIsRefusedOncePartiallyFilled() {
        WorkingOrder partial = openLimit(100, 190.0).markOpen(NOW)
                .withFill(new OrderFill(10, 190.0, 0.0, 0.0, NOW));

        assertThrows(IllegalStateException.class,
                () -> partial.modify(OrderRequest.limit(AAPL, Side.BUY, 50, 180.0), NOW));
    }

    @Test
    void lifecycleGuardKnowsLegalPairs() {
        assertTrue(OrderLifecycle.isLegal(OrderStatus.NEW, OrderStatus.OPEN));
        assertTrue(OrderLifecycle.isLegal(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED));
        assertTrue(OrderLifecycle.isLegal(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED));
        assertTrue(OrderLifecycle.isLegal(OrderStatus.OPEN, OrderStatus.CANCELLED));

        assertFalse(OrderLifecycle.isLegal(OrderStatus.FILLED, OrderStatus.OPEN));
        assertFalse(OrderLifecycle.isLegal(OrderStatus.CANCELLED, OrderStatus.OPEN));
        assertFalse(OrderLifecycle.isLegal(OrderStatus.REJECTED, OrderStatus.FILLED));
    }
}
