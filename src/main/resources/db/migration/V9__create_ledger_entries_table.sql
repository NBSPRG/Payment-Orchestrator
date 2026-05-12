-- Double-entry ledger for recording financial events from payment outcomes.
-- Each captured payment creates a DEBIT and CREDIT pair.
-- Idempotent: (event_id, entry_type) is unique.

CREATE TABLE ledger_entries (
    id              BIGSERIAL       NOT NULL,
    payment_id      VARCHAR(26)     NOT NULL,
    merchant_id     VARCHAR(255)    NOT NULL,
    entry_type      VARCHAR(20)     NOT NULL,           -- DEBIT | CREDIT
    amount_value    BIGINT          NOT NULL,
    amount_currency VARCHAR(3)      NOT NULL,
    event_id        VARCHAR(255)    NOT NULL,            -- Kafka eventId for idempotency
    event_type      VARCHAR(200)    NOT NULL,            -- payment.v1.captured etc.
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT uq_ledger_event_entry UNIQUE (event_id, entry_type)
);

CREATE INDEX idx_ledger_payment ON ledger_entries (payment_id);
CREATE INDEX idx_ledger_merchant ON ledger_entries (merchant_id);
