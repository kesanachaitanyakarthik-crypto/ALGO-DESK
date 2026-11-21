package com.tradingdesk.marketdata.ws;

/**
 * Exponential-backoff reconnection policy with a staleness watchdog.
 *
 * <p>Kept as a pure state machine so the timing behaviour is unit-testable without waiting on real
 * sockets. The watchdog deadline is what turns "the socket is open but silent" into an actual
 * reconnect, which is the failure mode heartbeat monitoring exists to catch.
 */
public class ReconnectPolicy {

    private final long initialDelayMillis;
    private final long maxDelayMillis;
    private final double multiplier;
    private final long staleTimeoutMillis;

    private int failures;

    public ReconnectPolicy() {
        this(1_000L, 30_000L, 2.0, 60_000L);
    }

    public ReconnectPolicy(long initialDelayMillis, long maxDelayMillis, double multiplier,
                           long staleTimeoutMillis) {
        if (initialDelayMillis <= 0 || maxDelayMillis < initialDelayMillis) {
            throw new IllegalArgumentException("invalid delay bounds");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1");
        }
        if (staleTimeoutMillis <= 0) {
            throw new IllegalArgumentException("staleTimeoutMillis must be positive");
        }
        this.initialDelayMillis = initialDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.multiplier = multiplier;
        this.staleTimeoutMillis = staleTimeoutMillis;
    }

    /** Delay to wait before the next attempt, growing with each consecutive failure. */
    public long nextDelayMillis() {
        double delay = initialDelayMillis * Math.pow(multiplier, Math.min(failures, 16));
        return Math.min(maxDelayMillis, (long) delay);
    }

    /** Records a failed connection attempt, which increases the next delay. */
    public void recordFailure() {
        if (failures < 16) {
            failures++;
        }
    }

    /** Records a successful connection, resetting the backoff. */
    public void recordSuccess() {
        failures = 0;
    }

    public int consecutiveFailures() {
        return failures;
    }

    /** True when the last message is older than the stale timeout. */
    public boolean isStale(long lastMessageAtMillis, long nowMillis) {
        if (lastMessageAtMillis <= 0) {
            return false;
        }
        return nowMillis - lastMessageAtMillis > staleTimeoutMillis;
    }

    public long staleTimeoutMillis() {
        return staleTimeoutMillis;
    }
}
