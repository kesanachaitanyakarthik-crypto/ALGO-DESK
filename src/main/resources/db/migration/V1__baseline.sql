-- V1: baseline marker.
--
-- The original seven tables (instruments, market_events, trading_signals, orders, executions,
-- portfolio_snapshots, system_metrics) are created idempotently by SchemaMigrator from
-- sql/schema.sql. This migration records that baseline in schema_version so later versions can
-- build on it, without re-declaring or touching those tables.

-- Intentionally empty of DDL: the marker row is written by MigrationRunner itself.
