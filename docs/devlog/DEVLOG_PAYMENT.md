# PaymentController — Detailed Dev Log (Phase 6)

**Table:** `payments` (V18)
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/PaymentController/payments_test.ps1`

## Endpoints

| Method | Path | Who | Result |
|---|---|---|---|
| POST | `/tenant-bills/{billId}/payments` | owner / manager | money received → APPROVED, applied now |
| POST | `/tenant-bills/{billId}/payment-proofs` | the bill's tenant | receipt (upload with purpose PAYMENT_PROOF) → PENDING |
| GET | `/tenant-bills/{billId}/payments` | property viewers, the bill's tenant | history of the bill |
| POST | `/payments/{id}/approve` | owner / manager | PENDING → APPROVED, applied |
| POST | `/payments/{id}/reject` `{reason}` | owner / manager | PENDING → REJECTED |
| POST | `/payments/{id}/cancel` | the tenant who sent it | PENDING → CANCELLED |
| GET | `/properties/{id}/payments?status=` | property viewers | list (e.g. pending approvals) |
| GET | `/me/payments` | signed-in tenant | own payments |

Body for record / proof: `{amount, method: CASH|BANK|WALLET|OTHER, paidAtBs, reference?, note?, proofFileId?}`. Optional header `Idempotency-Key`.

## Rules

* **Applied exactly once.** Only the move to APPROVED changes the bill (`amount_paid`, `balance_due`, `payment_status`), inside one transaction holding row locks on the payment and the bill. `chk_payments_applied` requires `applied_at` to be set exactly when status is APPROVED. Approving, rejecting or withdrawing anything that is not PENDING → 409 `PAYMENT_ALREADY_DECIDED`.
* **Never more than owed.** Amount above the bill's balance → 400 `AMOUNT_EXCEEDS_BALANCE`; a fully paid bill → 409 `BILL_ALREADY_PAID`; approving a proof that would overpay (because cash was recorded meanwhile) → 409 `EXCEEDS_BALANCE`.
* **Only live bills.** Draft, cancelled or superseded bills → 400 `BILL_NOT_PAYABLE`.
* **One proof waiting per bill** (409 `PAYMENT_PENDING`; also a partial unique index).
* **Proof file** must be the tenant's own upload with purpose PAYMENT_PROOF (400 `PROOF_REQUIRED` / `PROOF_INVALID`). The owner reads it through the file rules (property access).
* **Duplicates.** Same Idempotency-Key from the same user returns the first payment (unique index on `(submitted_by, idempotency_key)`).
* **Billing runs** with approved or pending payments cannot be cancelled (409 `RUN_HAS_PAYMENTS`, `PaymentGuard`) — this is the "payment-state gating" the billing devlog deferred to Phase 6.
* Totals follow automatically: tenant list owed, property summary outstanding, My Stay owed all read `balance_due`. The summary also counts `pendingPayments`.

## App

Bill detail (owner): payment history with Received / Waiting for approval / Rejected / Withdrawn, receipt image, Approve / Reject (reason), and Record payment (amount defaults to what is owed; Cash / Bank / eSewa-Khalti / Other; one Idempotency-Key per sheet). `PaymentRepository.sendProof` is ready for the tenant's Pay Rent screens.
