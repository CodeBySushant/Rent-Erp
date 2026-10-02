-- ============================================================
-- V21: how tenants pay the owner, per property
--
-- One row per property: the owner's payment QR (an upload with purpose
-- PAYMENT_QR tied to this property), wallet details (eSewa / Khalti / IME Pay)
-- and bank details. Shown to the property's active tenants on Pay Rent.
-- These are the owner's own receiving details, entered by the owner to share
-- with their tenants.
-- ============================================================

CREATE TABLE property_payment_details (
    property_id     UUID          PRIMARY KEY REFERENCES properties (id),
    qr_file_id      UUID          REFERENCES stored_files (id),
    wallet_name     VARCHAR(50),
    wallet_id       VARCHAR(100),
    bank_name       VARCHAR(100),
    account_name    VARCHAR(150),
    account_number  VARCHAR(50),
    branch          VARCHAR(100),
    notes           VARCHAR(500),
    updated_by      UUID          REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
