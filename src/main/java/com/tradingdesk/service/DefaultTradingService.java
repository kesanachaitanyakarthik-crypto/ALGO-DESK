package com.tradingdesk.service;

import com.tradingdesk.concurrency.ConcurrentPriceCache;
import com.tradingdesk.concurrency.ExecutorManager;
import com.tradingdesk.concurrency.MarketEventQueue;
import com.tradingdesk.concurrency.MetricsRegistry;
import com.tradingdesk.concurrency.WorkerRegistry;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.RiskAlert;
import com.tradingdesk.model.TradeSignal;
import com.tradingdesk.orders.OrderManagementSystem;
import com.tradingdesk.orders.OrderStatistics;
import com.tradingdesk.portfolio.PortfolioManager;
import com.tradingdesk.processor.QueuedMarketDataProcessor;
import com.tradingdesk.simulator.SimulatedMarketDataSimulator;
import com.tradingdesk.simulator.SimulationRunner;
import com.tradingdesk.utils.Formatters;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultTradingService implements TradingService {

    private final SimulationRunner runner;
    private final QueuedMarketDataProcessor processor;
    private final ExecutorManager executors;
    private final MetricsRegistry metrics;
    private final WorkerRegistry workers;
    private final MarketEventQueue<MarketEvent> queue;
    private final TradingPipeline pipeline;
    private final OrderManagementSystem orderManagementSystem;
    private final PortfolioManager portfolioManager;
    private final ConcurrentPriceCache<String> prices;
    private final List<Instrument> watchlist;
    private final ConcurrentMap<String, Long> volumes = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> logs = new CopyOnWriteArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private volatile String databaseStatus = "Not connected";

    public DefaultTradingService(SimulationRunner runner,
                                 QueuedMarketDataProcessor processor,
                                 ExecutorManager executors,
                                 MetricsRegistry metrics,
                                 WorkerRegistry workers,
                                 MarketEventQueue<MarketEvent> queue,
                                 TradingPipeline pipeline,
                                 OrderManagementSystem orderManagementSystem,
                                 PortfolioManager portfolioManager,
                                 ConcurrentPriceCache<String> prices,
                                 List<Instrument> watchlist) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.orderManagementSystem = Objects.requireNonNull(orderManagementSystem, "orderManagementSystem");
        this.portfolioManager = Objects.requireNonNull(portfolioManager, "portfolioManager");
        this.prices = Objects.requireNonNull(prices, "prices");
        this.watchlist = List.copyOf(watchlist);
        watchlist.forEach(instrument -> selected.add(instrument.symbol()));
        pipeline.addOrderListener(result -> {
            if (result.rejected()) {
                log("REJECTED " + result.order().instrument().symbol() + " - " + result.rejectionReason());
            } else {
                log("EXECUTED " + result.order().side() + " " + result.order().instrument().symbol()
                        + " @ " + Formatters.price(result.execution().get().price()));
            }
        });
    }

    @Override
    public void start() {
        processor.start();
        metrics.reset();
        runner.start();
        log("Simulation started");
    }

    @Override
    public void stop() {
        runner.stop();
        processor.stop();
        log("Simulation stopped");
    }

    @Override
    public boolean isRunning() {
        return runner.isRunning();
    }

    @Override
    public void reset() {
        pipeline.reset();
        orderManagementSystem.reset();
        metrics.reset();
        volumes.clear();
        log("State reset");
    }

    @Override
    public Set<String> availableSymbols() {
        Set<String> symbols = new LinkedHashSet<>();
        watchlist.forEach(instrument -> symbols.add(instrument.symbol()));
        return symbols;
    }

    @Override
    public Set<String> selectedSymbols() {
        synchronized (selected) {
            return new LinkedHashSet<>(selected);
        }
    }

    @Override
    public void selectSymbols(Set<String> symbols) {
        synchronized (selected) {
            selected.clear();
            for (String symbol : symbols) {
                if (availableSymbols().contains(symbol)) {
                    selected.add(symbol);
                }
            }
            if (selected.isEmpty()) {
                watchlist.forEach(instrument -> selected.add(instrument.symbol()));
            }
        }
        log("Symbols selected: " + String.join(", ", selectedSymbols()));
    }

    @Override
    public Map<String, Double> latestPrices() {
        Map<String, Double> all = prices.snapshot();
        Map<String, Double> filtered = new LinkedHashMap<>();
        for (String symbol : selectedSymbols()) {
            Double price = all.get(symbol);
            if (price != null) {
                filtered.put(symbol, price);
            }
        }
        return filtered;
    }

    @Override
    public Map<String, Long> priceVolumes() {
        Map<String, Long> all = pipeline.volumes();
        Map<String, Long> filtered = new LinkedHashMap<>();
        for (String symbol : selectedSymbols()) {
            filtered.put(symbol, all.getOrDefault(symbol, 0L));
        }
        return filtered;
    }

    @Override
    public double averageSpread() {
        return pipeline.averageSpread().orElse(0.0);
    }

    @Override
    public com.tradingdesk.indicators.IndicatorSnapshot indicatorsFor(String symbol) {
        return pipeline.indicatorsFor(symbol);
    }

    @Override
    public List<TradeSignal> recentSignals(int limit) {
        return pipeline.recentSignals(limit);
    }

    @Override
    public List<Order> openOrders(int limit) {
        return orderManagementSystem.openOrders().stream().limit(limit).toList();
    }

    @Override
    public List<Order> orderHistory(int limit) {
        return orderManagementSystem.history(limit);
    }

    @Override
    public List<RiskAlert> recentAlerts(int limit) {
        return pipeline.recentAlerts(limit);
    }

    @Override
    public PortfolioSnapshot portfolioSnapshot() {
        return portfolioManager.snapshot(prices.snapshot());
    }

    @Override
    public OrderStatistics orderStatistics() {
        return orderManagementSystem.statistics();
    }

    @Override
    public long eventsProcessed() {
        return metrics.eventsProcessed();
    }

    @Override
    public long eventsDropped() {
        return metrics.eventsDropped();
    }

    @Override
    public int queueDepth() {
        return queue.depth();
    }

    @Override
    public int queueCapacity() {
        return queue.capacity();
    }

    @Override
    public int activeWorkerThreads() {
        return workers.activeWorkers();
    }

    @Override
    public double averageLatencyMicros() {
        return MetricsRegistry.micros(metrics.averageLatency());
    }

    @Override
    public double peakLatencyMicros() {
        return MetricsRegistry.micros(metrics.peakLatency());
    }

    @Override
    public double throughputPerSecond() {
        return metrics.throughputPerSecond();
    }

    @Override
    public String databaseStatus() {
        return databaseStatus;
    }

    public void setDatabaseStatus(String status) {
        this.databaseStatus = status;
    }

    @Override
    public List<String> recentLogs(int limit) {
        int bounded = Math.max(0, Math.min(limit, logs.size()));
        List<String> tail = new ArrayList<>(logs.subList(logs.size() - bounded, logs.size()));
        return List.copyOf(tail);
    }

    @Override
    public List<Instrument> watchlist() {
        return watchlist;
    }

    public void log(String message) {
        logs.add(Formatters.time(Instant.now()) + "  " + message);
        while (logs.size() > 400) {
            logs.remove(0);
        }
    }

    public Optional<SimulatedMarketDataSimulator> simulator() {
        return Optional.of(runner.simulator());
    }

    public TradingPipeline pipeline() {
        return pipeline;
    }

    public ExecutorManager executors() {
        return executors;
    }
}
