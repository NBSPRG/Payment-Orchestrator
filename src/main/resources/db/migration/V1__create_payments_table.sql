CREATE TABLE payments (
    id                      VARCHAR(26)     NOT NULL,
    merchant_id             VARCHAR(255)    NOT NULL,
    idempotency_key         VARCHAR(255)    NOT NULL,
    merchant_reference      VARCHAR(255),
    status                  VARCHAR(50)     NOT NULL,
    payment_method_type     VARCHAR(50)     NOT NULL,
    amount_value            BIGINT          NOT NULL,
    amount_currency         VARCHAR(3)      NOT NULL,
    provider_name           VARCHAR(100),
    provider_transaction_id VARCHAR(255),
    description             VARCHAR(500),
    webhook_url             VARCHAR(2048),
    return_url              VARCHAR(2048),
    metadata                JSONB,
    failure_reason          VARCHAR(1000),
    failure_error_code      VARCHAR(100),
    retry_count             INT             NOT NULL DEFAULT 0,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT uq_payments_idempotency UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_payments_merchant_id ON payments (merchant_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_merchant_reference ON payments (merchant_reference);
CREATE INDEX idx_payments_created_at ON payments (created_at);
CREATE INDEX idx_payments_metadata_gin ON payments USING GIN (metadata);
