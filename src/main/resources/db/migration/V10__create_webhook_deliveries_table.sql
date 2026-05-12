-- Webhook delivery tracking with retry support.

CREATE TABLE webhook_deliveries (
    id              BIGSERIAL       NOT NULL,
    payment_id      VARCHAR(26)     NOT NULL,
    merchant_id     VARCHAR(255)    NOT NULL,
    webhook_url     VARCHAR(2048)   NOT NULL,
    event_type      VARCHAR(200)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',  -- PENDING | DELIVERED | FAILED
    http_status     INT,
    attempt_count   INT             NOT NULL DEFAULT 0,
    max_attempts    INT             NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMPTZ,
    last_error      VARCHAR(2000),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_webhook_deliveries PRIMARY KEY (id)
);

CREATE INDEX idx_webhook_pending ON webhook_deliveries (next_retry_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhook_payment ON webhook_deliveries (payment_id);
