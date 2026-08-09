-- ============================================================
-- V10: Tenancy — profiles, KYC, join requests, memberships, room
--       assignments, and property-scoped tenant blocks.
--
-- Phase 4 core. Six tables. tenant_deposits/advance_rent/opening_balances/
-- rent_increments land in TenantFinanceController (next pass) — this pass
-- is the identity/relationship layer only.
--
-- property_blocked_tenants moves into tenancy scope (was mapped to
-- PropertyController in CONTROLLER_TABLE_MAP.md). Reason: T3 landlord-
-- blocks-tenant is a tenant↔property relationship, not a property config,
-- and join-request creation needs to consult this table directly.
-- ============================================================

-- ─── tenant_profiles ────────────────────────────────────────
--
-- Tenant identity, independent of `users`. Linked tenants have user_id
-- set (the tenant installed the app and authenticated via OTP); unlinked
-- tenants have user_id NULL (landlord entered them in the setup wizard,
-- spec §10.3). Every downstream row (memberships, kyc, room assignments,
-- deposits later) references THIS table, never users directly — so
-- unlinked tenants participate fully without ever creating a users row.

CREATE TABLE tenant_profiles (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID            REFERENCES users (id),                 -- NULL for unlinked tenants
    full_name                   VARCHAR(255)    NOT NULL,
    phone                       VARCHAR(20)     NOT NULL,                              -- ^(97|98)\d{8}$ enforced app-side
    whatsapp_number             VARCHAR(20),                                           -- optional, used by §16.4 free-share bill delivery
    preferred_language          VARCHAR(5)      NOT NULL DEFAULT 'en',                 -- en | ne
    tenant_category             VARCHAR(20)     NOT NULL DEFAULT 'INDIVIDUAL',         -- INDIVIDUAL | FAMILY | BUSINESS (§10.4)
    num_occupants               SMALLINT        NOT NULL DEFAULT 1,
    emergency_contact_name      VARCHAR(255),
    emergency_contact_phone     VARCHAR(20),
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tenant_category CHECK (tenant_category IN ('INDIVIDUAL','FAMILY','BUSINESS')),
    CONSTRAINT chk_preferred_language CHECK (preferred_language IN ('en','ne')),
    -- One tenant_profiles per user_id for linked tenants; unlinked (NULL user_id)
    -- rows are not constrained — a phone number may reappear across different
    -- landlords with no shared identity (spec §10.3).
    CONSTRAINT uq_tenant_profiles_user_id UNIQUE (user_id)
);
CREATE INDEX idx_tenant_profiles_phone ON tenant_profiles (phone);

-- Now that tenant_profiles exists, add the deferred FK on
-- meters.designated_tenant_id. Nullable so meters without a designated
-- tenant remain valid. We intentionally do NOT populate any existing
-- rows here — designation flows through a future MeterController endpoint
-- once tenants exist in the property.
ALTER TABLE meters
    ADD CONSTRAINT fk_meters_designated_tenant
    FOREIGN KEY (designated_tenant_id) REFERENCES tenant_profiles (id);

-- ─── tenant_kyc ─────────────────────────────────────────────
--
-- Per spec §10.4, KYC is verified manually by an admin team (not the
-- landlord, not automated). id_type + id_number are permanent; photo
-- URLs are cleared 30 days after verification decision (purge job is a
-- follow-up commit — column stays present).

CREATE TABLE tenant_kyc (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_profile_id           UUID            NOT NULL REFERENCES tenant_profiles (id),

    id_type                     VARCHAR(30)     NOT NULL,   -- CITIZENSHIP | PASSPORT | DRIVING_LICENSE | NATIONAL_ID
    id_number                   VARCHAR(100)    NOT NULL,

    photo_front_url             VARCHAR(500),
    photo_back_url              VARCHAR(500),
    photo_selfie_url            VARCHAR(500),

    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED | FLAGGED
    submitted_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    verified_at                 TIMESTAMPTZ,
    verified_by                 UUID,                                        -- admin user; FK wires when admin RBAC lands
    rejection_reason            TEXT,
    flag_reason                 TEXT,                                        -- T6 — landlord flags physical mismatch
    resubmit_count              SMALLINT        NOT NULL DEFAULT 0,          -- T5 — cap at 5

    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_kyc_id_type   CHECK (id_type IN ('CITIZENSHIP','PASSPORT','DRIVING_LICENSE','NATIONAL_ID')),
    CONSTRAINT chk_kyc_status    CHECK (status IN ('PENDING','APPROVED','REJECTED','FLAGGED')),
    -- Only one live KYC row per tenant profile — resubmission edits it in place,
    -- resubmit_count tracks how many times. Full audit trail comes with the
    -- audit_log table (Phase 8). Retaining historical KYC rows here would
    -- conflict with the photo-purge doctrine anyway.
    CONSTRAINT uq_tenant_kyc_profile UNIQUE (tenant_profile_id)
);
CREATE INDEX idx_tenant_kyc_status ON tenant_kyc (status);

-- ─── join_requests ──────────────────────────────────────────
--
-- Linked-tenant → landlord flow only (spec §10.1). Unlinked tenants
-- bypass this entirely — landlord creates them directly via POST
-- /tenant-profiles + POST /memberships.

