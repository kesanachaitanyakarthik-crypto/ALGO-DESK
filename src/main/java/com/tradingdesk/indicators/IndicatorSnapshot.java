package com.tradingdesk.indicators;

import java.time.Instant;
import java.util.Objects;

/**
 * All computed indicators for one instrument at one point in time.
 *
 * <p>The original fields are unchanged so existing call sites keep working; the advanced readings
 * are additive. Every value is computed from the same rolling window, so a snapshot is internally
 * consistent.
 */
public record IndicatorSnapshot(
        double simpleMovingAverage,
        double exponentialMovingAverage,
        double rateOfChange,
        double rollingVolatility,
        double orderBookImbalance,
        int sampleCount,
        Instant capturedAt,
        double rsi,
        double macd,
        double macdSignal,
        double macdHistogram,
        double bollingerUpper,
        double bollingerMiddle,
        double bollingerLower,
        double atr,
        double stochasticK,
        double stochasticD,
        double vwap
) {

    public IndicatorSnapshot {
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must not be negative");
        }
    }

    public boolean hasSufficientData() {
        return sampleCount >= 2;
    }

    public static IndicatorSnapshot empty(Instant at) {
        return new IndicatorSnapshot(0.0, 0.0, 0.0, 0.0, 0.0, 0, at,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /** Convenience for callers that only have the core five values. */
    public static IndicatorSnapshot ofCore(double sma, double ema, double roc, double volatility,
                                           double imbalance, int sampleCount, Instant at) {
        return new IndicatorSnapshot(sma, ema, roc, volatility, imbalance, sampleCount, at,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /** True when the window was long enough for RSI and Bollinger Bands to be meaningful. */
    public boolean hasAdvancedData() {
        return sampleCount >= 20;
    }
}
