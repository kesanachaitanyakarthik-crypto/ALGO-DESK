package com.tradingdesk.concurrency;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MetricsRegistry {

    private final LongAdder eventsProcessed = new LongAdder();
    private final LongAdder eventsFailed = new LongAdder();
    private final LongAdder eventsDropped = new LongAdder();
    private final LongAdder eventsRejected = new LongAdder();
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private final AtomicLong peakLatencyNanos = new AtomicLong();
    private final AtomicLong lastLatencyNanos = new AtomicLong();
    private final AtomicLong peakQueueDepth = new AtomicLong();
    private final AtomicLong startedAtMillis = new AtomicLong(System.currentTimeMillis());
    private final LatencySampler latencySampler = new LatencySampler(4_096);

    public void recordProcessed(Duration latency) {
        long nanos = Math.max(0L, latency.toNanos());
        eventsProcessed.increment();
        totalLatencyNanos.addAndGet(nanos);
        lastLatencyNanos.set(nanos);
        peakLatencyNanos.accumulateAndGet(nanos, Math::max);
        latencySampler.record(nanos);
    }

    public void recordFailure() {
        eventsFailed.increment();
    }

    public void recordDropped() {
        eventsDropped.increment();
    }

    /** An event that was accepted but deliberately discarded (for example a duplicate tick). */
    public void recordRejected() {
        eventsRejected.increment();
    }

    /** Latency percentiles over the retained sample window, in nanoseconds. */
    public long p50LatencyNanos() {
        return latencySampler.p50();
    }

    public long p95LatencyNanos() {
        return latencySampler.p95();
    }

    public long p99LatencyNanos() {
        return latencySampler.p99();
    }

    public int latencySampleCount() {
        return latencySampler.size();
    }

    public void recordQueueDepth(int depth) {
        peakQueueDepth.accumulateAndGet(depth, Math::max);
    }

    public long eventsProcessed() {
        return eventsProcessed.sum();
    }

    public long eventsFailed() {
        return eventsFailed.sum();
    }

    public long eventsDropped() {
        return eventsDropped.sum();
    }

    public long eventsRejected() {
        return eventsRejected.sum();
    }

    /** Process CPU load in the range 0..1, or -1 when unavailable. */
    public double processCpuLoad() {
        try {
            java.lang.management.OperatingSystemMXBean bean =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
                double load = sun.getProcessCpuLoad();
                return load < 0.0 ? -1.0 : load;
            }
        } catch (RuntimeException e) {
            // The extended bean is not guaranteed on every JVM.
        }
        return -1.0;
    }

    public Duration lastLatency() {
        return Duration.ofNanos(lastLatencyNanos.get());
    }

    public Duration peakLatency() {
        return Duration.ofNanos(peakLatencyNanos.get());
    }

    public Duration averageLatency() {
        long processed = eventsProcessed.sum();
        return processed == 0
                ? Duration.ZERO
                : Duration.ofNanos(totalLatencyNanos.get() / processed);
    }

    public long peakQueueDepth() {
        return peakQueueDepth.get();
    }

    public double throughputPerSecond() {
        long elapsed = Math.max(1L, System.currentTimeMillis() - startedAtMillis.get());
        return eventsProcessed.sum() * 1_000.0 / elapsed;
    }

    public void reset() {
        eventsProcessed.reset();
        eventsFailed.reset();
        eventsDropped.reset();
        eventsRejected.reset();
        totalLatencyNanos.set(0L);
        peakLatencyNanos.set(0L);
        lastLatencyNanos.set(0L);
        peakQueueDepth.set(0L);
        startedAtMillis.set(System.currentTimeMillis());
        latencySampler.reset();
    }

    public static long millis(Duration duration) {
        return Objects.requireNonNull(duration).toMillis();
    }

    public static double micros(Duration duration) {
        return duration.toNanos() / 1_000.0;
    }
}
