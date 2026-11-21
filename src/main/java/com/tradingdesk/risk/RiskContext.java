package com.tradingdesk.risk;

import com.tradingdesk.model.Order;
import com.tradingdesk.model.PortfolioSnapshot;

import java.time.Instant;
import java.util.Objects;

public record RiskContext(
        Order order,
        PortfolioSnapshot portfolio,
        double marketPrice,
        double projectedPositionValue,
        Instant evaluatedAt
) {

    public RiskContext {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (marketPrice <= 0.0) {
            throw new IllegalArgumentException("marketPrice must be positive");
        }
    }

    public static RiskContext of(Order order, PortfolioSnapshot portfolio,
                                 double marketPrice, double projectedPositionValue) {
        return new RiskContext(order, portfolio, marketPrice, projectedPositionValue, Instant.now());
    }
}
