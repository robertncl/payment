-- Seata AT undo log (ADR-0004): before/after row images for the gateway branch, written in
-- the same local transaction as the business change and deleted on global commit, or used
-- to compensate on global rollback. Schema per Seata 2.x client script (MySQL dialect).
CREATE TABLE undo_log (
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT          NOT NULL,
    log_created   DATETIME(6)  NOT NULL,
    log_modified  DATETIME(6)  NOT NULL
);

CREATE UNIQUE INDEX ux_undo_log ON undo_log (xid, branch_id);
