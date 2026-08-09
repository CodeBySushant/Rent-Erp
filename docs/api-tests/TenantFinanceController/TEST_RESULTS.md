# TenantFinanceController — API Test Results

**Date:** 2026-07-31
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 33/33 passed

Raw responses in [`responses/`](responses/). Postman collection: [`RentERP-TenantFinanceController.postman_collection.json`](RentERP-TenantFinanceController.postman_collection.json).

Covers the four resources under this pass — deposits, advance rent, opening balances, rent increments — plus the piggyback `room_assignments.monthly_rent` field.

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | RoomAssignment without `monthlyRent` → 400 (new required field) | POST | `/memberships/{mid}/room-assignments` | 400 | 400 | ✅ |
| 2 | RoomAssignment with `monthlyRent` | POST | `/memberships/{mid}/room-assignments` | 201 | 201 | ✅ |
| 3 | Negative rent → 400 | POST | `/memberships/{mid}/room-assignments` | 400 | 400 | ✅ |
| 4 | Second room assigned to same tenant (T11 shape) | POST | `/memberships/{mid}/room-assignments` | 201 | 201 | ✅ |
| 5 | Create deposit | POST | `/memberships/{mid}/deposit` | 201 | 201 | ✅ |
| 6 | Duplicate deposit → 409 | POST | `/memberships/{mid}/deposit` | 409 | 409 | ✅ |
| 7 | GET deposit | GET | `/memberships/{mid}/deposit` | 200 | 200 | ✅ |
| 8 | Update deposit (correction) | PUT | `/memberships/{mid}/deposit` | 200 | 200 | ✅ |
| 9 | Negative deposit amount → 400 | PUT | `/memberships/{mid}/deposit` | 400 | 400 | ✅ |
| 10 | Deposit on unknown membership → 404 | POST | `/memberships/{bad}/deposit` | 404 | 404 | ✅ |
| 11 | Create advance rent | POST | `/memberships/{mid}/advance-rent` | 201 | 201 | ✅ |
| 12 | Second advance rent row (multiple allowed) | POST | `/memberships/{mid}/advance-rent` | 201 | 201 | ✅ |
| 13 | Zero months → 400 | POST | `/memberships/{mid}/advance-rent` | 400 | 400 | ✅ |
| 14 | `coveredFromBs > coveredToBs` → 400 | POST | `/memberships/{mid}/advance-rent` | 400 | 400 | ✅ |
| 15 | List advance rent | GET | `/memberships/{mid}/advance-rent` | 200 | 200 | ✅ |
| 16 | GET advance rent by id (top-level) | GET | `/advance-rent/{id}` | 200 | 200 | ✅ |
| 17 | Create opening balance (OWED_BY_TENANT) | POST | `/memberships/{mid}/opening-balance` | 201 | 201 | ✅ |
| 18 | Duplicate opening balance → 409 (immutable, one-shot) | POST | `/memberships/{mid}/opening-balance` | 409 | 409 | ✅ |
| 19 | GET opening balance | GET | `/memberships/{mid}/opening-balance` | 200 | 200 | ✅ |
| 20 | Missing `direction` → 400 | POST | `/memberships/{mid}/opening-balance` | 400 | 400 | ✅ |
| 21 | Apply rent increment (T14) — 15000 → 17000, `previousAmount` captured from assignment | POST | `/memberships/{mid}/rent-increments` | 201 | 201 | ✅ |
| 22 | Verify assignment's `monthlyRent` updated atomically to 17000 | GET | `/memberships/{mid}/room-assignments` | 200, `monthlyRent=17000` | matches | ✅ |
| 23 | Rent increment on unknown assignment → 404 | POST | `/memberships/{mid}/rent-increments` | 404 | 404 | ✅ |
| 24 | Rent increment cross-membership (assignment belongs to a different membership) → 400 | POST | `/memberships/{mid}/rent-increments` | 400 | 400 | ✅ |
| 25 | `effectiveBs` before assignment's `effectiveFromBs` → 400 | POST | `/memberships/{mid}/rent-increments` | 400 | 400 | ✅ |
| 26 | Negative `newAmount` → 400 | POST | `/memberships/{mid}/rent-increments` | 400 | 400 | ✅ |
| 27 | Rent increment on ended assignment → 400 | POST | `/memberships/{mid}/rent-increments` | 400 | 400 | ✅ |
| 28 | List rent increments | GET | `/memberships/{mid}/rent-increments` | 200 | 200 | ✅ |
| 29 | GET rent increment by id (top-level) | GET | `/rent-increments/{id}` | 200 | 200 | ✅ |
| 30 | Deposit on TERMINATED membership → 400 | POST | `/memberships/{mid}/deposit` | 400 | 400 | ✅ |
| 31 | Rent increment on TERMINATED → 400 | POST | `/memberships/{mid}/rent-increments` | 400 | 400 | ✅ |
| 32 | Advance rent on TERMINATED → 400 | POST | `/memberships/{mid}/advance-rent` | 400 | 400 | ✅ |
| 33 | GET nonexistent deposit → 404 | GET | `/memberships/{mid}/deposit` | 404 | 404 | ✅ |

---

## Notes

- **§14.3 T14 rent increment atomicity** — scenarios 21/22 verify end-to-end. The service captures `previousAmount` from the assignment's current `monthlyRent` (never accepted from the client), appends the `rent_increments` row, and updates `room_assignments.monthly_rent` all in one `@Transactional` unit. Verified live: RA1 starts at 15000, POST rent-increment with newAmount=17000, response shows `previousAmount=15000.00`/`newAmount=17000.00`, and the follow-up GET of the assignments list shows `monthlyRent=17000.00`.
- **Reference integrity** — scenarios 23/24. The rent-increment endpoint validates the `roomAssignmentId` exists (404), then that it belongs to the URL's membership (400 with a clear message). Both cases have distinct HTTP semantics.
- **Immutability** — scenarios 18 (opening balance one-shot) and 27 (ended-assignment rejection). Opening balance is truly one-shot per membership; corrections require admin intervention (deferred to audit_log era). Ended room assignments freeze their rent — a rent change requires an active window.
- **TERMINATED gate** — scenarios 30/31/32. All three finance surfaces refuse operations on TERMINATED memberships. This protects vacancy settlement math — once a tenancy closes, its financial state is Vacancy's (Phase 7) to reconcile, not additive.
- **`monthly_rent` piggyback** — scenario 1 verifies the new required field is enforced (rejecting the request without it → 400). Scenario 2 confirms happy path. The DB column is nullable so the tenancy-pass fixture rows remain valid — the API refuses new creates that omit it.
- Test data isolated under fresh landlord `9800002001` and its own two properties. Left in place for regression re-runs.
