package com.tradingdesk.strategy;

/**
 * A strategy's decision at one point in time, with the risk levels it wants applied and a readable
 * explanation for the UI and logs.
 *
 * @param action       what to do
 * @param stopLoss     absolute price for the protective stop, or 0 if none
 * @param takeProfit   absolute price for the profit target, or 0 if none
 * @param sizeFraction fraction of available capital to deploy (0..1)
 * @param rationale    human-readable reason, shown in the UI
 */
public record StrategyDecision(
        StrategyAction action,
        double stopLoss,
        double takeProfit,
        double sizeFraction,
        String rationale
) {

    public StrategyDecision {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (stopLoss < 0.0 || takeProfit < 0.0) {
            throw new IllegalArgumentException("stop loss and take profit must not be negative");
        }
        if (sizeFraction < 0.0 || sizeFraction > 1.0) {
            throw new IllegalArgumentException("sizeFraction must be within [0, 1]");
        }
        rationale = rationale == null ? "" : rationale;
    }

    public static StrategyDecision hold(String why) {
        return new StrategyDecision(StrategyAction.HOLD, 0.0, 0.0, 0.0, why);
    }

    public boolean isEntry() {
        return action == StrategyAction.ENTER_LONG || action == StrategyAction.ENTER_SHORT;
    }
}
