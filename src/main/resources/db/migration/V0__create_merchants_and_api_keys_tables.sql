CREATE TABLE merchants (
    id                      VARCHAR(255)    NOT NULL,
    name                    VARCHAR(255)    NOT NULL,
    status                  VARCHAR(50)     NOT NULL,
    environment             VARCHAR(20)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_merchants PRIMARY KEY (id)
);

CREATE TABLE merchant_api_keys (
    id                      VARCHAR(26)     NOT NULL,
    merchant_id             VARCHAR(255)    NOT NULL,
    key_prefix              VARCHAR(32)     NOT NULL,
    key_hash                VARCHAR(255)    NOT NULL,
    environment             VARCHAR(20)     NOT NULL,
    status                  VARCHAR(50)     NOT NULL,
    allowed_payment_methods JSONB,
    rate_limit_tier         VARCHAR(50),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ,

    CONSTRAINT pk_merchant_api_keys PRIMARY KEY (id),
    CONSTRAINT fk_api_keys_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
);

CREATE UNIQUE INDEX uq_merchant_api_keys_prefix ON merchant_api_keys (key_prefix);
CREATE INDEX idx_merchant_api_keys_merchant ON merchant_api_keys (merchant_id);

INSERT INTO merchants (id, name, status, environment)
VALUES ('merchant_demo', 'Demo Merchant', 'ACTIVE', 'TEST');

-- Demo key: test_api_key_123
INSERT INTO merchant_api_keys (id, merchant_id, key_prefix, key_hash, environment, status, rate_limit_tier)
VALUES (
    'key_01HX000000000000000000',
    'merchant_demo',
    'test_api',
    '3738a9db044b02c2849ff7eb06aa66659462b920e2368335229f25b144343fcf',
    'TEST',
    'ACTIVE',
    'standard'
);
