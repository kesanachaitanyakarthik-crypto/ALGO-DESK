package com.tradingdesk.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Execution(UUID orderId, double price, long quantity, Instant executedAt) {

    public Execution {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(executedAt, "executedAt");
        if (price <= 0.0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
