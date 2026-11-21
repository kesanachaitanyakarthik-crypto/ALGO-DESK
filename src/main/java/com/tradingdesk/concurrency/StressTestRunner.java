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
            int queueCapacity,
            /** What the timing boundaries include, so reports can never be compared unfairly. */
            String latencyScope,
            /** Time from publish to dequeue — the queue wait, measured separately. */
            long queueWaitP50Nanos,
            /** Time from publish to work complete — queue wait plus processing. */
            long endToEndP50Nanos
    ) {
        public String summary() {
            return String.format(java.util.Locale.ROOT,
                    "producers %d, workers %d, attempted %d, accepted %d, consumed %d, "
                            + "dropped %d, %.0f events/s, processing p50 %.1f us p95 %.1f us "
                            + "p99 %.1f us, queue-wait p50 %.1f us, end-to-end p50 %.1f us, "
                            + "peak queue %d/%d [%s]",
                    producers, workers, attempted, accepted, consumed, dropped,
                    throughputPerSecond,
                    p50Nanos / 1_000.0, p95Nanos / 1_000.0, p99Nanos / 1_000.0,
                    queueWaitP50Nanos / 1_000.0, endToEndP50Nanos / 1_000.0,
                    maxQueueDepth, queueCapacity, latencyScope);
        }
    }

    /**
     * The latency this runner measures, stated once so every report is self-describing.
     *
     * <p>It is <strong>processing latency only</strong>: the span from dequeuing an item to finishing
     * the supplied work. It deliberately excludes the time an item spent queued, because with a
     * saturated bounded queue that wait dominates and would be reported as if it were CPU time.
     */
    public static final String PROCESSING_LATENCY_SCOPE =
            "processing only (dequeue→work done; excludes queue wait)";

    /** A payload plus the moment it was published, so queue wait can be measured honestly. */
    private record Item(String value, long publishedAtNanos) {
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

        MarketEventQueue<Item> queue = new MarketEventQueue<>(capacity);
        int samples = (int) Math.min(65_536, Math.max(1_024, events));
        LatencySampler sampler = new LatencySampler(samples);
        LatencySampler queueWaitSampler = new LatencySampler(samples);
        LatencySampler endToEndSampler = new LatencySampler(samples);
        AtomicLong dropped = new AtomicLong();
        AtomicLong consumed = new AtomicLong();
        AtomicLong peakDepth = new AtomicLong();

        ExecutorService consumerPool = Executors.newFixedThreadPool(workers);
        ExecutorService producerPool = Executors.newFixedThreadPool(producers);

        CountDownLatch producersDone = new CountDownLatch(producers);
        CountDownLatch consumersDone = new CountDownLatch(workers);
        long startedAt = System.nanoTime();

        for (int w = 0; w < workers; w++) {
            consumerPool.submit(() -> {
                try {
                    while (true) {
                        Item item;
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
                        // Three separate boundaries: dequeue, processing, completion.
                        long dequeuedAt = System.nanoTime();
                        queueWaitSampler.record(Math.max(0L, dequeuedAt - item.publishedAtNanos()));
                        try {
                            work.get();
                        } catch (RuntimeException ignored) {
                            // A failing item must not stop the consumer.
                        }
                        long completedAt = System.nanoTime();
                        sampler.record(completedAt - dequeuedAt);
                        endToEndSampler.record(completedAt - item.publishedAtNanos());
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
                        Item item = new Item("p" + producerId + "-" + i, System.nanoTime());
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
                capacity,
                PROCESSING_LATENCY_SCOPE,
                queueWaitSampler.p50(),
                endToEndSampler.p50());
    }

    /** Convenience: a small, fast run suitable for a UI button. */
    public StressReport quickRun() {
        return run(4, 4, 5_000, 512, () -> "x");
    }
}
