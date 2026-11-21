package com.tradingdesk.strategy.impl;

import com.tradingdesk.indicators.AdvancedIndicators;
import com.tradingdesk.strategy.StrategyAction;
import com.tradingdesk.strategy.StrategyContext;
import com.tradingdesk.strategy.StrategyDecision;
import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.TradingStrategy;

import java.util.Locale;

/**
 * Bollinger Band reversal: buy when price closes below the lower band (stretched), sell short when
 * it closes above the upper band, and exit as it returns toward the middle band.
 */
public class BollingerReversalStrategy implements TradingStrategy {

    public static final String PERIOD = "period";
    public static final String DEVIATIONS = "deviations";
    public static final String EXIT_TO_MIDDLE = "exitToMiddle";
    public static final String STOP_PCT = "stopPercent";
    public static final String TARGET_PCT = "targetPercent";
    public static final String SIZE = "sizeFraction";

    @Override
    public String id() {
        return "bollinger-reversal";
    }

    @Override
    public String name() {
        return "Bollinger Band Reversal";
    }

    @Override
    public String description() {
        return "Mean reversion at Bollinger extremes. Deterministic rules, not a trained model.";
    }

    @Override
    public StrategyParameters defaultParameters() {
        return StrategyParameters.empty()
                .with(PERIOD, 20)
                .with(DEVIATIONS, 2.0)
                .with(EXIT_TO_MIDDLE, 1)
                .with(STOP_PCT, 2.0)
                .with(TARGET_PCT, 4.0)
                .with(SIZE, 1.0);
    }

    @Override
    public boolean canEvaluate(StrategyContext context, StrategyParameters parameters) {
        return context.hasAtLeast(parameters.getInt(PERIOD, 20));
    }

    @Override
    public StrategyDecision evaluate(StrategyContext context, StrategyParameters parameters) {
        int period = parameters.requireInt(PERIOD, 20, 2, 200);
        double deviations = parameters.require(DEVIATIONS, 2.0, 0.5, 5.0);
        if (!canEvaluate(context, parameters)) {
            return StrategyDecision.hold("Insufficient history for " + period + "-bar bands");
        }

        AdvancedIndicators.BollingerBands bands =
                AdvancedIndicators.bollingerBands(context.priceList(), period, deviations);
        double last = context.last();
        double stopPct = parameters.get(STOP_PCT, 2.0) / 100.0;
        double targetPct = parameters.get(TARGET_PCT, 4.0) / 100.0;
        double size = parameters.get(SIZE, 1.0);

        String reason = String.format(Locale.ROOT,
                "close %.4f vs bands [%.4f, %.4f]", last, bands.lower(), bands.upper());

        if (context.inPosition()) {
            boolean exitToMiddle = parameters.get(EXIT_TO_MIDDLE, 1.0) >= 0.5;
            if (exitToMiddle && Math.abs(last - bands.middle()) <= bands.width() * 0.1) {
                return new StrategyDecision(StrategyAction.EXIT, 0, 0, 0,
                        reason + " returned to the middle band");
            }
            return StrategyDecision.hold(reason + " holding");
        }

        if (last < bands.lower()) {
            return new StrategyDecision(StrategyAction.ENTER_LONG,
                    last * (1 - stopPct), last * (1 + targetPct), size,
                    reason + " below the lower band");
        }
        if (last > bands.upper()) {
            return new StrategyDecision(StrategyAction.ENTER_SHORT,
                    last * (1 + stopPct), last * (1 - targetPct), size,
                    reason + " above the upper band");
        }
        return StrategyDecision.hold(reason + " inside the bands");
    }
}
