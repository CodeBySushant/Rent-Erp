-- ============================================================
-- V20: tenant requests
--
-- ROOM_CHANGE, VACATE, MAINTENANCE, OTHER — raised by the tenant (or the
-- owner on their behalf) against one tenancy.
-- PENDING → APPROVED → COMPLETED, or PENDING → REJECTED (with a note), or
-- PENDING → CANCELLED (withdrawn). Approving a VACATE opens the move-out
-- notice; approving a ROOM_CHANGE with a target room moves the tenant at once
-- (COMPLETED). One open ROOM_CHANGE / VACATE request per tenancy.
-- ============================================================

CREATE TABLE tenant_requests (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id      UUID          NOT NULL REFERENCES tenant_property_memberships (id),
    property_id        UUID          NOT NULL REFERENCES properties (id),
    type               VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    title              VARCHAR(150)  NOT NULL,
    description        VARCHAR(2000),
    preferred_date_bs  VARCHAR(10),
    photo_file_id      UUID          REFERENCES stored_files (id),
    created_by         UUID          NOT NULL REFERENCES users (id),
    by_tenant          BOOLEAN       NOT NULL,
    owner_note         VARCHAR(1000),
    decided_by         UUID          REFERENCES users (id),
    decided_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    cancelled_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_requests_type CHECK (type IN ('ROOM_CHANGE', 'VACATE', 'MAINTENANCE', 'OTHER')),
    CONSTRAINT chk_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_requests_property_status ON tenant_requests (property_id, status, created_at DESC);
CREATE INDEX idx_requests_membership ON tenant_requests (membership_id, created_at DESC);
CREATE UNIQUE INDEX uq_requests_open_change_or_vacate ON tenant_requests (membership_id, type)
    WHERE type IN ('ROOM_CHANGE', 'VACATE') AND status IN ('PENDING', 'APPROVED');
