package com.tradingdesk.prediction;

import java.util.Objects;

public record PredictionConfig(
        double momentumThreshold,
        double volatilityCeiling,
        int minimumSamples
) {

    public PredictionConfig {
        if (momentumThreshold <= 0.0) {
            throw new IllegalArgumentException("momentumThreshold must be positive");
        }
        if (volatilityCeiling <= 0.0) {
            throw new IllegalArgumentException("volatilityCeiling must be positive");
        }
        if (minimumSamples < 2) {
            throw new IllegalArgumentException("minimumSamples must be at least 2");
        }
        Objects.requireNonNull(momentumThreshold);
    }

    public static PredictionConfig defaults() {
        return new PredictionConfig(0.0015, 5.0, 8);
    }
}
