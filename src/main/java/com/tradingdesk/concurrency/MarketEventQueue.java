package com.tradingdesk.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MarketEventQueue<T> {

    private final BlockingQueue<T> queue;

    public MarketEventQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public boolean offer(T event) {
        return queue.offer(event);
    }

    public boolean offer(T event, long timeoutMillis) throws InterruptedException {
        return queue.offer(event, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void put(T event) throws InterruptedException {
        queue.put(event);
    }

    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public T take() throws InterruptedException {
        return queue.take();
    }

    public int drainTo(List<T> target, int maxItems) {
        return queue.drainTo(target, maxItems);
    }

    public List<T> drainAll() {
        List<T> drained = new ArrayList<>();
        queue.drainTo(drained);
        return drained;
    }

    public int depth() {
        return queue.size();
    }

    public int capacity() {
        return queue.size() + queue.remainingCapacity();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }
}
