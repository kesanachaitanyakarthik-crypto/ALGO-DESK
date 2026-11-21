package com.tradingdesk.strategy.impl;

import com.tradingdesk.indicators.TechnicalIndicators;
import com.tradingdesk.strategy.StrategyAction;
import com.tradingdesk.strategy.StrategyContext;
import com.tradingdesk.strategy.StrategyDecision;
import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.TradingStrategy;

import java.util.Locale;

/**
 * Momentum: follows a sustained rate of change above a threshold, exits when momentum decays.
 * Unlike the reversal strategies this one is trend-following by design.
 */
public class MomentumStrategy implements TradingStrategy {

    public static final String LOOKBACK = "lookback";
    public static final String THRESHOLD_PCT = "thresholdPercent";
    public static final String EXIT_PCT = "exitPercent";
    public static final String STOP_PCT = "stopPercent";
    public static final String TARGET_PCT = "targetPercent";
    public static final String SIZE = "sizeFraction";

    @Override
    public String id() {
        return "momentum";
    }

    @Override
    public String name() {
        return "Momentum";
    }

    @Override
    public String description() {
        return "Rate-of-change trend following. Deterministic rules, not a trained model.";
    }

    @Override
    public StrategyParameters defaultParameters() {
        return StrategyParameters.empty()
                .with(LOOKBACK, 10)
                .with(THRESHOLD_PCT, 2.0)
                .with(EXIT_PCT, 0.5)
                .with(STOP_PCT, 3.0)
                .with(TARGET_PCT, 8.0)
                .with(SIZE, 1.0);
    }

    @Override
    public boolean canEvaluate(StrategyContext context, StrategyParameters parameters) {
        return context.hasAtLeast(parameters.getInt(LOOKBACK, 10) + 1);
    }

    @Override
    public StrategyDecision evaluate(StrategyContext context, StrategyParameters parameters) {
        int lookback = parameters.requireInt(LOOKBACK, 10, 1, 200);
        if (!canEvaluate(context, parameters)) {
            return StrategyDecision.hold("Insufficient history for momentum(" + lookback + ")");
        }

        double rateOfChange = TechnicalIndicators.rateOfChange(context.priceList(), lookback) * 100.0;
        double threshold = parameters.get(THRESHOLD_PCT, 2.0);
        double exitThreshold = parameters.get(EXIT_PCT, 0.5);
        double last = context.last();
        double stopPct = parameters.get(STOP_PCT, 3.0) / 100.0;
        double targetPct = parameters.get(TARGET_PCT, 8.0) / 100.0;
        double size = parameters.get(SIZE, 1.0);

        String reason = String.format(Locale.ROOT, "ROC(%d) = %.2f%%", lookback, rateOfChange);

        if (context.inPosition()) {
            if (Math.abs(rateOfChange) < exitThreshold) {
                return new StrategyDecision(StrategyAction.EXIT, 0, 0, 0,
                        reason + " momentum decayed below " + exitThreshold + "%");
            }
            return StrategyDecision.hold(reason + " momentum still active");
        }

        if (rateOfChange > threshold) {
            return new StrategyDecision(StrategyAction.ENTER_LONG,
                    last * (1 - stopPct), last * (1 + targetPct), size, reason + " above threshold");
        }
        if (rateOfChange < -threshold) {
            return new StrategyDecision(StrategyAction.ENTER_SHORT,
                    last * (1 + stopPct), last * (1 - targetPct), size, reason + " below threshold");
        }
        return StrategyDecision.hold(reason + " within threshold");
    }
}
