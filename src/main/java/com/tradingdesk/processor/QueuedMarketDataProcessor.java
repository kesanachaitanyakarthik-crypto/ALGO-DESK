package com.tradingdesk.processor;

import com.tradingdesk.concurrency.ExecutorManager;
import com.tradingdesk.concurrency.MarketEventQueue;
import com.tradingdesk.concurrency.MetricsRegistry;
import com.tradingdesk.concurrency.WorkerRegistry;
import com.tradingdesk.model.MarketEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueuedMarketDataProcessor implements MarketDataProcessor {

    private static final Logger LOGGER = Logger.getLogger(QueuedMarketDataProcessor.class.getName());

    private final MarketEventQueue<MarketEvent> queue;
    private final ExecutorManager executors;
    private final MetricsRegistry metrics;
    private final WorkerRegistry workers;
    private final List<MarketEventConsumer> consumers;
    private volatile boolean running;
    private volatile CountDownLatch stoppedWorkers = new CountDownLatch(0);

    public QueuedMarketDataProcessor(MarketEventQueue<MarketEvent> queue,
                                     ExecutorManager executors,
                                     MetricsRegistry metrics,
                                     WorkerRegistry workers,
                                     List<MarketEventConsumer> consumers) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.consumers = List.copyOf(consumers);
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        stoppedWorkers = new CountDownLatch(executors.workerCount());
        for (int i = 0; i < executors.workerCount(); i++) {
            executors.workerPool().submit(this::processLoop);
        }
    }

    private void processLoop() {
        workers.workerStarted();
        try {
            while (running) {
                MarketEvent event;
                try {
                    event = queue.poll(200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (event == null) {
                    continue;
                }
                process(event);
            }
        } finally {
            workers.workerStopped();
            stoppedWorkers.countDown();
        }
    }

    void process(MarketEvent event) {
        Instant startedAt = Instant.now();
        try {
            for (MarketEventConsumer consumer : consumers) {
                consumer.consume(event);
            }
            metrics.recordProcessed(Duration.between(startedAt, Instant.now()));
        } catch (RuntimeException e) {
            metrics.recordFailure();
            LOGGER.log(Level.FINE, "Market event processing failed", e);
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public boolean awaitWorkerShutdown(long timeoutMillis) {
        try {
            return stoppedWorkers.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public int queueDepth() {
        return queue.depth();
    }

    @Override
    public void close() {
        stop();
    }
}
