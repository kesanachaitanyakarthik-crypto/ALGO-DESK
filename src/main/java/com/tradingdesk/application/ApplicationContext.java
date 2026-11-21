package com.tradingdesk.application;

import com.tradingdesk.concurrency.ConcurrentPriceCache;
import com.tradingdesk.concurrency.ExecutorManager;
import com.tradingdesk.concurrency.MarketEventQueue;
import com.tradingdesk.concurrency.MetricsRegistry;
import com.tradingdesk.concurrency.WorkerRegistry;
import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.database.MySqlPersistence;
import com.tradingdesk.database.Persistence;
import com.tradingdesk.model.Instrument;
import com.tradingdesk.model.InstrumentType;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.PortfolioSnapshot;
import com.tradingdesk.model.SystemMetric;
import com.tradingdesk.orders.DefaultOrderManagementSystem;
import com.tradingdesk.orders.OrderManagementSystem;
import com.tradingdesk.orders.OrderResult;
import com.tradingdesk.portfolio.DefaultPortfolioManager;
import com.tradingdesk.portfolio.PortfolioManager;
import com.tradingdesk.prediction.PredictionConfig;
import com.tradingdesk.prediction.PredictionEngine;
import com.tradingdesk.prediction.RuleBasedPredictionEngine;
import com.tradingdesk.processor.QueuedMarketDataProcessor;
import com.tradingdesk.risk.DefaultRiskManager;
import com.tradingdesk.risk.RiskConfiguration;
import com.tradingdesk.risk.RiskManager;
import com.tradingdesk.service.DefaultTradingService;
import com.tradingdesk.service.TradingPipeline;
import com.tradingdesk.service.TradingService;
import com.tradingdesk.simulator.SimulatedMarketDataSimulator;
import com.tradingdesk.simulator.SimulationRunner;
import com.tradingdesk.simulator.SimulatorConfig;
import com.tradingdesk.utils.Logging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApplicationContext implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(ApplicationContext.class.getName());

    private static final int QUEUE_CAPACITY = 25_000;
    private static final int WINDOW_SIZE = 20;
    private static final int ROC_PERIOD = 5;
    private static final long SIGNAL_COOLDOWN_MILLIS = 250L;

    private final ExecutorManager executors;
    private final MarketEventQueue<MarketEvent> queue = new MarketEventQueue<>(QUEUE_CAPACITY);
    private final MetricsRegistry metrics = new MetricsRegistry();
    private final WorkerRegistry workers = new WorkerRegistry();
    private final ConcurrentPriceCache<String> prices = new ConcurrentPriceCache<>();
    private final List<Instrument> watchlist;
    private final double[] startingPrices = {189.45, 421.10, 158.22, 183.75, 121.60};

    private final PortfolioManager portfolioManager;
    private final OrderManagementSystem orderManagementSystem;
    private final SimulationRunner runner;
    private final QueuedMarketDataProcessor processor;
    private final TradingPipeline pipeline;
    private final DefaultTradingService tradingService;
    private final ScheduledExecutorService persistenceTimer;
    private final Persistence persistence;
    private volatile boolean closed;

    public ApplicationContext(boolean connectToDatabase) {
        Logging.configure(Level.INFO);
        int workersCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        this.executors = new ExecutorManager(workersCount);
        this.watchlist = List.of(
                new Instrument("AAPL", "Apple Inc.", InstrumentType.EQUITY),
                new Instrument("MSFT", "Microsoft Corp.", InstrumentType.EQUITY),
                new Instrument("GOOG", "Alphabet Inc.", InstrumentType.EQUITY),
                new Instrument("AMZN", "Amazon.com Inc.", InstrumentType.EQUITY),
                new Instrument("NVDA", "NVIDIA Corp.", InstrumentType.EQUITY));

        this.persistence = connectToDatabase ? connectPersistence() : null;

        if (persistence != null) {
            watchlist.forEach(persistence.instruments()::upsert);
        }

        SimulatedMarketDataSimulator simulator = new SimulatedMarketDataSimulator(
                watchlist, startingPrices, event -> { }, SimulatorConfig.defaults());
        this.runner = new SimulationRunner(simulator, queue, metrics);

        RiskManager riskManager = new DefaultRiskManager(RiskConfiguration.defaults());
        this.portfolioManager = new DefaultPortfolioManager(1_000_000.0);
        this.orderManagementSystem = new DefaultOrderManagementSystem(riskManager, portfolioManager, 100L);
        PredictionEngine predictionEngine = new RuleBasedPredictionEngine(PredictionConfig.defaults());

        this.pipeline = new TradingPipeline(prices, predictionEngine, orderManagementSystem,
                portfolioManager, () -> tradingService().selectedSymbols(),
                WINDOW_SIZE, ROC_PERIOD, SIGNAL_COOLDOWN_MILLIS);

        this.processor = new QueuedMarketDataProcessor(
                queue, executors, metrics, workers, List.of(pipeline));

        this.tradingService = new DefaultTradingService(
                runner, processor, executors, metrics, workers, queue,
                pipeline, orderManagementSystem, portfolioManager, prices, watchlist);

        if (persistence != null) {
            tradingService.setDatabaseStatus("Connected: " + persistence.database().config().url());
            pipeline.addOrderListener(this::persistOrder);
        } else {
            tradingService.setDatabaseStatus("In-memory only (MySQL not configured)");
        }

        this.persistenceTimer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "td-persistence");
            thread.setDaemon(true);
            return thread;
        });
        persistenceTimer.scheduleAtFixedRate(this::persistMetrics, 5, 5, TimeUnit.SECONDS);
    }

    private Persistence connectPersistence() {
        try {
            Persistence connected = MySqlPersistence.connect();
            List<String> missing = connected.migrator().missingTables();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Missing tables: " + missing);
            }
            LOGGER.info("MySQL connected and schema verified");
            return connected;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "MySQL unavailable, running in-memory only: " + e.getMessage());
            return null;
        }
    }

    private void persistOrder(OrderResult result) {
        Persistence active = persistence;
        if (active == null) {
            return;
        }
        Order order = result.order();
        if (order.status() == OrderStatus.EXECUTED && result.execution().isPresent()) {
            active.orderPersistence().persistExecution(order, result.execution().get());
        } else if (order.status() == OrderStatus.REJECTED) {
            active.orders().insert(order);
        }
    }

    private void persistMetrics() {
        Persistence active = persistence;
        if (active == null || closed) {
            return;
        }
        try {
            Instant now = Instant.now();
            List<SystemMetric> batch = new ArrayList<>(4);
            batch.add(new SystemMetric("events_processed", metrics.eventsProcessed(), "count", now));
            batch.add(new SystemMetric("average_latency", metrics.averageLatency().toNanos() / 1000.0, "micros", now));
            batch.add(new SystemMetric("queue_depth", queue.depth(), "count", now));
            batch.add(new SystemMetric("throughput", metrics.throughputPerSecond(), "events/s", now));
            active.metrics().insertBatch(batch);

            PortfolioSnapshot snapshot = portfolioManager.snapshot(prices.snapshot());
            active.portfolioSnapshots().insert(snapshot);

            List<com.tradingdesk.model.TradeSignal> signals = pipeline.recentSignals(20);
            if (!signals.isEmpty()) {
                active.signals().insertBatch(signals);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Metric persistence failed: " + e.getMessage());
            tradingService.setDatabaseStatus("Database error: " + e.getMessage());
        }
    }

    public TradingService tradingService() {
        return tradingService;
    }

    public TradingPipeline pipeline() {
        return pipeline;
    }

    public Persistence persistence() {
        return persistence;
    }

    public Optional<DatabaseConnection> database() {
        return Optional.ofNullable(persistence).map(Persistence::database);
    }

    public int workerCount() {
        return executors.workerCount();
    }

    public void start() {
        tradingService.start();
    }

    public void stop() {
        tradingService.stop();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        tradingService.stop();
        persistenceTimer.shutdownNow();
        processor.stop();
        runner.close();
        executors.shutdown();
        if (persistence != null) {
            persistence.database().close();
        }
    }
}
