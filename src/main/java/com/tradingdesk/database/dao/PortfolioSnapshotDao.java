package com.tradingdesk.database.dao;

import com.tradingdesk.model.PortfolioSnapshot;

import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotDao {

    void insert(PortfolioSnapshot snapshot);

    Optional<PortfolioSnapshot> findLatest();

    List<PortfolioSnapshot> findRecent(int limit);

    long count();

    int deleteAll();
}
