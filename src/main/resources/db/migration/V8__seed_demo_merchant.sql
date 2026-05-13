-- Demo merchant and API key for local development and testing.
-- API key raw value: test_api_key_123
-- API key hash: SHA-256("test_api_key_123")

INSERT INTO merchants (id, name, status, environment)
VALUES ('merchant_demo', 'Demo Merchant', 'ACTIVE', 'TEST')
ON CONFLICT (id) DO NOTHING;

INSERT INTO merchant_api_keys (id, merchant_id, key_prefix, key_hash, environment, status, rate_limit_tier)
VALUES (
    'key_demo_000000000000001',
    'merchant_demo',
    'test_api',
    '3738a9db044b02c2849ff7eb06aa66659462b920e2368335229f25b144343fcf',
    'TEST',
    'ACTIVE',
    'STANDARD'
)
ON CONFLICT (key_prefix) DO NOTHING;
