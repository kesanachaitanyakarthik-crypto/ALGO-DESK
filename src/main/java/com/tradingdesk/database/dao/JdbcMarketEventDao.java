package com.tradingdesk.database.dao;

import com.tradingdesk.database.DataAccessException;
import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.model.MarketEvent;
import com.tradingdesk.model.MarketEventType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcMarketEventDao implements MarketEventDao {

    private static final String INSERT = """
            INSERT INTO market_events
                (sequence_no, symbol, event_type, last_price, bid_price, ask_price, volume, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_RECENT = """
            SELECT sequence_no, symbol, event_type, last_price, bid_price, ask_price, volume, occurred_at
            FROM market_events
            WHERE symbol = ?
            ORDER BY sequence_no DESC
            LIMIT ?
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM market_events";

    private static final String DELETE_ALL = "DELETE FROM market_events";

    private final DatabaseConnection database;
    private final InstrumentResolver instruments;

    public JdbcMarketEventDao(DatabaseConnection database, InstrumentResolver instruments) {
        this.database = database;
        this.instruments = instruments;
    }

    @Override
    public void insert(MarketEvent event) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setLong(1, event.sequence());
            statement.setString(2, event.instrument().symbol());
            statement.setString(3, event.type().name());
            statement.setDouble(4, event.lastPrice());
            statement.setDouble(5, event.bidPrice());
            statement.setDouble(6, event.askPrice());
            statement.setLong(7, event.volume());
            JdbcSupport.setInstant(statement, 8, event.timestamp());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to insert market event " + event.sequence(), e);
        }
    }

    @Override
    public void insertBatch(List<MarketEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                for (MarketEvent event : events) {
                    statement.setLong(1, event.sequence());
                    statement.setString(2, event.instrument().symbol());
                    statement.setString(3, event.type().name());
                    statement.setDouble(4, event.lastPrice());
                    statement.setDouble(5, event.bidPrice());
                    statement.setDouble(6, event.askPrice());
                    statement.setLong(7, event.volume());
                    JdbcSupport.setInstant(statement, 8, event.timestamp());
                    statement.addBatch();
                }
                return statement.executeBatch().length;
            }
        });
    }

    @Override
    public List<MarketEvent> findRecent(String symbol, int limit) {
        List<MarketEvent> events = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(FIND_RECENT)) {
            statement.setString(1, symbol);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(new MarketEvent(
                            resultSet.getLong("sequence_no"),
                            instruments.resolve(resultSet.getString("symbol")),
                            JdbcSupport.getEnum(resultSet, "event_type", MarketEventType.class),
                            resultSet.getDouble("last_price"),
                            resultSet.getDouble("bid_price"),
                            resultSet.getDouble("ask_price"),
                            resultSet.getLong("volume"),
                            JdbcSupport.getInstant(resultSet, "occurred_at")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read market events for " + symbol, e);
        }
        return events;
    }

    @Override
    public long count() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(COUNT);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to count market events", e);
        }
    }

    @Override
    public int deleteAll() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to delete market events", e);
        }
    }
}
