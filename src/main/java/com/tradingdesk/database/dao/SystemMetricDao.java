package com.tradingdesk.database.dao;

import com.tradingdesk.model.SystemMetric;

import java.util.List;

public interface SystemMetricDao {

    void insert(SystemMetric metric);

    void insertBatch(List<SystemMetric> metrics);

    List<SystemMetric> findRecent(String name, int limit);

    long count();

    int deleteAll();
}
