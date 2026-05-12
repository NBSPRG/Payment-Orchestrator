ALTER TABLE outbox_events ADD COLUMN claimed_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN claimed_by VARCHAR(255);

CREATE INDEX idx_outbox_claimable
    ON outbox_events (id)
    WHERE published = FALSE;
