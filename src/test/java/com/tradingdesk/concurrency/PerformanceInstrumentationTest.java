package com.tradingdesk.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the latency sampler and the stress runner with deterministic, offline workloads.
 */
class PerformanceInstrumentationTest {

    // ------------------------------------------------------------------ latency sampler

    @Test
    void emptySamplerReportsZero() {
        LatencySampler sampler = new LatencySampler(100);
        assertEquals(0L, sampler.p50());
        assertEquals(0L, sampler.p99());
        assertEquals(0, sampler.size());
    }

    @Test
    void percentilesMatchAKnownDistribution() {
        LatencySampler sampler = new LatencySampler(1_000);
        // 1..1000 ns, so P50 = 500, P95 = 950, P99 = 990 by nearest-rank.
        for (int i = 1; i <= 1_000; i++) {
            sampler.record(i);
        }

        assertEquals(500L, sampler.p50());
        assertEquals(950L, sampler.p95());
        assertEquals(990L, sampler.p99());
    }

    @Test
    void percentilesAreOrdered() {
        LatencySampler sampler = new LatencySampler(200);
        for (int i = 0; i < 200; i++) {
            sampler.record(i * 10L);
        }
        assertTrue(sampler.p50() <= sampler.p95(), "p50 must not exceed p95");
        assertTrue(sampler.p95() <= sampler.p99(), "p95 must not exceed p99");
    }

    @Test
    void samplerWindowIsBounded() {
        LatencySampler sampler = new LatencySampler(50);
        for (int i = 0; i < 10_000; i++) {
            sampler.record(1_000L);
        }
        assertEquals(50, sampler.size(), "the sampler must retain only its capacity");
    }

    @Test
    void negativeSamplesAreClampedToZero() {
        LatencySampler sampler = new LatencySampler(10);
        sampler.record(-5L);
        assertEquals(0L, sampler.p50());
    }

    @Test
    void samplerRejectsInvalidPercentile() {
        LatencySampler sampler = new LatencySampler(10);
        assertThrows(IllegalArgumentException.class, () -> sampler.percentile(-1));
        assertThrows(IllegalArgumentException.class, () -> sampler.percentile(101));
    }

    @Test
    void samplerIsThreadSafe() throws InterruptedException {
        LatencySampler sampler = new LatencySampler(1_000);
        int threads = 8;
        int perThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        sampler.record(500L);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(500L, sampler.p50(), "concurrent writes must not corrupt the window");
        assertTrue(sampler.size() <= 1_000);
    }

    @Test
    void metricsRegistryExposesPercentilesAndRejections() {
        MetricsRegistry metrics = new MetricsRegistry();
        metrics.recordProcessed(java.time.Duration.ofNanos(1_000));
        metrics.recordProcessed(java.time.Duration.ofNanos(2_000));
        metrics.recordProcessed(java.time.Duration.ofNanos(3_000));
        metrics.recordRejected();

        assertEquals(3, metrics.latencySampleCount());
        assertTrue(metrics.p50LatencyNanos() > 0);
        assertEquals(1, metrics.eventsRejected());
        assertTrue(metrics.processCpuLoad() >= -1.0, "CPU load must be a valid reading");
    }

    // ------------------------------------------------------------------ stress runner

    @Test
    void stressRunMeasuresRealThroughputAndLatency() {
        StressTestRunner runner = new StressTestRunner();

        StressTestRunner.StressReport report = runner.run(2, 2, 1_000, 256, () -> "work");

        assertEquals(2_000, report.attempted());
        assertTrue(report.consumed() > 0, "consumers must process events");
        assertTrue(report.consumed() <= report.accepted(),
                "consumed cannot exceed the events actually accepted by the queue");
        assertTrue(report.throughputPerSecond() > 0.0, "throughput must be measured, not assumed");
        assertTrue(report.elapsedMillis() >= 0);
        assertTrue(report.p99Nanos() >= report.p50Nanos());
    }

    @Test
    void smallQueueProducesMeasurableBackpressure() {
        StressTestRunner runner = new StressTestRunner();

        // Deliberately tiny queue with slow consumers to force drops.
        StressTestRunner.StressReport report = runner.run(4, 1, 3_000, 4,
                () -> {
                    Thread.onSpinWait();
                    return "work";
                });

        assertTrue(report.dropped() > 0, "a 4-slot queue under load must drop events");
        assertEquals(report.attempted() - report.dropped(), report.accepted(),
                "every attempt is either accepted or dropped");
        assertTrue(report.maxQueueDepth() <= report.queueCapacity(),
                "depth can never exceed capacity");
    }

    @Test
    void accountingIsConsistent() {
        StressTestRunner.StressReport report =
                new StressTestRunner().run(3, 3, 500, 128, () -> "work");

        // Every event is either consumed, still queued, or dropped. Consumed can never exceed the
        // number successfully accepted by the queue.
        long producedSuccessfully = report.accepted();
        assertTrue(report.consumed() <= producedSuccessfully,
                "consumed " + report.consumed() + " must not exceed accepted "
                        + producedSuccessfully);
    }

    @Test
    void consumerFailuresDoNotStopTheRun() {
        AtomicLong calls = new AtomicLong();
        StressTestRunner.StressReport report = new StressTestRunner().run(2, 2, 300, 256, () -> {
            if (calls.incrementAndGet() % 3 == 0) {
                throw new IllegalStateException("injected failure");
            }
            return "ok";
        });

        assertTrue(report.consumed() > 0, "a failing item must not kill the consumer");
    }

    @Test
    void stressRunnerRejectsInvalidSizes() {
        StressTestRunner runner = new StressTestRunner();
        assertThrows(IllegalArgumentException.class, () -> runner.run(0, 1, 10, 10, () -> "x"));
        assertThrows(IllegalArgumentException.class, () -> runner.run(1, 0, 10, 10, () -> "x"));
        assertThrows(IllegalArgumentException.class, () -> runner.run(1, 1, 0, 10, () -> "x"));
        assertThrows(IllegalArgumentException.class, () -> runner.run(1, 1, 10, 0, () -> "x"));
    }

    @Test
    void reportSummaryIsHumanReadable() {
        StressTestRunner.StressReport report =
                new StressTestRunner().run(1, 1, 100, 64, () -> "x");

        String summary = report.summary();
        assertTrue(summary.contains("producers"));
        assertTrue(summary.contains("p99"));
        assertTrue(summary.contains("peak queue"));
    }
}
