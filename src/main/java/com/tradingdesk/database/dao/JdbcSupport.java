package com.tradingdesk.database.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

final class JdbcSupport {

    private JdbcSupport() {
    }

    static void setInstant(PreparedStatement statement, int index, Instant instant) throws SQLException {
        statement.setTimestamp(index, Timestamp.from(instant));
    }

    static Instant getInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    static <E extends Enum<E>> E getEnum(ResultSet resultSet, String column, Class<E> type) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }
}
