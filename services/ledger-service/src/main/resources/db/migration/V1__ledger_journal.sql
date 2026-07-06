-- Double-entry, append-only journal. Journal rows are never UPDATEd or DELETEd:
-- corrections are reversing entries (entry_type REFUND / future ADJUSTMENT).
CREATE TABLE accounts (
    id         VARCHAR(64)  NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    owner_ref  VARCHAR(64)  NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE journal_entries (
    id          VARCHAR(64) NOT NULL,
    payment_id  VARCHAR(64) NOT NULL,
    entry_type  VARCHAR(16) NOT NULL,
    fx_quote_id VARCHAR(64) NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

-- posting idempotency: one entry per (payment, type)
CREATE UNIQUE INDEX uk_journal_payment_type ON journal_entries (payment_id, entry_type);

CREATE TABLE journal_lines (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    entry_id   VARCHAR(64)  NOT NULL,
    account_id VARCHAR(64)  NOT NULL,
    currency   CHAR(3)      NOT NULL,
    direction  VARCHAR(6)   NOT NULL,
    amount     DECIMAL(20,4) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_lines_entry ON journal_lines (entry_id);
CREATE INDEX idx_lines_account ON journal_lines (account_id, currency);
