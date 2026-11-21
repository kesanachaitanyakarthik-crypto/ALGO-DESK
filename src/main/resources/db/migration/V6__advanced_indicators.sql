-- V6: persist the advanced indicator values alongside each generated signal.
--
-- Columns are added with defaults so existing signal rows remain valid and no data is rewritten.
-- MySQL 8+/9 does not support "ADD COLUMN IF NOT EXISTS", so this migration is written to be
-- re-runnable through the version table (it runs exactly once) and uses a single ALTER per column.

ALTER TABLE trading_signals
    ADD COLUMN rsi               DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN macd              DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN macd_signal       DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN macd_histogram    DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN bollinger_upper   DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN bollinger_middle  DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN bollinger_lower   DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN atr               DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN stochastic_k      DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN stochastic_d      DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN vwap              DECIMAL(18,6) NOT NULL DEFAULT 0;
