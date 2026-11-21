package com.tradingdesk.strategy.impl;

import com.tradingdesk.strategy.StrategyAction;
import com.tradingdesk.strategy.StrategyContext;
import com.tradingdesk.strategy.StrategyDecision;
import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.TradingStrategy;

import java.util.List;
import java.util.Locale;

/**
 * Moving-average crossover: long when the fast SMA crosses above the slow SMA, exit when it crosses
 * back below. Stops and targets are expressed as a percentage of the entry price.
 */
public class MovingAverageCrossoverStrategy implements TradingStrategy {

    public static final String FAST = "fastPeriod";
    public static final String SLOW = "slowPeriod";
    public static final String STOP_PCT = "stopPercent";
    public static final String TARGET_PCT = "targetPercent";
    public static final String SIZE = "sizeFraction";

    @Override
    public String id() {
        return "ma-crossover";
    }

    @Override
    public String name() {
        return "Moving Average Crossover";
    }

    @Override
    public String description() {
        return "Fast SMA crossing a slow SMA. Deterministic rules, not a trained model.";
    }

    @Override
    public StrategyParameters defaultParameters() {
        return StrategyParameters.empty()
                .with(FAST, 10)
                .with(SLOW, 30)
                .with(STOP_PCT, 2.0)
                .with(TARGET_PCT, 5.0)
                .with(SIZE, 1.0);
    }

    @Override
    public boolean canEvaluate(StrategyContext context, StrategyParameters parameters) {
        int slow = parameters.getInt(SLOW, 30);
        return context.hasAtLeast(slow + 1);
    }

    @Override
    public StrategyDecision evaluate(StrategyContext context, StrategyParameters parameters) {
        int fast = parameters.getInt(FAST, 10);
        int slow = parameters.getInt(SLOW, 30);
        if (fast <= 0 || slow <= fast) {
            throw new IllegalArgumentException("fastPeriod must be positive and less than slowPeriod");
        }
        if (!canEvaluate(context, parameters)) {
            return StrategyDecision.hold("Insufficient history: need " + (slow + 1) + " bars");
        }

        List<Double> prices = context.priceList();
        double previousFast = sma(prices.subList(0, prices.size() - 1), fast);
        double previousSlow = sma(prices.subList(0, prices.size() - 1), slow);
        double currentFast = sma(prices, fast);
        double currentSlow = sma(prices, slow);

        boolean crossedUp = previousFast <= previousSlow && currentFast > currentSlow;
        boolean crossedDown = previousFast >= previousSlow && currentFast < currentSlow;

        double last = context.last();
        double stopPct = parameters.get(STOP_PCT, 2.0) / 100.0;
        double targetPct = parameters.get(TARGET_PCT, 5.0) / 100.0;
        double size = parameters.get(SIZE, 1.0);

        if (context.inPosition()) {
            if (crossedDown) {
                return new StrategyDecision(StrategyAction.EXIT, 0, 0, 0,
                        String.format(Locale.ROOT, "Fast SMA %.4f crossed below slow SMA %.4f",
                                currentFast, currentSlow));
            }
            return StrategyDecision.hold("Holding: fast SMA still above slow SMA");
        }

        if (crossedUp) {
            return new StrategyDecision(StrategyAction.ENTER_LONG,
                    last * (1 - stopPct), last * (1 + targetPct), size,
                    String.format(Locale.ROOT, "Fast SMA %.4f crossed above slow SMA %.4f",
                            currentFast, currentSlow));
        }
        return StrategyDecision.hold("No crossover");
    }

    private static double sma(List<Double> prices, int period) {
        double sum = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }
}
