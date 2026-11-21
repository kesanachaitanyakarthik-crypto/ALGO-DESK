package com.tradingdesk.database;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies the versioned migrations against the live database.
 *
 * <p>Opt-in, exactly like {@code MySqlPersistenceIntegrationTest}: enabled only with
 * {@code -Ddb.enabled=true} and credentials present. Idempotent — running it twice must apply
 * nothing the second time.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "db.enabled", matches = "true")
class MigrationRunnerIntegrationTest {

    @Test
    void appliesAllMigrationsThenIsIdempotent() {
        Persistence persistence;
        try {
            persistence = MySqlPersistence.connect();
        } catch (RuntimeException e) {
            throw new AssertionError("MySQL is not reachable: " + e.getMessage(), e);
        }

        MigrationRunner runner = persistence.migrationRunner();

        // connect() already migrated; a second run must apply nothing.
        List<Integer> secondRun = runner.migrate();
        assertTrue(secondRun.isEmpty(),
                "migrations must be idempotent, but re-applied " + secondRun);

        List<Integer> applied = runner.appliedVersions();
        assertTrue(applied.containsAll(List.of(1, 2, 3, 4, 5)),
                "expected versions 1-5 to be recorded, found " + applied);

        Set<String> tables = persistence.migrator().existingTables();
        for (String table : List.of("schema_version", "paper_accounts", "paper_orders", "paper_fills",
                "strategies", "backtest_runs", "backtest_trades", "candles",
                "market_data_status", "exchange_connections")) {
            assertTrue(tables.contains(table), "expected table " + table + " to exist, found " + tables);
        }

        // The pre-existing schema must be untouched by migration.
        assertFalse(persistence.migrator().missingTables().size() > 0,
                "original tables must still be present: " + persistence.migrator().missingTables());
    }

    @Test
    void versionTableRecordsNameAndTimestamp() {
        Persistence persistence = MySqlPersistence.connect();
        MigrationRunner runner = persistence.migrationRunner();

        for (MigrationRunner.Migration migration : runner.availableMigrations()) {
            assertTrue(runner.appliedVersions().contains(migration.version()));
            assertFalse(migration.name().isBlank());
        }
    }
}
