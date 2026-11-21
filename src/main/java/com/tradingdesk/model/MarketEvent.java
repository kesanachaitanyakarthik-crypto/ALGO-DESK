package com.tradingdesk.model;

import java.time.Instant;
import java.util.Objects;

public record MarketEvent(
        long sequence,
        Instrument instrument,
        MarketEventType type,
        double lastPrice,
        double bidPrice,
        double askPrice,
        long volume,
        Instant timestamp
) {

    public MarketEvent {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
        if (lastPrice <= 0.0 || bidPrice <= 0.0 || askPrice <= 0.0) {
            throw new IllegalArgumentException("prices must be positive");
        }
        if (bidPrice > askPrice) {
            throw new IllegalArgumentException("bid price must not exceed ask price");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }

    public double spread() {
        return askPrice - bidPrice;
    }

    public double midPrice() {
        return (bidPrice + askPrice) / 2.0;
    }

    public int simulatedOrderBookImbalance() {
        return (int) Math.round(100.0 * (lastPrice - midPrice()) / Math.max(1e-9, spread()));
    }
}
