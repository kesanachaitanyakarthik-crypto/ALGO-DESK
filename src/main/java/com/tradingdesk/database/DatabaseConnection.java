package com.tradingdesk.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection implements AutoCloseable {

    private final DatabaseConfig config;
    private volatile boolean driverLoaded;

    public DatabaseConnection(DatabaseConfig config) {
        this.config = config;
    }

    public Connection open() throws SQLException {
        ensureDriver();
        return DriverManager.getConnection(config.url(), config.username(), config.password());
    }

    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = open()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database transaction failed", e);
        }
    }

    public boolean testConnection() {
        try (Connection connection = open()) {
            return connection.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    public DatabaseConfig config() {
        return config;
    }

    private void ensureDriver() throws SQLException {
        if (driverLoaded) {
            return;
        }
        try {
            Class.forName(config.driver());
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + config.driver(), e);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the original failure is more useful than a rollback failure
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean value) {
        try {
            connection.setAutoCommit(value);
        } catch (SQLException ignored) {
            // connection is being closed anyway
        }
    }

    @Override
    public void close() {
        // connections are opened per operation and closed in try-with-resources
    }
}
