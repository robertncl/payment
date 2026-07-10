-- Executed by the oceanbase-ce image after tenant bootstrap (mounted at /root/boot/init.d).
-- NOTE: init.d only runs on first bootstrap of the ob-data volume. On an existing volume,
-- apply manually: docker compose exec oceanbase obclient -uroot@paylab -p... < this file.

-- Service-owned databases (schema-per-service; tables come from each service's Flyway).
CREATE DATABASE IF NOT EXISTS paylab_gateway;
CREATE DATABASE IF NOT EXISTS paylab_ledger;
CREATE DATABASE IF NOT EXISTS paylab_risk;

-- Seata AT bootstrap deadlock (ADR-0004): DataSourceProxy refuses to start when undo_log is
-- missing, but Flyway (which would create it) needs that datasource — so undo_log must exist
-- BEFORE the services boot. The Flyway V2 migrations are IF NOT EXISTS no-ops on this path;
-- they remain authoritative for H2 test runs where Seata is disabled.
CREATE TABLE IF NOT EXISTS paylab_gateway.undo_log (
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT          NOT NULL,
    log_created   DATETIME(6)  NOT NULL,
    log_modified  DATETIME(6)  NOT NULL,
    UNIQUE KEY ux_undo_log (xid, branch_id)
);
CREATE TABLE IF NOT EXISTS paylab_ledger.undo_log (
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT          NOT NULL,
    log_created   DATETIME(6)  NOT NULL,
    log_modified  DATETIME(6)  NOT NULL,
    UNIQUE KEY ux_undo_log (xid, branch_id)
);

-- Phase 0 canary (health/gate check target).
CREATE TABLE IF NOT EXISTS phase0_canary (
    id INT PRIMARY KEY,
    note VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO phase0_canary (id, note) VALUES (1, 'oceanbase bootstrapped')
ON DUPLICATE KEY UPDATE note = VALUES(note);
