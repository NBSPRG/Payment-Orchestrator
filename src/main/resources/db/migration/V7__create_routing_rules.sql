CREATE TABLE routing_rules (
    id                      BIGSERIAL       NOT NULL,
    payment_method_type     VARCHAR(50)     NOT NULL,
    provider_name           VARCHAR(100)    NOT NULL,
    priority                INT             NOT NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    min_amount_value        BIGINT,
    max_amount_value        BIGINT,
    currency                VARCHAR(3),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_routing_rules PRIMARY KEY (id),
    CONSTRAINT fk_routing_rules_provider FOREIGN KEY (provider_name) REFERENCES providers (name)
);

CREATE INDEX idx_routing_rules_method ON routing_rules (payment_method_type, is_active, priority);

INSERT INTO routing_rules (payment_method_type, provider_name, priority, currency) VALUES
    ('CARD', 'PROVIDER_A', 1, 'INR'),
    ('CARD', 'PROVIDER_B', 2, 'INR'),
    ('UPI', 'PROVIDER_B', 1, 'INR');
