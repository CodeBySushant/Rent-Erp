-- ============================================================
-- V11: Tenant finance — deposits, advance rent, opening balances,
--       rent increments. Also piggybacks a monthly_rent column onto
--       room_assignments (rent lives per-tenant per-room; see design
--       decision in DEVLOG_TENANTFINANCE.md).
--
-- Phase 4 completion. tenant_deposits/advance_rent/opening_balances are
-- read-and-hold at this stage — refund/apply/consume workflows land in
-- Billing (Phase 5) and Vacancy (Phase 7). This pass books amounts, it
-- does not settle them.
-- ============================================================

-- ─── Rent storage (Option A, confirmed with user) ───────────
--
-- Rent lives per room_assignment. A tenant's total rent = sum of their
-- active assignments' monthly_rent. T11 (add room) opens a new assignment
-- with its own rent; T12 (partial vacate) ends an assignment, dropping
-- that room's contribution. rent_increments references the specific
-- assignment whose rent changed.
--
-- Nullable initially — existing rows from the TenancyController pass
-- have no rent, and we're leaving them NULL rather than backfilling to 0
-- (test data is expendable; production has none yet). New assignments
-- created via POST /memberships/{id}/room-assignments require a value.
ALTER TABLE room_assignments ADD COLUMN monthly_rent NUMERIC(10,2);

-- ─── tenant_deposits ────────────────────────────────────────
--
-- One HELD security deposit per membership (§8.4 — deposit is per-tenancy,
-- not per-room; beta explicitly excludes per-room deposits). Refund /
-- forfeit / apply-to-balance transitions happen at vacancy (Phase 7).
-- This pass records the receipt and permits corrections while ACTIVE.

CREATE TABLE tenant_deposits (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id     UUID           NOT NULL UNIQUE REFERENCES tenant_property_memberships (id),
    amount            NUMERIC(10,2)  NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'NPR',           -- multi-currency out of scope for beta
    status            VARCHAR(30)    NOT NULL DEFAULT 'HELD',
    received_at_bs    VARCHAR(20)    NOT NULL,
    notes             TEXT,
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_deposit_status CHECK (status IN ('HELD','REFUNDED_PARTIAL','REFUNDED_FULL','FORFEITED','APPLIED_TO_BALANCE')),
    CONSTRAINT chk_deposit_amount_nonneg CHECK (amount >= 0)
);

-- ─── tenant_advance_rent ────────────────────────────────────
--
-- Rent paid ahead. Multiple rows per membership allowed (spec §10.3:
-- tenant may pre-pay again mid-tenancy). Consumption during billing is
-- Phase 5's concern — this pass only records amount + covered window.

CREATE TABLE tenant_advance_rent (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id     UUID           NOT NULL REFERENCES tenant_property_memberships (id),
    amount            NUMERIC(10,2)  NOT NULL,
    months_covered    SMALLINT       NOT NULL,
    covered_from_bs   VARCHAR(20)    NOT NULL,
    covered_to_bs     VARCHAR(20)    NOT NULL,
    status            VARCHAR(30)    NOT NULL DEFAULT 'HELD',
    notes             TEXT,
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_advance_status CHECK (status IN ('HELD','CONSUMED','REFUNDED')),
    CONSTRAINT chk_advance_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_advance_months_positive CHECK (months_covered > 0)
);
CREATE INDEX idx_advance_rent_membership ON tenant_advance_rent (membership_id);

-- ─── tenant_opening_balances ────────────────────────────────
--
-- One-shot pre-app financial position, captured at onboarding (T7 —
-- existing tenant with pre-app history). Immutable after create (no PUT
-- endpoint) — corrections require deletion + resubmission by an admin
-- (deletion not exposed this pass; audit_log will supersede the need
-- when it lands in Phase 8). Applied on the first bill by Billing engine.

CREATE TABLE tenant_opening_balances (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id     UUID           NOT NULL UNIQUE REFERENCES tenant_property_memberships (id),
    amount            NUMERIC(10,2)  NOT NULL,
    direction         VARCHAR(30)    NOT NULL,               -- OWED_BY_TENANT | CREDIT_TO_TENANT
    as_of_bs          VARCHAR(20)    NOT NULL,
    notes             TEXT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_opening_direction CHECK (direction IN ('OWED_BY_TENANT','CREDIT_TO_TENANT')),
    CONSTRAINT chk_opening_amount_nonneg CHECK (amount >= 0)
);

-- ─── rent_increments ────────────────────────────────────────
--
-- Historical trail of rent changes (§10.5 / T14). Append-only, immutable
-- once written — corrections happen by issuing a new increment that
-- reverses the wrong one, never by editing the row. Applied atomically:
-- append the row + update the referenced room_assignments.monthly_rent
-- inside one transaction. Billing engine reads this table for mid-cycle
-- segment boundaries (§9.3).

CREATE TABLE rent_increments (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id         UUID           NOT NULL REFERENCES tenant_property_memberships (id),
    room_assignment_id    UUID           NOT NULL REFERENCES room_assignments (id),
    previous_amount       NUMERIC(10,2),                                             -- NULL when the assignment had no prior rent set
    new_amount            NUMERIC(10,2)  NOT NULL,
    effective_bs          VARCHAR(20)    NOT NULL,
    reason                TEXT,
    notified_tenant       BOOLEAN        NOT NULL DEFAULT FALSE,
    changed_by            UUID,                                                       -- landlord user; FK when auth lands
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_rent_increment_amount_nonneg CHECK (new_amount >= 0)
);
CREATE INDEX idx_rent_increments_membership   ON rent_increments (membership_id);
CREATE INDEX idx_rent_increments_assignment   ON rent_increments (room_assignment_id);
CREATE INDEX idx_rent_increments_effective_bs ON rent_increments (membership_id, effective_bs);
