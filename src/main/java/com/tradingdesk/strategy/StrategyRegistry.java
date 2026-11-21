package com.tradingdesk.strategy;

import com.tradingdesk.strategy.impl.BollingerReversalStrategy;
import com.tradingdesk.strategy.impl.MacdCrossoverStrategy;
import com.tradingdesk.strategy.impl.MomentumStrategy;
import com.tradingdesk.strategy.impl.MovingAverageCrossoverStrategy;
import com.tradingdesk.strategy.impl.RsiReversalStrategy;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Discoverable registry of the built-in strategies.
 *
 * <p>Registration is explicit (no reflection) so the set of available strategies is obvious and
 * stable, which keeps the UI and persistence simple.
 */
public class StrategyRegistry {

    private final Map<String, TradingStrategy> byId = new LinkedHashMap<>();

    public StrategyRegistry() {
        register(new MovingAverageCrossoverStrategy());
        register(new RsiReversalStrategy());
        register(new MacdCrossoverStrategy());
        register(new BollingerReversalStrategy());
        register(new MomentumStrategy());
    }

    public final void register(TradingStrategy strategy) {
        if (byId.containsKey(strategy.id())) {
            throw new IllegalArgumentException("duplicate strategy id: " + strategy.id());
        }
        byId.put(strategy.id(), strategy);
    }

    public Optional<TradingStrategy> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public TradingStrategy require(String id) {
        return find(id).orElseThrow(
                () -> new IllegalArgumentException("unknown strategy: " + id));
    }

    public Collection<TradingStrategy> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
