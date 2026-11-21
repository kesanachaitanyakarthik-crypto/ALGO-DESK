package com.tradingdesk.model;

import java.util.Objects;

/**
 * A holding in one instrument. Quantity is signed: positive is long, negative is short.
 */
public record Position(Instrument instrument, long quantity, double averagePrice) {

    public Position {
        Objects.requireNonNull(instrument, "instrument");
        if (averagePrice < 0.0) {
            throw new IllegalArgumentException("averagePrice must not be negative");
        }
        if (quantity == 0L && averagePrice != 0.0) {
            throw new IllegalArgumentException("a flat position must have a zero average price");
        }
    }

    public static Position flat(Instrument instrument) {
        return new Position(instrument, 0L, 0.0);
    }

    public boolean isFlat() {
        return quantity == 0L;
    }

    public boolean isShort() {
        return quantity < 0L;
    }

    public long absoluteQuantity() {
        return Math.abs(quantity);
    }

    public double marketValue(double currentPrice) {
        return quantity * currentPrice;
    }

    public double unrealizedProfitLoss(double currentPrice) {
        return (currentPrice - averagePrice) * quantity;
    }
}
