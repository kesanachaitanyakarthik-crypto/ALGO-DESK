package com.tradingdesk.model;

import java.time.Instant;
import java.util.Objects;

public record PortfolioSnapshot(
        double cash,
        double marketValue,
        double realizedProfitLoss,
        double unrealizedProfitLoss,
        Instant capturedAt
) {

    public PortfolioSnapshot {
        Objects.requireNonNull(capturedAt, "capturedAt");
    }

    public double totalEquity() {
        return cash + marketValue;
    }
}
