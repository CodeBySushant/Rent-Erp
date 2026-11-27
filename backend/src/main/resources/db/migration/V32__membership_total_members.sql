-- Total Members (owner's Add Tenant): how many people live in this tenancy.
-- Per tenancy, not per tenant profile: one person can rent at two properties
-- with a different household at each. Existing tenancies count as 1.
ALTER TABLE tenant_property_memberships
    ADD COLUMN total_members SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE tenant_property_memberships
    ADD CONSTRAINT chk_membership_total_members CHECK (total_members BETWEEN 1 AND 50);
