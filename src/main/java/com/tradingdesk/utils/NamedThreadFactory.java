package com.tradingdesk.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
