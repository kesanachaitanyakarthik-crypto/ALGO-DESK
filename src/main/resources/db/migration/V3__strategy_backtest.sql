-- V3: strategy definitions and backtest results.

CREATE TABLE IF NOT EXISTS strategies (
    strategy_id   VARCHAR(36)  NOT NULL,
    name          VARCHAR(96)  NOT NULL,
    strategy_type VARCHAR(32)  NOT NULL,
    parameters    VARCHAR(1024) NOT NULL,
    symbols       VARCHAR(255) NOT NULL,
    active        TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (strategy_id),
    INDEX idx_strategies_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS backtest_runs (
    run_id            VARCHAR(36)   NOT NULL,
    strategy_type     VARCHAR(32)   NOT NULL,
    symbol            VARCHAR(24)   NOT NULL,
    parameters        VARCHAR(1024) NOT NULL,
    initial_capital   DECIMAL(18,2) NOT NULL,
    final_equity      DECIMAL(18,2) NOT NULL,
    total_return_pct  DECIMAL(12,4) NOT NULL,
    win_rate_pct      DECIMAL(12,4) NOT NULL,
    max_drawdown_pct  DECIMAL(12,4) NOT NULL,
    profit_factor     DECIMAL(12,4) NOT NULL,
    sharpe_ratio      DECIMAL(12,4) NOT NULL,
    trade_count       INT           NOT NULL,
    started_at        DATETIME(3)   NOT NULL,
    completed_at      DATETIME(3)   NOT NULL,
    PRIMARY KEY (run_id),
    INDEX idx_backtest_runs_strategy (strategy_type, completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS backtest_trades (
    trade_id   BIGINT        NOT NULL AUTO_INCREMENT,
    run_id     VARCHAR(36)   NOT NULL,
    side       VARCHAR(4)    NOT NULL,
    quantity   BIGINT        NOT NULL,
    entry_price DECIMAL(18,6) NOT NULL,
    exit_price DECIMAL(18,6) NOT NULL,
    pnl        DECIMAL(18,6) NOT NULL,
    fees       DECIMAL(18,6) NOT NULL,
    opened_at  DATETIME(3)   NOT NULL,
    closed_at  DATETIME(3)   NOT NULL,
    PRIMARY KEY (trade_id),
    INDEX idx_backtest_trades_run (run_id),
    CONSTRAINT fk_backtest_trades_run
        FOREIGN KEY (run_id) REFERENCES backtest_runs (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
