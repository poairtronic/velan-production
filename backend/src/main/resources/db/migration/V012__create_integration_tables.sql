-- Phase 7.6: Integration tables (outbox_events, rm_sync_history — kept data)

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(128) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    payload        JSONB NOT NULL,
    status         VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts       INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_retry_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at        TIMESTAMPTZ NULL,
    sc_id          UUID NULL
);

CREATE INDEX idx_outbox_events_status_retry ON outbox_events (status, next_retry_at);
CREATE INDEX idx_outbox_events_sc_id ON outbox_events (sc_id) WHERE sc_id IS NOT NULL;

CREATE TABLE rm_sync_history (
    id              UUID PRIMARY KEY,
    sync_type       VARCHAR(64) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMPTZ NULL,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('STARTED', 'SUCCESS', 'FAILED')),
    records_read    INT NOT NULL DEFAULT 0 CHECK (records_read >= 0),
    records_written INT NOT NULL DEFAULT 0 CHECK (records_written >= 0),
    error_message   TEXT NULL,
    sc_id           UUID NULL
);

CREATE INDEX idx_rm_sync_history_started_at ON rm_sync_history (started_at);
CREATE INDEX idx_rm_sync_history_sc_id ON rm_sync_history (sc_id) WHERE sc_id IS NOT NULL;
