-- ============================================================
-- V14: who created a tenant profile
--
-- Tenant profiles are not tied to one property (the same person can rent from
-- several landlords), so a landlord's right to see a profile comes from a
-- membership or join request in one of their properties - or from having
-- created it. created_by records the latter, so a profile a landlord has just
-- entered is visible to them before its membership exists.
--
-- Additive: existing profiles keep NULL and stay visible through their
-- memberships / join requests.
-- ============================================================

ALTER TABLE tenant_profiles
    ADD COLUMN created_by UUID REFERENCES users (id);

CREATE INDEX idx_tenant_profiles_created_by ON tenant_profiles (created_by)
    WHERE created_by IS NOT NULL;
