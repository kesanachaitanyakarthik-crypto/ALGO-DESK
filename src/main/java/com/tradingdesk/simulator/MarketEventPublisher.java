package com.tradingdesk.simulator;

import com.tradingdesk.model.MarketEvent;

@FunctionalInterface
public interface MarketEventPublisher {

    void publish(MarketEvent event);
}
