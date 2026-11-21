package com.tradingdesk.backtest;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.PriceBar;
import com.tradingdesk.strategy.StrategyAction;
import com.tradingdesk.strategy.StrategyContext;
import com.tradingdesk.strategy.StrategyDecision;
import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestEngineTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static PriceBar bar(int index, double open, double high, double low, double close) {
        return new PriceBar(AAPL, open, high, low, close, 1_000, T0.plusSeconds(86_400L * index));
    }

    private static HistoricalDataset ramp(double from, double to, int count) {
        List<PriceBar> bars = new ArrayList<>();
        double step = (to - from) / (count - 1);
        for (int i = 0; i < count; i++) {
            double close = from + step * i;
            double open = i == 0 ? from : from + step * (i - 1);
            bars.add(bar(i, open, Math.max(open, close) + 0.5, Math.min(open, close) - 0.5, close));
        }
        return new HistoricalDataset("AAPL", bars);
    }

    /** Always buys on the first bar it can see and never sells. */
    private static final class BuyOnceStrategy implements TradingStrategy {
        @Override public String id() { return "buy-once"; }
        @Override public String name() { return "Buy Once"; }
        @Override public String description() { return "test strategy; not a trained model"; }
        @Override public StrategyParameters defaultParameters() { return StrategyParameters.empty(); }
        @Override public boolean canEvaluate(StrategyContext context, StrategyParameters p) { return true; }
        @Override public StrategyDecision evaluate(StrategyContext context, StrategyParameters p) {
            return context.inPosition()
                    ? StrategyDecision.hold("holding")
                    : new StrategyDecision(StrategyAction.ENTER_LONG, 0, 0, 1.0, "buy");
        }
    }

    /** Records the largest index it was ever shown, to prove no look-ahead. */
    private static final class IndexWatchingStrategy implements TradingStrategy {
        final List<Integer> seenIndexes = new ArrayList<>();
        final List<Integer> seenLengths = new ArrayList<>();

        @Override public String id() { return "index-watch"; }
        @Override public String name() { return "Index Watch"; }
        @Override public String description() { return "test strategy; not a trained model"; }
        @Override public StrategyParameters defaultParameters() { return StrategyParameters.empty(); }
        @Override public boolean canEvaluate(StrategyContext context, StrategyParameters p) { return true; }
        @Override public StrategyDecision evaluate(StrategyContext context, StrategyParameters p) {
            seenIndexes.add(context.index());
            seenLengths.add(context.size());
            return StrategyDecision.hold("watching");
        }
    }

    // ------------------------------------------------------------------ look-ahead

    @Test
    void strategyNeverSeesFutureBars() {
        HistoricalDataset dataset = ramp(100, 150, 30);
        IndexWatchingStrategy strategy = new IndexWatchingStrategy();

        new BacktestEngine(BacktestEngine.Config.frictionless())
                .run(strategy, dataset, StrategyParameters.empty());

        for (int i = 0; i < strategy.seenIndexes.size(); i++) {
            int index = strategy.seenIndexes.get(i);
            int visible = strategy.seenLengths.get(i);
            assertEquals(index + 1, visible,
                    "at index " + index + " the strategy must see exactly " + (index + 1) + " bars");
            assertTrue(visible <= dataset.size(), "never more bars than exist");
        }
        assertTrue(strategy.seenIndexes.stream().max(Integer::compareTo).orElse(-1)
                < dataset.size() - 1, "the final bar can never be used for a decision");
    }

    // ------------------------------------------------------------------ mechanics

    @Test
    void entryFillsAtNextBarOpenNotCurrentClose() {
        // Bar 0 closes at 100; bar 1 opens at 200. The fill must be 200, not 100.
        List<PriceBar> bars = List.of(
                bar(0, 100, 100.5, 99.5, 100),
                bar(1, 200, 200.5, 199.5, 200),
                bar(2, 200, 200.5, 199.5, 200));
        HistoricalDataset dataset = new HistoricalDataset("AAPL", bars);

        BacktestResult result = new BacktestEngine(BacktestEngine.Config.frictionless())
                .run(new BuyOnceStrategy(), dataset, StrategyParameters.empty());

        assertTrue(result.hasTrades());
        assertEquals(200.0, result.trades().get(0).entryPrice(), 1e-9,
                "the fill must be the next bar's open, not the decision bar's close");
    }

    @Test
    void frictionlessCostsMatchRawPriceMove() {
        // Buy at 100 (bar 1 open), exit at 120. 100 units -> +2000 profit.
        List<PriceBar> bars = List.of(
                bar(0, 100, 100.5, 99.5, 100),
                bar(1, 100, 100.5, 99.5, 110),
                bar(2, 120, 120.5, 119.5, 120));
        HistoricalDataset dataset = new HistoricalDataset("AAPL", bars);

        BacktestResult result = new BacktestEngine(
                new BacktestEngine.Config(10_000.0, 0.0, 0.0, 1.0))
                .run(new BuyOnceStrategy(), dataset, StrategyParameters.empty());

        assertEquals(1, result.trades().size());
        assertEquals(0.0, result.trades().get(0).fees(), 1e-9);
        assertEquals(10_000.0 + 2_000.0, result.metrics().finalEquity(), 1.0);
    }

    @Test
    void commissionAndSlippageReduceProfit() {
        HistoricalDataset dataset = ramp(100, 120, 10);

        BacktestResult frictionless = new BacktestEngine(BacktestEngine.Config.frictionless())
                .run(new BuyOnceStrategy(), dataset, StrategyParameters.empty());
        BacktestResult costly = new BacktestEngine(
                new BacktestEngine.Config(100_000.0, 10.0, 10.0, 1.0))
                .run(new BuyOnceStrategy(), dataset, StrategyParameters.empty());

        assertTrue(costly.metrics().finalEquity() < frictionless.metrics().finalEquity(),
                "costs must reduce the final equity");
        assertTrue(costly.trades().get(0).fees() > 0.0, "fees must be recorded on the trade");
    }

    @Test
    void openPositionIsClosedAtTheFinalBar() {
        HistoricalDataset dataset = ramp(100, 200, 10);
        BacktestResult result = new BacktestEngine(BacktestEngine.Config.frictionless())
                .run(new BuyOnceStrategy(), dataset, StrategyParameters.empty());

        assertTrue(result.hasTrades(), "the open position must be closed so the run is complete");
        // One equity point per bar, including the final bar.
        assertEquals(dataset.size(), result.equityCurve().size());
    }

    @Test
    void equityCurveHasOnePointPerBar() {
        HistoricalDataset dataset = ramp(100, 110, 25);
        BacktestResult result = new BacktestEngine(BacktestEngine.Config.frictionless())
                .run(new BuyOnceStrategy(), dataset, StrategyParameters.empty());

        assertEquals(25, result.equityCurve().size());
        assertEquals(100_000.0, result.equityCurve().get(0), 1e-9);
    }

    @Test
    void stopLossClosesTheTrade() {
        // Enter at 100, then the bar's low pierces the stop at 90.
        List<PriceBar> bars = List.of(
                bar(0, 100, 100.5, 99.5, 100),
                bar(1, 100, 100.5, 89.0, 95),
                bar(2, 95, 95.5, 94.5, 95));
        HistoricalDataset dataset = new HistoricalDataset("AAPL", bars);

        TradingStrategy withStop = new TradingStrategy() {
            @Override public String id() { return "stop-test"; }
            @Override public String name() { return "Stop Test"; }
            @Override public String description() { return "test; not a trained model"; }
            @Override public StrategyParameters defaultParameters() { return StrategyParameters.empty(); }
            @Override public boolean canEvaluate(StrategyContext c, StrategyParameters p) { return true; }
            @Override public StrategyDecision evaluate(StrategyContext c, StrategyParameters p) {
                return c.inPosition()
                        ? StrategyDecision.hold("holding")
                        : new StrategyDecision(StrategyAction.ENTER_LONG, 90.0, 0.0, 1.0, "entry");
            }
        };

        BacktestResult result = new BacktestEngine(BacktestEngine.Config.frictionless())
                .run(withStop, dataset, StrategyParameters.empty());

        assertTrue(result.hasTrades());
        assertEquals(90.0, result.trades().get(0).exitPrice(), 1e-9,
                "the stop price must be the exit fill");
    }

    @Test
    void datasetRejectsNonChronologicalBars() {
        List<PriceBar> outOfOrder = List.of(
                bar(2, 100, 101, 99, 100),
                bar(1, 100, 101, 99, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new HistoricalDataset("AAPL", outOfOrder));
    }

    @Test
    void datasetRequiresAtLeastTwoBars() {
        assertThrows(IllegalArgumentException.class,
                () -> new HistoricalDataset("AAPL", List.of(bar(0, 1, 1, 1, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> new HistoricalDataset("AAPL", List.of()));
    }

    @Test
    void configRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new BacktestEngine.Config(0.0, 5, 3, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BacktestEngine.Config(100, -1, 3, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BacktestEngine.Config(100, 5, 3, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new BacktestEngine.Config(100, 5, 3, 1.5));
    }

    @Test
    void noTradesMeansFlatEquity() {
        TradingStrategy neverTrades = new TradingStrategy() {
            @Override public String id() { return "idle"; }
            @Override public String name() { return "Idle"; }
            @Override public String description() { return "test; not a trained model"; }
            @Override public StrategyParameters defaultParameters() { return StrategyParameters.empty(); }
            @Override public boolean canEvaluate(StrategyContext c, StrategyParameters p) { return true; }
            @Override public StrategyDecision evaluate(StrategyContext c, StrategyParameters p) {
                return StrategyDecision.hold("idle");
            }
        };
        BacktestResult result = new BacktestEngine(BacktestEngine.Config.defaults())
                .run(neverTrades, ramp(100, 200, 20), StrategyParameters.empty());

        assertFalse(result.hasTrades());
        assertEquals(0, result.metrics().tradeCount());
        assertEquals(0.0, result.metrics().totalReturnPercent(), 1e-9);
    }
}
