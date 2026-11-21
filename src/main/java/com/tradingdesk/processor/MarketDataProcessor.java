package com.tradingdesk.processor;

public interface MarketDataProcessor extends AutoCloseable {

    void start();

    void stop();

    boolean isRunning();

    @Override
    void close();
}
