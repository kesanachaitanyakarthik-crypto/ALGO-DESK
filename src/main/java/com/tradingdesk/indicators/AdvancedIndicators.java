package com.tradingdesk.indicators;

import com.tradingdesk.model.PriceBar;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced technical indicators: RSI, MACD, Bollinger Bands, ATR, Stochastic Oscillator and VWAP.
 *
 * <p>Kept in a separate class from {@link TechnicalIndicators} so the original, already-tested API
 * stays byte-for-byte stable while the platform grows.
 *
 * <p>All methods are pure functions over the supplied series and use only data up to and including
 * the requested index, which is what makes them safe to use in a backtest without look-ahead bias.
 */
public final class AdvancedIndicators {

    private AdvancedIndicators() {
    }

    /** A MACD result: the MACD line, its signal line, and the histogram between them. */
    public record Macd(double macd, double signal, double histogram) {
    }

    /** A Bollinger Bands result: middle (SMA), upper and lower bands. */
    public record BollingerBands(double middle, double upper, double lower) {
        public double width() {
            return upper - lower;
        }
    }

    /** A Stochastic Oscillator result: %K and its smoothed %D line. */
    public record Stochastic(double k, double d) {
    }

    /**
     * Relative Strength Index using Wilder's smoothing.
     *
     * <p>Seeds the average gain/loss from the first {@code period} changes, then smooths each
     * subsequent change by {@code (n-1)/n}. Returns 100 when there are no losses (a monotonic rise)
     * and 0 when there are no gains (a monotonic fall) — the mathematically correct limits.
     *
     * @param prices closing prices, oldest first
     * @param period lookback, typically 14
     */
    public static double rsi(List<Double> prices, int period) {
        requireAtLeast(prices, period + 1, "RSI");
        double gainSum = 0.0;
        double lossSum = 0.0;

        for (int i = 1; i <= period; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                gainSum += change;
            } else {
                lossSum -= change;
            }
        }
        double averageGain = gainSum / period;
        double averageLoss = lossSum / period;

        for (int i = period + 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            double gain = Math.max(0.0, change);
            double loss = Math.max(0.0, -change);
            averageGain = ((averageGain * (period - 1)) + gain) / period;
            averageLoss = ((averageLoss * (period - 1)) + loss) / period;
        }

