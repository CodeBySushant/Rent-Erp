# Owner payment details — Detailed Dev Log

**Table:** `property_payment_details` (V21) — one row per property
**Endpoints:** `GET /api/v1/properties/{id}/payment-details` (property viewers and the property's active tenants), `PUT` the same path (owner / manager)
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/PaymentDetails/payment_details_test.ps1`

## Rules

* Fields: `qrFileId`, `walletName`, `walletId`, `bankName`, `accountName`, `accountNumber`, `branch`, `notes`. PUT replaces the whole set.
* At least a QR, a wallet ID or a bank account number (400 `DETAILS_REQUIRED`).
* The QR must be an upload with purpose PAYMENT_QR tied to the same property (400 `QR_INVALID`); the file rules already let the property's active tenants open it.
* Readers: anyone with access to the property, or a tenant with an ACTIVE tenancy there (`ResourceAccess.propertyOrActiveTenant`); everyone else 403. GET returns `data: null` until the owner sets them.

## App

Owner: Profile → Payment Details (selected property): upload / change / remove the QR, wallet (eSewa, Khalti, IME Pay, other) and number, bank name, account name and number, branch, note. Tenant Pay Rent shows a "Pay to" card with the QR and the details (selectable to copy).
