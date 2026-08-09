-- ============================================================
-- V12: Billing engine (Phase 5)
--
-- Seven tables: tariff_versions, billing_runs, billing_run_segments,
-- billing_run_progress, tenant_bills, tenant_bill_adjustments,
-- bill_corrections.
--
-- PASS 1 (this migration lands all 7 tables; the engine wired in this
-- pass populates the non-metered subset):
--   - tariff_versions  — full CRUD (NEA slab reference data), consumed by
--                        the blended-rate calc that lands in pass 2.
--   - billing_runs     — create/confirm/cancel for FIXED_PER_TENANT,
--                        INCLUDED_IN_RENT and MAIN_METER_ONLY electricity,
--                        INCLUDED/FIXED water, charge_templates, rent,
--                        opening balance (T7), advance rent (T8), penalties,
--                        rounding+remainder (B9), grace-from-generation (B4).
--   - tenant_bills     — immutable per-tenant output with a JSONB line-item
--                        snapshot.
--   - tenant_bill_adjustments — carry-forward charges/credits; the engine
--                        applies any PENDING rows onto the next bill. Manual
--                        one-time add (P9) exposed this pass.
--   - billing_run_segments / billing_run_progress / bill_corrections —
--                        tables created now; their write paths (mid-period
--                        segment engine T9/M9, async workers B15, bill
--                        correction POST B8) land in pass 2.
--
-- Money: NUMERIC(10,2) for per-tenant amounts, NUMERIC(12,2) for run
-- totals (a large property's total can exceed a single tenant's ceiling),
-- NUMERIC(10,4) for the NEA blended rate. BS dates are VARCHAR(20).
-- JSONB is display/audit snapshot only — never filtered on.
-- ============================================================


-- ─── tariff_versions ────────────────────────────────────────
--
-- National NEA slab reference data (spec §6.3 / §9.2). NOT property-scoped —
-- one schedule applies to every property; the property only chooses FLAT_RATE
-- vs BLENDED_RATE (properties.nea_tariff_mode). Effective-dated so historical
-- bills reprice against the schedule that was in force (B5 — no retroactive
-- repricing). Slabs are held as JSONB (display/calc snapshot: an ordered array
-- of {"uptoUnits": n|null, "ratePerUnit": r}); the blended-rate engine (pass 2)
-- reads them. Soft-deleted, never hard-deleted.

CREATE TABLE tariff_versions (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255)   NOT NULL,
    effective_from_bs   VARCHAR(20)    NOT NULL,
    effective_to_bs     VARCHAR(20),                          -- NULL = still in force
    slabs               JSONB          NOT NULL DEFAULT '[]', -- [{uptoUnits, ratePerUnit}, ...]
    demand_charge       NUMERIC(10,2)  NOT NULL DEFAULT 0,
    service_charge      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    minimum_charge      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    vat_percent         NUMERIC(5,2)   NOT NULL DEFAULT 13.00,
    notes               TEXT,
    is_active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tariff_charges_nonneg CHECK (
        demand_charge >= 0 AND service_charge >= 0 AND minimum_charge >= 0 AND vat_percent >= 0
    )
);
-- Only one active schedule may start on a given BS date (our own rule — keeps
-- "the tariff effective as of date X" unambiguous). Partial: cancelled/superseded
-- versions (is_active=false) don't collide.
CREATE UNIQUE INDEX uq_tariff_effective_from_active
    ON tariff_versions (effective_from_bs) WHERE is_active;
CREATE INDEX idx_tariff_effective_from ON tariff_versions (effective_from_bs);


-- ─── billing_runs ───────────────────────────────────────────
--
-- One run per property per billing period (a BS year-month, e.g. "2082-04").
-- Lifecycle DRAFT → CONFIRMED (immutable) | CANCELLED. Confirming runs a hard
-- reconciliation assertion (B9) and enforces oldest-period-first (B13).
--
-- Concurrency (B10), 3 of the 4 layers live in the backend (the 4th is the UI
-- disabling the button):
--   - idempotency_key       → unique per (property, key): a retried request with
--                             the same key returns the existing run, never a dup.
--   - uq per (property, period) among non-cancelled → two devices generating the
--                             same month collide at the DB, not silently double-bill.
--   - SELECT ... FOR UPDATE  → taken on the property row in the service (row lock).

