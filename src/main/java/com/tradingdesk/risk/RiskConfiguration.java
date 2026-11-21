package com.tradingdesk.risk;

public record RiskConfiguration(
        long maxOrderQuantity,
        double maxPositionValue,
        double dailyMaxLoss,
        double maxPriceDeviationPercent,
        long duplicateOrderWindowMillis
) {

    public RiskConfiguration {
        if (maxOrderQuantity <= 0) {
            throw new IllegalArgumentException("maxOrderQuantity must be positive");
        }
        if (maxPositionValue <= 0.0) {
            throw new IllegalArgumentException("maxPositionValue must be positive");
        }
        if (dailyMaxLoss <= 0.0) {
            throw new IllegalArgumentException("dailyMaxLoss must be positive");
        }
        if (maxPriceDeviationPercent <= 0.0 || maxPriceDeviationPercent > 1.0) {
            throw new IllegalArgumentException("maxPriceDeviationPercent must be within (0, 1]");
        }
        if (duplicateOrderWindowMillis <= 0) {
            throw new IllegalArgumentException("duplicateOrderWindowMillis must be positive");
        }
    }

    public static RiskConfiguration defaults() {
        return new RiskConfiguration(1_000L, 250_000.0, 25_000.0, 0.05, 750L);
    }
}
