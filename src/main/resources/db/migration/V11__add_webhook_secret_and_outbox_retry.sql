-- Add webhook_secret to merchants for HMAC-SHA256 signing of webhook payloads.
ALTER TABLE merchants ADD COLUMN webhook_secret VARCHAR(255);

-- Add retry tracking columns to outbox_events for production-grade outbox processing.
ALTER TABLE outbox_events ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN max_retries INT NOT NULL DEFAULT 10;
ALTER TABLE outbox_events ADD COLUMN last_error VARCHAR(2000);
