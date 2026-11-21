package com.tradingdesk.database.dao;

import com.tradingdesk.model.Execution;

import java.util.List;
import java.util.UUID;

public interface ExecutionDao {

    void insert(Execution execution);

    List<Execution> findByOrder(UUID orderId);

    long count();

    int deleteAll();
}
