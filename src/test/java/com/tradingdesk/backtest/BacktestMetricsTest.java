package com.tradingdesk.backtest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestMetricsTest {

    /**
     * A long trade entered at 1000 that exits at a valid price, achieving the requested P&L.
     * The high entry price keeps the exit positive even for the larger losses used below.
     */
    private static BacktestTrade trade(double pnl) {
        double exitPrice = 1_000.0 + pnl;
        return new BacktestTrade("LONG", 1, 1_000.0, exitPrice, pnl, 0.0,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
    }

    @Test
    void maxDrawdownFindsTheDeepestPeakToTroughDecline() {
        // 100 -> 120 (peak) -> 90 (trough) = 25% drawdown.
        List<Double> curve = List.of(100.0, 120.0, 90.0, 110.0);
        assertEquals(25.0, BacktestMetrics.maxDrawdownPercent(curve), 1e-9);
    }

    @Test
    void maxDrawdownIsZeroOnAMonotonicRise() {
        assertEquals(0.0, BacktestMetrics.maxDrawdownPercent(List.of(100.0, 110.0, 120.0)), 1e-9);
    }

    @Test
    void maxDrawdownOfEmptyCurveIsZero() {
        assertEquals(0.0, BacktestMetrics.maxDrawdownPercent(List.of()), 1e-9);
    }

    @Test
    void profitFactorIsGrossProfitOverGrossLoss() {
        // wins 200 + 100 = 300; losses 100 -> PF 3.0
        List<BacktestTrade> trades = List.of(trade(200), trade(100), trade(-100));
        BacktestMetrics metrics = BacktestMetrics.from(
                1_000, List.of(1_000.0, 1_200.0), trades, 252);

        assertEquals(3.0, metrics.profitFactor(), 1e-9);
        assertEquals(2, metrics.winningTrades());
        assertEquals(1, metrics.losingTrades());
        assertEquals(66.6667, metrics.winRatePercent(), 1e-3);
    }

    @Test
    void profitFactorWithNoLossesIsInfinite() {
        BacktestMetrics metrics = BacktestMetrics.from(
                1_000, List.of(1_000.0, 1_500.0), List.of(trade(100)), 252);
        assertTrue(Double.isInfinite(metrics.profitFactor()));
    }

    @Test
    void totalReturnIsRelativeToInitialCapital() {
        BacktestMetrics metrics = BacktestMetrics.from(
                10_000, List.of(10_000.0, 11_000.0), List.of(trade(1_000)), 252);
        assertEquals(10.0, metrics.totalReturnPercent(), 1e-9);
    }

    @Test
    void averageWinAndLossAreComputedSeparately() {
        List<BacktestTrade> trades = List.of(trade(300), trade(100), trade(-50), trade(-150));
        BacktestMetrics metrics = BacktestMetrics.from(
                1_000, List.of(1_000.0, 1_200.0), trades, 252);

        assertEquals(200.0, metrics.averageWin(), 1e-9);
        assertEquals(-100.0, metrics.averageLoss(), 1e-9);
        assertEquals(50.0, metrics.averageTrade(), 1e-9);
    }

    @Test
    void sharpeIsZeroForAFlatCurve() {
        assertEquals(0.0, BacktestMetrics.sharpe(List.of(100.0, 100.0, 100.0), 252), 1e-9);
    }

    @Test
    void sharpeIsPositiveForASteadyRise() {
        List<Double> curve = List.of(100.0, 101.0, 102.0, 103.0, 104.0);
        assertTrue(BacktestMetrics.sharpe(curve, 252) > 0.0);
    }

    @Test
    void sharpeIsZeroWithTooFewPoints() {
        assertEquals(0.0, BacktestMetrics.sharpe(List.of(100.0), 252), 1e-9);
        assertEquals(0.0, BacktestMetrics.sharpe(List.of(), 252), 1e-9);
    }

    @Test
    void emptyTradeListProducesZeroCounts() {
        BacktestMetrics metrics = BacktestMetrics.from(
                1_000, List.of(1_000.0, 1_000.0), List.of(), 252);

        assertEquals(0, metrics.tradeCount());
        assertEquals(0.0, metrics.winRatePercent(), 1e-9);
        assertEquals(0.0, metrics.totalReturnPercent(), 1e-9);
    }

    @Test
    void summaryIsHumanReadable() {
        BacktestMetrics metrics = BacktestMetrics.from(
                1_000, List.of(1_000.0, 1_100.0), List.of(trade(100)), 252);
        String summary = metrics.summary();
        assertTrue(summary.contains("return"));
        assertTrue(summary.contains("trades"));
        assertTrue(summary.contains("sharpe"));
    }
}
