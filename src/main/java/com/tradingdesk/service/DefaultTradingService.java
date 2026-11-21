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
import com.tradingdesk.model.Side;
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
    private volatile String marketDataMode = "SIMULATED";
    private volatile com.tradingdesk.marketdata.MarketDataSourceSupervisor marketDataSupervisor;
    private volatile com.tradingdesk.paper.PaperBroker paperBroker;

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

    @Override
    public String marketDataStatus() {
        return marketDataMode;
    }

    @Override
    public double p50LatencyMicros() {
        return metrics.p50LatencyNanos() / 1_000.0;
    }

    @Override
    public double p95LatencyMicros() {
        return metrics.p95LatencyNanos() / 1_000.0;
    }

    @Override
    public double p99LatencyMicros() {
        return metrics.p99LatencyNanos() / 1_000.0;
    }

    @Override
    public long eventsRejected() {
        return metrics.eventsRejected();
    }

    @Override
    public double processCpuLoad() {
        return metrics.processCpuLoad();
    }

    @Override
    public PaperTradingView paperTrading() {
        com.tradingdesk.paper.PaperBroker broker = paperBroker;
        if (broker == null) {
            return new PaperTradingView("-", "-", "-", "-", "-", java.util.List.of(), 0);
        }
        var account = broker.account();
        java.util.List<PaperTradingView.Row> rows = broker.history(40).stream()
                .map(order -> new PaperTradingView.Row(
                        order.id().toString().substring(0, 8),
                        order.instrument().symbol(),
                        order.side().name(),
                        order.request().kind().name(),
                        Long.toString(order.quantity()),
                        Long.toString(order.filledQuantity()),
                        order.filledQuantity() == 0 ? "-" : Formatters.price(order.averagePrice()),
                        order.status().name(),
                        order.rejectionReason() == null ? "" : order.rejectionReason()))
                .toList();
        return new PaperTradingView(
                account.accountId(),
                Formatters.money(account.cash()),
                Formatters.money(account.usedMargin()),
                Formatters.money(account.equity()),
                Formatters.money(account.realizedPnl()),
                rows,
                broker.openOrders().size());
    }

    @Override
    public String placePaperOrder(String symbol, String side, String kind,
                                  long quantity, double price) {
        com.tradingdesk.paper.PaperBroker broker = paperBroker;
        if (broker == null) {
            return "Paper trading is not available";
        }
        Instrument instrument = watchlist.stream()
                .filter(candidate -> candidate.symbol().equals(symbol))
                .findFirst()
                .orElse(null);
        if (instrument == null) {
            return "Unknown instrument: " + symbol;
        }
        try {
            Side orderSide = Side.valueOf(side);
            com.tradingdesk.paper.OrderKind orderKind =
                    com.tradingdesk.paper.OrderKind.valueOf(kind);
            double referencePrice = price > 0.0
                    ? price
                    : latestPrices().getOrDefault(symbol, 0.0);
            if (referencePrice <= 0.0) {
                return "No market price available for " + symbol;
            }

            com.tradingdesk.paper.OrderRequest request = switch (orderKind) {
                case MARKET -> com.tradingdesk.paper.OrderRequest.market(
                        instrument, orderSide, quantity);
                case LIMIT -> com.tradingdesk.paper.OrderRequest.limit(
                        instrument, orderSide, quantity, price);
                case STOP -> com.tradingdesk.paper.OrderRequest.stop(
                        instrument, orderSide, quantity, price);
                case STOP_LIMIT -> com.tradingdesk.paper.OrderRequest.stopLimit(
                        instrument, orderSide, quantity, price, price);
                case TAKE_PROFIT -> com.tradingdesk.paper.OrderRequest.takeProfit(
                        instrument, orderSide, quantity, price);
            };

            var submission = broker.submit(request, referencePrice);
            if (submission.rejected()) {
                String reason = submission.order().rejectionReason();
                log("PAPER REJECTED " + symbol + ": " + reason);
                return "Rejected: " + reason;
            }
            log("PAPER " + orderKind + " " + orderSide + " " + quantity + " " + symbol);
            return "Order " + submission.order().status() + " (" + symbol + " "
                    + orderSide + " " + quantity + ")";
        } catch (IllegalArgumentException e) {
            return "Invalid order: " + e.getMessage();
        }
    }

    @Override
    public String runStressTest() {
        try {
            var report = new com.tradingdesk.concurrency.StressTestRunner().quickRun();
            log("Stress test: " + report.summary());
            return report.summary();
        } catch (RuntimeException e) {
            log("Stress test failed: " + e.getMessage());
            return "Stress test failed: " + e.getMessage();
        }
    }

    /** Attaches the market data supervisor so the UI can display the real source. */
    public void attachMarketData(com.tradingdesk.marketdata.MarketDataSourceSupervisor supervisor) {
        this.marketDataSupervisor = supervisor;
        this.marketDataMode = supervisor.display();
    }

    /** Attaches the paper broker so the Orders screen can place and list virtual orders. */
    public void attachPaperBroker(com.tradingdesk.paper.PaperBroker broker) {
        this.paperBroker = broker;
    }

    /** Records the mode actually achieved, for honest display. */
    public void setMarketDataMode(String mode) {
        this.marketDataMode = mode;
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
