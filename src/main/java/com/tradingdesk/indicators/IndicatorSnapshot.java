package com.tradingdesk.indicators;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record IndicatorSnapshot(
        double simpleMovingAverage,
        double exponentialMovingAverage,
        double rateOfChange,
        double rollingVolatility,
        double orderBookImbalance,
        int sampleCount,
        Instant capturedAt
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
        return new IndicatorSnapshot(0.0, 0.0, 0.0, 0.0, 0.0, 0, at);
    }
}
