package com.tradingdesk.database;

import com.tradingdesk.database.dao.ExecutionDao;
import com.tradingdesk.database.dao.InstrumentDao;
import com.tradingdesk.database.dao.InstrumentResolver;
import com.tradingdesk.database.dao.JdbcExecutionDao;
import com.tradingdesk.database.dao.JdbcInstrumentDao;
import com.tradingdesk.database.dao.JdbcMarketEventDao;
import com.tradingdesk.database.dao.JdbcOrderDao;
import com.tradingdesk.database.dao.JdbcPortfolioSnapshotDao;
import com.tradingdesk.database.dao.JdbcSystemMetricDao;
import com.tradingdesk.database.dao.JdbcTradeSignalDao;
import com.tradingdesk.database.dao.MarketEventDao;
import com.tradingdesk.database.dao.OrderDao;
import com.tradingdesk.database.dao.OrderPersistence;
import com.tradingdesk.database.dao.PortfolioSnapshotDao;
import com.tradingdesk.database.dao.SystemMetricDao;
import com.tradingdesk.database.dao.TradeSignalDao;

public class MySqlPersistence implements Persistence {

    private final DatabaseConnection database;
    private final SchemaMigrator migrator;
    private final InstrumentDao instruments;
    private final MarketEventDao marketEvents;
    private final TradeSignalDao signals;
    private final OrderDao orders;
    private final ExecutionDao executions;
    private final PortfolioSnapshotDao portfolioSnapshots;
    private final SystemMetricDao metrics;
    private final OrderPersistence orderPersistence;

    public MySqlPersistence(DatabaseConfig config) {
        this.database = new DatabaseConnection(config);
        this.migrator = new SchemaMigrator(database);
        InstrumentDao instrumentDao = new JdbcInstrumentDao(database);
        InstrumentResolver resolver = new InstrumentResolver(instrumentDao);
        this.instruments = instrumentDao;
        this.marketEvents = new JdbcMarketEventDao(database, resolver);
        this.signals = new JdbcTradeSignalDao(database, resolver);
        this.orders = new JdbcOrderDao(database, resolver);
        this.executions = new JdbcExecutionDao(database);
        this.portfolioSnapshots = new JdbcPortfolioSnapshotDao(database);
        this.metrics = new JdbcSystemMetricDao(database);
        this.orderPersistence = new OrderPersistence(database);
    }

    public static MySqlPersistence connect() {
        MySqlPersistence persistence = new MySqlPersistence(DatabaseConfig.load());
        persistence.migrator().migrate();
        return persistence;
    }

    @Override
    public DatabaseConnection database() {
        return database;
    }

    @Override
    public SchemaMigrator migrator() {
        return migrator;
    }

    @Override
    public InstrumentDao instruments() {
        return instruments;
    }

    @Override
    public MarketEventDao marketEvents() {
        return marketEvents;
    }

    @Override
    public TradeSignalDao signals() {
        return signals;
    }

    @Override
    public OrderDao orders() {
        return orders;
    }

    @Override
    public ExecutionDao executions() {
        return executions;
    }

    @Override
    public PortfolioSnapshotDao portfolioSnapshots() {
        return portfolioSnapshots;
    }

    @Override
    public SystemMetricDao metrics() {
        return metrics;
    }

    @Override
    public OrderPersistence orderPersistence() {
        return orderPersistence;
    }
}
