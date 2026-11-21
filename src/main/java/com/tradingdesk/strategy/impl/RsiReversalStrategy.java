package com.tradingdesk.strategy.impl;

import com.tradingdesk.indicators.AdvancedIndicators;
import com.tradingdesk.strategy.StrategyAction;
import com.tradingdesk.strategy.StrategyContext;
import com.tradingdesk.strategy.StrategyDecision;
import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.TradingStrategy;

import java.util.Locale;

/**
 * RSI reversal: buy when RSI is oversold, sell when overbought. Exits once RSI returns to the
 * neutral band, which is the classic mean-reversion exit.
 */
public class RsiReversalStrategy implements TradingStrategy {

    public static final String PERIOD = "period";
    public static final String OVERSOLD = "oversold";
    public static final String OVERBOUGHT = "overbought";
    public static final String EXIT_LEVEL = "exitLevel";
    public static final String STOP_PCT = "stopPercent";
    public static final String TARGET_PCT = "targetPercent";
    public static final String SIZE = "sizeFraction";

    @Override
    public String id() {
        return "rsi-reversal";
    }

    @Override
    public String name() {
        return "RSI Overbought/Oversold";
    }

    @Override
    public String description() {
        return "Mean reversion on Wilder RSI extremes. Deterministic rules, not a trained model.";
    }

    @Override
    public StrategyParameters defaultParameters() {
        return StrategyParameters.empty()
                .with(PERIOD, 14)
                .with(OVERSOLD, 30)
                .with(OVERBOUGHT, 70)
                .with(EXIT_LEVEL, 50)
                .with(STOP_PCT, 2.0)
                .with(TARGET_PCT, 4.0)
                .with(SIZE, 1.0);
    }

    @Override
    public boolean canEvaluate(StrategyContext context, StrategyParameters parameters) {
        return context.hasAtLeast(parameters.getInt(PERIOD, 14) + 1);
    }

    @Override
    public StrategyDecision evaluate(StrategyContext context, StrategyParameters parameters) {
        int period = parameters.requireInt(PERIOD, 14, 2, 200);
        if (!canEvaluate(context, parameters)) {
            return StrategyDecision.hold("Insufficient history for RSI(" + period + ")");
        }

        double oversold = parameters.require(OVERSOLD, 30, 1, 49);
        double overbought = parameters.require(OVERBOUGHT, 70, 51, 99);
        double exitLevel = parameters.get(EXIT_LEVEL, 50);
        double rsi = AdvancedIndicators.rsi(context.priceList(), period);
        double last = context.last();
        double stopPct = parameters.get(STOP_PCT, 2.0) / 100.0;
        double targetPct = parameters.get(TARGET_PCT, 4.0) / 100.0;
        double size = parameters.get(SIZE, 1.0);

        String reason = String.format(Locale.ROOT, "RSI(%d) = %.2f", period, rsi);

        if (context.inPosition()) {
            if (rsi >= exitLevel) {
                return new StrategyDecision(StrategyAction.EXIT, 0, 0, 0,
                        reason + " reached neutral exit level " + exitLevel);
            }
            return StrategyDecision.hold(reason + " still below exit level");
        }

        if (rsi <= oversold) {
            return new StrategyDecision(StrategyAction.ENTER_LONG,
                    last * (1 - stopPct), last * (1 + targetPct), size,
                    reason + " oversold");
        }
        if (rsi >= overbought) {
            return new StrategyDecision(StrategyAction.ENTER_SHORT,
                    last * (1 + stopPct), last * (1 - targetPct), size,
                    reason + " overbought");
        }
        return StrategyDecision.hold(reason + " neutral");
    }
}
