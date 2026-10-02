-- ============================================================
-- V19: move-outs (Phase 7, vacancy)
--
-- NOTICE_GIVEN : tenant or owner announced the move-out date.
-- SETTLED      : owner settled — deposit applied to unpaid bills, the rest
--                refunded, tenancy ended (rooms freed). The numbers are kept
--                here as the record of the settlement.
-- CANCELLED    : notice withdrawn before settlement.
-- At most one open (NOTICE_GIVEN) move-out per membership.
-- ============================================================

CREATE TABLE move_outs (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id        UUID          NOT NULL REFERENCES tenant_property_memberships (id),
    property_id          UUID          NOT NULL REFERENCES properties (id),
    status               VARCHAR(20)   NOT NULL,
    requested_by         UUID          NOT NULL REFERENCES users (id),
    requested_by_tenant  BOOLEAN       NOT NULL,
    notice_date_bs       VARCHAR(10)   NOT NULL,
    planned_move_out_bs  VARCHAR(10)   NOT NULL,
    short_notice         BOOLEAN       NOT NULL DEFAULT FALSE,
    reason               VARCHAR(500),
    -- settlement record
    moved_out_bs         VARCHAR(10),
    outstanding_before   NUMERIC(10,2),
    final_charges        NUMERIC(10,2),
    final_charges_note   VARCHAR(500),
    deductions           NUMERIC(10,2),
    deductions_note      VARCHAR(500),
    deposit_held         NUMERIC(10,2),
    deposit_applied      NUMERIC(10,2),
    refund_amount        NUMERIC(10,2),
    tenant_still_owes    NUMERIC(10,2),
    settled_by           UUID          REFERENCES users (id),
    settled_at           TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_move_outs_status CHECK (status IN ('NOTICE_GIVEN', 'SETTLED', 'CANCELLED')),
    CONSTRAINT chk_move_outs_settled CHECK ((status = 'SETTLED') = (settled_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_move_outs_open_per_membership ON move_outs (membership_id) WHERE status = 'NOTICE_GIVEN';
CREATE INDEX idx_move_outs_property_status ON move_outs (property_id, status);
