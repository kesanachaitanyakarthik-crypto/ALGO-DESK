package com.tradingdesk.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SchemaMigrator {

    public static final String SCHEMA_RESOURCE = "sql/schema.sql";

    private static final List<String> REQUIRED_TABLES = List.of(
            "instruments",
            "market_events",
            "trading_signals",
            "orders",
            "executions",
            "portfolio_snapshots",
            "system_metrics");

    private final DatabaseConnection database;

    public SchemaMigrator(DatabaseConnection database) {
        this.database = database;
    }

    public void migrate() {
        for (String statement : readSchemaStatements()) {
            try (Connection connection = database.open();
                 Statement sql = connection.createStatement()) {
                sql.executeUpdate(statement);
            } catch (SQLException e) {
                throw new DataAccessException("Schema migration failed: " + firstLine(statement), e);
            }
        }
    }

    public Set<String> existingTables() {
        Set<String> tables = new LinkedHashSet<>();
        try (Connection connection = database.open();
             ResultSet resultSet = connection.getMetaData()
                     .getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read table metadata", e);
        }
        return tables;
    }

    public List<String> missingTables() {
        Set<String> existing = existingTables();
        List<String> missing = new ArrayList<>();
        for (String table : REQUIRED_TABLES) {
            if (!existing.contains(table)) {
                missing.add(table);
            }
        }
        return missing;
    }

    public boolean schemaIsComplete() {
        return missingTables().isEmpty();
    }

    public void dropAll() {
        for (String table : List.of("executions", "orders", "trading_signals",
                "market_events", "portfolio_snapshots", "system_metrics", "instruments")) {
            try (Connection connection = database.open();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE IF EXISTS " + table);
            } catch (SQLException e) {
                throw new DataAccessException("Unable to drop table " + table, e);
            }
        }
    }

    static List<String> readSchemaStatements() {
        String script = readResource();
        List<String> statements = new ArrayList<>();
        for (String chunk : script.split(";")) {
            String cleaned = stripComments(chunk).trim();
            if (!cleaned.isEmpty()) {
                statements.add(cleaned);
            }
        }
        return statements;
    }

    private static String readResource() {
        try (InputStream stream = SchemaMigrator.class.getClassLoader()
                .getResourceAsStream(SCHEMA_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing SQL resource: " + SCHEMA_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + SCHEMA_RESOURCE, e);
        }
    }

    private static String stripComments(String sql) {
        StringBuilder builder = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (!line.trim().startsWith("--")) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static String firstLine(String statement) {
        String trimmed = statement.trim();
        int newline = trimmed.indexOf('\n');
        return newline < 0 ? trimmed : trimmed.substring(0, newline);
    }
}