        if (averageLoss == 0.0) {
            return averageGain == 0.0 ? 50.0 : 100.0;
        }
        double relativeStrength = averageGain / averageLoss;
        return 100.0 - (100.0 / (1.0 + relativeStrength));
    }

    /**
     * MACD: the difference between a fast and slow EMA, plus a signal EMA of that line.
     *
     * <p>Returns zeros when there is insufficient history for the signal period, which lets callers
     * treat "not enough data" as a neutral reading rather than throwing mid-pipeline.
     */
    public static Macd macd(List<Double> prices, int fastPeriod, int slowPeriod, int signalPeriod) {
        if (prices == null || prices.size() < slowPeriod) {
            return new Macd(0.0, 0.0, 0.0);
        }
        List<Double> macdSeries = new ArrayList<>();
        for (int i = slowPeriod; i <= prices.size(); i++) {
            List<Double> window = prices.subList(0, i);
            double fast = TechnicalIndicators.exponentialMovingAverage(window, fastPeriod);
            double slow = TechnicalIndicators.exponentialMovingAverage(window, slowPeriod);
            macdSeries.add(fast - slow);
        }
        double macdLine = macdSeries.get(macdSeries.size() - 1);
        if (macdSeries.size() < signalPeriod) {
            return new Macd(macdLine, 0.0, macdLine);
        }
        double signalLine = TechnicalIndicators.exponentialMovingAverage(macdSeries, signalPeriod);
        return new Macd(macdLine, signalLine, macdLine - signalLine);
    }

    /**
     * Bollinger Bands: an SMA middle band with upper/lower bands {@code k} standard deviations away.
     * Uses the population standard deviation over the window, matching the existing
     * {@link TechnicalIndicators#rollingVolatility}.
     */
    public static BollingerBands bollingerBands(List<Double> prices, int period, double deviations) {
        requireAtLeast(prices, period, "Bollinger Bands");
        if (deviations <= 0.0) {
            throw new IllegalArgumentException("deviations must be positive");
        }
        double middle = TechnicalIndicators.simpleMovingAverage(prices, period);
        double standardDeviation = TechnicalIndicators.rollingVolatility(prices, period);
        double offset = standardDeviation * deviations;
        return new BollingerBands(middle, middle + offset, middle - offset);
    }

    /**
     * Average True Range using Wilder's smoothing.
     *
     * <p>The true range is the greatest of: high-low, |high - previous close|, |low - previous close|.
     */
    public static double atr(List<PriceBar> bars, int period) {
        if (bars == null) {
            throw new IllegalArgumentException("bars must not be null");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        if (bars.size() < period + 1) {
            throw new IllegalArgumentException(
                    "ATR(" + period + ") needs at least " + (period + 1) + " bars, got " + bars.size());
        }

        double sum = 0.0;
        for (int i = 1; i <= period; i++) {
            sum += trueRange(bars, i);
        }
        double average = sum / period;

        for (int i = period + 1; i < bars.size(); i++) {
            average = ((average * (period - 1)) + trueRange(bars, i)) / period;
        }
        return average;
    }

    private static double trueRange(List<PriceBar> bars, int index) {
        PriceBar current = bars.get(index);
        double previousClose = bars.get(index - 1).close();
        return Math.max(current.high() - current.low(),
                Math.max(Math.abs(current.high() - previousClose),
                        Math.abs(current.low() - previousClose)));
    }

    /**
     * Stochastic Oscillator over {@code period} bars: %K is the close's position within the high-low
     * range, and %D is a {@code smoothing} period SMA of %K.
     */
    public static Stochastic stochastic(List<PriceBar> bars, int period, int smoothing) {
        if (bars == null) {
            throw new IllegalArgumentException("bars must not be null");
        }
        if (period <= 0 || smoothing <= 0) {
            throw new IllegalArgumentException("period and smoothing must be positive");
        }
        if (bars.size() < period) {
            throw new IllegalArgumentException(
                    "Stochastic(" + period + ") needs at least " + period + " bars, got " + bars.size());
        }

        List<Double> kValues = new ArrayList<>();
        for (int end = period; end <= bars.size(); end++) {
            int start = end - period;
            double highest = Double.NEGATIVE_INFINITY;
            double lowest = Double.POSITIVE_INFINITY;
            for (int i = start; i < end; i++) {
                highest = Math.max(highest, bars.get(i).high());
                lowest = Math.min(lowest, bars.get(i).low());
            }
            double close = bars.get(end - 1).close();
            double range = highest - lowest;
            kValues.add(range == 0.0 ? 50.0 : (close - lowest) / range * 100.0);
        }

        double latestK = kValues.get(kValues.size() - 1);
        double latestD = kValues.size() >= smoothing
                ? TechnicalIndicators.simpleMovingAverage(kValues, smoothing)
                : latestK;
        return new Stochastic(latestK, latestD);
    }

    /**
     * Volume-Weighted Average Price over the supplied bars.
     *
     * <p>Uses the typical price {@code (high + low + close) / 3} for each bar, which is the standard
     * intraday definition. Falls back to the simple average price when total volume is zero.
     */
    public static double vwap(List<PriceBar> bars) {
        if (bars == null || bars.isEmpty()) {
            throw new IllegalArgumentException("bars must not be empty");
        }
        double cumulativePriceVolume = 0.0;
        double cumulativeVolume = 0.0;
        double priceSum = 0.0;

        for (PriceBar bar : bars) {
            double typicalPrice = (bar.high() + bar.low() + bar.close()) / 3.0;
            priceSum += typicalPrice;
            cumulativePriceVolume += typicalPrice * bar.volume();
            cumulativeVolume += bar.volume();
        }

        if (cumulativeVolume == 0.0) {
            return priceSum / bars.size();
        }
        return cumulativePriceVolume / cumulativeVolume;
    }

    /** Convenience overload operating on closes for indicators that only need prices. */
    public static List<Double> closes(List<PriceBar> bars) {
        List<Double> closes = new ArrayList<>(bars.size());
        for (PriceBar bar : bars) {
            closes.add(bar.close());
        }
        return closes;
    }

    private static void requireAtLeast(List<Double> prices, int required, String name) {
        if (prices == null) {
            throw new IllegalArgumentException("prices must not be null");
        }
        if (prices.size() < required) {
            throw new IllegalArgumentException(
                    name + " needs at least " + required + " prices, got " + prices.size());
        }
    }
}
