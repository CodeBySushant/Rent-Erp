-- ============================================================
-- V25: pre-deployment hardening
--
-- Additive except where noted. See docs/devlog/DEVLOG_HARDENING.md for the
-- reason behind every change (issue ids in brackets).
--
--   [L4]  version column on every audited table (optimistic locking)
--   [H11] rate_limit_counters: shared, restart-proof request limits
--   [H6]  tenant_kyc: file ids for the three photos (URL columns kept)
--   [H13] floors / rooms: uniqueness only among ACTIVE rows, so a removed
--         floor number or room name can be used again
--   [H14] charge_templates.deactivated_at_bs: the date a charge stopped
--   [M4]  properties.penalty_amount: the value the penalty type uses;
--         tenant_bill_adjustments source PENALTY
--   [M12] device_tokens.session_id: the session that registered the token,
--         so logout removes it
-- ============================================================

-- ── [L4] optimistic locking ────────────────────────────────────────────────────
ALTER TABLE billing_runs                ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE charge_templates            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE device_tokens               ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE floors                      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE infrastructure_meter_scope  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE join_requests               ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE meter_reading_log           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE meter_room_coverage         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE meters                      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE move_outs                   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notifications               ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE owner_identity              ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments                    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE properties                  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE property_access             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE property_blocked_tenants    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE property_payment_details    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE rent_increments             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE room_assignments            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE rooms                       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tariff_versions             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_advance_rent         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_bill_adjustments     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_bills                ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_deposits             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_kyc                  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_opening_balances     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_profiles             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_property_memberships ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tenant_requests             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users                       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ── [H11] rate limits ─────────────────────────────────────────────────────────
-- One row per (bucket, fixed window). Incremented with an upsert, so every
-- backend instance shares the same counts and a restart does not reset them.
-- Rows older than a day are removed by the housekeeping job.
CREATE TABLE rate_limit_counters (
    bucket_key    VARCHAR(200) NOT NULL,
    window_start  TIMESTAMPTZ  NOT NULL,
    hits          INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_key, window_start)
);
CREATE INDEX idx_rate_limit_window ON rate_limit_counters (window_start);

-- ── [H6] tenant KYC photos as file ids ─────────────────────────────────────────
ALTER TABLE tenant_kyc
    ADD COLUMN id_front_file_id UUID REFERENCES stored_files (id),
    ADD COLUMN id_back_file_id  UUID REFERENCES stored_files (id),
    ADD COLUMN selfie_file_id   UUID REFERENCES stored_files (id);

-- ── [H13] uniqueness among active rows only ────────────────────────────────────
ALTER TABLE floors DROP CONSTRAINT uq_floors_property_floor_number;
CREATE UNIQUE INDEX uq_floors_property_floor_number_active
    ON floors (property_id, floor_number) WHERE is_active;

ALTER TABLE rooms DROP CONSTRAINT uq_rooms_floor_name;
CREATE UNIQUE INDEX uq_rooms_floor_name_active
    ON rooms (floor_id, name) WHERE is_active;

-- ── [H14] when a charge stopped ────────────────────────────────────────────────
ALTER TABLE charge_templates ADD COLUMN deactivated_at_bs VARCHAR(10);

-- ── [M4] penalties ─────────────────────────────────────────────────────────────
-- FIXED: rupees per occurrence; PERCENTAGE: percent of the overdue balance;
-- PER_DAY: rupees per day late. NULL = no penalty is charged.
ALTER TABLE properties ADD COLUMN penalty_amount NUMERIC(10,2);
ALTER TABLE properties ADD CONSTRAINT chk_properties_penalty_amount
    CHECK (penalty_amount IS NULL OR penalty_amount >= 0);

ALTER TABLE tenant_bill_adjustments DROP CONSTRAINT chk_adjustment_source;
ALTER TABLE tenant_bill_adjustments ADD CONSTRAINT chk_adjustment_source
    CHECK (source IN ('ONE_TIME','BILL_CORRECTION','OVERPAYMENT','MANUAL','PENALTY'));
CREATE INDEX idx_adjustments_origin_bill ON tenant_bill_adjustments (origin_bill_id)
    WHERE origin_bill_id IS NOT NULL;

-- ── [M12] device tokens follow the session that registered them ───────────────
ALTER TABLE device_tokens ADD COLUMN session_id UUID;
CREATE INDEX idx_device_tokens_session ON device_tokens (session_id);
-- (idx_device_tokens_user already exists from V23.)
