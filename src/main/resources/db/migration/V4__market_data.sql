-- V4: market data — OHLCV candles cached from external providers, plus provider status history.

CREATE TABLE IF NOT EXISTS candles (
    candle_id   BIGINT        NOT NULL AUTO_INCREMENT,
    provider    VARCHAR(32)   NOT NULL,
    symbol      VARCHAR(32)   NOT NULL,
    interval_code VARCHAR(8)  NOT NULL,
    open_time   DATETIME(3)   NOT NULL,
    open_price  DECIMAL(18,8) NOT NULL,
    high_price  DECIMAL(18,8) NOT NULL,
    low_price   DECIMAL(18,8) NOT NULL,
    close_price DECIMAL(18,8) NOT NULL,
    volume      DECIMAL(24,8) NOT NULL,
    PRIMARY KEY (candle_id),
    UNIQUE KEY uq_candles_provider_symbol_interval_time
        (provider, symbol, interval_code, open_time),
    INDEX idx_candles_symbol_time (symbol, open_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS market_data_status (
    status_id    BIGINT       NOT NULL AUTO_INCREMENT,
    provider     VARCHAR(32)  NOT NULL,
    provider_kind VARCHAR(16) NOT NULL,
    connected    TINYINT(1)   NOT NULL,
    detail       VARCHAR(255) NULL,
    recorded_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (status_id),
    INDEX idx_market_data_status_time (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
