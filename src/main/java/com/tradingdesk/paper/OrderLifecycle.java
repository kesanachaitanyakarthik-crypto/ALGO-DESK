package com.tradingdesk.paper;

import com.tradingdesk.model.OrderStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The legal order-status transitions for the paper engine.
 *
 * <pre>
 *   NEW ──► OPEN ──► PARTIALLY_FILLED ──► FILLED
 *    │       │               │
 *    │       └───────────────┴──────────► CANCELLED
 *    └──────────────────────────────────► REJECTED
 * </pre>
 *
 * Anything else (for example {@code FILLED → OPEN}, or reopening a {@code CANCELLED} order) is
 * rejected loudly, so a bug cannot silently resurrect a completed order.
 */
public final class OrderLifecycle {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.NEW, EnumSet.of(OrderStatus.OPEN, OrderStatus.REJECTED,
                OrderStatus.CANCELLED, OrderStatus.FILLED));
        ALLOWED.put(OrderStatus.OPEN, EnumSet.of(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED,
                OrderStatus.CANCELLED, OrderStatus.REJECTED));
        ALLOWED.put(OrderStatus.PARTIALLY_FILLED, EnumSet.of(OrderStatus.PARTIALLY_FILLED,
                OrderStatus.FILLED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.FILLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));

        // Legacy vocabulary reaches a terminal state only; it is never reopened.
        ALLOWED.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.APPROVED, OrderStatus.REJECTED,
                OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.APPROVED, EnumSet.of(OrderStatus.EXECUTED, OrderStatus.CANCELLED,
                OrderStatus.REJECTED));
        ALLOWED.put(OrderStatus.EXECUTED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderLifecycle() {
    }

    public static boolean isLegal(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> targets = ALLOWED.get(from);
        return targets != null && targets.contains(to);
    }

    static void requireLegal(OrderStatus from, OrderStatus to, String reason) {
        if (from == to) {
            // Idempotent re-application (e.g. another partial fill) is allowed where the target
            // set permits self-transition; otherwise treat it as a no-op-safe repeat.
            return;
        }
        if (!isLegal(from, to)) {
            throw new IllegalStateException(
                    "Illegal order transition " + from + " -> " + to
                            + (reason == null ? "" : " (" + reason + ")"));
        }
    }

    /** Only non-terminal, unfilled orders may receive executions. */
    static void requireFillable(OrderStatus status) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Cannot fill an order in status " + status);
        }
    }
}
