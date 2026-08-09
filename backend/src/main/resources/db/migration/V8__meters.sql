-- ============================================================
-- V8: Meters, room coverage, infrastructure scope
--
-- Setup wizard step 7 (spec §6.1/§7): meters are the structural core.
-- Meters live independently of rooms and tenants; they attach via two
-- separate, dated relationships:
--   - meter_room_coverage      → structural (which physical rooms the meter wires to)
--   - meter_tenant_assignments → dynamic   (who currently pays for this meter's usage)
--
-- This migration builds:
--   1. meters                       — the meter itself (main / tenant_supply / infrastructure)
--   2. meter_room_coverage          — dated meter↔room mapping
--   3. infrastructure_meter_scope   — for infra meters with scope_type=SCOPED, which floors/tenants
--
-- Deliberately NOT built in this pass (also mapped to MeterController in
-- CONTROLLER_TABLE_MAP.md, deferred to TenancyController — Phase 4):
--   - meter_tenant_assignments (whole table) — needs tenant identity, doesn't exist yet
--   - infrastructure_meter_scope rows with scope_by=TENANT — same reason
--
-- Reading-chain tables (meter_reading_log, meter_replacement_events,
-- meter_coverage_events, meter_coverage_event_changes) belong to
-- MeterReadingController per CONTROLLER_TABLE_MAP.md and are the next
-- pass, not this one.
-- ============================================================

CREATE TABLE meters (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id                     UUID            NOT NULL REFERENCES properties (id),
    serial_number                   VARCHAR(100),                   -- manufacturer serial, optional
    label                           VARCHAR(255)    NOT NULL,       -- landlord-facing name, e.g. "Ground Floor Pump"

    meter_purpose                   VARCHAR(30)     NOT NULL,       -- ELECTRICITY | WATER
    meter_type                      VARCHAR(30)     NOT NULL,       -- MAIN | TENANT_SUPPLY | INFRASTRUCTURE
    infrastructure_scope_type       VARCHAR(20),                    -- GLOBAL | SCOPED | NULL (only when meter_type=INFRASTRUCTURE)

    split_rule_override             VARCHAR(20),                    -- EQUAL | ROOM_WEIGHTED | CUSTOM | NULL (NULL = fall back to property.default_split_rule)

    -- Spec §7.6 / §14.1 M18. Infra meters default to LANDLORD_ONLY at the app layer
    -- (pump rooms are physically locked); the DB accepts any of the three.
    reading_responsibility          VARCHAR(30)     NOT NULL,       -- FIRST_SUBMISSION_WINS | DESIGNATED_TENANT | LANDLORD_ONLY

    -- Populated in Phase 4 (TenancyController). Nullable so we can create meters now
    -- and hook up designated tenants once the tenants table exists.
    designated_tenant_id            UUID,

    -- Populated by MeterReadingController's replacement event workflow (§7.8 / §14.1 M4).
    -- Self-FK, nullable, always null for active meters.
    replaced_by_meter_id            UUID            REFERENCES meters (id),

    status                          VARCHAR(20)     NOT NULL,       -- ACTIVE | INACTIVE (spec §7.8 — soft-marked, never deleted)
    is_active                       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Spec §14.1 M12: two meters in the same property may not share a serial. Serial is
    -- optional so we scope uniqueness with a partial index instead of a table constraint.
    -- Property-scoped, not global — two landlords can independently own meters that
    -- coincidentally share a serial from different manufacturers.
    CONSTRAINT chk_meter_purpose               CHECK (meter_purpose IN ('ELECTRICITY','WATER')),
    CONSTRAINT chk_meter_type                  CHECK (meter_type IN ('MAIN','TENANT_SUPPLY','INFRASTRUCTURE')),
    CONSTRAINT chk_infra_scope_type            CHECK (infrastructure_scope_type IS NULL OR infrastructure_scope_type IN ('GLOBAL','SCOPED')),
    CONSTRAINT chk_meter_split_override        CHECK (split_rule_override IS NULL OR split_rule_override IN ('EQUAL','ROOM_WEIGHTED','CUSTOM')),
    CONSTRAINT chk_reading_responsibility      CHECK (reading_responsibility IN ('FIRST_SUBMISSION_WINS','DESIGNATED_TENANT','LANDLORD_ONLY')),
    CONSTRAINT chk_meter_status                CHECK (status IN ('ACTIVE','INACTIVE')),

    -- infrastructure_scope_type is only meaningful when the meter is INFRASTRUCTURE. For MAIN
    -- and TENANT_SUPPLY it must be NULL — enforced here rather than left as a soft convention.
    CONSTRAINT chk_infra_scope_matches_type    CHECK (
        (meter_type = 'INFRASTRUCTURE' AND infrastructure_scope_type IS NOT NULL)
        OR (meter_type <> 'INFRASTRUCTURE' AND infrastructure_scope_type IS NULL)
    )
);

CREATE INDEX idx_meters_property               ON meters (property_id);
CREATE INDEX idx_meters_property_type          ON meters (property_id, meter_type);

-- Spec §14.1 M12 — property-scoped duplicate serial block. Partial: rows with NULL serial
-- are exempt, since serial is optional and NULL is not a value that can collide.
CREATE UNIQUE INDEX uq_meters_property_serial
    ON meters (property_id, serial_number)
    WHERE serial_number IS NOT NULL;

-- ============================================================
-- meter_room_coverage — dated meter↔room mapping (spec §7.1 / §7.5)
--
-- This is the "coverage" side of the meter model — which physical rooms a meter
-- wires to, historically. Rows are dated with BS strings (VARCHAR, not DATE — see
-- RENT_ERP_CONTEXT.md DB rules) so historical bills can compute against the coverage
-- as-of the billing date, not the current shape (spec §14.1 M9).
--
-- effective_to_bs = NULL means the row is currently active. Ending a coverage row
-- (structural change, coverage split, meter deactivation) sets effective_to_bs on
-- the existing row rather than deleting it.
-- ============================================================
CREATE TABLE meter_room_coverage (
    id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    meter_id          UUID            NOT NULL REFERENCES meters (id),
    room_id           UUID            NOT NULL REFERENCES rooms (id),
    effective_from_bs VARCHAR(20)     NOT NULL,   -- "2082-04-01" style; app layer validates BS date
    effective_to_bs   VARCHAR(20),                -- NULL = currently active
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_meter_room_coverage_meter ON meter_room_coverage (meter_id);
CREATE INDEX idx_meter_room_coverage_room  ON meter_room_coverage (room_id);

-- Our own rule (not §20 spec-mandated) — the same (meter, room, from-date) tuple
-- should never appear twice; if a room reconnects to a meter later, its from-date
-- differs. Prevents accidental duplicate inserts from double-taps or retries.
CREATE UNIQUE INDEX uq_meter_room_coverage_meter_room_from
    ON meter_room_coverage (meter_id, room_id, effective_from_bs);

-- ============================================================
-- infrastructure_meter_scope — which floors/tenants an infra meter covers (spec §7.3)
--
-- Only relevant when meters.meter_type = 'INFRASTRUCTURE' AND meters.infrastructure_scope_type = 'SCOPED'.
-- A GLOBAL infra meter has no rows here — everyone is in scope by default.
--
-- Two scope kinds:
--   - FLOOR   → row references floor_id, tenant_id is NULL. Buildable this pass.
--   - TENANT  → row references tenant_id, floor_id is NULL. DEFERRED to TenancyController;
--              tenant_id column is present but no rows will be inserted from
--              MeterController — enforced at the service layer.
-- ============================================================
CREATE TABLE infrastructure_meter_scope (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    meter_id    UUID          NOT NULL REFERENCES meters (id),
    scope_by    VARCHAR(20)   NOT NULL,           -- FLOOR | TENANT
    floor_id    UUID          REFERENCES floors (id),
    tenant_id   UUID,                             -- FK added when tenants table exists (Phase 4)
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_infra_scope_by       CHECK (scope_by IN ('FLOOR','TENANT')),
    CONSTRAINT chk_infra_scope_target   CHECK (
        (scope_by = 'FLOOR'  AND floor_id  IS NOT NULL AND tenant_id IS NULL)
        OR (scope_by = 'TENANT' AND tenant_id IS NOT NULL AND floor_id IS NULL)
    )
);

CREATE INDEX idx_infra_meter_scope_meter ON infrastructure_meter_scope (meter_id);

-- No duplicate (meter, floor) rows — our own rule, same shape as coverage uniqueness above.
CREATE UNIQUE INDEX uq_infra_meter_scope_meter_floor
    ON infrastructure_meter_scope (meter_id, floor_id)
    WHERE scope_by = 'FLOOR';

CREATE UNIQUE INDEX uq_infra_meter_scope_meter_tenant
    ON infrastructure_meter_scope (meter_id, tenant_id)
    WHERE scope_by = 'TENANT';
