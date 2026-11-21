package com.tradingdesk.database.dao;

import com.tradingdesk.model.MarketEvent;

import java.util.List;

public interface MarketEventDao {

    void insert(MarketEvent event);

    void insertBatch(List<MarketEvent> events);

    List<MarketEvent> findRecent(String symbol, int limit);

    long count();

    int deleteAll();
}
