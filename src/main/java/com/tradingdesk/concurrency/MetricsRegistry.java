package com.tradingdesk.concurrency;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MetricsRegistry {

    private final LongAdder eventsProcessed = new LongAdder();
    private final LongAdder eventsFailed = new LongAdder();
    private final LongAdder eventsDropped = new LongAdder();
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private final AtomicLong peakLatencyNanos = new AtomicLong();
    private final AtomicLong lastLatencyNanos = new AtomicLong();
    private final AtomicLong peakQueueDepth = new AtomicLong();
    private final AtomicLong startedAtMillis = new AtomicLong(System.currentTimeMillis());

    public void recordProcessed(Duration latency) {
        long nanos = Math.max(0L, latency.toNanos());
        eventsProcessed.increment();
        totalLatencyNanos.addAndGet(nanos);
        lastLatencyNanos.set(nanos);
        peakLatencyNanos.accumulateAndGet(nanos, Math::max);
    }

    public void recordFailure() {
        eventsFailed.increment();
    }

    public void recordDropped() {
        eventsDropped.increment();
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
        totalLatencyNanos.set(0L);
        peakLatencyNanos.set(0L);
        lastLatencyNanos.set(0L);
        peakQueueDepth.set(0L);
        startedAtMillis.set(System.currentTimeMillis());
    }

    public static long millis(Duration duration) {
        return Objects.requireNonNull(duration).toMillis();
    }

    public static double micros(Duration duration) {
        return duration.toNanos() / 1_000.0;
    }
}
