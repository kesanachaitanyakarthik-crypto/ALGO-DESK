package com.tradingdesk.concurrency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyComponentsTest {

    @Test
    void boundedQueueRejectsWhenFull() {
        MarketEventQueue<Integer> queue = new MarketEventQueue<>(3);
        assertTrue(queue.offer(1));
        assertTrue(queue.offer(2));
        assertTrue(queue.offer(3));
        assertFalse(queue.offer(4), "bounded queue must reject when full");
        assertEquals(3, queue.depth());
        assertEquals(0, queue.remainingCapacity());
        assertEquals(3, queue.capacity());
        assertEquals(List.of(1, 2, 3), queue.drainAll());
        assertTrue(queue.isEmpty());
    }

    @Test
    void timedPollReturnsNullWhenIdle() throws InterruptedException {
        MarketEventQueue<String> queue = new MarketEventQueue<>(2);
        queue.put("event");
        assertEquals("event", queue.poll(50, TimeUnit.MILLISECONDS));
        assertEquals(null, queue.poll(20, TimeUnit.MILLISECONDS));
    }

    @Test
    void metricsAggregateLatencySafelyUnderConcurrency() throws InterruptedException {
        MetricsRegistry metrics = new MetricsRegistry();
        int threads = 8;
        int iterations = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        metrics.recordProcessed(Duration.ofNanos(1_000));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals((long) threads * iterations, metrics.eventsProcessed());
        assertEquals(1_000.0, metrics.averageLatency().toNanos(), 1.0);
        assertEquals(1_000.0, metrics.peakLatency().toNanos(), 1.0);
    }

    @Test
    void priceCacheIsThreadSafeUnderContention() throws InterruptedException {
        ConcurrentPriceCache<String> cache = new ConcurrentPriceCache<>();
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            String symbol = "SYM" + t;
            pool.submit(() -> {
                try {
                    for (int i = 1; i <= 200; i++) {
                        cache.update(symbol, i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(threads, cache.size());
        assertEquals(200.0, cache.find("SYM0").orElseThrow(), 1e-9);
    }

    @Test
    void workerRegistryTracksActiveThreads() {
        WorkerRegistry registry = new WorkerRegistry();
        registry.workerStarted();
        registry.workerStarted();
        assertEquals(2, registry.activeWorkers());
        registry.workerStopped();
        assertEquals(1, registry.activeWorkers());
        assertEquals(2, registry.totalWorkers());
    }

    @Test
    void executorManagerShutsDownBothPools() {
        ExecutorManager manager = new ExecutorManager(2);
        manager.shutdown();
        assertTrue(manager.isShutdown());
        assertTrue(manager.workerPool().isShutdown());
        assertTrue(manager.scheduler().isShutdown());
    }

    @Test
    void workersDrainQueueWithoutLossUnderLoad() throws InterruptedException {
        int workers = 4;
        int events = 2_000;
        MarketEventQueue<Integer> queue = new MarketEventQueue<>(4_000);
        MetricsRegistry metrics = new MetricsRegistry();
        ExecutorManager executors = new ExecutorManager(workers);
        List<Integer> consumed = java.util.Collections.synchronizedList(new ArrayList<>());

        CountDownLatch done = new CountDownLatch(workers);
        for (int w = 0; w < workers; w++) {
            executors.workerPool().submit(() -> {
                try {
                    while (true) {
                        Integer event = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (event == null) {
                            return;
                        }
                        metrics.recordProcessed(Duration.ofNanos(500));
                        consumed.add(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        for (int i = 0; i < events; i++) {
            assertTrue(queue.offer(i));
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        executors.shutdown();

        assertEquals(events, consumed.size(), "every queued event must be processed exactly once");
        assertEquals(events, metrics.eventsProcessed());
        assertEquals(events, consumed.stream().distinct().count(), "no duplicates under contention");
    }
}
