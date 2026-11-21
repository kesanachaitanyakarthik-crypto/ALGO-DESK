package com.tradingdesk.strategy;

/**
 * The information a strategy may act upon at one decision point.
 *
 * <p>The series is deliberately exposed only up to the current index: a strategy physically cannot
 * read a future bar, which is what prevents look-ahead bias in backtests.
 *
 * @param prices   closing prices from the start of the series through the current bar
 * @param highs    highs aligned with {@code prices}
 * @param lows     lows aligned with {@code prices}
 * @param volumes  volumes aligned with {@code prices}
 * @param index    index of the current bar within the full dataset (for reporting only)
 * @param inPosition true when the simulated account currently holds this instrument
 */
public record StrategyContext(
        double[] prices,
        double[] highs,
        double[] lows,
        double[] volumes,
        int index,
        boolean inPosition
) {

    public StrategyContext {
        if (prices == null || highs == null || lows == null || volumes == null) {
            throw new IllegalArgumentException("series must not be null");
        }
        if (prices.length != highs.length || prices.length != lows.length
                || prices.length != volumes.length) {
            throw new IllegalArgumentException("series must have equal length");
        }
        if (prices.length == 0) {
            throw new IllegalArgumentException("series must not be empty");
        }
    }

    public int size() {
        return prices.length;
    }

    public double last() {
        return prices[prices.length - 1];
    }

    /** True when at least {@code count} observations are available. */
    public boolean hasAtLeast(int count) {
        return prices.length >= count;
    }

    public java.util.List<Double> priceList() {
        return boxed(prices);
    }

    private static java.util.List<Double> boxed(double[] values) {
        java.util.List<Double> list = new java.util.ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list;
    }
}
