-- ============================================================
-- V4: Property policy settings
--
-- Spec §6.4 defines 17 property-level policy fields the billing
-- engine reads at calculation time (§9.2 step 1). V3 only shipped
-- 3 of them (default_split_rule, billing_day, grace_period_days).
-- This migration adds the remaining ones, found missing during
-- the spec-vs-code audit on 2026-07-30.
--
-- Also fixes: billing_day's real range is 1-32 (BS months run
-- 29-32 days, never 30 — see project's own BS-calendar rule),
-- and grace_period_days' spec default is 7, not 5.
-- ============================================================

ALTER TABLE properties
    ALTER COLUMN grace_period_days SET DEFAULT 7;

ALTER TABLE properties
    ADD COLUMN payment_model_default            VARCHAR(50)   NOT NULL DEFAULT 'PAY_IN_ADVANCE',   -- PAY_IN_ADVANCE | PAY_AFTER_STAY
    ADD COLUMN vacancy_notice_period_days       SMALLINT      NOT NULL DEFAULT 30,
    ADD COLUMN move_out_day_chargeable          BOOLEAN       NOT NULL DEFAULT TRUE,
    ADD COLUMN common_units_charged_to_tenants  BOOLEAN       NOT NULL DEFAULT TRUE,

    ADD COLUMN penalty_type                     VARCHAR(50)   NOT NULL DEFAULT 'NONE',             -- NONE | FIXED | PERCENTAGE | PER_DAY
    ADD COLUMN penalty_frequency                VARCHAR(50)   NOT NULL DEFAULT 'PER_MONTH',         -- ONE_TIME | PER_MONTH | PER_DAY
    ADD COLUMN penalty_grace_days               SMALLINT      NOT NULL DEFAULT 5,
    ADD COLUMN penalty_cap_amount                NUMERIC(10,2),                                     -- NULL = no cap (spec default)

    ADD COLUMN late_departure_charge_type        VARCHAR(50)   NOT NULL DEFAULT 'NONE',             -- NONE | FIXED_PER_DAY | PERCENT_OF_DAILY_RENT | FLAT_FINE
    ADD COLUMN late_departure_charge_amount      NUMERIC(10,2),                                     -- fixed amount or percentage value, depending on type; NULL when type = NONE

    ADD COLUMN rounding_method                   VARCHAR(50)   NOT NULL DEFAULT 'WHOLE_NUMBER',     -- STANDARD | CEILING | FLOOR | WHOLE_NUMBER | EXACT
    ADD COLUMN rounding_remainder_to             VARCHAR(50)   NOT NULL DEFAULT 'HIGHEST_SHARE',     -- HIGHEST_SHARE | FIRST_TENANT | LAST_TENANT

    ADD COLUMN overage_threshold_percent         NUMERIC(5,2)  NOT NULL DEFAULT 3.00,
    ADD COLUMN overage_action                    VARCHAR(50)   NOT NULL DEFAULT 'NOTIFY_AND_CONFIRM', -- NOTIFY_ONLY | NOTIFY_AND_CONFIRM | BLOCK

    ADD COLUMN tds_enabled                        BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN tds_rate_percent                   NUMERIC(5,2),                                     -- NULL while tds_enabled = false

    ADD COLUMN dispute_window_days                SMALLINT      NOT NULL DEFAULT 7;

COMMENT ON COLUMN properties.billing_day IS 'Day 1-32 of the BS month billing runs generate on (validated in CreatePropertyRequest/UpdatePropertyRequest — BS months run 29-32 days, never assume 30)';