CREATE TABLE billing_runs (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id         UUID           NOT NULL REFERENCES properties (id),

    billing_month_bs    VARCHAR(7)     NOT NULL,              -- "YYYY-MM" BS period label
    period_start_bs     VARCHAR(20)    NOT NULL,              -- first day of covered window
    period_end_bs       VARCHAR(20)    NOT NULL,              -- last day of covered window (inclusive)

    status              VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    tariff_mode         VARCHAR(20)    NOT NULL,              -- snapshot of property.nea_tariff_mode at run time

    -- NEA blended-rate reconciliation (populated by pass 2's blended engine; NULL
    -- for FLAT_RATE runs and for pass-1 runs).
    tariff_version_id   UUID           REFERENCES tariff_versions (id),
    nea_total_units     NUMERIC(12,2),
    nea_energy_cost     NUMERIC(12,2),
    nea_demand_charge   NUMERIC(12,2),
    nea_vat_amount      NUMERIC(12,2),
    nea_total_bill      NUMERIC(12,2),
    nea_blended_rate    NUMERIC(10,4),

    tenant_count        INTEGER        NOT NULL DEFAULT 0,
    total_billed        NUMERIC(12,2)  NOT NULL DEFAULT 0,

    generated_at_bs     VARCHAR(20)    NOT NULL,              -- B4: grace period counts from here, not billing day
    idempotency_key     VARCHAR(100),                          -- B10; NULL allowed for internally-triggered runs

    is_async            BOOLEAN        NOT NULL DEFAULT FALSE, -- B15 (pass 2)

    notes               TEXT,
    confirmed_at        TIMESTAMPTZ,
    confirmed_by        UUID,
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        UUID,
    cancellation_reason TEXT,

    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_billing_run_status  CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
    CONSTRAINT chk_billing_run_mode    CHECK (tariff_mode IN ('FLAT_RATE','BLENDED_RATE')),
    CONSTRAINT chk_billing_run_totals  CHECK (total_billed >= 0 AND tenant_count >= 0)
);
-- One live (non-cancelled) run per property per period. A cancelled run leaves the
-- slot free to regenerate (B8). This is concurrency layer 3 (unique DB index).
CREATE UNIQUE INDEX uq_billing_run_property_period_live
    ON billing_runs (property_id, billing_month_bs) WHERE status <> 'CANCELLED';
