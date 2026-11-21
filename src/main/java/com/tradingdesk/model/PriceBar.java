package com.tradingdesk.model;

import java.time.Instant;
import java.util.Objects;

public record PriceBar(
        Instrument instrument,
        double open,
        double high,
        double low,
        double close,
        long volume,
        Instant openedAt
) {

    public PriceBar {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(openedAt, "openedAt");
        if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) {
            throw new IllegalArgumentException("prices must be positive");
        }
        if (high < low) {
            throw new IllegalArgumentException("high must not be below low");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }
}
