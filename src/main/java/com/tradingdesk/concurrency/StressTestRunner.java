package com.tradingdesk.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Drives the bounded queue with several concurrent producers and a worker pool, and reports what was
 * actually measured.
 *
 * <p>This is the module the presentation's "concurrency stress testing" slide refers to. It exists to
 * demonstrate backpressure, throughput and latency under load — every figure it returns is measured
 * during the run, never assumed.
 */
public class StressTestRunner {

    /** Measured outcome of one stress run. */
    public record StressReport(
            int producers,
            int workers,
            long attempted,
            long accepted,
            long consumed,
            long dropped,
            long elapsedMillis,
            double throughputPerSecond,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos,
            long maxQueueDepth,
            int queueCapacity
    ) {
        public String summary() {
            return String.format(java.util.Locale.ROOT,
                    "producers %d, workers %d, attempted %d, accepted %d, consumed %d, "
                            + "dropped %d, %.0f events/s, p50 %.1f us, p95 %.1f us, "
                            + "p99 %.1f us, peak queue %d/%d",
                    producers, workers, attempted, accepted, consumed, dropped,
                    throughputPerSecond,
                    p50Nanos / 1_000.0, p95Nanos / 1_000.0, p99Nanos / 1_000.0,
                    maxQueueDepth, queueCapacity);
        }
    }

    /**
     * Runs a stress test.
     *
     * @param producers    number of concurrent producer threads
     * @param workers      number of consumer threads
     * @param events       total events each producer attempts to publish
     * @param capacity     bounded queue capacity (small values force backpressure)
     * @param work         per-event work, executed by the consumer
     */
    public StressReport run(int producers, int workers, long events, int capacity,
                            Supplier<String> work) {
        if (producers <= 0 || workers <= 0 || events <= 0 || capacity <= 0) {
            throw new IllegalArgumentException("all sizes must be positive");
        }

        MarketEventQueue<String> queue = new MarketEventQueue<>(capacity);
        LatencySampler sampler = new LatencySampler((int) Math.min(65_536, Math.max(1_024, events)));
        AtomicLong dropped = new AtomicLong();
        AtomicLong consumed = new AtomicLong();
        AtomicLong peakDepth = new AtomicLong();
        AtomicLong totalLatency = new AtomicLong();

        ExecutorService consumerPool = Executors.newFixedThreadPool(workers);
        ExecutorService producerPool = Executors.newFixedThreadPool(producers);

        CountDownLatch producersDone = new CountDownLatch(producers);
        CountDownLatch consumersDone = new CountDownLatch(workers);
        long startedAt = System.nanoTime();

        for (int w = 0; w < workers; w++) {
            consumerPool.submit(() -> {
                try {
                    while (true) {
                        String item;
                        try {
                            item = queue.poll(100, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        if (item == null) {
                            if (producersDone.getCount() == 0 && queue.isEmpty()) {
                                return;
                            }
                            continue;
                        }
                        long begin = System.nanoTime();
                        try {
                            work.get();
                        } catch (RuntimeException ignored) {
                            // A failing item must not stop the consumer.
                        }
                        long elapsed = System.nanoTime() - begin;
                        sampler.record(elapsed);
                        totalLatency.addAndGet(elapsed);
                        consumed.incrementAndGet();
                    }
                } finally {
                    consumersDone.countDown();
                }
            });
        }

        for (int p = 0; p < producers; p++) {
            final int producerId = p;
            producerPool.submit(() -> {
                try {
                    for (long i = 0; i < events; i++) {
                        String item = "p" + producerId + "-" + i;
                        if (!queue.offer(item)) {
                            // Backpressure: the queue is full, so this event is deliberately dropped
                            // and counted rather than blocking the producer.
                            dropped.incrementAndGet();
                        }
                        peakDepth.accumulateAndGet(queue.depth(), Math::max);
                    }
                } finally {
                    producersDone.countDown();
                }
            });
        }

        try {
            producersDone.await(60, TimeUnit.SECONDS);
            consumersDone.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            producerPool.shutdownNow();
            consumerPool.shutdownNow();
        }

        long elapsedNanos = Math.max(1L, System.nanoTime() - startedAt);
        long attempted = (long) producers * events;
        long droppedCount = dropped.get();

        return new StressReport(
                producers,
                workers,
                attempted,
                attempted - droppedCount,
                consumed.get(),
                droppedCount,
                elapsedNanos / 1_000_000L,
                consumed.get() * 1_000_000_000.0 / elapsedNanos,
                sampler.p50(),
                sampler.p95(),
                sampler.p99(),
                peakDepth.get(),
                capacity);
    }

    /** Convenience: a small, fast run suitable for a UI button. */
    public StressReport quickRun() {
        return run(4, 4, 5_000, 512, () -> "x");
    }
}
