package com.tradingdesk.exchange;

/**
 * Whether the application is operating against simulated money or a real account.
 *
 * <p>{@link #PAPER} is the default and the only mode reachable without explicit, multi-step
 * authorization. There is deliberately no constructor or setter that flips this to LIVE as a side
 * effect of anything else.
 */
public enum TradingMode {
    PAPER("Paper trading — simulated funds"),
    LIVE("LIVE trading — real funds");

    private final String description;

    TradingMode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public boolean isLive() {
        return this == LIVE;
    }
}
