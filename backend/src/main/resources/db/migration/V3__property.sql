-- ============================================================
-- V3: Property table
--
-- Core property record + the billing-mode configuration that
-- every downstream module (meters, billing engine) reads from.
--
-- property_ownership_transfers, property_mode_changes and
-- property_blocked_tenants are event-log tables that get added
-- alongside the features that write to them (ownership transfer,
-- mode-change workflow, tenant blocking) — not plain CRUD, so they
-- are deferred to that point, same as otp_attempts/user_sessions
-- were deferred out of V1's user-facing surface.
-- ============================================================

CREATE TABLE properties (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id                   UUID            NOT NULL REFERENCES users (id),
    name                            VARCHAR(255)    NOT NULL,
    address                         VARCHAR(500),
    city                            VARCHAR(100),

    -- Billing configuration — drives the billing engine, set once at property creation,
    -- editable via settings screen
    electricity_billing_mode        VARCHAR(50)     NOT NULL DEFAULT 'SUB_METERED',      -- SUB_METERED | MAIN_METER_ONLY | FIXED_PER_TENANT | INCLUDED_IN_RENT
    water_mode                      VARCHAR(50)     NOT NULL DEFAULT 'INCLUDED_IN_RENT', -- INCLUDED_IN_RENT | FIXED_PER_TENANT | KUKL_SPLIT | BORING_PUMP_ONLY | KUKL_AND_BORING
    default_split_rule              VARCHAR(50)     NOT NULL DEFAULT 'EQUAL',            -- EQUAL | ROOM_WEIGHTED | CUSTOM
    nea_tariff_mode                 VARCHAR(50)     NOT NULL DEFAULT 'FLAT_RATE',        -- FLAT_RATE | BLENDED_RATE
    mid_month_departure_rate_mode   VARCHAR(50)     NOT NULL DEFAULT 'PREVIOUS_MONTH',   -- PREVIOUS_MONTH | DEFERRED

    billing_day                     SMALLINT        NOT NULL DEFAULT 1,   -- 1-28, day of month billing runs generate
    grace_period_days               SMALLINT        NOT NULL DEFAULT 5,

    is_active                       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_properties_owner   ON properties (owner_user_id);
CREATE INDEX idx_properties_active  ON properties (is_active);
