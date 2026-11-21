package com.tradingdesk.concurrency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Produces the performance benchmarks quoted in the development report.
 *
 * <p>Every figure printed here is measured during the run on this machine. Three timing scopes are
 * reported separately, because conflating them is the classic way to publish a meaningless number:
 *
 * <ul>
 *   <li><b>processing</b> — dequeue to work-complete. Pure consumer cost.</li>
 *   <li><b>queue wait</b> — publish to dequeue. Grows with load and queue saturation.</li>
 *   <li><b>end-to-end</b> — publish to work-complete. What a producer would actually observe.</li>
 * </ul>
 *
 * <p>The earlier 0.1–0.6 µs figures were processing-only on a no-op workload, so they measured
 * dequeue overhead rather than market-data handling. This test reports all three scopes for a
 * workload that does real work, so the numbers can be compared with the ~99 µs figure.
 */
class BenchmarkReportTest {

    /** A small amount of realistic per-event work, so processing is not a no-op. */
    private static String realisticWork() {
        double accumulator = 0.0;
        for (int i = 0; i < 64; i++) {
            accumulator += Math.sqrt(i + 1.0);
        }
        return accumulator > 0.0 ? "ok" : "bad";
    }

    @Test
    void measureThroughputAndLatencyAtSeveralLoads() {
        StressTestRunner runner = new StressTestRunner();

        System.out.println("=== ALGO DESK BENCHMARKS (measured on this machine) ===");
        System.out.printf("%-34s %11s %9s %9s %9s %9s %9s %9s%n",
                "scenario", "events/s", "proc p50", "proc p99", "wait p50", "e2e p50",
                "dropped", "peak q");

        String[] labels = {
                "4 prod / 4 workers, q=512",
                "8 prod / 4 workers, q=256",
                "8 prod / 2 workers, q=64 (saturated)",
                "2 prod / 8 workers, q=4096 (low wait)"
        };
        int[][] configs = {
                {4, 4, 512},
                {8, 4, 256},
                {8, 2, 64},
                {2, 8, 4_096}
        };

        for (int i = 0; i < configs.length; i++) {
            int producers = configs[i][0];
            int workers = configs[i][1];
            int capacity = configs[i][2];
            StressTestRunner.StressReport report =
                    runner.run(producers, workers, 5_000, capacity, BenchmarkReportTest::realisticWork);

            System.out.printf("%-34s %11.0f %9.2f %9.2f %9.2f %9.2f %9d %9s%n",
                    labels[i],
                    report.throughputPerSecond(),
                    report.p50Nanos() / 1_000.0,
                    report.p99Nanos() / 1_000.0,
                    report.queueWaitP50Nanos() / 1_000.0,
                    report.endToEndP50Nanos() / 1_000.0,
                    report.dropped(),
                    report.maxQueueDepth() + "/" + report.queueCapacity());

            // Self-consistency of the three timing boundaries.
            assertTrue(report.attempted() > 0);
            assertEquals(report.attempted() - report.dropped(), report.accepted());
            assertTrue(report.consumed() <= report.accepted());
            assertTrue(report.p99Nanos() >= report.p50Nanos());
            assertTrue(report.endToEndP50Nanos() >= report.p50Nanos(),
                    "end-to-end must include the processing time it contains");
        }

        System.out.println("scopes: proc = dequeue->work done, wait = publish->dequeue, "
                + "e2e = publish->work done");
        System.out.println("====================================================");
    }

    @Test
    void emptyWorkloadReproducesTheOriginalMicrosecondFigures() {
        // Reproduces the no-op measurement, so the report can explain the 0.1-0.6 us figure instead of
        // leaving it looking like a contradiction of the ~99 us number.
        StressTestRunner.StressReport report =
                new StressTestRunner().run(4, 4, 5_000, 512, () -> "x");
        System.out.printf("no-op workload: processing p50 %.2f us, p99 %.2f us, "
                        + "queue-wait p50 %.2f us, end-to-end p50 %.2f us%n",
                report.p50Nanos() / 1_000.0,
                report.p99Nanos() / 1_000.0,
                report.queueWaitP50Nanos() / 1_000.0,
                report.endToEndP50Nanos() / 1_000.0);
        assertTrue(report.endToEndP50Nanos() >= report.p50Nanos());
    }
}
