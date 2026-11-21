package com.tradingdesk.database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline checks for the migration infrastructure. The live application of migrations is covered by
 * {@link MigrationRunnerIntegrationTest}, which requires a database.
 */
class MigrationRunnerTest {

    @Test
    void manifestDeclaresVersionsInAscendingOrder() {
        MigrationRunner runner = new MigrationRunner(null);
        List<MigrationRunner.Migration> migrations = runner.availableMigrations();

        assertFalse(migrations.isEmpty(), "the manifest must declare at least the baseline");

        int previous = 0;
        for (MigrationRunner.Migration migration : migrations) {
            assertTrue(migration.version() > previous,
                    "versions must be strictly ascending, saw " + migration.version());
            previous = migration.version();
        }
    }

    @Test
    void manifestCoversAllPlannedVersions() {
        List<MigrationRunner.Migration> migrations = new MigrationRunner(null).availableMigrations();
        List<Integer> versions = migrations.stream().map(MigrationRunner.Migration::version).toList();
        assertEquals(List.of(1, 2, 3, 4, 5, 6), versions);
    }

    @Test
    void everyDeclaredMigrationResourceExists() {
        for (MigrationRunner.Migration migration : new MigrationRunner(null).availableMigrations()) {
            assertTrue(MigrationRunnerTest.class.getClassLoader()
                            .getResourceAsStream(migration.resource()) != null,
                    "missing migration resource: " + migration.resource());
        }
    }

    @Test
    void statementSplitterDropsCommentsAndBlankStatements() {
        String script = """
                -- a comment line
                CREATE TABLE a (id INT NOT NULL);

                -- another comment
                CREATE TABLE b (id INT NOT NULL);
                """;

        List<String> statements = MigrationRunner.splitStatements(script);

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE a"));
        assertTrue(statements.get(1).startsWith("CREATE TABLE b"));
    }

    @Test
    void statementSplitterIgnoresTrailingSemicolon() {
        assertEquals(1, MigrationRunner.splitStatements("SELECT 1;").size());
        assertEquals(0, MigrationRunner.splitStatements("-- only a comment\n").size());
    }

    @Test
    void migrationsAreAdditiveByPolicy() {
        // Guards the "never drop or reset" requirement: no migration may contain destructive DDL.
        for (MigrationRunner.Migration migration : new MigrationRunner(null).availableMigrations()) {
            String script = readResource(migration.resource()).toUpperCase(java.util.Locale.ROOT);
            assertFalse(script.contains("DROP TABLE"),
                    "V" + migration.version() + " must not drop tables");
            assertFalse(script.contains("TRUNCATE"),
                    "V" + migration.version() + " must not truncate tables");
        }
    }

    private static String readResource(String path) {
        try (var stream = MigrationRunnerTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
