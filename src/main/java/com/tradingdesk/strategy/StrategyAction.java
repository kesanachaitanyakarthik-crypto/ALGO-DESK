package com.tradingdesk.strategy;

/**
 * What a strategy wants to do at a given point in the series.
 */
public enum StrategyAction {
    /** Open or add to a long position. */
    ENTER_LONG,
    /** Open or add to a short position. */
    ENTER_SHORT,
    /** Close an existing position. */
    EXIT,
    /** Do nothing. */
    HOLD
}
