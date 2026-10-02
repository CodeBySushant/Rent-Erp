-- ============================================================
-- V18: payments (Phase 6)
--
-- A payment is money against one tenant bill.
--   OWNER_RECORDED : cash / bank the owner received → APPROVED at once.
--   TENANT_PROOF   : tenant uploads a receipt → PENDING until the owner
--                    approves (APPROVED) or rejects (REJECTED); the tenant may
--                    withdraw it while pending (CANCELLED).
--
-- Only APPROVED payments change the bill, and each does so exactly once:
-- applied_at is set in the same transaction that adds the amount to
-- tenant_bills.amount_paid, under a row lock on the payment and the bill.
-- idempotency_key makes a repeated submit (double tap, retry) return the
-- first payment instead of creating another.
-- ============================================================

CREATE TABLE payments (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id       UUID          NOT NULL REFERENCES properties (id),
    membership_id     UUID          NOT NULL REFERENCES tenant_property_memberships (id),
    bill_id           UUID          NOT NULL REFERENCES tenant_bills (id),
    amount            NUMERIC(10,2) NOT NULL,
    method            VARCHAR(20)   NOT NULL,
    source            VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    paid_at_bs        VARCHAR(10)   NOT NULL,
    reference         VARCHAR(100),
    note              VARCHAR(500),
    proof_file_id     UUID          REFERENCES stored_files (id),
    submitted_by      UUID          NOT NULL REFERENCES users (id),
    decided_by        UUID          REFERENCES users (id),
    decided_at        TIMESTAMPTZ,
    rejection_reason  VARCHAR(500),
    applied_at        TIMESTAMPTZ,
    idempotency_key   VARCHAR(100),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_method CHECK (method IN ('CASH', 'BANK', 'WALLET', 'OTHER')),
    CONSTRAINT chk_payments_source CHECK (source IN ('OWNER_RECORDED', 'TENANT_PROOF')),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    -- applied exactly when approved
    CONSTRAINT chk_payments_applied CHECK ((status = 'APPROVED') = (applied_at IS NOT NULL)),
    CONSTRAINT chk_payments_proof CHECK (source <> 'TENANT_PROOF' OR proof_file_id IS NOT NULL)
);

CREATE INDEX idx_payments_bill ON payments (bill_id);
CREATE INDEX idx_payments_membership ON payments (membership_id, created_at DESC);
CREATE INDEX idx_payments_property_status ON payments (property_id, status);
CREATE UNIQUE INDEX uq_payments_idempotency ON payments (submitted_by, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
-- At most one proof waiting per bill.
CREATE UNIQUE INDEX uq_payments_pending_per_bill ON payments (bill_id) WHERE status = 'PENDING';
