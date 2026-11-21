package com.tradingdesk.database.dao;

import com.tradingdesk.database.DatabaseConnection;
import com.tradingdesk.model.Execution;
import com.tradingdesk.model.Order;

import java.sql.PreparedStatement;
import java.util.List;

public class OrderPersistence {

    private static final String INSERT_ORDER = """
            INSERT INTO orders
                (order_id, symbol, side, order_type, quantity, requested_price,
                 status, rejection_reason, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final DatabaseConnection database;

    public OrderPersistence(DatabaseConnection database) {
        this.database = database;
    }

    public void persistExecution(Order order, Execution execution) {
        database.inTransaction(connection -> {
            try (PreparedStatement insertOrder = connection.prepareStatement(INSERT_ORDER)) {
                insertOrder.setString(1, order.id().toString());
                insertOrder.setString(2, order.instrument().symbol());
                insertOrder.setString(3, order.side().name());
                insertOrder.setString(4, order.type().name());
                insertOrder.setLong(5, order.quantity());
                insertOrder.setDouble(6, order.requestedPrice());
                insertOrder.setString(7, order.status().name());
                insertOrder.setString(8, order.rejectionReason());
                JdbcSupport.setInstant(insertOrder, 9, order.createdAt());
                JdbcSupport.setInstant(insertOrder, 10, order.updatedAt());
                insertOrder.executeUpdate();
            }
            try (PreparedStatement insertExecution = connection.prepareStatement(
                    "INSERT INTO executions (order_id, price, quantity, executed_at) VALUES (?, ?, ?, ?)")) {
                JdbcExecutionDao.bind(insertExecution, execution);
                insertExecution.executeUpdate();
            }
            return null;
        });
    }

    public void persistAll(List<Order> orders) {
        if (orders.isEmpty()) {
            return;
        }
        database.inTransaction(connection -> {
            try (PreparedStatement insertOrder = connection.prepareStatement(INSERT_ORDER)) {
                for (Order order : orders) {
                    insertOrder.setString(1, order.id().toString());
                    insertOrder.setString(2, order.instrument().symbol());
                    insertOrder.setString(3, order.side().name());
                    insertOrder.setString(4, order.type().name());
                    insertOrder.setLong(5, order.quantity());
                    insertOrder.setDouble(6, order.requestedPrice());
                    insertOrder.setString(7, order.status().name());
                    insertOrder.setString(8, order.rejectionReason());
                    JdbcSupport.setInstant(insertOrder, 9, order.createdAt());
                    JdbcSupport.setInstant(insertOrder, 10, order.updatedAt());
                    insertOrder.addBatch();
                }
                return insertOrder.executeBatch().length;
            }
        });
    }
}
