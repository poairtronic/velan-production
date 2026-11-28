-- Phase 7.6: Notification table (user_notifications — kept data, never purged with SC)

CREATE TABLE user_notifications (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    type        VARCHAR(64) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   VARCHAR(128) NOT NULL,
    sc_id       UUID NULL,
    read_at     TIMESTAMPTZ NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_notifications_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_notifications_user_read ON user_notifications (user_id, read_at);
CREATE INDEX idx_user_notifications_sc_id ON user_notifications (sc_id) WHERE sc_id IS NOT NULL;
