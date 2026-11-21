package com.tradingdesk.exchange;

import java.time.Instant;
import java.util.Objects;

/** An open position as reported by the exchange. */
public record ExchangePosition(String symbol, double quantity, double entryPrice, Instant openedAt) {

    public ExchangePosition {
        Objects.requireNonNull(symbol, "symbol");
        if (entryPrice < 0.0) {
            throw new IllegalArgumentException("entryPrice must not be negative");
        }
    }

    public boolean isLong() {
        return quantity > 0;
    }

    public double notional(double markPrice) {
        return Math.abs(quantity) * markPrice;
    }
}
