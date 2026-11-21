package com.tradingdesk.ml;

import com.tradingdesk.indicators.AdvancedIndicators;
import com.tradingdesk.indicators.TechnicalIndicators;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a price window into a normalised feature vector for the classifier.
 *
 * <p>Every feature is computed only from the supplied window, so a feature vector built at bar
 * {@code i} cannot contain information from bar {@code i+1}. That is what keeps the model free of
 * look-ahead bias.
 *
 * <p>Feature names are exposed so a model's weights can be interpreted and shown in the UI — this
 * model is deliberately explainable rather than a black box.
 */
public final class FeatureExtractor {

    public static final List<String> FEATURE_NAMES = List.of(
            "smaRatio",       // price relative to its SMA
            "emaRatio",       // price relative to its EMA
            "rsiScaled",      // RSI mapped to [-1, 1]
            "rocScaled",      // rate of change scaled
            "volatilityScaled", // rolling volatility scaled
            "macdScaled",     // MACD histogram scaled
            "imbalance");     // order-book imbalance in [-1, 1]

    public static final int FEATURE_COUNT = FEATURE_NAMES.size();

    /** Minimum window needed for the slowest feature (RSI-14). */
    public static final int MIN_WINDOW = 16;

    private final int rocPeriod;
    private final double volatilityScale;
    private final double rocScale;

    public FeatureExtractor() {
        this(5, 2.0, 5.0);
    }

    /**
     * @param rocPeriod       lookback for rate of change
     * @param volatilityScale divisor that maps typical volatility to ~1
     * @param rocScale        divisor that maps typical ROC (in %) to ~1
     */
    public FeatureExtractor(int rocPeriod, double volatilityScale, double rocScale) {
        if (rocPeriod <= 0 || volatilityScale <= 0.0 || rocScale <= 0.0) {
            throw new IllegalArgumentException("scales and period must be positive");
        }
        this.rocPeriod = rocPeriod;
        this.volatilityScale = volatilityScale;
        this.rocScale = rocScale;
    }

    /** True when the window is long enough to build a feature vector. */
    public boolean canExtract(double[] window) {
        return window != null && window.length >= MIN_WINDOW;
    }

    /**
     * Builds the feature vector for the most recent point in {@code window}.
     *
     * @param window closing prices, oldest first
     * @param imbalance order-book imbalance in [-1, 1], or 0 when unavailable
     * @throws IllegalArgumentException when the window is too short
     */
    public double[] extract(double[] window, double imbalance) {
        if (!canExtract(window)) {
            throw new IllegalArgumentException(
                    "need at least " + MIN_WINDOW + " prices, got "
                            + (window == null ? 0 : window.length));
        }
        List<Double> prices = toList(window);
        double last = prices.get(prices.size() - 1);

        double sma = TechnicalIndicators.simpleMovingAverage(prices, 10);
        double ema = TechnicalIndicators.exponentialMovingAverage(prices, 10);
        double rsi = AdvancedIndicators.rsi(prices, 14);
        double roc = TechnicalIndicators.rateOfChange(prices, rocPeriod);
        double volatility = TechnicalIndicators.rollingVolatility(prices, Math.min(10, prices.size()));
        AdvancedIndicators.Macd macd = AdvancedIndicators.macd(prices, 3, 6, 3);

        double smaRatio = sma == 0.0 ? 0.0 : (last - sma) / sma;
        double emaRatio = ema == 0.0 ? 0.0 : (last - ema) / ema;

        return new double[]{
                clamp(smaRatio * 100.0),
                clamp(emaRatio * 100.0),
                clamp((rsi - 50.0) / 50.0),
                clamp(roc * 100.0 / rocScale),
                clamp(volatility / volatilityScale),
                clamp(macd.histogram() / Math.max(1e-9, last) * 100.0),
                clamp(imbalance)
        };
    }

    /** Clamps a feature to a sane range so one outlier cannot dominate the model. */
    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(-3.0, Math.min(3.0, value));
    }

    private static List<Double> toList(double[] window) {
        List<Double> prices = new ArrayList<>(window.length);
        for (double price : window) {
            prices.add(price);
        }
        return prices;
    }

    public int rocPeriod() {
        return rocPeriod;
    }
}
