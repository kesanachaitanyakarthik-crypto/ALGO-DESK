package com.tradingdesk.strategy.impl;

import com.tradingdesk.indicators.AdvancedIndicators;
import com.tradingdesk.strategy.StrategyAction;
import com.tradingdesk.strategy.StrategyContext;
import com.tradingdesk.strategy.StrategyDecision;
import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.TradingStrategy;

import java.util.Locale;

/** MACD crossover: long when the MACD line crosses above its signal line, exit on the reverse. */
public class MacdCrossoverStrategy implements TradingStrategy {

    public static final String FAST = "fastPeriod";
    public static final String SLOW = "slowPeriod";
    public static final String SIGNAL = "signalPeriod";
    public static final String STOP_PCT = "stopPercent";
    public static final String TARGET_PCT = "targetPercent";
    public static final String SIZE = "sizeFraction";

    @Override
    public String id() {
        return "macd-crossover";
    }

    @Override
    public String name() {
        return "MACD Crossover";
    }

    @Override
    public String description() {
        return "MACD line crossing its signal line. Deterministic rules, not a trained model.";
    }

    @Override
    public StrategyParameters defaultParameters() {
        return StrategyParameters.empty()
                .with(FAST, 12)
                .with(SLOW, 26)
                .with(SIGNAL, 9)
                .with(STOP_PCT, 2.5)
                .with(TARGET_PCT, 6.0)
                .with(SIZE, 1.0);
    }

    @Override
    public boolean canEvaluate(StrategyContext context, StrategyParameters parameters) {
        int slow = parameters.getInt(SLOW, 26);
        int signal = parameters.getInt(SIGNAL, 9);
        return context.hasAtLeast(slow + signal + 1);
    }

    @Override
    public StrategyDecision evaluate(StrategyContext context, StrategyParameters parameters) {
        int fast = parameters.getInt(FAST, 12);
        int slow = parameters.getInt(SLOW, 26);
        int signal = parameters.getInt(SIGNAL, 9);
        if (fast <= 0 || slow <= fast || signal <= 0) {
            throw new IllegalArgumentException("invalid MACD periods");
        }
        if (!canEvaluate(context, parameters)) {
            return StrategyDecision.hold("Insufficient history for MACD(" + fast + "," + slow + "," + signal + ")");
        }

        var prices = context.priceList();
        AdvancedIndicators.Macd previous = AdvancedIndicators.macd(
                prices.subList(0, prices.size() - 1), fast, slow, signal);
        AdvancedIndicators.Macd current = AdvancedIndicators.macd(prices, fast, slow, signal);

        boolean crossedUp = previous.macd() <= previous.signal() && current.macd() > current.signal();
        boolean crossedDown = previous.macd() >= previous.signal() && current.macd() < current.signal();

        double last = context.last();
        double stopPct = parameters.get(STOP_PCT, 2.5) / 100.0;
        double targetPct = parameters.get(TARGET_PCT, 6.0) / 100.0;
        double size = parameters.get(SIZE, 1.0);

        String reason = String.format(Locale.ROOT, "MACD %.5f vs signal %.5f",
                current.macd(), current.signal());

        if (context.inPosition()) {
            if (crossedDown) {
                return new StrategyDecision(StrategyAction.EXIT, 0, 0, 0, reason + " crossed down");
            }
            return StrategyDecision.hold(reason + " holding");
        }

        if (crossedUp) {
            return new StrategyDecision(StrategyAction.ENTER_LONG,
                    last * (1 - stopPct), last * (1 + targetPct), size, reason + " crossed up");
        }
        return StrategyDecision.hold(reason + " no crossover");
    }
}
