package com.tradingdesk.simulator;

import com.tradingdesk.concurrency.MarketEventQueue;
import com.tradingdesk.concurrency.MetricsRegistry;
import com.tradingdesk.model.MarketEvent;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives the simulator on a scheduled tick and publishes into a bounded queue.
 * When the queue is full the event is counted as dropped (backpressure), never blocked.
 */
public class SimulationRunner implements MarketEventPublisher, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SimulationRunner.class.getName());

    private final SimulatedMarketDataSimulator simulator;
    private final MarketEventQueue<MarketEvent> queue;
    private final MetricsRegistry metrics;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile ScheduledFuture<?> task;
    /**
     * When real market data is streaming, the simulator must stop publishing: mixing generated
     * prices into the same queue would put fake numbers next to genuine ones and make the LIVE
     * badge a lie. The workers keep running; only the synthetic source is muted.
     */
    private volatile boolean syntheticPublishingEnabled = true;

    public SimulationRunner(SimulatedMarketDataSimulator simulator,
                            MarketEventQueue<MarketEvent> queue,
                            MetricsRegistry metrics) {
        this.simulator = Objects.requireNonNull(simulator, "simulator");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "td-simulator");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        simulator.start();
        task = scheduler.scheduleAtFixedRate(this::tick,
                0, simulator.config().tickIntervalMillis(), TimeUnit.MILLISECONDS);
    }

    /** Mutes or unmutes the synthetic source without disturbing the worker pool. */
    public void setSyntheticPublishingEnabled(boolean enabled) {
        this.syntheticPublishingEnabled = enabled;
    }

    public boolean isSyntheticPublishingEnabled() {
        return syntheticPublishingEnabled;
    }

    private void tick() {
        if (!syntheticPublishingEnabled) {
            return;
        }
        try {
            publish(simulator.nextEvent());
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Simulator tick failed", e);
        }
    }

    @Override
    public void publish(MarketEvent event) {
        if (!queue.offer(event)) {
            dropped.incrementAndGet();
            metrics.recordDropped();
            return;
        }
        published.incrementAndGet();
        metrics.recordQueueDepth(queue.depth());
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        simulator.stop();
        ScheduledFuture<?> current = task;
        if (current != null) {
            current.cancel(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public long published() {
        return published.get();
    }

    public long dropped() {
        return dropped.get();
    }

    public SimulatedMarketDataSimulator simulator() {
        return simulator;
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }
}
