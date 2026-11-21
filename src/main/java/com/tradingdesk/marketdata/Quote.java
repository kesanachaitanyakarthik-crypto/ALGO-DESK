package com.tradingdesk.marketdata;

import java.time.Instant;
import java.util.Objects;

/**
 * A top-of-book quote.
 *
 * @param symbol    provider-native symbol (for example {@code BTCUSDT})
 * @param bid       best bid, or 0 when unavailable
 * @param ask       best ask, or 0 when unavailable
 * @param last      last traded price, or 0 when unavailable
 * @param bidSize   quantity available at the bid
 * @param askSize   quantity available at the ask
 * @param timestamp exchange timestamp as reported by the provider
 * @param kind      provenance, so callers can label it honestly
 * @param source    provider name, for display
 */
public record Quote(
        String symbol,
        double bid,
        double ask,
        double last,
        double bidSize,
        double askSize,
        Instant timestamp,
        ProviderKind kind,
        String source
) {

    public Quote {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        if (bid < 0.0 || ask < 0.0 || last < 0.0) {
            throw new IllegalArgumentException("prices must not be negative");
        }
        if (bid > 0.0 && ask > 0.0 && bid > ask) {
            throw new IllegalArgumentException("bid must not exceed ask");
        }
    }

    public double spread() {
        return (bid > 0.0 && ask > 0.0) ? ask - bid : 0.0;
    }

    public double mid() {
        return (bid > 0.0 && ask > 0.0) ? (bid + ask) / 2.0 : last;
    }

    /** The price a buy would pay; falls back to last, then ask. */
    public double executableBuyPrice() {
        return ask > 0.0 ? ask : last;
    }

    /** The price a sell would receive; falls back to last, then bid. */
    public double executableSellPrice() {
        return bid > 0.0 ? bid : last;
    }

    public boolean hasBothSides() {
        return bid > 0.0 && ask > 0.0;
    }
}
