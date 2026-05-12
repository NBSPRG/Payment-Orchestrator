CREATE TABLE outbox_events (
    id                      BIGSERIAL       NOT NULL,
    aggregate_id            VARCHAR(26)     NOT NULL,
    aggregate_type          VARCHAR(100)    NOT NULL,
    event_type              VARCHAR(200)    NOT NULL,
    kafka_topic             VARCHAR(500)    NOT NULL,
    kafka_key               VARCHAR(255)    NOT NULL,
    payload                 JSONB           NOT NULL,
    published               BOOLEAN         NOT NULL DEFAULT FALSE,
    published_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published = FALSE;
