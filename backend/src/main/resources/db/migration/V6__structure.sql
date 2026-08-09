-- ============================================================
-- V6: Property structure — floors and rooms
--
-- Setup wizard step 2 (spec §6.1): "Floors -> rooms per floor -> room names".
-- Rooms deliberately hold no meter or tenant reference (spec §20.1) — that
-- coupling is added later via meter_room_coverage (Phase 3) and
-- room_assignments (Phase 4). No vacant/occupied status column here either;
-- occupancy is always derived from room_assignments, never stored on the
-- room itself (same "derived, never hand-entered" principle as meters, P1).
-- ============================================================

CREATE TABLE floors (
    id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id    UUID            NOT NULL REFERENCES properties (id),
    name           VARCHAR(255)    NOT NULL,
    floor_number   SMALLINT        NOT NULL,   -- ordering key; ground floor = 0, basements negative
    is_active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Not spec-mandated (no floor/room constraint appears in §20.2) — our own rule so two
    -- floors in the same building can't collide on the same structural position.
    CONSTRAINT uq_floors_property_floor_number UNIQUE (property_id, floor_number)
);

CREATE INDEX idx_floors_property ON floors (property_id);

CREATE TABLE rooms (
    id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    floor_id       UUID            NOT NULL REFERENCES floors (id),
    name           VARCHAR(100)    NOT NULL,
    is_active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Same reasoning as the floor_number constraint above — our own rule, not spec-mandated.
    CONSTRAINT uq_rooms_floor_name UNIQUE (floor_id, name)
);

CREATE INDEX idx_rooms_floor ON rooms (floor_id);
