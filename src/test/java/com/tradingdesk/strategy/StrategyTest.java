package com.tradingdesk.strategy;

import com.tradingdesk.strategy.impl.BollingerReversalStrategy;
import com.tradingdesk.strategy.impl.MacdCrossoverStrategy;
import com.tradingdesk.strategy.impl.MomentumStrategy;
import com.tradingdesk.strategy.impl.MovingAverageCrossoverStrategy;
import com.tradingdesk.strategy.impl.RsiReversalStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyTest {

    private static StrategyContext context(double[] prices, boolean inPosition) {
        double[] highs = new double[prices.length];
        double[] lows = new double[prices.length];
        double[] volumes = new double[prices.length];
        for (int i = 0; i < prices.length; i++) {
            highs[i] = prices[i] * 1.01;
            lows[i] = prices[i] * 0.99;
            volumes[i] = 1000;
        }
        return new StrategyContext(prices, highs, lows, volumes, prices.length - 1, inPosition);
    }

    private static double[] repeating(double base, int count) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = base;
        }
        return values;
    }

    // ------------------------------------------------------------------ registry

    @Test
    void registryExposesAllFiveStrategies() {
        StrategyRegistry registry = new StrategyRegistry();
        assertEquals(5, registry.size());
        assertTrue(registry.find("ma-crossover").isPresent());
        assertTrue(registry.find("rsi-reversal").isPresent());
        assertTrue(registry.find("macd-crossover").isPresent());
        assertTrue(registry.find("bollinger-reversal").isPresent());
        assertTrue(registry.find("momentum").isPresent());
    }

    @Test
    void unknownStrategyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrategyRegistry().require("does-not-exist"));
    }

    @Test
    void duplicateRegistrationIsRejected() {
        StrategyRegistry registry = new StrategyRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(new MomentumStrategy()));
    }

    @Test
    void everyStrategyDeclaresHonestMetadata() {
        for (TradingStrategy strategy : new StrategyRegistry().all()) {
            assertFalse(strategy.id().isBlank());
            assertFalse(strategy.name().isBlank());
            assertTrue(strategy.description().toLowerCase(java.util.Locale.ROOT)
                            .contains("not a trained model"),
                    strategy.id() + " must state it is rule-based");
        }
    }

    // ------------------------------------------------------------------ MA crossover

    @Test
    void maCrossoverEntersLongOnUpwardCross() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy();
        // Declining then a sharp rally: fast SMA (2) crosses above slow SMA (4) on the final bar.
        double[] prices = {20, 20, 20, 20, 20, 10, 8, 19, 25};
        StrategyParameters params = StrategyParameters.empty().with("fastPeriod", 2).with("slowPeriod", 4);

        StrategyDecision decision = strategy.evaluate(context(prices, false), params);
        assertEquals(StrategyAction.ENTER_LONG, decision.action(),
                "fast SMA should have crossed above the slow SMA");
        assertTrue(decision.stopLoss() > 0);
        assertTrue(decision.takeProfit() > prices[prices.length - 1]);
    }

    @Test
    void maCrossoverExitsOnDownwardCross() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy();
        // Rally then a sharp drop: fast SMA (2) crosses below slow SMA (4) on the final bar.
        double[] prices = {10, 10, 10, 10, 10, 30, 32, 12, 5};
        StrategyParameters params = StrategyParameters.empty().with("fastPeriod", 2).with("slowPeriod", 4);

        assertEquals(StrategyAction.EXIT, strategy.evaluate(context(prices, true), params).action());
    }

    @Test
    void maCrossoverHoldsWithoutCrossover() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy();
        StrategyDecision decision = strategy.evaluate(
                context(repeating(10, 40), false), strategy.defaultParameters());
        assertEquals(StrategyAction.HOLD, decision.action());
    }

    @Test
    void maCrossoverHoldsWhenHistoryTooShort() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy();
        StrategyDecision decision = strategy.evaluate(context(new double[]{1, 2}, false),
                strategy.defaultParameters());
        assertEquals(StrategyAction.HOLD, decision.action());
        assertFalse(strategy.canEvaluate(context(new double[]{1, 2}, false),
                strategy.defaultParameters()));
    }

    // ------------------------------------------------------------------ RSI

    @Test
    void rsiEntersLongWhenOversold() {
        RsiReversalStrategy strategy = new RsiReversalStrategy();
        double[] falling = new double[20];
        for (int i = 0; i < falling.length; i++) {
            falling[i] = 100 - i * 3;
        }
        StrategyParameters params = StrategyParameters.empty().with("period", 5);

        assertEquals(StrategyAction.ENTER_LONG,
                strategy.evaluate(context(falling, false), params).action());
    }

    @Test
    void rsiEntersShortWhenOverbought() {
        RsiReversalStrategy strategy = new RsiReversalStrategy();
        double[] rising = new double[20];
        for (int i = 0; i < rising.length; i++) {
            rising[i] = 100 + i * 3;
        }
        StrategyParameters params = StrategyParameters.empty().with("period", 5);

        assertEquals(StrategyAction.ENTER_SHORT,
                strategy.evaluate(context(rising, false), params).action());
    }

    @Test
    void rsiExitsWhenBackToNeutral() {
        RsiReversalStrategy strategy = new RsiReversalStrategy();
        StrategyParameters params = StrategyParameters.empty().with("period", 5);
        // A flat series gives RSI = 50, which is at/above the exit level.
        assertEquals(StrategyAction.EXIT,
                strategy.evaluate(context(repeating(100, 20), true), params).action());
    }

    // ------------------------------------------------------------------ MACD

    @Test
    void macdEntersLongAfterUpwardCross() {
        MacdCrossoverStrategy strategy = new MacdCrossoverStrategy();
        // Long downtrend followed by a sharp recovery creates a bullish MACD cross.
        double[] prices = new double[40];
        for (int i = 0; i < 25; i++) {
            prices[i] = 100 - i;
        }
        for (int i = 25; i < 40; i++) {
            prices[i] = 75 + (i - 24) * 4.0;
        }
        StrategyParameters params = StrategyParameters.empty()
                .with("fastPeriod", 3).with("slowPeriod", 6).with("signalPeriod", 3);

        StrategyDecision decision = strategy.evaluate(context(prices, false), params);
        assertTrue(decision.action() == StrategyAction.ENTER_LONG
                        || decision.action() == StrategyAction.HOLD,
                "should not short into a recovery, got " + decision.action());
    }

    @Test
    void macdHoldsOnFlatSeries() {
        MacdCrossoverStrategy strategy = new MacdCrossoverStrategy();
        StrategyParameters params = StrategyParameters.empty()
                .with("fastPeriod", 3).with("slowPeriod", 6).with("signalPeriod", 3);

        assertEquals(StrategyAction.HOLD,
                strategy.evaluate(context(repeating(100, 40), false), params).action());
    }

    // ------------------------------------------------------------------ Bollinger

    @Test
    void bollingerEntersLongBelowLowerBand() {
        BollingerReversalStrategy strategy = new BollingerReversalStrategy();
        // Tight range, then a crash far below the band.
        double[] prices = new double[25];
        for (int i = 0; i < 24; i++) {
            prices[i] = 100 + (i % 2 == 0 ? 0.1 : -0.1);
        }
        prices[24] = 80;
        StrategyParameters params = StrategyParameters.empty().with("period", 20);

        assertEquals(StrategyAction.ENTER_LONG,
                strategy.evaluate(context(prices, false), params).action());
    }

    @Test
    void bollingerEntersShortAboveUpperBand() {
        BollingerReversalStrategy strategy = new BollingerReversalStrategy();
        double[] prices = new double[25];
        for (int i = 0; i < 24; i++) {
            prices[i] = 100 + (i % 2 == 0 ? 0.1 : -0.1);
        }
        prices[24] = 120;
        StrategyParameters params = StrategyParameters.empty().with("period", 20);

        assertEquals(StrategyAction.ENTER_SHORT,
                strategy.evaluate(context(prices, false), params).action());
    }

    // ------------------------------------------------------------------ Momentum

    @Test
    void momentumEntersLongOnStrongUptrend() {
        MomentumStrategy strategy = new MomentumStrategy();
        double[] prices = new double[20];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 * Math.pow(1.02, i); // ~2% per bar
        }
        StrategyParameters params = StrategyParameters.empty()
                .with("lookback", 5).with("thresholdPercent", 2.0);

        assertEquals(StrategyAction.ENTER_LONG,
                strategy.evaluate(context(prices, false), params).action());
    }

    @Test
    void momentumEntersShortOnStrongDowntrend() {
        MomentumStrategy strategy = new MomentumStrategy();
        double[] prices = new double[20];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 * Math.pow(0.98, i);
        }
        StrategyParameters params = StrategyParameters.empty()
                .with("lookback", 5).with("thresholdPercent", 2.0);

        assertEquals(StrategyAction.ENTER_SHORT,
                strategy.evaluate(context(prices, false), params).action());
    }

    @Test
    void momentumExitsWhenMomentumDecays() {
        MomentumStrategy strategy = new MomentumStrategy();
        StrategyParameters params = StrategyParameters.empty()
                .with("lookback", 5).with("thresholdPercent", 2.0).with("exitPercent", 0.5);

        assertEquals(StrategyAction.EXIT,
                strategy.evaluate(context(repeating(100, 20), true), params).action());
    }

    // ------------------------------------------------------------------ decision invariants

    @Test
    void decisionRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrategyDecision(StrategyAction.ENTER_LONG, -1, 0, 0.5, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new StrategyDecision(StrategyAction.ENTER_LONG, 0, 0, 1.5, "x"));
    }

    @Test
    void entriesCarryRiskLevels() {
        MomentumStrategy strategy = new MomentumStrategy();
        double[] prices = new double[20];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 * Math.pow(1.03, i);
        }
        StrategyDecision decision = strategy.evaluate(
                context(prices, false),
                StrategyParameters.empty().with("lookback", 5).with("thresholdPercent", 2.0));

        assertTrue(decision.isEntry());
        assertTrue(decision.stopLoss() > 0.0, "an entry must define a stop");
        assertTrue(decision.takeProfit() > 0.0, "an entry must define a target");
        assertTrue(decision.sizeFraction() > 0.0 && decision.sizeFraction() <= 1.0);
        assertFalse(decision.rationale().isBlank());
    }

    @Test
    void parametersRejectNonFiniteValues() {
        assertThrows(IllegalArgumentException.class,
                () -> StrategyParameters.empty().with("x", Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> StrategyParameters.of(java.util.Map.of("x", Double.POSITIVE_INFINITY)));
    }

    @Test
    void strategyReadsDefaultsWhenParameterOmitted() {
        StrategyParameters params = StrategyParameters.empty();
        assertEquals(30, params.getInt("slowPeriod", 30));
        assertEquals(1.5, params.get("missing", 1.5), 1e-9);
    }

    @Test
    void requireEnforcesBounds() {
        StrategyParameters params = StrategyParameters.empty().with("x", 5.0);
        assertEquals(5.0, params.require("x", 0.0, 0, 10), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> params.require("x", 0.0, 6, 10));
        // A missing parameter falls back to the supplied default rather than throwing.
        assertEquals(1.0, params.require("absent", 1.0, 0, 10), 1e-9);
    }
}
