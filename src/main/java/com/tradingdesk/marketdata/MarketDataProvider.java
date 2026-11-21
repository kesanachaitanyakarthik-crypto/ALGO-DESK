package com.tradingdesk.marketdata;

import java.util.List;

/**
 * Provider-independent market data contract.
 *
 * <p>Implementations differ in what they support (a simulator has no order book; a REST provider may
 * have no streaming feed), so {@link #capabilities()} lets callers adapt rather than assume.
 */
public interface MarketDataProvider extends AutoCloseable {

    /** Stable provider identifier. */
    String name();

    /** Provenance of everything this provider returns. */
    ProviderKind kind();

    /** What this provider can actually do. */
    Capabilities capabilities();

    /** Establishes any required connection. Returns true on success. */
    boolean connect();

    void disconnect();

    boolean isConnected();

    /** Current health and last-update information. */
    MarketDataStatus status();

    /** Latest top-of-book quote, or empty when unavailable. */
    java.util.Optional<Quote> latest(String symbol);

    /**
     * Historical candles for a symbol.
     *
     * @param symbol   provider-native symbol
     * @param interval provider interval code, e.g. {@code 1h}
     * @param limit    maximum candles to return
     */
    List<Candle> history(String symbol, String interval, int limit);

    @Override
    default void close() {
        disconnect();
    }

    /** Declares provider capabilities so callers can degrade gracefully. */
    record Capabilities(
            boolean quotes,
            boolean streaming,
            boolean historical,
            boolean orderBook
    ) {
        public static Capabilities quotesOnly() {
            return new Capabilities(true, false, false, false);
        }

        public static Capabilities restWithHistory() {
            return new Capabilities(true, false, true, false);
        }

        public static Capabilities fullStreaming() {
            return new Capabilities(true, true, true, false);
        }
    }
}
