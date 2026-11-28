-- =============================================================================
-- Phase 9, Part 9.2: Authentication lockout, login format constraint,
-- refresh_tokens table, and idempotent SYSTEM department seed.
-- =============================================================================

-- 1. Add account lockout, forced-password-change, and password-changed timestamp
--    columns to users (D4), plus the login format CHECK constraint (P1).
ALTER TABLE users
    ADD COLUMN failed_login_count   INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until         TIMESTAMPTZ NULL,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at  TIMESTAMPTZ NULL,
    ADD CONSTRAINT chk_users_failed_login_count CHECK (failed_login_count >= 0),
    ADD CONSTRAINT chk_users_login_format CHECK (login ~ '^[A-Za-z0-9._-]{3,64}$');

-- 2. Create refresh_tokens table for server-side rotation and reuse detection (D3, P7, P8).
--    Only the SHA-256 hex digest (token_hash) is stored; raw tokens are never persisted.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    token_hash  CHAR(64) NOT NULL,
    family_id   UUID NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NULL,
    replaced_by UUID NULL,
    CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_expires_after_issued CHECK (expires_at > issued_at)
);

-- 3. Indexes for refresh_tokens lookups.
--    token_hash is already backed by the unique B-tree index uq_refresh_tokens_token_hash.
--    Index user_id for revoking all sessions of a user (logout-all, password change, suspension).
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

--    Index family_id for revoking an entire token family upon reuse detection.
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

--    Partial index on unrevoked tokens per user to speed up active session queries.
CREATE INDEX idx_refresh_tokens_active_user ON refresh_tokens (user_id, expires_at) WHERE revoked_at IS NULL;

-- 4. Seed the default SYSTEM department idempotently on its natural unique key (code)
--    so the first Admin bootstrap runner can create the initial ADMIN user (P3).
INSERT INTO departments (id, code, name, active)
VALUES ('11111111-1111-4111-8111-111111111001', 'SYSTEM', 'SYSTEM', TRUE)
ON CONFLICT (code) DO NOTHING;
