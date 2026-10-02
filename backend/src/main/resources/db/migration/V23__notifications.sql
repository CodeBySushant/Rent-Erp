-- ============================================================
-- V23: notifications
--
-- notifications : one row per recipient. `type` drives the app's (translated)
--                 title; `body` is a short detail (amount, month, name);
--                 entity_type / entity_id let the app open the right screen.
--                 Written in the same transaction as the change it reports,
--                 so a rolled-back change never notifies.
-- device_tokens : push tokens per device, for Firebase later (not sent yet).
-- ============================================================

CREATE TABLE notifications (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL REFERENCES users (id),
    type          VARCHAR(40)   NOT NULL,
    title         VARCHAR(150)  NOT NULL,
    body          VARCHAR(500),
    property_id   UUID          REFERENCES properties (id),
    entity_type   VARCHAR(40),
    entity_id     UUID,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE device_tokens (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL REFERENCES users (id),
    token         VARCHAR(500)  NOT NULL UNIQUE,
    platform      VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_device_tokens_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);
