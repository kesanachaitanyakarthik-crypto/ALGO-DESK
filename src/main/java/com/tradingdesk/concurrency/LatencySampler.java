package com.tradingdesk.concurrency;

import java.util.Arrays;

/**
 * Bounded latency sampler that reports P50 / P95 / P99 without unbounded memory growth.
 *
 * <p>Holds at most {@code capacity} recent samples in a ring buffer. Percentiles are computed on a
 * sorted copy of the current window, which is O(n log n) over a fixed n — cheap enough to call from
 * the UI refresh and exact for the retained window (no histogram approximation error).
 *
 * <p>Thread-safety: writes and reads are synchronized. Contention is negligible because recording is
 * a single array store and reads happen at UI refresh rate.
 */
public class LatencySampler {

    private final long[] samples;
    private int size;
    private int next;

    public LatencySampler(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.samples = new long[capacity];
    }

    /** Records one observation in nanoseconds. Negative values are treated as zero. */
    public synchronized void record(long nanos) {
        samples[next] = Math.max(0L, nanos);
        next = (next + 1) % samples.length;
        if (size < samples.length) {
            size++;
        }
    }

    /**
     * Percentile in the range [0, 100].
     *
     * @return the value at that percentile, or 0 when no samples have been recorded
     */
    public synchronized long percentile(double percentile) {
        if (percentile < 0.0 || percentile > 100.0) {
            throw new IllegalArgumentException("percentile must be within [0, 100]");
        }
        if (size == 0) {
            return 0L;
        }
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);

        if (percentile >= 100.0) {
            return sorted[size - 1];
        }
        // Nearest-rank method: index = ceil(p/100 * n) - 1, clamped.
        int index = (int) Math.ceil(percentile / 100.0 * size) - 1;
        index = Math.max(0, Math.min(size - 1, index));
        return sorted[index];
    }

    public synchronized long p50() {
        return percentile(50.0);
    }

    public synchronized long p95() {
        return percentile(95.0);
    }

    public synchronized long p99() {
        return percentile(99.0);
    }

    public synchronized int size() {
        return size;
    }

    public synchronized void reset() {
        size = 0;
        next = 0;
        Arrays.fill(samples, 0L);
    }
}