-- Idempotency: a client-supplied key is unique within a property.
CREATE UNIQUE INDEX uq_billing_run_idempotency
    ON billing_runs (property_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_billing_runs_property ON billing_runs (property_id);
CREATE INDEX idx_billing_runs_status   ON billing_runs (property_id, status);


-- ─── billing_run_segments ───────────────────────────────────
--
-- One row per continuous sub-period a membership was billed under within a run.
-- A membership with no mid-period event has a single FULL_PERIOD segment. Mid-
-- period join/exit/meter events (T9, M9) split it — that splitting engine lands
-- in pass 2; pass 1 writes the single FULL_PERIOD segment for audit parity.
-- denominator_count is the number of billable tenants sharing costs in that
-- segment (vacant rooms never counted — spec split rule).

CREATE TABLE billing_run_segments (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_run_id      UUID           NOT NULL REFERENCES billing_runs (id),
    membership_id       UUID           NOT NULL REFERENCES tenant_property_memberships (id),

    segment_start_bs    VARCHAR(20)    NOT NULL,
    segment_end_bs      VARCHAR(20)    NOT NULL,
    days                INTEGER        NOT NULL,
    denominator_count   INTEGER        NOT NULL,
    reason              VARCHAR(30)    NOT NULL DEFAULT 'FULL_PERIOD',
    details             JSONB          NOT NULL DEFAULT '{}',

    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_segment_reason CHECK (reason IN
        ('FULL_PERIOD','MID_MONTH_JOIN','MID_MONTH_EXIT','METER_EVENT','RENT_CHANGE')),
    CONSTRAINT chk_segment_days   CHECK (days > 0 AND denominator_count >= 0)
);
CREATE INDEX idx_segments_run        ON billing_run_segments (billing_run_id);
CREATE INDEX idx_segments_membership ON billing_run_segments (membership_id);


-- ─── billing_run_progress ───────────────────────────────────
--
-- Async progress for large properties (B15 — 1 job per tenant, 5 parallel
-- workers, polled every 2s). One row per async run. Pass 1 runs synchronously
-- and does not write this; the async worker path lands in pass 2.

CREATE TABLE billing_run_progress (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_run_id      UUID           NOT NULL UNIQUE REFERENCES billing_runs (id),
    total_tenants       INTEGER        NOT NULL DEFAULT 0,
    processed_tenants   INTEGER        NOT NULL DEFAULT 0,
    failed_tenants      INTEGER        NOT NULL DEFAULT 0,
    status              VARCHAR(20)    NOT NULL DEFAULT 'RUNNING',
    last_error          TEXT,
    started_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_progress_status CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
    CONSTRAINT chk_progress_counts CHECK (
        total_tenants >= 0 AND processed_tenants >= 0 AND failed_tenants >= 0
    )
);


-- ─── tenant_bills ───────────────────────────────────────────
--
-- Immutable per-tenant bill. Produced only by a billing run — no direct POST/PUT.
-- A wrong bill is never edited: unpaid → cancel + regenerate (superseded_by_bill_id
-- links the chain); paid/partial → a tenant_bill_adjustments row carries onto the
-- next bill (B8). Every computed component is stored as its own typed column AND
-- captured in the line_items JSONB snapshot for display.
--
-- Proration (B14/T8): days_occupied / days_in_period, both from BsCalendar — the
-- denominator is the actual BS-month length, never 30.
--
-- payment_status / amount_paid / balance_due default to UNPAID/0/total_due here;
-- the Payment phase (Phase 6) owns their transitions.

CREATE TABLE tenant_bills (
    id                      UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_run_id          UUID           NOT NULL REFERENCES billing_runs (id),
    property_id             UUID           NOT NULL REFERENCES properties (id),
    membership_id           UUID           NOT NULL REFERENCES tenant_property_memberships (id),

    billing_month_bs        VARCHAR(7)     NOT NULL,
    period_start_bs         VARCHAR(20)    NOT NULL,
    period_end_bs           VARCHAR(20)    NOT NULL,
    days_occupied           INTEGER        NOT NULL,
    days_in_period          INTEGER        NOT NULL,
    is_prorated             BOOLEAN        NOT NULL DEFAULT FALSE,

    -- Line-item component amounts (all NUMERIC(10,2), per-tenant scale).
    rent_amount             NUMERIC(10,2)  NOT NULL DEFAULT 0,
    electricity_amount      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    water_amount            NUMERIC(10,2)  NOT NULL DEFAULT 0,
    charges_amount          NUMERIC(10,2)  NOT NULL DEFAULT 0,   -- sum of charge_templates
    penalty_amount          NUMERIC(10,2)  NOT NULL DEFAULT 0,
    adjustments_amount      NUMERIC(10,2)  NOT NULL DEFAULT 0,   -- net of applied carry-forward rows (+charge/-credit)
    advance_applied_amount  NUMERIC(10,2)  NOT NULL DEFAULT 0,   -- advance rent consumed this bill (T8), reduces due
    previous_balance        NUMERIC(10,2)  NOT NULL DEFAULT 0,   -- opening balance (T7) + carried unpaid

    subtotal                NUMERIC(10,2)  NOT NULL DEFAULT 0,   -- before TDS + rounding
    tds_amount              NUMERIC(10,2)  NOT NULL DEFAULT 0,
    rounding_adjustment     NUMERIC(10,2)  NOT NULL DEFAULT 0,   -- B9 remainder allocated to this bill (+/-)
    total_due               NUMERIC(10,2)  NOT NULL DEFAULT 0,

    amount_paid             NUMERIC(10,2)  NOT NULL DEFAULT 0,
    balance_due             NUMERIC(10,2)  NOT NULL DEFAULT 0,
    payment_status          VARCHAR(20)    NOT NULL DEFAULT 'UNPAID',

    line_items              JSONB          NOT NULL DEFAULT '[]',

    status                  VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',  -- DRAFT while run is DRAFT; ISSUED on confirm; CANCELLED on cancel/regenerate
    generated_at_bs         VARCHAR(20)    NOT NULL,
    grace_period_days       SMALLINT       NOT NULL,                  -- snapshot of property.grace_period_days (B4)
    due_date_bs             VARCHAR(20)    NOT NULL,                  -- generated_at_bs + grace, from BsCalendar
    notes                   TEXT,

    -- B8 correction chain.
    supersedes_bill_id      UUID           REFERENCES tenant_bills (id),  -- this bill replaces that cancelled one
    superseded_by_bill_id   UUID           REFERENCES tenant_bills (id),  -- this cancelled bill was replaced by that one

    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_bill_status         CHECK (status IN ('DRAFT','ISSUED','CANCELLED')),
    CONSTRAINT chk_bill_payment_status CHECK (payment_status IN ('UNPAID','PARTIAL','PAID')),
    CONSTRAINT chk_bill_days           CHECK (days_occupied > 0 AND days_in_period > 0 AND days_occupied <= days_in_period)
);
-- One live bill per membership per run (a regenerated bill lives in a different,
-- later run; a cancelled bill in the same run frees the slot).
CREATE UNIQUE INDEX uq_tenant_bill_run_membership_live
    ON tenant_bills (billing_run_id, membership_id) WHERE status <> 'CANCELLED';
CREATE INDEX idx_tenant_bills_run        ON tenant_bills (billing_run_id);
CREATE INDEX idx_tenant_bills_membership ON tenant_bills (membership_id, billing_month_bs);
CREATE INDEX idx_tenant_bills_property   ON tenant_bills (property_id);


-- ─── tenant_bill_adjustments ────────────────────────────────
--
-- Carry-forward charges and credits that attach to a membership's NEXT bill:
--   - a paid/partial bill's correction becomes an adjustment (B8 paid path, pass 2)
--   - a genuine overpayment becomes a CREDIT (P3, Payment phase)
--   - a one-time charge/credit added mid-cycle follows the tenant, incl. to the
--     vacancy bill (P9) — manual add exposed this pass
-- The engine consumes PENDING rows when it generates a bill, stamps applied_bill_id
-- and flips status to APPLIED. Immutable once applied; VOID is a soft cancel.

CREATE TABLE tenant_bill_adjustments (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id       UUID           NOT NULL REFERENCES tenant_property_memberships (id),
    property_id         UUID           NOT NULL REFERENCES properties (id),

    adjustment_type     VARCHAR(20)    NOT NULL,              -- CHARGE | CREDIT
    source              VARCHAR(30)    NOT NULL,              -- ONE_TIME | BILL_CORRECTION | OVERPAYMENT | MANUAL
    amount              NUMERIC(10,2)  NOT NULL,              -- always positive; direction is adjustment_type
    reason              TEXT,

    origin_bill_id      UUID           REFERENCES tenant_bills (id),   -- the bill that spawned it (corrections), NULL for one-time
    applied_bill_id     UUID           REFERENCES tenant_bills (id),   -- set when consumed
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',     -- PENDING | APPLIED | VOID
    created_by          UUID,

    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_adjustment_type   CHECK (adjustment_type IN ('CHARGE','CREDIT')),
    CONSTRAINT chk_adjustment_source CHECK (source IN ('ONE_TIME','BILL_CORRECTION','OVERPAYMENT','MANUAL')),
    CONSTRAINT chk_adjustment_status CHECK (status IN ('PENDING','APPLIED','VOID')),
    CONSTRAINT chk_adjustment_amount CHECK (amount > 0)
);
CREATE INDEX idx_adjustments_membership_pending
    ON tenant_bill_adjustments (membership_id) WHERE status = 'PENDING';
CREATE INDEX idx_adjustments_membership ON tenant_bill_adjustments (membership_id);


-- ─── bill_corrections ───────────────────────────────────────
--
-- Audit record of a bill correction (B8). CANCEL_REGENERATE links the cancelled
-- original to its replacement; NEXT_BILL_ADJUSTMENT links the paid/partial
-- original to the tenant_bill_adjustments row that carries the delta forward.
-- The correction POST endpoint lands in pass 2; the table is created now so the
-- FK targets exist and the schema is stable.

CREATE TABLE bill_corrections (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    original_bill_id    UUID           NOT NULL REFERENCES tenant_bills (id),
    membership_id       UUID           NOT NULL REFERENCES tenant_property_memberships (id),
    correction_type     VARCHAR(30)    NOT NULL,              -- CANCEL_REGENERATE | NEXT_BILL_ADJUSTMENT
    regenerated_bill_id UUID           REFERENCES tenant_bills (id),
    adjustment_id       UUID           REFERENCES tenant_bill_adjustments (id),
    reason              TEXT           NOT NULL,
    corrected_by        UUID,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_correction_type CHECK (correction_type IN ('CANCEL_REGENERATE','NEXT_BILL_ADJUSTMENT'))
);
CREATE INDEX idx_bill_corrections_original ON bill_corrections (original_bill_id);
CREATE INDEX idx_bill_corrections_membership ON bill_corrections (membership_id);
