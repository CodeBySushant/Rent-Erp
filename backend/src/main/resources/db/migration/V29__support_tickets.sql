-- ============================================================
-- V29: support tickets
--
-- Contact Support → "Send us a Message": one row per message, from a
-- signed-in user, with an optional attachment (an upload with purpose
-- SUPPORT_ATTACHMENT, owned by that user, used by one ticket only).
-- OPEN → RESOLVED by an admin, with an optional note the user sees.
-- See docs/devlog/DEVLOG_SUPPORT.md.
-- ============================================================

CREATE TABLE support_tickets (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID           NOT NULL REFERENCES users (id),
    topic               VARCHAR(20)    NOT NULL,
    message             VARCHAR(4000)  NOT NULL,
    attachment_file_id  UUID           REFERENCES stored_files (id),
    status              VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    resolution_note     VARCHAR(1000),
    resolved_by         UUID           REFERENCES users (id),
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version             BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_support_topic CHECK (topic IN ('ACCOUNT', 'PROPERTY', 'BILLING', 'TENANTS', 'OTHER')),
    CONSTRAINT chk_support_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT chk_support_message CHECK (length(btrim(message)) > 0)
);

CREATE INDEX idx_support_tickets_user ON support_tickets (user_id, created_at DESC);
CREATE INDEX idx_support_tickets_status ON support_tickets (status, created_at DESC);
CREATE UNIQUE INDEX uq_support_tickets_attachment ON support_tickets (attachment_file_id)
    WHERE attachment_file_id IS NOT NULL;

-- Attachments are ordinary uploads with their own purpose (photo or PDF).
ALTER TABLE stored_files DROP CONSTRAINT chk_stored_files_purpose;
ALTER TABLE stored_files ADD CONSTRAINT chk_stored_files_purpose CHECK (purpose IN (
    'KYC_ID_FRONT', 'KYC_ID_BACK', 'KYC_SELFIE', 'METER_PHOTO', 'PAYMENT_PROOF',
    'PAYMENT_QR', 'PROFILE_PHOTO', 'AGREEMENT', 'REQUEST_PHOTO', 'OTHER', 'SUPPORT_ATTACHMENT'));
