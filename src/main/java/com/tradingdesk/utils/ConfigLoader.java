package com.tradingdesk.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class ConfigLoader {

    public static final String LOCAL_CONFIG_PATH = "config/db.properties";

    private final Properties properties;
    private final boolean consultAmbientSources;

    private ConfigLoader(Properties properties, boolean consultAmbientSources) {
        this.properties = properties;
        this.consultAmbientSources = consultAmbientSources;
    }

    public static ConfigLoader fromLocalFile() {
        Properties loaded = new Properties();
        Path path = Path.of(LOCAL_CONFIG_PATH);
        if (Files.isReadable(path)) {
            try (InputStream stream = Files.newInputStream(path)) {
                loaded.load(stream);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read " + LOCAL_CONFIG_PATH, e);
            }
        }
        return new ConfigLoader(loaded, true);
    }

    /**
     * A loader backed only by the supplied properties.
     *
     * <p>Unlike {@link #fromLocalFile()}, this deliberately ignores system properties and
     * environment variables, so code that is handed an explicit configuration cannot be
     * affected by the ambient environment. That is the behaviour callers expect from
     * "here is my properties", and it is what makes the credential handling testable.
     */
    public static ConfigLoader explicit(Properties properties) {
        return new ConfigLoader(properties, false);
    }

    /** A loader with normal precedence: system property, then environment, then the given file. */
    public static ConfigLoader of(Properties properties) {
        return new ConfigLoader(properties, true);
    }

    public Optional<String> find(String key) {
        if (consultAmbientSources) {
            String systemProperty = System.getProperty(key);
            if (systemProperty != null && !systemProperty.isBlank()) {
                return Optional.of(systemProperty.trim());
            }
            String environmentValue = System.getenv(key);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return Optional.of(environmentValue.trim());
            }
        }
        String propertyValue = properties.getProperty(key);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Optional.of(propertyValue.trim());
        }
        return Optional.empty();
    }

    public String require(String key) {
        return find(key).orElseThrow(
                () -> new IllegalStateException("Missing required configuration value: " + key));
    }
}
