package com.tradingdesk.indicators;

import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.PriceBar;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class RollingIndicatorEngine implements IndicatorEngine {

    private final int windowSize;
    private final int rocPeriod;
    private final Deque<Double> prices = new ArrayDeque<>();
    private final Deque<Double> imbalances = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();

    public RollingIndicatorEngine(int windowSize, int rocPeriod) {
        if (windowSize <= 0 || rocPeriod <= 0) {
            throw new IllegalArgumentException("window sizes must be positive");
        }
        this.windowSize = windowSize;
        this.rocPeriod = rocPeriod;
    }

    @Override
    public void ingest(MarketEvent event) {
        lock.lock();
        try {
            append(event.lastPrice());
            imbalances.addLast(event.simulatedOrderBookImbalance() / 100.0);
            while (imbalances.size() > windowSize) {
                imbalances.removeFirst();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void ingest(PriceBar bar) {
        lock.lock();
        try {
            append(bar.close());
        } finally {
            lock.unlock();
        }
    }

    private void append(double price) {
        prices.addLast(price);
        while (prices.size() > windowSize) {
            prices.removeFirst();
        }
    }

    @Override
    public IndicatorSnapshot latest() {
        lock.lock();
        try {
            List<Double> window = List.copyOf(prices);
            if (window.isEmpty()) {
                return IndicatorSnapshot.empty(Instant.now());
            }
            int period = Math.min(windowSize, window.size());
            double sma = TechnicalIndicators.simpleMovingAverage(window, period);
            double ema = TechnicalIndicators.exponentialMovingAverage(window, period);
            double roc = window.size() > rocPeriod
                    ? TechnicalIndicators.rateOfChange(window, rocPeriod)
                    : 0.0;
            double volatility = TechnicalIndicators.rollingVolatility(window, period);
            double imbalance = imbalances.isEmpty()
                    ? 0.0
                    : imbalances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            return new IndicatorSnapshot(sma, ema, roc, volatility, imbalance,
                    window.size(), Instant.now());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void reset() {
        lock.lock();
        try {
            prices.clear();
            imbalances.clear();
        } finally {
            lock.unlock();
        }
    }

    public int windowSize() {
        return windowSize;
    }

    public int rocPeriod() {
        return rocPeriod;
    }
}
