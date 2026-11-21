package com.tradingdesk.marketdata.ws;

import java.time.Instant;
import java.util.Objects;

/**
 * One decoded streaming market message.
 *
 * <p>Timestamps come from the exchange event time, never the local clock, so ordering and duplicate
 * detection are consistent with the venue even under network jitter.
 *
 * @param symbol      provider-native symbol, e.g. {@code BTCUSDT}
 * @param eventTime   exchange event time
 * @param kind        trade or book update
 * @param price       last traded price (or mid for a book update)
 * @param bid         best bid, or 0 when not applicable
 * @param ask         best ask, or 0 when not applicable
 * @param quantity    trade size
 * @param tradeId     exchange trade id, used for duplicate suppression; 0 when not applicable
 */
public record StreamMessage(
        String symbol,
        Instant eventTime,
        Kind kind,
        double price,
        double bid,
        double ask,
        double quantity,
        long tradeId
) {

    public enum Kind {
        TRADE,
        BOOK_TICKER
    }

    public StreamMessage {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(eventTime, "eventTime");
        Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(price) || price < 0.0) {
            throw new IllegalArgumentException("price must be finite and non-negative");
        }
        if (quantity < 0.0 || !Double.isFinite(quantity)) {
            throw new IllegalArgumentException("quantity must be finite and non-negative");
        }
    }

    public double spread() {
        return (bid > 0.0 && ask > 0.0) ? ask - bid : 0.0;
    }

    public double mid() {
        return (bid > 0.0 && ask > 0.0) ? (bid + ask) / 2.0 : price;
    }

    /**
     * A stable key for duplicate suppression: the trade id for trades, or the symbol plus event time
     * for book updates (which have no id).
     */
    public String dedupeKey() {
        return kind == Kind.TRADE
                ? symbol + ":trade:" + tradeId
                : symbol + ":book:" + eventTime.toEpochMilli();
    }
}
