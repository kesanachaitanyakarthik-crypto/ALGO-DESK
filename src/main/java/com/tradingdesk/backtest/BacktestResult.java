package com.tradingdesk.backtest;

import java.util.List;

/**
 * The outcome of one backtest run: the equity curve and every trade, plus the derived metrics.
 */
public record BacktestResult(
        String strategyId,
        String symbol,
        List<Double> equityCurve,
        List<BacktestTrade> trades,
        BacktestMetrics metrics
) {

    public BacktestResult {
        equityCurve = List.copyOf(equityCurve);
        trades = List.copyOf(trades);
    }

    public boolean hasTrades() {
        return !trades.isEmpty();
    }
}
