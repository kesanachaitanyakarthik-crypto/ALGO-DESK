package com.tradingdesk.paper;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.Side;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A live paper order and its accumulated executions.
 *
 * <p>Immutable: every state change returns a new instance, so a listener can never observe a
 * half-updated order. {@link #withFill(OrderFill)} enforces that filled quantity never exceeds the
 * requested quantity.
 */
public record WorkingOrder(
        UUID id,
        OrderRequest request,
        OrderStatus status,
        long filledQuantity,
        double averageFillPrice,
        double feesPaid,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        List<OrderFill> fills
) {

    public WorkingOrder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        fills = List.copyOf(fills);
        if (filledQuantity < 0) {
            throw new IllegalArgumentException("filledQuantity must not be negative");
        }
        if (filledQuantity > request.quantity()) {
            throw new IllegalArgumentException("filledQuantity must not exceed requested quantity");
        }
        if (status == OrderStatus.REJECTED && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("rejected orders require a reason");
        }
    }

    /** Opens a new order in {@link OrderStatus#NEW}. */
    public static WorkingOrder open(OrderRequest request, Instant now) {
        return new WorkingOrder(UUID.randomUUID(), request, OrderStatus.NEW,
                0L, 0.0, 0.0, null, now, now, List.of());
    }

    public Instrument instrument() {
        return request.instrument();
    }

    public Side side() {
        return request.side();
    }

    public long quantity() {
        return request.quantity();
    }

    public long remainingQuantity() {
        return request.quantity() - filledQuantity;
    }

    public boolean isFilled() {
        return filledQuantity >= request.quantity();
    }

    /** The average price actually paid across all fills, or 0 when nothing has filled. */
    public double averagePrice() {
        return averageFillPrice;
    }

    /** Moves to a new status through the lifecycle guard. */
    WorkingOrder transition(OrderStatus newStatus, String reason, Instant when) {
        OrderLifecycle.requireLegal(status, newStatus, reason);
        return new WorkingOrder(id, request, newStatus, filledQuantity, averageFillPrice,
                feesPaid, reason, createdAt, when, fills);
    }

    /**
     * Applies an execution: accumulates quantity, recomputes the weighted average price, and moves
     * to {@link OrderStatus#PARTIALLY_FILLED} or {@link OrderStatus#FILLED}.
     */
    public WorkingOrder withFill(OrderFill fill) {
        OrderLifecycle.requireFillable(status);
        if (fill.quantity() > remainingQuantity()) {
            throw new IllegalArgumentException("fill quantity " + fill.quantity()
                    + " exceeds remaining " + remainingQuantity());
        }
        long newFilled = filledQuantity + fill.quantity();
        double newAverage = ((averageFillPrice * filledQuantity) + (fill.price() * fill.quantity())) / newFilled;

        List<OrderFill> newFills = new ArrayList<>(fills);
        newFills.add(fill);

        OrderStatus newStatus = newFilled >= request.quantity()
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;

        return new WorkingOrder(id, request, newStatus, newFilled, newAverage,
                feesPaid + fill.fee(), rejectionReason, createdAt, fill.filledAt(), newFills);
    }

    public WorkingOrder reject(String reason, Instant when) {
        return transition(OrderStatus.REJECTED, reason, when);
    }

    public WorkingOrder markOpen(Instant when) {
        return transition(OrderStatus.OPEN, null, when);
    }

    public WorkingOrder cancelled(Instant when) {
        return transition(OrderStatus.CANCELLED, null, when);
    }

    /** Order modification: replaces the request while preserving fills and identity. */
    public WorkingOrder modify(OrderRequest newRequest, Instant when) {
        if (filledQuantity > 0) {
            throw new IllegalStateException("cannot modify a partially filled order");
        }
        return new WorkingOrder(id, newRequest, status, filledQuantity, averageFillPrice,
                feesPaid, rejectionReason, createdAt, when, fills);
    }
}
