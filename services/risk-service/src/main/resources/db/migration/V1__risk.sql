-- Risk-owned state: denylist, per-corridor caps, and the append-only decision log that
-- doubles as the velocity counter source.
CREATE TABLE denylist (
    subject_type VARCHAR(16)  NOT NULL,
    subject_id   VARCHAR(64)  NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (subject_type, subject_id)
);

CREATE TABLE corridor_limits (
    source_currency CHAR(3)       NOT NULL,
    target_currency CHAR(3)       NOT NULL,
    max_amount      DECIMAL(20,4) NOT NULL,
    PRIMARY KEY (source_currency, target_currency)
);

CREATE TABLE risk_assessments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id      VARCHAR(40)   NOT NULL,
    payer_id        VARCHAR(64)   NOT NULL,
    merchant_id     VARCHAR(64)   NOT NULL,
    source_currency CHAR(3)       NOT NULL,
    target_currency CHAR(3)       NOT NULL,
    amount          DECIMAL(20,4) NOT NULL,
    approved        TINYINT       NOT NULL,
    reason_code     VARCHAR(40)   NOT NULL,
    detail          VARCHAR(255)  NULL,
    assessed_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
);

-- decision idempotency: one assessment per payment
CREATE UNIQUE INDEX uq_risk_assessments_payment ON risk_assessments (payment_id);
CREATE INDEX idx_risk_assessments_payer ON risk_assessments (payer_id, assessed_at);

-- Corridor caps for every supported pair (MYR/SGD/USD/EUR/CNY, cross-border only).
-- 10k in source currency is the lab-wide single-payment cap.
INSERT INTO corridor_limits (source_currency, target_currency, max_amount) VALUES
    ('MYR','SGD',10000.0000),('MYR','USD',10000.0000),('MYR','EUR',10000.0000),('MYR','CNY',10000.0000),
    ('SGD','MYR',10000.0000),('SGD','USD',10000.0000),('SGD','EUR',10000.0000),('SGD','CNY',10000.0000),
    ('USD','MYR',10000.0000),('USD','SGD',10000.0000),('USD','EUR',10000.0000),('USD','CNY',10000.0000),
    ('EUR','MYR',10000.0000),('EUR','SGD',10000.0000),('EUR','USD',10000.0000),('EUR','CNY',10000.0000),
    ('CNY','MYR',10000.0000),('CNY','SGD',10000.0000),('CNY','USD',10000.0000),('CNY','EUR',10000.0000);

-- Demo/e2e fixtures: deterministic decline subjects (synthetic data only, spec hard rule).
INSERT INTO denylist (subject_type, subject_id, reason, created_at) VALUES
    ('PAYER', 'payer-denylisted', 'lab fixture: sanctions screening hit', CURRENT_TIMESTAMP(6)),
    ('MERCHANT', 'merchant-denylisted', 'lab fixture: fraudulent merchant', CURRENT_TIMESTAMP(6));
