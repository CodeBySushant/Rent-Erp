-- Add Tenant: the deposit and advance agreed with the tenant, kept with the
-- tenancy. What was actually collected stays in tenant_deposits and
-- tenant_advance_rent. Both are optional: existing tenancies keep NULL
-- (not recorded), nothing else changes.
ALTER TABLE tenant_property_memberships
    ADD COLUMN agreed_deposit_amount NUMERIC(10, 2),
    ADD COLUMN agreed_advance_amount NUMERIC(10, 2);

ALTER TABLE tenant_property_memberships
    ADD CONSTRAINT chk_membership_agreed_deposit CHECK (agreed_deposit_amount IS NULL OR agreed_deposit_amount >= 0),
    ADD CONSTRAINT chk_membership_agreed_advance CHECK (agreed_advance_amount IS NULL OR agreed_advance_amount >= 0);
