package com.tradingdesk.simulator;

import java.util.Objects;

public record SimulatorConfig(
        long tickIntervalMillis,
        double baseVolatility,
        double driftBias,
        int minSpreadBasisPoints,
        int maxSpreadBasisPoints,
        long minVolume,
        long maxVolume
) {

    public SimulatorConfig {
        if (tickIntervalMillis <= 0) {
            throw new IllegalArgumentException("tickIntervalMillis must be positive");
        }
        if (baseVolatility <= 0.0) {
            throw new IllegalArgumentException("baseVolatility must be positive");
        }
        if (minSpreadBasisPoints <= 0 || maxSpreadBasisPoints < minSpreadBasisPoints) {
            throw new IllegalArgumentException("invalid spread range");
        }
        if (minVolume <= 0 || maxVolume < minVolume) {
            throw new IllegalArgumentException("invalid volume range");
        }
        Objects.requireNonNull(minVolume);
    }

    public static SimulatorConfig defaults() {
        return new SimulatorConfig(30L, 0.0009, 0.0, 2, 8, 100L, 5_000L);
    }
}
