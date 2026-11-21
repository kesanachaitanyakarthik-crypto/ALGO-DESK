package com.tradingdesk.exchange;

import java.util.ArrayList;
import java.util.List;

/**
 * Hard ceilings applied to live orders, independent of the strategy that generated them.
 *
 * <p>These exist so a runaway strategy cannot drain an account even if it is otherwise "enabled".
 */
public record LiveSafetyLimits(
        double maxOrderNotional,
        double maxDailyLoss,
        int maxOpenPositions,
        boolean duplicateOrderPrevention
) {

    public LiveSafetyLimits {
        if (maxOrderNotional <= 0.0) {
            throw new IllegalArgumentException("maxOrderNotional must be positive");
        }
        if (maxDailyLoss <= 0.0) {
            throw new IllegalArgumentException("maxDailyLoss must be positive");
        }
        if (maxOpenPositions <= 0) {
            throw new IllegalArgumentException("maxOpenPositions must be positive");
        }
    }

    /** Conservative defaults suitable for an initial live deployment. */
    public static LiveSafetyLimits defaults() {
        return new LiveSafetyLimits(1_000.0, 500.0, 3, true);
    }

    /**
     * Checks a prospective order against the limits.
     *
     * @return the list of violations; empty means the order is acceptable
     */
    public List<String> violations(ExchangeOrder order, double realisedLossToday,
                                   int openPositions, boolean duplicate) {
        List<String> problems = new ArrayList<>();

        if (order.notional() > maxOrderNotional) {
            problems.add(String.format("order notional %.2f exceeds live limit %.2f",
                    order.notional(), maxOrderNotional));
        }
        if (realisedLossToday >= maxDailyLoss) {
            problems.add(String.format("daily loss %.2f reached live limit %.2f",
                    realisedLossToday, maxDailyLoss));
        }
        if (openPositions >= maxOpenPositions) {
            problems.add("open positions " + openPositions + " reached live limit " + maxOpenPositions);
        }
        if (duplicateOrderPrevention && duplicate) {
            problems.add("duplicate order detected");
        }
        return List.copyOf(problems);
    }
}
