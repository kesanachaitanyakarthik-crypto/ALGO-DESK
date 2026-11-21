package com.tradingdesk.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies versioned SQL migrations from {@code db/migration}, recording each applied version in
 * {@code schema_version}.
 *
 * <p>Migrations are additive by policy: they may create or alter, but this runner never issues a
 * DROP or TRUNCATE, so existing data survives every upgrade. Each migration runs inside its own
 * transaction, and the version row is written in the same transaction, so a failure leaves neither
 * a half-applied migration nor a false version record.
 *
 * <p>The legacy {@link SchemaMigrator} (which creates the original seven tables from
 * {@code sql/schema.sql}) remains in place and still runs first; V1 here is a no-op baseline marker
 * that simply records the fact for subsequent versioned work.
 */
public class MigrationRunner {

    private static final Logger LOGGER = Logger.getLogger(MigrationRunner.class.getName());

    static final String MIGRATION_PATH = "db/migration/manifest.txt";
    private static final String VERSION_TABLE = "schema_version";

    private final DatabaseConnection database;

    public MigrationRunner(DatabaseConnection database) {
        this.database = database;
    }

    /** One migration file: its numeric version, name, and the SQL resource to execute. */
    public record Migration(int version, String name, String resource) {
    }

    /**
     * Ensures the version table exists, then applies every migration whose version is not yet
     * recorded. Returns the versions actually applied in this call.
     */
    public List<Integer> migrate() {
        ensureVersionTable();
        List<Integer> applied = appliedVersions();
        List<Integer> newlyApplied = new ArrayList<>();

        for (Migration migration : availableMigrations()) {
            if (applied.contains(migration.version())) {
                continue;
            }
            apply(migration);
            newlyApplied.add(migration.version());
        }
        return newlyApplied;
    }

    /** Versions already recorded, in ascending order. */
    public List<Integer> appliedVersions() {
        ensureVersionTable();
        List<Integer> versions = new ArrayList<>();
        try (Connection connection = database.open();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version FROM " + VERSION_TABLE + " ORDER BY version")) {
            while (resultSet.next()) {
                versions.add(resultSet.getInt(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to read applied migration versions", e);
        }
        return versions;
    }

    /** Migration files declared in the manifest, sorted by version. */
    public List<Migration> availableMigrations() {
        List<Migration> migrations = new ArrayList<>();
        for (String line : readManifest().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            // Format: <version>|<name>|<resource path>
            String[] parts = trimmed.split("\\|");
            if (parts.length != 3) {
                throw new IllegalStateException("Malformed migration entry: " + trimmed);
            }
            migrations.add(new Migration(
                    Integer.parseInt(parts[0].trim()),
                    parts[1].trim(),
                    parts[2].trim()));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        return migrations;
    }

    private void apply(Migration migration) {
        String script = readResource(migration.resource());
        try (Connection connection = database.open()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (String statementSql : splitStatements(script)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate(statementSql);
                    }
                }
                recordVersion(connection, migration);
                connection.commit();
                LOGGER.info("Applied migration V" + migration.version() + " " + migration.name());
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                throw new DataAccessException(
                        "Migration V" + migration.version() + " (" + migration.name() + ") failed", e);
            } finally {
                restoreAutoCommit(connection, previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Unable to apply migration V" + migration.version(), e);
        }
    }

    private void recordVersion(Connection connection, Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + VERSION_TABLE + " (version, name, applied_at) VALUES (?, ?, NOW(3))")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.name());
            statement.executeUpdate();
        }
    }

    private void ensureVersionTable() {
        try (Connection connection = database.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version    INT          NOT NULL,
                        name       VARCHAR(96)  NOT NULL,
                        applied_at DATETIME(3)  NOT NULL,
                        PRIMARY KEY (version)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (SQLException e) {
            throw new DataAccessException("Unable to create " + VERSION_TABLE, e);
        }
    }

    /**
     * Splits a script on semicolons, dropping comment-only lines. Sufficient for the simple DDL used
     * here; deliberately not a general-purpose SQL parser.
     */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
        }
        for (String chunk : current.toString().split(";")) {
            String cleaned = chunk.trim();
            if (!cleaned.isEmpty()) {
                statements.add(cleaned);
            }
        }
        return statements;
    }

    private static String readManifest() {
        return readResource(MIGRATION_PATH);
    }

    private static String readResource(String path) {
        try (InputStream stream = MigrationRunner.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing migration resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read migration resource: " + path, e);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Rollback failed", e);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean value) {
        try {
            connection.setAutoCommit(value);
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Unable to restore autocommit", e);
        }
    }
}
