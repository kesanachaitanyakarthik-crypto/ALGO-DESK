package com.tradingdesk.strategy;

import java.util.Map;

/**
 * A configurable trading strategy.
 *
 * <p>Implementations must be pure with respect to {@link StrategyContext}: given the same visible
 * series they must return the same decision, and they may never read beyond the current index.
 */
public interface TradingStrategy {

    /** Stable identifier used by the registry and persistence. */
    String id();

    /** Display name for the UI. */
    String name();

    /** One-line description, including the honest caveat that this is rules, not machine learning. */
    String description();

    /** Default parameters for this strategy. */
    StrategyParameters defaultParameters();

    /**
     * Evaluates the strategy at the end of the supplied series.
     *
     * @throws IllegalArgumentException if the series is too short for the configured parameters
     */
    StrategyDecision evaluate(StrategyContext context, StrategyParameters parameters);

    /** True when the series is long enough to produce a meaningful decision. */
    boolean canEvaluate(StrategyContext context, StrategyParameters parameters);

    /** Parameter metadata for a generic settings UI: name -> default value. */
    default Map<String, Double> parameterDefaults() {
        return defaultParameters().asMap();
    }
}
