package com.tradingdesk.backtest;

import com.tradingdesk.strategy.StrategyParameters;
import com.tradingdesk.strategy.StrategyRegistry;
import com.tradingdesk.strategy.TradingStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs several strategies over the <em>same</em> dataset and configuration, so the resulting metrics
 * are directly comparable. This is what makes "which strategy is better here?" a meaningful
 * question rather than apples-to-oranges.
 */
public class BacktestComparison {

    /** One row of the comparison table. */
    public record Row(String strategyId, String strategyName, BacktestMetrics metrics) {
    }

    private final BacktestEngine engine;

    public BacktestComparison(BacktestEngine engine) {
        this.engine = engine;
    }

    /**
     * Runs every registered strategy and returns the rows sorted by total return, best first.
     */
    public List<Row> compare(StrategyRegistry registry, HistoricalDataset dataset) {
        List<Row> rows = new ArrayList<>();
        for (TradingStrategy strategy : registry.all()) {
            BacktestResult result = engine.run(strategy, dataset, strategy.defaultParameters());
            rows.add(new Row(strategy.id(), strategy.name(), result.metrics()));
        }
        rows.sort(Comparator.comparingDouble((Row row) -> row.metrics().totalReturnPercent()).reversed());
        return rows;
    }

    /** Runs an explicit set of strategies, useful for comparing custom parameterisations. */
    public List<Row> compare(List<TradingStrategy> strategies, HistoricalDataset dataset,
                             StrategyParameters parameters) {
        List<Row> rows = new ArrayList<>();
        for (TradingStrategy strategy : strategies) {
            BacktestResult result = engine.run(strategy, dataset, parameters);
            rows.add(new Row(strategy.id(), strategy.name(), result.metrics()));
        }
        rows.sort(Comparator.comparingDouble((Row row) -> row.metrics().totalReturnPercent()).reversed());
        return rows;
    }
}
