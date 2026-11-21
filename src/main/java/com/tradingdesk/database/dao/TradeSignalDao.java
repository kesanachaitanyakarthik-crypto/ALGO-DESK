package com.tradingdesk.database.dao;

import com.tradingdesk.model.TradeSignal;

import java.util.List;

public interface TradeSignalDao {

    void insert(TradeSignal signal);

    void insertBatch(List<TradeSignal> signals);

    List<TradeSignal> findRecent(int limit);

    long count();

    int deleteAll();
}
