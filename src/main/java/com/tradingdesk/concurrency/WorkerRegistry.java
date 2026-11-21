package com.tradingdesk.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

public class WorkerRegistry {

    private final AtomicInteger activeWorkers = new AtomicInteger();
    private final AtomicInteger totalWorkers = new AtomicInteger();

    public void workerStarted() {
        activeWorkers.incrementAndGet();
        totalWorkers.incrementAndGet();
    }

    public void workerStopped() {
        activeWorkers.decrementAndGet();
    }

    public int activeWorkers() {
        return activeWorkers.get();
    }

    public int totalWorkers() {
        return totalWorkers.get();
    }

    public void reset() {
        activeWorkers.set(0);
        totalWorkers.set(0);
    }
}
