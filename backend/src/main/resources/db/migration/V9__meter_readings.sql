-- ============================================================
-- V9: Meter readings — append-only chain + replacement/coverage events
--
-- Spec §7.5 / §14.1. Reading chain is append-only and immutable once CONFIRMED.
-- Corrections are NEW rows of type CORRECTION with corrects_reading_id → original,
-- never edits. Wrong-but-confirmed rows stay in the chain for audit; the correction
-- is what participates in downstream billing math.
--
-- Reading types (10 total per spec §7.5). Enum column stores all of them for schema
-- completeness; the controller refuses to accept the tenant/vacancy-dependent types
-- (TENANT_JOIN, VACANCY, DEPARTURE_TOPUP, GAP_ABSORBED) until Phases 4/7.
-- ============================================================

-- Meters gain a per-meter max_reading_value used to compute rollover consumption
-- (§14.1 M1 — (max − previous) + current). Default 99999 fits the common 5-digit
-- electricity meter; landlord can override per meter for higher-digit displays.
ALTER TABLE meters ADD COLUMN max_reading_value NUMERIC(12,2) NOT NULL DEFAULT 99999;

-- ============================================================
-- meter_reading_log — the chain itself
-- ============================================================
CREATE TABLE meter_reading_log (
    id                       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    meter_id                 UUID            NOT NULL REFERENCES meters (id),
    property_id              UUID            NOT NULL REFERENCES properties (id),

    reading_type             VARCHAR(30)     NOT NULL,   -- INITIAL | BILLING_RUN | VACANCY | TENANT_JOIN | DEPARTURE_TOPUP | REPLACEMENT_CLOSE | REPLACEMENT_OPEN | COVERAGE_ANCHOR | GAP_ABSORBED | CORRECTION
    reading_value            NUMERIC(12,2)   NOT NULL,

    reading_date_bs          VARCHAR(20)     NOT NULL,   -- physical date the meter was read
    submission_date_bs       VARCHAR(20)     NOT NULL,   -- date the row was submitted (may differ → M16 backdated)
    is_backdated             BOOLEAN         NOT NULL DEFAULT FALSE,

    status                   VARCHAR(20)     NOT NULL DEFAULT 'PENDING',   -- PENDING | CONFIRMED

    -- consumption is stamped at confirm-time from (this - previous_confirmed) with
    -- rollover math applied if is_rollover=true. NULL until confirmed. INITIAL /
    -- REPLACEMENT_OPEN rows have no consumption (chain-anchor rows, not delta rows).
    consumption              NUMERIC(12,2),

    is_rollover              BOOLEAN         NOT NULL DEFAULT FALSE,
    is_estimated             BOOLEAN         NOT NULL DEFAULT FALSE,
    estimation_basis         VARCHAR(30),                -- ROLLING_3_MONTH_AVG | MANUAL | NULL
    is_gap_absorbed          BOOLEAN         NOT NULL DEFAULT FALSE,       -- §14.1 M14, set only by later phases

    photo_url                VARCHAR(500),
    notes                    TEXT,

    -- CORRECTION rows point back at the (wrong-but-confirmed) row they replace.
    corrects_reading_id      UUID            REFERENCES meter_reading_log (id),
    -- Event-linked rows carry the parent event id so the audit trail is queryable.
    replacement_event_id     UUID,
    coverage_event_id        UUID,

    submitted_by             UUID,                                                 -- FK added when auth lands
    confirmed_by             UUID,                                                 -- same
    confirmed_at             TIMESTAMPTZ,

    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_reading_type   CHECK (reading_type IN (
        'INITIAL','BILLING_RUN','VACANCY','TENANT_JOIN','DEPARTURE_TOPUP',
        'REPLACEMENT_CLOSE','REPLACEMENT_OPEN','COVERAGE_ANCHOR','GAP_ABSORBED','CORRECTION'
    )),
    CONSTRAINT chk_reading_status CHECK (status IN ('PENDING','CONFIRMED')),
    CONSTRAINT chk_estimation_basis CHECK (
        estimation_basis IS NULL
        OR estimation_basis IN ('ROLLING_3_MONTH_AVG','MANUAL')
    ),
    -- Confirmed rows must have confirmed_at; pending rows must not.
    CONSTRAINT chk_confirmed_at_matches_status CHECK (
        (status = 'CONFIRMED' AND confirmed_at IS NOT NULL)
        OR (status = 'PENDING' AND confirmed_at IS NULL)
    )
);

CREATE INDEX idx_reading_meter_date        ON meter_reading_log (meter_id, reading_date_bs);
CREATE INDEX idx_reading_meter_status      ON meter_reading_log (meter_id, status);
CREATE INDEX idx_reading_property          ON meter_reading_log (property_id);
CREATE INDEX idx_reading_type              ON meter_reading_log (meter_id, reading_type);
CREATE INDEX idx_reading_corrects          ON meter_reading_log (corrects_reading_id) WHERE corrects_reading_id IS NOT NULL;
CREATE INDEX idx_reading_replacement_event ON meter_reading_log (replacement_event_id) WHERE replacement_event_id IS NOT NULL;
CREATE INDEX idx_reading_coverage_event    ON meter_reading_log (coverage_event_id) WHERE coverage_event_id IS NOT NULL;

-- ============================================================
-- meter_replacement_events — one per meter replacement (§14.1 M4)
-- ============================================================
CREATE TABLE meter_replacement_events (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    old_meter_id          UUID           NOT NULL REFERENCES meters (id),
    new_meter_id          UUID           NOT NULL REFERENCES meters (id),
    close_reading_id      UUID           NOT NULL REFERENCES meter_reading_log (id),
    open_reading_id       UUID           NOT NULL REFERENCES meter_reading_log (id),
    replacement_date_bs   VARCHAR(20)    NOT NULL,
    reason                TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_replacement_old ON meter_replacement_events (old_meter_id);
CREATE INDEX idx_replacement_new ON meter_replacement_events (new_meter_id);

-- ============================================================
-- meter_coverage_events + meter_coverage_event_changes
-- One header row per structural coverage change (spec §14.1 M9/M10). Each affected
-- meter contributes one _changes row carrying its rooms_added / rooms_removed /
-- anchor reading.
-- ============================================================
CREATE TABLE meter_coverage_events (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id       UUID           NOT NULL REFERENCES properties (id),
    event_type        VARCHAR(20)    NOT NULL,           -- SPLIT | MERGE | ADD_ROOM | REMOVE_ROOM
    event_date_bs     VARCHAR(20)    NOT NULL,
    notes             TEXT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_coverage_event_type CHECK (
        event_type IN ('SPLIT','MERGE','ADD_ROOM','REMOVE_ROOM')
    )
);
CREATE INDEX idx_coverage_event_property ON meter_coverage_events (property_id);

CREATE TABLE meter_coverage_event_changes (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    coverage_event_id     UUID           NOT NULL REFERENCES meter_coverage_events (id),
    meter_id              UUID           NOT NULL REFERENCES meters (id),
    anchor_reading_id     UUID           REFERENCES meter_reading_log (id),   -- may be NULL for the meter that only sheds coverage
    rooms_added           JSONB          NOT NULL DEFAULT '[]'::jsonb,        -- array of room UUIDs
    rooms_removed         JSONB          NOT NULL DEFAULT '[]'::jsonb,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_coverage_event_changes_event ON meter_coverage_event_changes (coverage_event_id);
CREATE INDEX idx_coverage_event_changes_meter ON meter_coverage_event_changes (meter_id);
