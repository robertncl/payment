-- Gateway-owned state: payments, transition timeline, idempotency keys, outbox.
CREATE TABLE payments (
    id              VARCHAR(40)   NOT NULL,
    payer_id        VARCHAR(64)   NOT NULL,
    merchant_id     VARCHAR(64)   NOT NULL,
    source_currency CHAR(3)       NOT NULL,
    target_currency CHAR(3)       NOT NULL,
    amount          DECIMAL(20,4) NOT NULL,
    fee_amount      DECIMAL(20,4) NOT NULL,
    target_amount   DECIMAL(20,4) NULL,
    fx_quote_id     VARCHAR(64)   NULL,
    fx_rate         DECIMAL(20,10) NULL,
    status          VARCHAR(20)   NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_payments_merchant ON payments (merchant_id, created_at);

CREATE TABLE payment_events (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    payment_id  VARCHAR(40) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status   VARCHAR(20) NOT NULL,
    detail      VARCHAR(255) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_payment_events_payment ON payment_events (payment_id, id);

CREATE TABLE idempotency_keys (
    idem_key      VARCHAR(100) NOT NULL,
    endpoint      VARCHAR(80)  NOT NULL,
    request_hash  CHAR(64)     NOT NULL,
    http_status   INT          NULL,
    response_body TEXT         NULL,
    payment_id    VARCHAR(40)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (idem_key)
);

CREATE TABLE outbox_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(40)  NOT NULL,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6)  NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at, id);
