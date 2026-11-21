package com.tradingdesk.simulator;

import com.tradingdesk.model.MarketEvent;

public interface MarketDataSimulator extends AutoCloseable {

    void start();

    void stop();

    boolean isRunning();

    MarketEvent nextEvent();

    MarketEvent emit();

    @Override
    void close();
}
