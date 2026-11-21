package com.tradingdesk.marketdata;

import java.time.Instant;
import java.util.Objects;

/**
 * A single OHLCV candle.
 *
 * <p>All fields are validated: an OHLC bar whose high is below its low, or with non-positive prices,
 * is rejected rather than propagated into indicators.
 */
public record Candle(
        String symbol,
        Instant openTime,
        double open,
        double high,
        double low,
        double close,
        double volume
) {

    public Candle {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(openTime, "openTime");
        if (!Double.isFinite(open) || !Double.isFinite(high)
                || !Double.isFinite(low) || !Double.isFinite(close)) {
            throw new IllegalArgumentException("candle prices must be finite");
        }
        if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) {
            throw new IllegalArgumentException("candle prices must be positive");
        }
        if (high < low) {
            throw new IllegalArgumentException("high (" + high + ") must not be below low (" + low + ")");
        }
        if (volume < 0.0 || !Double.isFinite(volume)) {
            throw new IllegalArgumentException("volume must be a finite non-negative number");
        }
    }

    /** True when the supplied price lies within this candle's range. */
    public boolean contains(double price) {
        return price >= low && price <= high;
    }

    public double typicalPrice() {
        return (high + low + close) / 3.0;
    }
}
