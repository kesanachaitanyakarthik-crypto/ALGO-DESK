-- =====================================================================
-- Algorithmic Trading Desk - MySQL schema
-- Academic simulation only. No real brokerage connectivity.
--
-- Run as the trading_app user against the trading_desk database:
--   mysql -u trading_app -p trading_desk < src/main/resources/sql/schema.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS instruments (
    symbol          VARCHAR(24)   NOT NULL,
    display_name    VARCHAR(96)   NOT NULL,
    instrument_type VARCHAR(16)   NOT NULL,
    PRIMARY KEY (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS market_events (
    sequence_no BIGINT        NOT NULL,
    symbol      VARCHAR(24)   NOT NULL,
    event_type  VARCHAR(16)   NOT NULL,
    last_price  DECIMAL(18,6) NOT NULL,
    bid_price   DECIMAL(18,6) NOT NULL,
    ask_price   DECIMAL(18,6) NOT NULL,
    volume      BIGINT        NOT NULL,
    occurred_at DATETIME(3)   NOT NULL,
    PRIMARY KEY (sequence_no),
    INDEX idx_market_events_symbol_time (symbol, occurred_at),
    CONSTRAINT fk_market_events_instrument
        FOREIGN KEY (symbol) REFERENCES instruments (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trading_signals (
    signal_id     BIGINT        NOT NULL AUTO_INCREMENT,
    symbol        VARCHAR(24)   NOT NULL,
    signal_type   VARCHAR(8)    NOT NULL,
    confidence    DECIMAL(6,4)  NOT NULL,
    rationale     VARCHAR(255)  NOT NULL,
    sma           DECIMAL(18,6) NOT NULL,
    ema           DECIMAL(18,6) NOT NULL,
    roc           DECIMAL(18,6) NOT NULL,
    volatility    DECIMAL(18,6) NOT NULL,
    imbalance     DECIMAL(18,6) NOT NULL,
    generated_at  DATETIME(3)   NOT NULL,
    PRIMARY KEY (signal_id),
    INDEX idx_signals_symbol_time (symbol, generated_at),
    CONSTRAINT fk_signals_instrument
        FOREIGN KEY (symbol) REFERENCES instruments (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    order_id         CHAR(36)      NOT NULL,
    symbol           VARCHAR(24)   NOT NULL,
    side             VARCHAR(4)    NOT NULL,
    order_type       VARCHAR(8)    NOT NULL,
    quantity         BIGINT        NOT NULL,
    requested_price  DECIMAL(18,6) NOT NULL,
    status           VARCHAR(12)   NOT NULL,
    rejection_reason VARCHAR(255)  NULL,
    created_at       DATETIME(3)   NOT NULL,
    updated_at       DATETIME(3)   NOT NULL,
    PRIMARY KEY (order_id),
    INDEX idx_orders_symbol_status (symbol, status),
    INDEX idx_orders_created (created_at),
    CONSTRAINT fk_orders_instrument
        FOREIGN KEY (symbol) REFERENCES instruments (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS executions (
    execution_id BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     CHAR(36)      NOT NULL,
    price        DECIMAL(18,6) NOT NULL,
    quantity     BIGINT        NOT NULL,
    executed_at  DATETIME(3)   NOT NULL,
    PRIMARY KEY (execution_id),
    INDEX idx_executions_order (order_id),
    CONSTRAINT fk_executions_order
        FOREIGN KEY (order_id) REFERENCES orders (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS portfolio_snapshots (
    snapshot_id    BIGINT        NOT NULL AUTO_INCREMENT,
    cash           DECIMAL(18,2) NOT NULL,
    market_value   DECIMAL(18,2) NOT NULL,
    realized_pnl   DECIMAL(18,2) NOT NULL,
    unrealized_pnl DECIMAL(18,2) NOT NULL,
    captured_at    DATETIME(3)   NOT NULL,
    PRIMARY KEY (snapshot_id),
    INDEX idx_snapshots_time (captured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_metrics (
    metric_id    BIGINT        NOT NULL AUTO_INCREMENT,
    metric_name  VARCHAR(64)   NOT NULL,
    metric_value DECIMAL(18,4) NOT NULL,
    unit         VARCHAR(16)   NOT NULL,
    recorded_at  DATETIME(3)   NOT NULL,
    PRIMARY KEY (metric_id),
    INDEX idx_metrics_name_time (metric_name, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
