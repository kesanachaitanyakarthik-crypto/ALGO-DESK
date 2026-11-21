package com.tradingdesk.database.dao;

import com.tradingdesk.database.DataAccessException;
import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.model.Order;
import com.tradingdesk.model.OrderStatus;
import com.tradingdesk.model.OrderType;
import com.tradingdesk.model.Side;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcOrderDao implements OrderDao {

    private static final String INSERT = """
            INSERT INTO orders
                (order_id, symbol, side, order_type, quantity, requested_price,
                 status, rejection_reason, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE orders
            SET status = ?, rejection_reason = ?, updated_at = ?
            WHERE order_id = ?
            """;

    private static final String FIND_BY_ID = """
            SELECT order_id, symbol, side, order_type, quantity, requested_price,
                   status, rejection_reason, created_at, updated_at
            FROM orders WHERE order_id = ?
            """;

    private static final String FIND_OPEN = """
            SELECT order_id, symbol, side, order_type, quantity, requested_price,
                   status, rejection_reason, created_at, updated_at
            FROM orders
            WHERE status IN ('PENDING', 'APPROVED')
            ORDER BY created_at DESC
            LIMIT ?
            """;

    private static final String FIND_HISTORY = """
            SELECT order_id, symbol, side, order_type, quantity, requested_price,
                   status, rejection_reason, created_at, updated_at
            FROM orders
            ORDER BY created_at DESC
            LIMIT ?
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM orders";
    private static final String COUNT_REJECTED = "SELECT COUNT(*) FROM orders WHERE status = 'REJECTED'";
    private static final String DELETE_ONE = "DELETE FROM orders WHERE order_id = ?";
    private static final String DELETE_ALL = "DELETE FROM orders";

    private final DatabaseConnection database;
    private final InstrumentResolver instruments;

    public JdbcOrderDao(DatabaseConnection database, InstrumentResolver instruments) {
        this.database = database;
        this.instruments = instruments;
    }

    @Override
    public void insert(Order order) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            bind(statement, order);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to insert order " + order.id(), e);
        }
    }

    @Override
    public boolean update(Order order) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, order.status().name());
            statement.setString(2, order.rejectionReason());
            JdbcSupport.setInstant(statement, 3, order.updatedAt());
            statement.setString(4, order.id().toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to update order " + order.id(), e);
        }
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setString(1, orderId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read order " + orderId, e);
        }
    }

    @Override
    public List<Order> findOpenOrders(int limit) {
        return query(FIND_OPEN, limit);
    }

    @Override
    public List<Order> findHistory(int limit) {
        return query(FIND_HISTORY, limit);
    }

    private List<Order> query(String sql, int limit) {
        List<Order> orders = new ArrayList<>();
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    orders.add(map(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read orders", e);
        }
        return orders;
    }

    @Override
    public long count() {
        return singleCount(COUNT);
    }

    @Override
    public long countRejected() {
        return singleCount(COUNT_REJECTED);
    }

    private long singleCount(String sql) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to count orders", e);
        }
    }

    @Override
    public boolean delete(UUID orderId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
            statement.setString(1, orderId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Unable to delete order " + orderId, e);
        }
    }

    @Override
    public int deleteAll() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Unable to delete orders", e);
        }
    }

    private static void bind(PreparedStatement statement, Order order) throws SQLException {
        statement.setString(1, order.id().toString());
        statement.setString(2, order.instrument().symbol());
        statement.setString(3, order.side().name());
        statement.setString(4, order.type().name());
        statement.setLong(5, order.quantity());
        statement.setDouble(6, order.requestedPrice());
        statement.setString(7, order.status().name());
        statement.setString(8, order.rejectionReason());
        JdbcSupport.setInstant(statement, 9, order.createdAt());
        JdbcSupport.setInstant(statement, 10, order.updatedAt());
    }

    private Order map(ResultSet resultSet) throws SQLException {
        return new Order(
                UUID.fromString(resultSet.getString("order_id")),
                instruments.resolve(resultSet.getString("symbol")),
                JdbcSupport.getEnum(resultSet, "side", Side.class),
                JdbcSupport.getEnum(resultSet, "order_type", OrderType.class),
                resultSet.getLong("quantity"),
                resultSet.getDouble("requested_price"),
                JdbcSupport.getEnum(resultSet, "status", OrderStatus.class),
                resultSet.getString("rejection_reason"),
                JdbcSupport.getInstant(resultSet, "created_at"),
                JdbcSupport.getInstant(resultSet, "updated_at"));
    }
}
