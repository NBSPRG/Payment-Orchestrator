CREATE TABLE payment_status_history (
    id                      BIGSERIAL       NOT NULL,
    payment_id              VARCHAR(26)     NOT NULL,
    from_status             VARCHAR(50),
    to_status               VARCHAR(50)     NOT NULL,
    reason                  VARCHAR(1000),
    triggered_by            VARCHAR(100),
    metadata                JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_payment_status_history PRIMARY KEY (id),
    CONSTRAINT fk_status_history_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_status_history_payment_id ON payment_status_history (payment_id);
