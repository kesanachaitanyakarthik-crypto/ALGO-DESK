-- V5: exchange connection metadata.
--
-- Deliberately stores NO credentials. Only non-secret connection metadata is persisted so the UI
-- can show which exchange/mode is configured. API keys and secrets live exclusively in the
-- process environment and are never written to the database, logs, or source.

CREATE TABLE IF NOT EXISTS exchange_connections (
    connection_id   VARCHAR(36)  NOT NULL,
    exchange_name   VARCHAR(32)  NOT NULL,
    mode            VARCHAR(8)   NOT NULL,
    sandbox         TINYINT(1)   NOT NULL DEFAULT 1,
    key_fingerprint VARCHAR(64)  NULL,
    enabled         TINYINT(1)   NOT NULL DEFAULT 0,
    last_checked_at DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (connection_id),
    INDEX idx_exchange_connections_mode (mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
