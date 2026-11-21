package com.tradingdesk.backtest;

import java.time.Instant;

/**
 * One completed round trip in a backtest.
 *
 * @param side      "LONG" or "SHORT"
 * @param quantity  units traded
 * @param entryPrice execution price on entry (after slippage)
 * @param exitPrice  execution price on exit (after slippage)
 * @param pnl        net profit or loss for the trade, after fees
 * @param fees       total fees paid on both sides
 * @param openedAt   entry timestamp
 * @param closedAt   exit timestamp
 */
public record BacktestTrade(
        String side,
        long quantity,
        double entryPrice,
        double exitPrice,
        double pnl,
        double fees,
        Instant openedAt,
        Instant closedAt
) {

    public BacktestTrade {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (entryPrice <= 0.0 || exitPrice <= 0.0) {
            throw new IllegalArgumentException("prices must be positive");
        }
    }

    public boolean isWin() {
        return pnl > 0.0;
    }

    /** Return on the trade's notional, as a fraction. */
    public double returnFraction() {
        double notional = entryPrice * quantity;
        return notional == 0.0 ? 0.0 : pnl / notional;
    }
}
