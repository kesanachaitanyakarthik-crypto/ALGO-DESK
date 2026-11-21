package com.tradingdesk.service;

import com.tradingdesk.concurrency.ConcurrentPriceCache;
import com.tradingdesk.indicators.IndicatorEngine;
import com.tradingdesk.indicators.IndicatorSnapshot;
import com.tradingdesk.indicators.RollingIndicatorEngine;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.RiskAlert;
import com.tradingdesk.model.SignalType;
import com.tradingdesk.model.TradeSignal;
import com.tradingdesk.orders.OrderManagementSystem;
import com.tradingdesk.orders.OrderResult;
import com.tradingdesk.portfolio.PortfolioManager;
import com.tradingdesk.prediction.PredictionEngine;
import com.tradingdesk.processor.MarketEventConsumer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TradingPipeline implements MarketEventConsumer {

    private final ConcurrentMap<String, IndicatorEngine> indicators = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastSignalAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> volumes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, double[]> quotes = new ConcurrentHashMap<>();
    private final ConcurrentPriceCache<String> prices;
    private final PredictionEngine predictionEngine;
    private final OrderManagementSystem orderManagementSystem;
    private final PortfolioManager portfolioManager;
    private final CopyOnWriteArrayList<TradeSignal> signals = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RiskAlert> alerts = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<OrderResult>> orderListeners = new CopyOnWriteArrayList<>();
    private final Supplier<Set<String>> activeSymbols;
    private final int windowSize;
    private final int rocPeriod;
    private final long signalCooldownMillis;
    private volatile boolean orderFlowEnabled = true;

    public TradingPipeline(ConcurrentPriceCache<String> prices,
                           PredictionEngine predictionEngine,
                           OrderManagementSystem orderManagementSystem,
                           PortfolioManager portfolioManager,
                           Supplier<Set<String>> activeSymbols,
                           int windowSize,
                           int rocPeriod,
                           long signalCooldownMillis) {
        this.prices = Objects.requireNonNull(prices, "prices");
        this.predictionEngine = Objects.requireNonNull(predictionEngine, "predictionEngine");
        this.orderManagementSystem = Objects.requireNonNull(orderManagementSystem, "orderManagementSystem");
        this.portfolioManager = Objects.requireNonNull(portfolioManager, "portfolioManager");
        this.activeSymbols = Objects.requireNonNull(activeSymbols, "activeSymbols");
        this.windowSize = windowSize;
        this.rocPeriod = rocPeriod;
        this.signalCooldownMillis = signalCooldownMillis;
    }

    public void addOrderListener(Consumer<OrderResult> listener) {
        orderListeners.add(listener);
    }

    @Override
    public void consume(MarketEvent event) {
        String symbol = event.instrument().symbol();
        prices.update(symbol, event.lastPrice());
        volumes.computeIfAbsent(symbol, key -> new LongAdder()).add(event.volume());
        quotes.put(symbol, new double[]{event.bidPrice(), event.askPrice()});

        if (!activeSymbols.get().contains(symbol)) {
            return;
        }

        IndicatorEngine engine = indicators.computeIfAbsent(symbol,
                key -> new RollingIndicatorEngine(windowSize, rocPeriod));
        engine.ingest(event);

        long now = System.currentTimeMillis();
        Long previous = lastSignalAt.get(symbol);
        if (previous != null && now - previous < signalCooldownMillis) {
            return;
        }

        IndicatorSnapshot snapshot = engine.latest();
        TradeSignal signal = predictionEngine.predict(event.instrument(), snapshot);
        lastSignalAt.put(symbol, now);
        signals.add(signal);
        trim(signals);

        if (signal.type() != SignalType.HOLD && orderFlowEnabled) {
            OrderResult result = orderManagementSystem.submit(signal, event.lastPrice());
            if (result.rejected()) {
                alerts.addAll(result.violations());
                trim(alerts);
            }
            for (Consumer<OrderResult> listener : orderListeners) {
                listener.accept(result);
            }
        }
    }

    private static <T> void trim(List<T> list) {
        if (list instanceof CopyOnWriteArrayList<T> cow) {
            while (cow.size() > 500) {
                cow.remove(0);
            }
        }
    }

    public List<TradeSignal> recentSignals(int limit) {
        return tail(signals, limit);
    }

    public List<RiskAlert> recentAlerts(int limit) {
        return tail(alerts, limit);
    }

    public IndicatorSnapshot indicatorsFor(String symbol) {
        IndicatorEngine engine = indicators.get(symbol);
        return engine == null ? IndicatorSnapshot.empty(Instant.now()) : engine.latest();
    }

    public Map<String, Long> volumes() {
        Map<String, Long> copy = new java.util.LinkedHashMap<>();
        volumes.forEach((symbol, adder) -> copy.put(symbol, adder.sum()));
        return copy;
    }

    public java.util.OptionalDouble averageSpread() {
        return quotes.values().stream()
                .mapToDouble(quote -> quote[1] - quote[0])
                .average();
    }

    public void setOrderFlowEnabled(boolean enabled) {
        this.orderFlowEnabled = enabled;
    }

    public void reset() {
        indicators.clear();
        signals.clear();
        alerts.clear();
        lastSignalAt.clear();
        volumes.clear();
        quotes.clear();
        prices.clear();
    }

    private static <T> List<T> tail(List<T> source, int limit) {
        int bounded = Math.max(0, Math.min(limit, source.size()));
        return List.copyOf(source.subList(source.size() - bounded, source.size()));
    }
}
