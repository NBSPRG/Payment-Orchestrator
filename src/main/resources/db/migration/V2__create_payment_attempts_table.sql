CREATE TABLE payment_attempts (
    id                      VARCHAR(26)     NOT NULL,
    payment_id              VARCHAR(26)     NOT NULL,
    attempt_number          INT             NOT NULL,
    provider_name           VARCHAR(100)    NOT NULL,
    provider_request        JSONB,
    provider_response       JSONB,
    status                  VARCHAR(50)     NOT NULL,
    error_code              VARCHAR(100),
    error_message           VARCHAR(1000),
    is_retryable            BOOLEAN         NOT NULL DEFAULT FALSE,
    duration_ms             BIGINT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_payment_attempts PRIMARY KEY (id),
    CONSTRAINT fk_payment_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_payment_attempts_payment_id ON payment_attempts (payment_id);
