package com.tradingdesk.database.dao;

import com.tradingdesk.database.DataAccessException;
import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.model.SystemMetric;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcSystemMetricDao implements SystemMetricDao {

    private static final String INSERT = """
            INSERT INTO system_metrics (metric_name, metric_value, unit, recorded_at)
            VALUES (?, ?, ?, ?)
            """;

    private static final String RECENT = """
            SELECT metric_name, metric_value, unit, recorded_at
            FROM system_metrics
            WHERE metric_name = ?
            ORDER BY recorded_at DESC, metric_id DESC
            LIMIT ?
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM system_metrics";
    private static final String DELETE_ALL = "DELETE FROM system_metrics";

    private final DatabaseConnection database;

    public JdbcSystemMetricDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void insert(SystemMetric metric) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, metric.name());
            statement.setDouble(2, metric.value());
            statement.setString(3, metric.unit());
            JdbcSupport.setInstant(statement, 4, metric.recordedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to insert system metric " + metric.name(), e);
        }
    }

    @Override
    public void insertBatch(List<SystemMetric> metrics) {
        if (metrics.isEmpty()) {
            return;
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                for (SystemMetric metric : metrics) {
                    statement.setString(1, metric.name());
                    statement.setDouble(2, metric.value());
                    statement.setString(3, metric.unit());
                    JdbcSupport.setInstant(statement, 4, metric.recordedAt());
                    statement.addBatch();
                }
                return statement.executeBatch().length;
            }
        });
    }

    @Override
    public List<SystemMetric> findRecent(String name, int limit) {
        List<SystemMetric> metrics = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(RECENT)) {
            statement.setString(1, name);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    metrics.add(new SystemMetric(
                            resultSet.getString("metric_name"),
                            resultSet.getDouble("metric_value"),
                            resultSet.getString("unit"),
                            JdbcSupport.getInstant(resultSet, "recorded_at")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read system metrics", e);
        }
        return metrics;
    }

    @Override
    public long count() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(COUNT);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to count system metrics", e);
        }
    }

    @Override
    public int deleteAll() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to delete system metrics", e);
        }
    }
}
