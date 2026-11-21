package com.tradingdesk.concurrency;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConcurrentPriceCache<K> {

    private final ConcurrentMap<K, Double> prices = new ConcurrentHashMap<>();

    public void update(K key, double price) {
        prices.put(key, price);
    }

    public Optional<Double> find(K key) {
        return Optional.ofNullable(prices.get(key));
    }

    public double getOrDefault(K key, double fallback) {
        return prices.getOrDefault(key, fallback);
    }

    public Map<K, Double> snapshot() {
        return new HashMap<>(prices);
    }

    public Collection<Double> values() {
        return java.util.List.copyOf(prices.values());
    }

    public int size() {
        return prices.size();
    }

    public void clear() {
        prices.clear();
    }
}
