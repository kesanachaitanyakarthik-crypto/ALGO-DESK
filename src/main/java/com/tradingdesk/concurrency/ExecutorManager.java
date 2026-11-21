package com.tradingdesk.concurrency;

import com.tradingdesk.utils.NamedThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecutorManager {

    private final int workerCount;
    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public ExecutorManager(int workerCount) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        this.workerCount = workerCount;
        this.workerPool = Executors.newFixedThreadPool(workerCount, new NamedThreadFactory("td-worker"));
        this.scheduler = Executors.newScheduledThreadPool(2, new NamedThreadFactory("td-scheduler"));
    }

    public ExecutorService workerPool() {
        return workerPool;
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public int workerCount() {
        return workerCount;
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            scheduler.shutdownNow();
            workerPool.shutdownNow();
            awaitTermination();
        }
    }

    private void awaitTermination() {
        try {
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
            scheduler.shutdownNow();
        }
    }
}
