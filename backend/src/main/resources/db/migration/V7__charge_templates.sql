-- ============================================================
-- V7: Charge templates
--
-- Setup wizard step 6 (spec §6.1/§8.1): reusable fixed/shared charge
-- definitions per property — Internet, Garbage, Maintenance, Security,
-- or landlord-defined custom charges. Rent, Electricity, Water and
-- one-time charges/credits are NOT charge_templates — they're computed
-- or ad-hoc, not reusable monthly definitions.
--
-- tenant_charge_overrides and tariff_versions (also mapped to
-- ChargeController in CONTROLLER_TABLE_MAP.md) are deliberately NOT
-- built in this pass:
--   - tenant_charge_overrides needs tenant assignment context (spec
--     §10.2) that doesn't exist until TenancyController (Phase 4).
--   - tariff_versions is national NEA reference data consumed only by
--     the Billing engine's blended-rate calculation (Phase 5) — inert
--     until then.
-- ============================================================

CREATE TABLE charge_templates (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id                 UUID            NOT NULL REFERENCES properties (id),
    name                        VARCHAR(255)    NOT NULL,
    amount                      NUMERIC(10,2)   NOT NULL,
    split_basis                 VARCHAR(50)     NOT NULL,   -- FIXED_PER_TENANT | SHARED

    -- Spec §14.2 B6: a charge amount of zero is far more often an error than an
    -- intention. This flag records that the landlord explicitly confirmed it.
    zero_amount_acknowledged    BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Spec §14.2 B7: removing a charge mid-tenancy never deletes it — the landlord
    -- chooses whether it applies prorated this cycle, from next cycle, or is voided
    -- entirely. Set only at deactivation time; NULL while the charge is active.
    -- The Billing engine (Phase 5, not yet built) will read this to decide how to
    -- treat the charge on the run that follows deactivation.
    deactivation_mode           VARCHAR(50),                -- THIS_CYCLE_PRORATED | NEXT_CYCLE | VOID | NULL

    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Not spec-mandated (no charge_templates constraint in §20.2) — our own rule,
    -- same reasoning as the floors/rooms uniqueness added in V6.
    CONSTRAINT uq_charge_templates_property_name UNIQUE (property_id, name)
);

CREATE INDEX idx_charge_templates_property ON charge_templates (property_id);
