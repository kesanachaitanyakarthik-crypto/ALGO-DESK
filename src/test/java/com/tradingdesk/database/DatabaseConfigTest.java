package com.tradingdesk.database;

import com.tradingdesk.utils.ConfigLoader;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    @Test
    void loadsUrlAndUsernameFromProperties() {
        Properties properties = new Properties();
        properties.setProperty("db.url", "jdbc:mysql://localhost:3306/trading_desk");
        properties.setProperty("db.username", "trading_app");
        properties.setProperty("db.password", "secret");

        // explicit(...) ignores the ambient environment, so a developer with real DB_*
        // variables set cannot make this assertion flaky.
        DatabaseConfig config = DatabaseConfig.from(ConfigLoader.explicit(properties));

        assertEquals("jdbc:mysql://localhost:3306/trading_desk", config.url());
        assertEquals("trading_app", config.username());
        assertEquals("secret", config.password());
        assertEquals("com.mysql.cj.jdbc.Driver", config.driver());
    }

    @Test
    void refusesToRunWithoutAPassword() {
        Properties properties = new Properties();
        properties.setProperty("db.url", "jdbc:mysql://localhost:3306/trading_desk");
        properties.setProperty("db.username", "trading_app");

        assertThrows(IllegalStateException.class,
                () -> DatabaseConfig.from(ConfigLoader.explicit(properties)));
    }

    @Test
    void ambientEnvironmentOverridesFileValues() {
        Properties properties = new Properties();
        properties.setProperty("db.url", "file-url");
        properties.setProperty("db.username", "file-user");
        properties.setProperty("db.password", "file-secret");

        // System properties take precedence over the supplied file in the "of" variant.
        System.setProperty("DB_URL", "env-url");
        try {
            DatabaseConfig config = DatabaseConfig.from(ConfigLoader.of(properties));
            assertEquals("env-url", config.url());
        } finally {
            System.clearProperty("DB_URL");
        }
    }

    @Test
    void neverExposesPasswordInToString() {
        DatabaseConfig config = new DatabaseConfig(
                "jdbc:mysql://localhost:3306/trading_desk", "trading_app", "super-secret", "com.mysql.cj.jdbc.Driver");
        assertTrue(!config.toString().contains("super-secret"), "toString must not leak credentials");
        assertTrue(config.toString().contains("***"));
    }

    @Test
    void rejectsBlankUrlAndUsername() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                " ", "trading_app", "secret", "com.mysql.cj.jdbc.Driver"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(
                "jdbc:mysql://localhost/db", " ", "secret", "com.mysql.cj.jdbc.Driver"));
    }
}
