-- ============================================================
-- V24: owner identity (ID document + selfie)
--
-- One row per owner (user). Stored only: nobody reviews it in the app, and it
-- is not a verification decision. A developer / admin can read it through
-- GET /api/v1/admin/owner-identities or straight from this table.
--
-- Tenant identity already has its own table (tenant_kyc, with a review flow);
-- this is the owner-side counterpart.
--
-- The files themselves are normal uploads (stored_files, purposes KYC_ID_FRONT,
-- KYC_ID_BACK, KYC_SELFIE) owned by the same user.
-- ============================================================

CREATE TABLE owner_identity (
    user_id           UUID         PRIMARY KEY REFERENCES users (id),
    id_type           VARCHAR(30)  NOT NULL,
    id_number         VARCHAR(100) NOT NULL,
    id_front_file_id  UUID         NOT NULL REFERENCES stored_files (id),
    id_back_file_id   UUID         REFERENCES stored_files (id),
    selfie_file_id    UUID         NOT NULL REFERENCES stored_files (id),
    submitted_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE owner_identity IS
    'Owner ID document and selfie, stored for later use. Not reviewed or approved by the app.';