CREATE TABLE join_requests (
    id                     UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_profile_id      UUID           NOT NULL REFERENCES tenant_profiles (id),
    property_id            UUID           NOT NULL REFERENCES properties (id),

    status                 VARCHAR(20)    NOT NULL DEFAULT 'PENDING',   -- PENDING | ACCEPTED | REJECTED | EXPIRED | CANCELLED
    message                TEXT,                                        -- optional tenant note (T2)
    requested_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    expires_at             TIMESTAMPTZ    NOT NULL,                     -- = requested_at + 5 min (T1)

    responded_at           TIMESTAMPTZ,
    responded_by           UUID,                                        -- landlord user; FK wires later
    response_message       TEXT,

    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_join_request_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED','CANCELLED'))
);
CREATE INDEX idx_join_requests_property ON join_requests (property_id);
CREATE INDEX idx_join_requests_tenant   ON join_requests (tenant_profile_id);
CREATE INDEX idx_join_requests_status   ON join_requests (status);
-- Only one PENDING request per (tenant, property) at a time.
CREATE UNIQUE INDEX uq_join_requests_pending_per_tenant_property
    ON join_requests (tenant_profile_id, property_id)
    WHERE status = 'PENDING';

-- ─── tenant_property_memberships ────────────────────────────
--
-- The relationship "this tenant is currently at this property". Created
-- by accepting a join_request (linked path) or by direct POST for the
-- unlinked/existing-tenant path (spec §10.3, T7). Terminated by the
-- vacancy flow (Phase 7); a raw /terminate exists here for admin/testing.

CREATE TABLE tenant_property_memberships (
    id                            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_profile_id             UUID           NOT NULL REFERENCES tenant_profiles (id),
    property_id                   UUID           NOT NULL REFERENCES properties (id),

    status                        VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | TERMINATED
    payment_model_override        VARCHAR(30),                              -- NULL = fall back to property.paymentModelDefault (T10)

    linked_from_join_request_id   UUID           REFERENCES join_requests (id),   -- linked path only; NULL for unlinked

    started_at_bs                 VARCHAR(20)    NOT NULL,                  -- BS move-in date; VARCHAR, never DATE
    ended_at_bs                   VARCHAR(20),                              -- NULL while ACTIVE
    termination_reason            TEXT,

    is_active                     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at                    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_membership_status CHECK (status IN ('ACTIVE','TERMINATED')),
    CONSTRAINT chk_payment_model_override CHECK (
        payment_model_override IS NULL OR payment_model_override IN ('PAY_IN_ADVANCE','PAY_AFTER_STAY')
    )
);
CREATE INDEX idx_memberships_property ON tenant_property_memberships (property_id);
CREATE INDEX idx_memberships_tenant   ON tenant_property_memberships (tenant_profile_id);
CREATE INDEX idx_memberships_status   ON tenant_property_memberships (status);
-- One ACTIVE membership per (tenant, property). Historical TERMINATED
-- rows may accumulate — same tenant may vacate and rejoin later.
CREATE UNIQUE INDEX uq_memberships_active_per_tenant_property
    ON tenant_property_memberships (tenant_profile_id, property_id)
    WHERE status = 'ACTIVE';

-- ─── room_assignments ──────────────────────────────────────
--
-- Dated tenant↔room mapping. Same shape as meter_room_coverage — VARCHAR
-- BS date range, effective_to_bs=NULL means active. Multiple concurrent
-- rows per membership are normal (one tenant, several rooms). A room may
-- only have one active assignment at any time (spec §10.2) — enforced at
-- the service layer, not with a partial unique index (would need cross-
-- membership scoping and Postgres partial-uniques can't do that cleanly).

CREATE TABLE room_assignments (
    id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id     UUID            NOT NULL REFERENCES tenant_property_memberships (id),
    room_id           UUID            NOT NULL REFERENCES rooms (id),
    effective_from_bs VARCHAR(20)     NOT NULL,
    effective_to_bs   VARCHAR(20),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_room_assignments_membership ON room_assignments (membership_id);
CREATE INDEX idx_room_assignments_room       ON room_assignments (room_id);
-- No duplicate (membership, room, from) triple.
CREATE UNIQUE INDEX uq_room_assignments_membership_room_from
    ON room_assignments (membership_id, room_id, effective_from_bs);

-- ─── property_blocked_tenants ──────────────────────────────
--
-- T3 — landlord blocks a specific tenant from re-requesting to join a
-- property. Blocks are property-scoped only (not global) and are
-- reversible. Landlord can block regardless of whether the tenant has
-- ever had a join request or membership.

CREATE TABLE property_blocked_tenants (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id           UUID            NOT NULL REFERENCES properties (id),
    tenant_profile_id     UUID            NOT NULL REFERENCES tenant_profiles (id),

    reason                TEXT,
    blocked_by            UUID,                                                  -- landlord user; FK later
    blocked_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    is_active             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_blocked_tenants_property ON property_blocked_tenants (property_id);
CREATE UNIQUE INDEX uq_blocked_tenants_active
    ON property_blocked_tenants (property_id, tenant_profile_id)
    WHERE is_active = TRUE;
