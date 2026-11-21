package com.tradingdesk.marketdata;

/**
 * Where a piece of market data came from and how trustworthy it is.
 *
 * <p>This exists so the UI can never mislabel synthetic or stale numbers as live market data — a
 * requirement of the brief and a real correctness concern for anyone reading the screen.
 */
public enum ProviderKind {

    /** Generated locally by the simulator. Never presented as real market data. */
    SIMULATED("Simulated", false),

    /** Real-time data from an external exchange. */
    LIVE("Live", true),

    /** Real external data that is delayed relative to the exchange. */
    DELAYED("Delayed", true),

    /** Historical candle data fetched from an external source. */
    HISTORICAL("Historical", true);

    private final String label;
    private final boolean real;

    ProviderKind(String label, boolean real) {
        this.label = label;
        this.real = real;
    }

    public String label() {
        return label;
    }

    /** True only for data that genuinely originates outside this application. */
    public boolean isReal() {
        return real;
    }

    /** The label a UI must show. Simulated data is always explicitly marked. */
    public String displayLabel() {
        return isReal() ? label : label + " (not live)";
    }
}
