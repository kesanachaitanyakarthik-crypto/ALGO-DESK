package com.tradingdesk.processor;

import com.tradingdesk.model.MarketEvent;

@FunctionalInterface
public interface MarketEventConsumer {

    void consume(MarketEvent event);
}
