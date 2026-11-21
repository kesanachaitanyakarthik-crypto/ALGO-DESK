package com.tradingdesk.database;

import com.tradingdesk.utils.ConfigLoader;

import java.util.Objects;

public record DatabaseConfig(String url, String username, String password, String driver) {

    public static final String ENV_URL = "DB_URL";
    public static final String ENV_USERNAME = "DB_USERNAME";
    public static final String ENV_PASSWORD = "DB_PASSWORD";

    public DatabaseConfig {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (driver == null || driver.isBlank()) {
            throw new IllegalArgumentException("driver must not be blank");
        }
    }

    public static DatabaseConfig load() {
        return from(ConfigLoader.fromLocalFile());
    }

    public static DatabaseConfig from(ConfigLoader loader) {
        String url = loader.find(ENV_URL)
                .orElseGet(() -> loader.find("db.url")
                        .orElse("jdbc:mysql://localhost:3306/trading_desk"
                                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"));
        String username = loader.find(ENV_USERNAME)
                .orElseGet(() -> loader.find("db.username").orElse("trading_app"));
        String password = loader.find(ENV_PASSWORD)
                .orElseGet(() -> loader.find("db.password").orElse(""));
        if (password.isBlank()) {
            throw new IllegalStateException(
                    "No MySQL password configured. Set the " + ENV_PASSWORD
                            + " environment variable or db.password in config/db.properties.");
        }
        String driver = loader.find("db.driver").orElse("com.mysql.cj.jdbc.Driver");
        return new DatabaseConfig(url, username, password, driver);
    }

    @Override
    public String toString() {
        return "DatabaseConfig[url=" + url + ", username=" + username + ", password=***]";
    }
}
