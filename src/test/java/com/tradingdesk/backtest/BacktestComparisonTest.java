package com.tradingdesk.backtest;

import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.PriceBar;
import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.StrategyRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestComparisonTest {

    private static final Instrument AAPL = new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY);
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    /** A long, wavy series with several trends so different strategies can distinguish themselves. */
    private static HistoricalDataset wavyDataset() {
        List<PriceBar> bars = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < 300; i++) {
            // Deterministic wave: enough regime changes for crossovers and reversals to trigger.
            double drift = Math.sin(i / 9.0) * 1.2 + Math.cos(i / 23.0) * 0.8;
            double open = price;
            price = Math.max(1.0, price + drift);
            bars.add(new PriceBar(AAPL, open, Math.max(open, price) + 0.4,
                    Math.min(open, price) - 0.4, price, 1_000, T0.plusSeconds(86_400L * i)));
        }
        return new HistoricalDataset("AAPL", bars);
    }

    @Test
    void comparesEveryRegisteredStrategyOnTheSameData() {
        HistoricalDataset dataset = wavyDataset();
        BacktestEngine engine = new BacktestEngine(BacktestEngine.Config.defaults());
        StrategyRegistry registry = new StrategyRegistry();

        List<BacktestComparison.Row> rows = new BacktestComparison(engine).compare(registry, dataset);

        assertEquals(registry.size(), rows.size(), "one row per registered strategy");
        for (BacktestComparison.Row row : rows) {
            assertTrue(row.metrics() != null);
            assertTrue(row.strategyName() != null && !row.strategyName().isBlank());
        }
    }

    @Test
    void rowsAreSortedByTotalReturnDescending() {
        List<BacktestComparison.Row> rows = new BacktestComparison(
                new BacktestEngine(BacktestEngine.Config.defaults()))
                .compare(new StrategyRegistry(), wavyDataset());

        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).metrics().totalReturnPercent()
                            >= rows.get(i).metrics().totalReturnPercent(),
                    "comparison must be ordered best-first");
        }
    }

    @Test
    void comparisonUsesIdenticalConfigurationForAllStrategies() {
        HistoricalDataset dataset = wavyDataset();
        BacktestEngine engine = new BacktestEngine(
                new BacktestEngine.Config(50_000.0, 10.0, 5.0, 0.5));

        List<BacktestComparison.Row> rows = new BacktestComparison(engine)
                .compare(new StrategyRegistry(), dataset);

        for (BacktestComparison.Row row : rows) {
            assertEquals(50_000.0, row.metrics().initialCapital(), 1e-9,
                    "every strategy must start from the same capital for a fair comparison");
        }
    }

    @Test
    void explicitStrategyListCanBeCompared() {
        List<BacktestComparison.Row> rows = new BacktestComparison(
                new BacktestEngine(BacktestEngine.Config.frictionless()))
                .compare(new StrategyRegistry().all().stream().limit(2).toList(),
                        wavyDataset(), StrategyParameters.empty());

        assertEquals(2, rows.size());
    }
}
