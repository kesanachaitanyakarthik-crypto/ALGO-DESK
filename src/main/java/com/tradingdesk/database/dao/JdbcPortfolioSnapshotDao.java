package com.tradingdesk.database.dao;

import com.tradingdesk.database.DataAccessException;
import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.model.PortfolioSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcPortfolioSnapshotDao implements PortfolioSnapshotDao {

    private static final String INSERT = """
            INSERT INTO portfolio_snapshots
                (cash, market_value, realized_pnl, unrealized_pnl, captured_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String LATEST = """
            SELECT cash, market_value, realized_pnl, unrealized_pnl, captured_at
            FROM portfolio_snapshots
            ORDER BY captured_at DESC, snapshot_id DESC
            LIMIT 1
            """;

    private static final String RECENT = """
            SELECT cash, market_value, realized_pnl, unrealized_pnl, captured_at
            FROM portfolio_snapshots
            ORDER BY captured_at DESC, snapshot_id DESC
            LIMIT ?
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM portfolio_snapshots";
    private static final String DELETE_ALL = "DELETE FROM portfolio_snapshots";

    private final DatabaseConnection database;

    public JdbcPortfolioSnapshotDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void insert(PortfolioSnapshot snapshot) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setDouble(1, snapshot.cash());
            statement.setDouble(2, snapshot.marketValue());
            statement.setDouble(3, snapshot.realizedProfitLoss());
            statement.setDouble(4, snapshot.unrealizedProfitLoss());
            JdbcSupport.setInstant(statement, 5, snapshot.capturedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to insert portfolio snapshot", e);
        }
    }

    @Override
    public Optional<PortfolioSnapshot> findLatest() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(LATEST);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read latest portfolio snapshot", e);
        }
    }

    @Override
    public List<PortfolioSnapshot> findRecent(int limit) {
        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(RECENT)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    snapshots.add(map(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read portfolio snapshots", e);
        }
        return snapshots;
    }

    @Override
    public long count() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(COUNT);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to count portfolio snapshots", e);
        }
    }

    @Override
    public int deleteAll() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to delete portfolio snapshots", e);
        }
    }

    private static PortfolioSnapshot map(ResultSet resultSet) throws SQLException {
        return new PortfolioSnapshot(
                resultSet.getDouble("cash"),
                resultSet.getDouble("market_value"),
                resultSet.getDouble("realized_pnl"),
                resultSet.getDouble("unrealized_pnl"),
                JdbcSupport.getInstant(resultSet, "captured_at"));
    }
}
