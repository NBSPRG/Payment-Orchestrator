CREATE TABLE idempotency_keys (
    id                      BIGSERIAL       NOT NULL,
    merchant_id             VARCHAR(255)    NOT NULL,
    idempotency_key         VARCHAR(255)    NOT NULL,
    request_hash            VARCHAR(64)     NOT NULL,
    response_status         INT             NOT NULL,
    response_body           JSONB           NOT NULL,
    payment_id              VARCHAR(26),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_keys UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);
