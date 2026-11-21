package com.tradingdesk.marketdata.ws;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Suppresses duplicate stream events.
 *
 * <p>Duplicates occur legitimately: a reconnect can replay the latest trade, and combined streams
 * can deliver the same trade on two subscriptions. Feeding them twice would double-count volume and
 * skew indicators, so each event key is remembered for a bounded window.
 */
public class DuplicateFilter {

    private final int maxTracked;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, Long> seenAt = new ConcurrentHashMap<>();
    private final AtomicLong suppressed = new AtomicLong();

    public DuplicateFilter() {
        this(20_000);
    }

    public DuplicateFilter(int maxTracked) {
        if (maxTracked <= 0) {
            throw new IllegalArgumentException("maxTracked must be positive");
        }
        this.maxTracked = maxTracked;
    }

    /**
     * @return true when this key has not been seen before (so the event should be processed)
     */
    public boolean firstOccurrence(String key, long nowMillis) {
        if (key == null) {
            return true;
        }
        boolean added = seen.add(key);
        if (!added) {
            suppressed.incrementAndGet();
            return false;
        }
        seenAt.put(key, nowMillis);
        if (seen.size() > maxTracked) {
            evictOldest();
        }
        return true;
    }

    /** Drops the oldest tracked keys once the window is full. */
    private void evictOldest() {
        long cutoff = System.currentTimeMillis() - 5 * 60_000L;
        seenAt.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoff) {
                seen.remove(entry.getKey());
                return true;
            }
            return false;
        });
        // If time-based eviction was not enough (huge burst), trim to the cap.
        while (seen.size() > maxTracked) {
            String victim = seenAt.keySet().stream().findFirst().orElse(null);
            if (victim == null) {
                break;
            }
            seenAt.remove(victim);
            seen.remove(victim);
        }
    }

    public long suppressedCount() {
        return suppressed.get();
    }

    public int trackedCount() {
        return seen.size();
    }

    public void clear() {
        seen.clear();
        seenAt.clear();
    }
}
