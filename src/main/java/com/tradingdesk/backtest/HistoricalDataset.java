package com.tradingdesk.backtest;

import com.tradingdesk.model.PriceBar;

import java.util.List;
import java.util.Objects;

/**
 * An immutable historical dataset for backtesting, with look-ahead prevented by construction.
 *
 * <p>The full bars are private. {@link #contextUpTo(int)} is the only way to obtain data for a
 * decision, and it returns a truncated view, so a strategy cannot read a future bar even by
 * accident.
 */
public final class HistoricalDataset {

    private final String symbol;
    private final List<PriceBar> bars;

    public HistoricalDataset(String symbol, List<PriceBar> bars) {
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(bars, "bars");
        if (bars.size() < 2) {
            throw new IllegalArgumentException("a dataset needs at least 2 bars");
        }
        for (int i = 1; i < bars.size(); i++) {
            if (bars.get(i).openedAt().isBefore(bars.get(i - 1).openedAt())) {
                throw new IllegalArgumentException("bars must be in chronological order");
            }
        }
        this.bars = List.copyOf(bars);
    }

    public String symbol() {
        return symbol;
    }

    public int size() {
        return bars.size();
    }

    /** The bar at {@code index}. Package-private so external callers cannot look ahead. */
    PriceBar barAt(int index) {
        return bars.get(index);
    }

    /** The full bar list, for reporting only — never for a strategy decision. */
    public List<PriceBar> allBars() {
        return bars;
    }

    /**
     * Builds a {@code StrategyContext} containing only bars {@code 0..index} inclusive.
     *
     * <p>This is the look-ahead guard: the arrays handed to the strategy physically stop at the
     * decision bar.
     */
    public com.tradingdesk.strategy.StrategyContext contextUpTo(int index, boolean inPosition) {
        if (index < 0 || index >= bars.size()) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        int length = index + 1;
        double[] closes = new double[length];
        double[] highs = new double[length];
        double[] lows = new double[length];
        double[] volumes = new double[length];

        for (int i = 0; i < length; i++) {
            PriceBar bar = bars.get(i);
            closes[i] = bar.close();
            highs[i] = bar.high();
            lows[i] = bar.low();
            volumes[i] = bar.volume();
        }
        return new com.tradingdesk.strategy.StrategyContext(closes, highs, lows, volumes, index, inPosition);
    }
}
