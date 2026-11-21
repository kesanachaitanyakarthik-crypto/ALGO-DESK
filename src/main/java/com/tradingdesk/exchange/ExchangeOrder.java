package com.tradingdesk.exchange;

import java.time.Instant;
import java.util.Objects;

/** An order as understood by the exchange layer, independent of any specific venue. */
public record ExchangeOrder(
        String clientOrderId,
        String exchangeOrderId,
        String symbol,
        String side,
        String type,
        double quantity,
        double price,
        String status,
        double filledQuantity,
        Instant createdAt
) {

    public ExchangeOrder {
        Objects.requireNonNull(clientOrderId, "clientOrderId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(status, "status");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    /** A requested order, before the exchange has assigned its own id. */
    public static ExchangeOrder request(String symbol, String side, String type,
                                        double quantity, double price) {
        return new ExchangeOrder(java.util.UUID.randomUUID().toString(), null, symbol,
                side, type, quantity, price, "NEW", 0.0, Instant.now());
    }

    public boolean isFilled() {
        return "FILLED".equalsIgnoreCase(status) || "EXECUTED".equalsIgnoreCase(status);
    }

    public boolean isTerminal() {
        return isFilled() || "CANCELLED".equalsIgnoreCase(status)
                || "REJECTED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status);
    }

    public double notional() {
        return quantity * price;
    }
}
