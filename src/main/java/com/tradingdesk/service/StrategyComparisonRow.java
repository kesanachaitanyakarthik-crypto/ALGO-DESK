package com.tradingdesk.service;

/**
 * One row of the strategy backtest comparison, pre-formatted from measured metrics.
 *
 * @param strategy     strategy display name
 * @param totalReturn  total return over the window
 * @param winRate      percentage of winning trades
 * @param maxDrawdown  largest peak-to-trough decline
 * @param profitFactor gross profit divided by gross loss
 * @param sharpe       annualised Sharpe ratio
 * @param trades       number of round trips
 */
public record StrategyComparisonRow(
        String strategy,
        String totalReturn,
        String winRate,
        String maxDrawdown,
        String profitFactor,
        String sharpe,
        String trades
) {
}
