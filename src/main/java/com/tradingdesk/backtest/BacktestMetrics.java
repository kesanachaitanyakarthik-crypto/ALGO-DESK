package com.tradingdesk.backtest;

import java.util.List;
import java.util.Locale;

/**
 * Performance summary of a backtest run.
 *
 * <p>Every metric is computed from the simulated equity curve and trade list — none is estimated or
 * assumed. Sharpe is computed from per-bar returns and annualised by the square root of the number
 * of bars per year supplied at construction, so the caller controls the convention.
 */
public record BacktestMetrics(
        double initialCapital,
        double finalEquity,
        double totalReturnPercent,
        int tradeCount,
        int winningTrades,
        int losingTrades,
        double winRatePercent,
        double maxDrawdownPercent,
        double profitFactor,
        double sharpeRatio,
        double averageWin,
        double averageLoss,
        double averageTrade
) {

    public static BacktestMetrics from(double initialCapital,
                                       List<Double> equityCurve,
                                       List<BacktestTrade> trades,
                                       double barsPerYear) {
        double finalEquity = equityCurve.isEmpty()
                ? initialCapital
                : equityCurve.get(equityCurve.size() - 1);
        double totalReturn = initialCapital == 0.0
                ? 0.0
                : (finalEquity - initialCapital) / initialCapital * 100.0;

        int wins = 0;
        int losses = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;
        double sum = 0.0;
        for (BacktestTrade trade : trades) {
            sum += trade.pnl();
            if (trade.pnl() > 0) {
                wins++;
                grossProfit += trade.pnl();
            } else if (trade.pnl() < 0) {
                losses++;
                grossLoss += -trade.pnl();
            }
        }

        int count = trades.size();
        double winRate = count == 0 ? 0.0 : (double) wins / count * 100.0;
        // A run with no losing trades has an undefined (infinite) profit factor; report a sentinel.
        double profitFactor = grossLoss == 0.0
                ? (grossProfit > 0 ? Double.POSITIVE_INFINITY : 0.0)
                : grossProfit / grossLoss;

        return new BacktestMetrics(
                initialCapital,
                finalEquity,
                round(totalReturn),
                count,
                wins,
                losses,
                round(winRate),
                round(maxDrawdownPercent(equityCurve)),
                Double.isInfinite(profitFactor) ? profitFactor : round(profitFactor),
                round(sharpe(equityCurve, barsPerYear)),
                round(wins == 0 ? 0.0 : grossProfit / wins),
                round(losses == 0 ? 0.0 : -grossLoss / losses),
                round(count == 0 ? 0.0 : sum / count));
    }

    /** Largest peak-to-trough decline of the equity curve, as a positive percentage. */
    public static double maxDrawdownPercent(List<Double> equityCurve) {
        if (equityCurve.isEmpty()) {
            return 0.0;
        }
        double peak = equityCurve.get(0);
        double worst = 0.0;
        for (double equity : equityCurve) {
            peak = Math.max(peak, equity);
            if (peak > 0.0) {
                double drawdown = (peak - equity) / peak * 100.0;
                worst = Math.max(worst, drawdown);
            }
        }
        return worst;
    }

    /**
     * Annualised Sharpe ratio of per-bar equity returns, with a zero risk-free rate.
     *
     * <p>Returns 0 when there are fewer than two observations or when variance is zero, rather than
     * producing NaN or infinity that would poison a comparison table.
     */
    public static double sharpe(List<Double> equityCurve, double barsPerYear) {
        if (equityCurve.size() < 3 || barsPerYear <= 0.0) {
            return 0.0;
        }
        double[] returns = new double[equityCurve.size() - 1];
        for (int i = 1; i < equityCurve.size(); i++) {
            double previous = equityCurve.get(i - 1);
            returns[i - 1] = previous == 0.0 ? 0.0 : (equityCurve.get(i) - previous) / previous;
        }

        double mean = 0.0;
        for (double r : returns) {
            mean += r;
        }
        mean /= returns.length;

        double variance = 0.0;
        for (double r : returns) {
            variance += (r - mean) * (r - mean);
        }
        variance /= returns.length;
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0.0) {
            return 0.0;
        }
        return mean / stdDev * Math.sqrt(barsPerYear);
    }

    public boolean beatBuyAndHold(double buyAndHoldReturnPercent) {
        return totalReturnPercent > buyAndHoldReturnPercent;
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "return %.2f%% | trades %d | win %.2f%% | maxDD %.2f%% | PF %s | sharpe %.2f",
                totalReturnPercent, tradeCount, winRatePercent, maxDrawdownPercent,
                Double.isInfinite(profitFactor) ? "inf" : String.format(Locale.ROOT, "%.2f", profitFactor),
                sharpeRatio);
    }

    private static double round(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
