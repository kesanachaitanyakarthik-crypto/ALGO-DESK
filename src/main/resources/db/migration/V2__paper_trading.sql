-- V2: paper trading.
--
-- Virtual accounts, the richer order lifecycle, and individual fills. These are new tables
-- alongside the existing `orders` / `executions`, which continue to serve the automated
-- signal path unchanged.

CREATE TABLE IF NOT EXISTS paper_accounts (
    account_id       VARCHAR(36)   NOT NULL,
    currency         VARCHAR(8)    NOT NULL,
    starting_balance DECIMAL(18,2) NOT NULL,
    cash             DECIMAL(18,2) NOT NULL,
    used_margin      DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    realized_pnl     DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    created_at       DATETIME(3)   NOT NULL,
    updated_at       DATETIME(3)   NOT NULL,
    PRIMARY KEY (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_orders (
    order_id         CHAR(36)      NOT NULL,
    account_id       VARCHAR(36)   NOT NULL,
    symbol           VARCHAR(24)   NOT NULL,
    side             VARCHAR(4)    NOT NULL,
    order_kind       VARCHAR(16)   NOT NULL,
    quantity         BIGINT        NOT NULL,
    limit_price      DECIMAL(18,6) NULL,
    stop_price       DECIMAL(18,6) NULL,
    take_profit_price DECIMAL(18,6) NULL,
    filled_quantity  BIGINT        NOT NULL DEFAULT 0,
    average_fill_price DECIMAL(18,6) NULL,
    fees_paid        DECIMAL(18,6) NOT NULL DEFAULT 0,
    status           VARCHAR(20)   NOT NULL,
    rejection_reason VARCHAR(255)  NULL,
    created_at       DATETIME(3)   NOT NULL,
    updated_at       DATETIME(3)   NOT NULL,
    PRIMARY KEY (order_id),
    INDEX idx_paper_orders_account_status (account_id, status),
    INDEX idx_paper_orders_symbol (symbol),
    INDEX idx_paper_orders_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_fills (
    fill_id      BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     CHAR(36)      NOT NULL,
    quantity     BIGINT        NOT NULL,
    price        DECIMAL(18,6) NOT NULL,
    fee          DECIMAL(18,6) NOT NULL,
    slippage     DECIMAL(18,6) NOT NULL DEFAULT 0,
    filled_at    DATETIME(3)   NOT NULL,
    PRIMARY KEY (fill_id),
    INDEX idx_paper_fills_order (order_id),
    CONSTRAINT fk_paper_fills_order
        FOREIGN KEY (order_id) REFERENCES paper_orders (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
