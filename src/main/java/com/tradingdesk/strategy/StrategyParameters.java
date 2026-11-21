package com.tradingdesk.strategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validated, immutable strategy configuration.
 *
 * <p>Parameters are exposed as a typed map so the UI can list and edit them generically, while
 * strategies still read them by name with defaults and bounds checked once at construction.
 */
public final class StrategyParameters {

    private final Map<String, Double> values;

    private StrategyParameters(Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static StrategyParameters of(Map<String, Double> values) {
        Objects.requireNonNull(values, "values");
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("parameter " + entry.getKey() + " must be finite");
            }
        }
        return new StrategyParameters(values);
    }

    /** Builder-style helper for readable construction. */
    public static StrategyParameters empty() {
        return new StrategyParameters(Map.of());
    }

    public StrategyParameters with(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("parameter " + name + " must be finite");
        }
        Map<String, Double> copy = new LinkedHashMap<>(values);
        copy.put(name, value);
        return new StrategyParameters(copy);
    }

    public double get(String name, double fallback) {
        return values.getOrDefault(name, fallback);
    }

    public int getInt(String name, int fallback) {
        return (int) Math.round(values.getOrDefault(name, (double) fallback));
    }

    /**
     * Reads a parameter, falling back to {@code fallback} when it is not supplied, and requiring
     * the resulting value to fall within inclusive bounds.
     *
     * <p>The fallback matters: strategies publish sensible defaults, and a caller that overrides
     * only one parameter must not be forced to restate every other one.
     */
    public double require(String name, double fallback, double min, double max) {
        double value = values.getOrDefault(name, fallback);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("parameter " + name + " must be finite");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "parameter " + name + "=" + value + " must be within [" + min + ", " + max + "]");
        }
        return value;
    }

    public int requireInt(String name, int fallback, int min, int max) {
        int value = getInt(name, fallback);
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "parameter " + name + "=" + value + " must be within [" + min + ", " + max + "]");
        }
        return value;
    }

    public Map<String, Double> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
