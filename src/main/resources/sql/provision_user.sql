-- =====================================================================
-- Algorithmic Trading Desk - one-time provisioning script
--
-- Run ONCE as a MySQL administrator (root in MySQL Workbench, or the
-- mysql CLI). The application itself never uses root.
--
-- Replace CHANGE_ME with a strong password of your choosing, then set the
-- same value locally as the DB_PASSWORD environment variable (preferred)
-- or in config/db.properties (gitignored).
-- =====================================================================

CREATE DATABASE IF NOT EXISTS trading_desk
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'trading_app'@'localhost'
    IDENTIFIED BY 'CHANGE_ME';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, REFERENCES
    ON trading_desk.*
    TO 'trading_app'@'localhost';

FLUSH PRIVILEGES;
