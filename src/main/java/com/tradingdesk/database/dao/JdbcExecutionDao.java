package com.tradingdesk.database.dao;

import com.tradingdesk.database.DataAccessException;
import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.model.Execution;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JdbcExecutionDao implements ExecutionDao {

    private static final String INSERT = """
            INSERT INTO executions (order_id, price, quantity, executed_at) VALUES (?, ?, ?, ?)
            """;

    private static final String FIND_BY_ORDER = """
            SELECT order_id, price, quantity, executed_at
            FROM executions WHERE order_id = ? ORDER BY execution_id
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM executions";
    private static final String DELETE_ALL = "DELETE FROM executions";

    private final DatabaseConnection database;

    public JdbcExecutionDao(DatabaseConnection database) {
        this.database = database;
    }

    @Override
    public void insert(Execution execution) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            bind(statement, execution);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to insert execution for order " + execution.orderId(), e);
        }
    }

    static void bind(PreparedStatement statement, Execution execution) throws SQLException {
        statement.setString(1, execution.orderId().toString());
        statement.setDouble(2, execution.price());
        statement.setLong(3, execution.quantity());
        JdbcSupport.setInstant(statement, 4, execution.executedAt());
    }

    @Override
    public List<Execution> findByOrder(UUID orderId) {
        List<Execution> executions = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ORDER)) {
            statement.setString(1, orderId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    executions.add(new Execution(
                            UUID.fromString(resultSet.getString("order_id")),
                            resultSet.getDouble("price"),
                            resultSet.getLong("quantity"),
                            JdbcSupport.getInstant(resultSet, "executed_at")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read executions for order " + orderId, e);
        }
        return executions;
    }

    @Override
    public long count() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(COUNT);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to count executions", e);
        }
    }

    @Override
    public int deleteAll() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to delete executions", e);
        }
    }
}
