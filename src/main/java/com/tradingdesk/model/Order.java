package com.tradingdesk.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Order(
        UUID id,
        Instrument instrument,
        Side side,
        OrderType type,
        long quantity,
        double requestedPrice,
        OrderStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (type == OrderType.LIMIT && requestedPrice <= 0.0) {
            throw new IllegalArgumentException("limit orders require a positive requested price");
        }
        if (status == OrderStatus.REJECTED && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("rejected orders require a rejection reason");
        }
    }

    public static Order pending(Instrument instrument, Side side, long quantity,
                                double requestedPrice, Instant createdAt) {
        return new Order(UUID.randomUUID(), instrument, side, OrderType.MARKET,
                quantity, requestedPrice, OrderStatus.PENDING, null, createdAt, createdAt);
    }

    public Order transitionTo(OrderStatus newStatus, String reason, Instant when) {
        return new Order(id, instrument, side, type, quantity, requestedPrice, newStatus, reason, createdAt, when);
    }

    public double notional(double marketPrice) {
        double reference = type == OrderType.LIMIT ? requestedPrice : marketPrice;
        return reference * quantity;
    }
}
