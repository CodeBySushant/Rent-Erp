# Move-out (Phase 7) — Detailed Dev Log

**Table:** `move_outs` (V19)
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/MoveOut/moveout_test.ps1`

## Endpoints

| Method | Path | Who | |
|---|---|---|---|
| POST | `/memberships/{id}/move-out` `{plannedMoveOutBs, reason?}` | the tenant or a manager | NOTICE_GIVEN |
| GET | `/memberships/{id}/move-out` | property viewers, the tenant | latest move-out (null if none) with a settlement preview while open |
| POST | `/move-outs/{id}/cancel` | the tenant or a manager | NOTICE_GIVEN → CANCELLED |
| POST | `/move-outs/{id}/settle` `{movedOutBs, finalCharges?, finalChargesNote?, deductions?, deductionsNote?}` | manager | NOTICE_GIVEN → SETTLED |
| GET | `/properties/{id}/move-outs?status=` | property viewers | list |

## Rules

* One open notice per tenancy (409 `MOVE_OUT_PENDING`, partial unique index). Date must be a real BS date, not in the past (400). Shorter than the property's notice period → `shortNotice` flag (allowed, shown).
* Settlement waits while a payment proof is pending (409 `PENDING_PAYMENTS`) and only runs once (409 `MOVE_OUT_CLOSED`, row lock). Ended tenancy → 400 `TENANCY_ENDED`.
* **Money, in one transaction:** the HELD deposit pays unpaid issued bills oldest first, each as an approved payment "Deposit applied at move-out" (`PaymentService.applyFromDeposit`: same lock and exactly-once rule as all payments); what is left covers final charges, then deductions; the rest is the refund. Deposit status → REFUNDED_FULL / REFUNDED_PARTIAL / APPLIED_TO_BALANCE. `tenantStillOwes` = bills the deposit did not cover + uncovered charges.
* **Tenancy:** ended through `MembershipService.terminate` on the move-out date, which closes room assignments — the rooms become vacant.
* **History:** bills are never deleted; uncovered bills stay owed; the move-out row keeps every number of the settlement.

## Not in this stage

The final bill for the last partial month is not generated automatically (the billing engine's departure top-up types are deferred); the owner enters it as "final charges" or runs the month's billing before settling.

## App

Owner tenant detail → Vacate: give notice, then settle (date, final charges, damages) or cancel; settled move-outs show the record. Tenant My Rooms → Move out: give or withdraw notice.
