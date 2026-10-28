-- ============================================================
-- V30: rental agreement document per tenancy
--
-- The owner (or a manager) attaches the signed agreement to a tenancy: an
-- upload with purpose AGREEMENT (PDF or photo). The tenant of that tenancy
-- can open it from My Agreement. Replacing or removing it deletes the old
-- file. See docs/devlog/DEVLOG_AGREEMENT.md.
-- ============================================================

ALTER TABLE tenant_property_memberships
    ADD COLUMN agreement_file_id     UUID REFERENCES stored_files (id),
    ADD COLUMN agreement_uploaded_at TIMESTAMPTZ,
    ADD COLUMN agreement_uploaded_by UUID REFERENCES users (id);

CREATE INDEX idx_memberships_agreement_file ON tenant_property_memberships (agreement_file_id)
    WHERE agreement_file_id IS NOT NULL;
