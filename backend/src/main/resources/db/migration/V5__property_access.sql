-- ============================================================
-- V5: Property access grants
--
-- Owner, manager and view-only grants, property-scoped (spec §4.2,
-- §20.1). One row per (property, user) pair — including the primary
-- owner, so permission checks have a single source of truth instead
-- of "check properties.owner_user_id OR check property_access".
-- PropertyService.createProperty() inserts the OWNER row automatically
-- in the same transaction as the property itself.
-- ============================================================

CREATE TABLE property_access (
    id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id   UUID            NOT NULL REFERENCES properties (id),
    user_id       UUID            NOT NULL REFERENCES users (id),
    role          VARCHAR(50)     NOT NULL,   -- OWNER | MANAGER | VIEW_ONLY
    granted_by    UUID            REFERENCES users (id),   -- NULL for the auto-created OWNER row
    is_active     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_property_access_property_user UNIQUE (property_id, user_id)
);

CREATE INDEX idx_property_access_property ON property_access (property_id);
CREATE INDEX idx_property_access_user     ON property_access (user_id);
