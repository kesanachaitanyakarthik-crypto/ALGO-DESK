package com.tradingdesk.concurrency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Produces the performance benchmarks quoted in the development report.
 *
 * <p>These are measurements taken on this machine, printed as they are found. There is no assertion
 * that any throughput or latency target is met — asserting one would be inventing a target. The only
 * assertions are that the run completes and the accounting is self-consistent.
 */
class BenchmarkReportTest {

    @Test
    void measureThroughputAndLatencyAtSeveralLoads() {
        StressTestRunner runner = new StressTestRunner();

        System.out.println("=== ALGO DESK BENCHMARKS (measured on this machine) ===");
        System.out.printf("%-32s %12s %12s %10s %10s %10s %10s%n",
                "scenario", "events/s", "p50 us", "p95 us", "p99 us", "dropped", "peak/queue");

        String[] labels = {
                "4 producers / 4 workers (q=512)",
                "8 producers / 4 workers (q=256)",
                "8 producers / 2 workers (q=64)  [saturated]",
                "2 producers / 8 workers (q=4096) [latency]"
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
                    runner.run(producers, workers, 5_000, capacity, () -> "x");

            System.out.printf("%-32s %12.0f %12.1f %10.1f %10.1f %10d %10s%n",
                    labels[i],
                    report.throughputPerSecond(),
                    report.p50Nanos() / 1_000.0,
                    report.p95Nanos() / 1_000.0,
                    report.p99Nanos() / 1_000.0,
                    report.dropped(),
                    report.maxQueueDepth() + "/" + report.queueCapacity());

            assertTrue(report.attempted() > 0);
            assertTrue(report.consumed() <= report.accepted());
            assertTrue(report.p99Nanos() >= report.p50Nanos());
        }

        System.out.println("====================================================");
    }
}
