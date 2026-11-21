package com.tradingdesk.indicators;

import java.util.List;

public final class TechnicalIndicators {

    private TechnicalIndicators() {
    }

    public static double simpleMovingAverage(List<Double> prices, int period) {
        requireEnough(prices, period);
        double sum = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }

    public static double exponentialMovingAverage(List<Double> prices, int period) {
        requireEnough(prices, period);
        double multiplier = 2.0 / (period + 1.0);
        int start = prices.size() - period;
        double ema = simpleMovingAverage(prices.subList(start, prices.size()), period);
        for (int i = start + 1; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }
        return ema;
    }

    public static double rateOfChange(List<Double> prices, int period) {
        requireEnough(prices, period + 1);
        double current = prices.get(prices.size() - 1);
        double previous = prices.get(prices.size() - 1 - period);
        if (previous == 0.0) {
            return 0.0;
        }
        return (current - previous) / previous;
    }

    public static double rollingVolatility(List<Double> prices, int period) {
        requireEnough(prices, period);
        int start = prices.size() - period;
        double mean = simpleMovingAverage(prices, period);
        double sumSquares = 0.0;
        for (int i = start; i < prices.size(); i++) {
            double deviation = prices.get(i) - mean;
            sumSquares += deviation * deviation;
        }
        return Math.sqrt(sumSquares / period);
    }

    public static double orderBookImbalance(long bidVolume, long askVolume) {
        if (bidVolume < 0 || askVolume < 0) {
            throw new IllegalArgumentException("volumes must not be negative");
        }
        long total = bidVolume + askVolume;
        if (total == 0L) {
            return 0.0;
        }
        return (double) (bidVolume - askVolume) / total;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void requireEnough(List<Double> prices, int period) {
        if (prices == null) {
            throw new IllegalArgumentException("prices must not be null");
        }
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        if (prices.size() < period) {
            throw new IllegalArgumentException(
                    "need at least " + period + " prices but received " + prices.size());
        }
        if (prices.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("prices must be finite numbers");
        }
    }
}
