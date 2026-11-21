package com.tradingdesk.database;

import com.tradingdesk.database.dao.ExecutionDao;
import com.tradingdesk.database.dao.InstrumentDao;
import com.tradingdesk.database.dao.MarketEventDao;
import com.tradingdesk.database.dao.OrderDao;
import com.tradingdesk.database.dao.OrderPersistence;
import com.tradingdesk.database.dao.PortfolioSnapshotDao;
import com.tradingdesk.database.dao.SystemMetricDao;
import com.tradingdesk.database.dao.TradeSignalDao;

public interface Persistence {

    DatabaseConnection database();

    SchemaMigrator migrator();

    MigrationRunner migrationRunner();

    InstrumentDao instruments();

    MarketEventDao marketEvents();

    TradeSignalDao signals();

    OrderDao orders();

    ExecutionDao executions();

    PortfolioSnapshotDao portfolioSnapshots();

    SystemMetricDao metrics();

    OrderPersistence orderPersistence();
}
