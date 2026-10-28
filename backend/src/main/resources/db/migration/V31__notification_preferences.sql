-- ============================================================
-- V31: notification preferences
--
-- One row per account, written the first time the user changes Profile →
-- Notification Setting; with no row the defaults below apply. Push choices
-- decide which in-app notifications are also pushed to the phone (the in-app
-- list keeps everything). Email choices are stored for the emails Rentlo will
-- send; none of those categories is emailed yet.
-- See docs/devlog/DEVLOG_NOTIFICATION_PREFS.md.
-- ============================================================

CREATE TABLE notification_preferences (
    user_id                  UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    push_rent_reminders      BOOLEAN      NOT NULL DEFAULT TRUE,
    push_payment_received    BOOLEAN      NOT NULL DEFAULT TRUE,
    push_payment_overdue     BOOLEAN      NOT NULL DEFAULT TRUE,
    push_new_tenant          BOOLEAN      NOT NULL DEFAULT TRUE,
    push_maintenance         BOOLEAN      NOT NULL DEFAULT FALSE,
    push_general_updates     BOOLEAN      NOT NULL DEFAULT FALSE,
    email_monthly_statements BOOLEAN      NOT NULL DEFAULT TRUE,
    email_payment_receipts   BOOLEAN      NOT NULL DEFAULT TRUE,
    email_important_alerts   BOOLEAN      NOT NULL DEFAULT TRUE,
    email_marketing          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version                  BIGINT       NOT NULL DEFAULT 0
);
