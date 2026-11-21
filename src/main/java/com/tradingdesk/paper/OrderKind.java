package com.tradingdesk.paper;

/**
 * The kind of order a paper trade represents.
 *
 * <p>Kept separate from the legacy {@code model.OrderType} (MARKET/LIMIT) so the existing automated
 * signal path is untouched while the paper engine supports the richer order set.
 */
public enum OrderKind {

    /** Execute immediately at the best available price. */
    MARKET,

    /** Execute only at {@code limitPrice} or better. */
    LIMIT,

    /** Becomes a market order once {@code stopPrice} is touched. */
    STOP,

    /** Becomes a limit order at {@code limitPrice} once {@code stopPrice} is touched. */
    STOP_LIMIT,

    /** Exit-only order that triggers a close once {@code takeProfitPrice} is touched. */
    TAKE_PROFIT;

    public boolean requiresLimitPrice() {
        return this == LIMIT || this == STOP_LIMIT;
    }

    public boolean requiresStopPrice() {
        return this == STOP || this == STOP_LIMIT;
    }

    public boolean requiresTakeProfitPrice() {
        return this == TAKE_PROFIT;
    }

    /** True for order kinds that wait for a trigger price before becoming executable. */
    public boolean isTriggered() {
        return this == STOP || this == STOP_LIMIT || this == TAKE_PROFIT;
    }
}
