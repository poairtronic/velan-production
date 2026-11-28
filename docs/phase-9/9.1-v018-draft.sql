-- =============================================================================
-- DRAFT ONLY — DO NOT PLACE IN backend/src/main/resources/db/migration/
-- Phase 9, Part 9.1: Proposed V018 DDL for Authentication, Lockout, and Refresh Tokens
-- Target migration in Part 9.2 (after Po approval): V018__add_auth_lockout_and_refresh_tokens.sql
-- =============================================================================

-- 1. Add account lockout and forced-password-change columns to users (D4)
--    - failed_login_count: consecutive failed login counter; constrained >= 0
--    - locked_until: UTC timestamp until which login is blocked (NULL = not locked)
--    - must_change_password: forces password change on first login or after admin reset
--    - password_changed_at: UTC timestamp of last password change (NULL if never changed)
ALTER TABLE users
    ADD COLUMN failed_login_count   INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until         TIMESTAMPTZ NULL,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at  TIMESTAMPTZ NULL,
    ADD CONSTRAINT chk_users_failed_login_count CHECK (failed_login_count >= 0);

-- 2. Create refresh_tokens table for server-side token rotation and reuse detection (D3)
--    - id: primary key (UUIDv4 / UUIDv7)
--    - user_id: owning user in users(id); ON DELETE RESTRICT matches V002 IAM FKs
--    - token_hash: SHA-256 hex digest (64 chars) of the refresh token; never store raw tokens
--    - family_id: rotation family identifier; all tokens in a login chain share one family_id
--    - issued_at: UTC timestamp when this token was issued
--    - expires_at: UTC timestamp when this token expires (must be > issued_at)
--    - revoked_at: UTC timestamp when revoked (logout, rotation, reuse detection, or suspension)
--    - replaced_by: points to the successor refresh_tokens(id) created on rotation (NULL if latest/unrotated)
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

-- 3. Indexes for refresh_tokens lookups (token_hash is already indexed by uq_refresh_tokens_token_hash)
--    - Explicit index on token_hash is omitted because uq_refresh_tokens_token_hash creates a unique B-tree index.
--    - Index on user_id for revoking all sessions of a user (logout-all, password change, suspension)
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

--    - Index on family_id for revoking an entire token family upon reuse detection
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

--    - Partial index on active (unrevoked) tokens by user_id to speed up active session queries
CREATE INDEX idx_refresh_tokens_active_user ON refresh_tokens (user_id, expires_at) WHERE revoked_at IS NULL;
