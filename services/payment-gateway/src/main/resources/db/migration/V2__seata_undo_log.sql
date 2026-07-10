-- Seata AT undo log (ADR-0004): before/after row images for the gateway branch, written in
-- the same local transaction as the business change and deleted on global commit, or used
-- to compensate on global rollback. Schema per Seata 2.x client script (MySQL dialect).
-- IF NOT EXISTS: on OceanBase the table is pre-created by compose init-sql, because Seata's
-- DataSourceProxy refuses to start without it — before Flyway gets a chance to run.
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT          NOT NULL,
    log_created   DATETIME(6)  NOT NULL,
    log_modified  DATETIME(6)  NOT NULL,
    UNIQUE KEY ux_undo_log (xid, branch_id)
);
