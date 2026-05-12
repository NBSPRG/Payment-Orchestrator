CREATE TABLE providers (
    name                    VARCHAR(100)    NOT NULL,
    display_name            VARCHAR(255)    NOT NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    max_retry_count         INT             NOT NULL DEFAULT 3,
    timeout_ms              INT             NOT NULL DEFAULT 5000,
    config                  JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_providers PRIMARY KEY (name)
);

INSERT INTO providers (name, display_name, max_retry_count, timeout_ms) VALUES
    ('PROVIDER_A', 'Provider A (Card)', 3, 5000),
    ('PROVIDER_B', 'Provider B (UPI)', 3, 5000);
