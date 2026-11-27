-- Phase 7.2: Shared-kernel tables (files, audit_logs, shedlock, idempotency_keys)
-- Note: files.sc_id and audit_logs.sc_id have no FK to sc_headers (audit_logs survives purge; files.sc_id FK deferred to 7.4).

CREATE TABLE files (
    id              UUID PRIMARY KEY,
    sc_id           UUID NULL,
    storage_key     VARCHAR(512) NOT NULL UNIQUE,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(128) NOT NULL,
    size_bytes      BIGINT NOT NULL CHECK (size_bytes >= 0),
    checksum_sha256 CHAR(64) NOT NULL,
    uploaded_by     UUID NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_files_sc_id ON files (sc_id) WHERE sc_id IS NOT NULL;

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY,
    sc_id       UUID NULL,
    request_id  VARCHAR(128) NULL,
    actor_id    UUID NULL,
    actor_role  VARCHAR(32) NULL,
    action      VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   VARCHAR(128) NOT NULL,
    details     JSONB NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_sc_id ON audit_logs (sc_id) WHERE sc_id IS NOT NULL;

CREATE TABLE shedlock (
    name       VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at  TIMESTAMPTZ NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY,
    actor_id        UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash    CHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body   JSONB NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_actor_key UNIQUE (actor_id, idempotency_key)
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
