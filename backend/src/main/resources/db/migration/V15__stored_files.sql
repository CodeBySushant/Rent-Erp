-- ============================================================
-- V15: uploaded files
--
-- One row per file uploaded through POST /api/v1/files. The bytes live in the
-- configured storage (local disk in development; S3 / Supabase Storage later);
-- this table is the only place that knows where, so the API contract does not
-- change when the storage does.
--
-- storage_key  : generated server-side (year/month/uuid) - never derived from
--                the uploaded file name
-- original_name: the client's name, cleaned, kept only for display
-- property_id  : the property the file belongs to, when it belongs to one;
--                drives who else may read it (see FileService)
-- deleted_at   : soft delete; the bytes are removed, the row stays for audit
-- ============================================================

CREATE TABLE stored_files (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id   UUID         NOT NULL REFERENCES users (id),
    property_id     UUID         REFERENCES properties (id),
    purpose         VARCHAR(30)  NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    sha256          VARCHAR(64)  NOT NULL,
    storage_key     VARCHAR(255) NOT NULL UNIQUE,
    original_name   VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT chk_stored_files_purpose CHECK (purpose IN (
        'KYC_ID_FRONT', 'KYC_ID_BACK', 'KYC_SELFIE', 'METER_PHOTO', 'PAYMENT_PROOF',
        'PAYMENT_QR', 'PROFILE_PHOTO', 'AGREEMENT', 'REQUEST_PHOTO', 'OTHER')),
    CONSTRAINT chk_stored_files_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_stored_files_owner    ON stored_files (owner_user_id);
CREATE INDEX idx_stored_files_property ON stored_files (property_id) WHERE property_id IS NOT NULL;
