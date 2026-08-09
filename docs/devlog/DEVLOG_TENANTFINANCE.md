# DEVLOG — TenantFinanceController

**Phase:** 4 — Tenancy (financial-onboarding layer)
**Date:** 2026-07-31
**Status:** ✅ Complete — 33/33 live PostgreSQL scenarios passed
**Scope tables (this pass):** `tenant_deposits`, `tenant_advance_rent`, `tenant_opening_balances`, `rent_increments` — plus a piggyback ALTER adding `room_assignments.monthly_rent`

Phase 4 is now complete — all TenantFinance tables land here, and the deferred "where does rent live?" gap from the tenancy pass is resolved.

---

## Scope confirmed with user (recommendation-set)

- **Option A** for rent storage: `room_assignments.monthly_rent NUMERIC(10,2)`, per-tenant per-room. T11 (add room adds rent) and T12 (partial vacate drops that room's contribution) fall out for free.
- Piggyback `room_assignments.monthly_rent` in V11, edit `CreateRoomAssignmentRequest` to require it going forward. Column is nullable so tenancy-pass fixture rows survive; API refuses new creates without it.
- No backfill of existing NULL `monthly_rent` rows — production is empty and tenancy-pass test data is expendable.
- Deposit refund/forfeit/apply endpoints truly deferred to Vacancy (Phase 7).
- Advance-rent has no consume endpoint — that's Billing-engine territory.
- **All our-own-rules explicitly enumerated in this DEVLOG per user request** (see below).

---

## Files created

| File | Purpose |
|------|---------|
| `db/migration/V11__tenant_finance.sql` | 4 tables + `room_assignments.monthly_rent` ALTER + CHECK constraints |
| `domain/tenantfinance/entity/TenantDeposit.java` | HELD deposit + terminal status transitions |
| `domain/tenantfinance/entity/TenantAdvanceRent.java` | Advance rent row with covered window + status |
| `domain/tenantfinance/entity/TenantOpeningBalance.java` | One-shot pre-app position, OWED_BY_TENANT / CREDIT_TO_TENANT |
| `domain/tenantfinance/entity/RentIncrement.java` | Append-only rent-change trail |
| `domain/tenantfinance/dto/*` | 9 request + response DTOs |
| `domain/tenantfinance/repository/*` | 4 Spring Data repos |
| `domain/tenantfinance/service/TenantDepositService.java` | Create + get + update deposit (correction path only) |
| `domain/tenantfinance/service/TenantAdvanceRentService.java` | Create + list + get advance rent |
| `domain/tenantfinance/service/TenantOpeningBalanceService.java` | Create + get (no update — immutable one-shot) |
| `domain/tenantfinance/service/RentIncrementService.java` | Atomic append + update assignment.monthlyRent, T14 |
| `domain/tenantfinance/controller/TenantFinanceController.java` | One controller for the whole domain — 13 endpoints |

### Files modified

| File | Change |
|------|--------|
| `domain/tenancy/entity/RoomAssignment.java` | Added `monthlyRent` field (BigDecimal, nullable in DB, required on new API calls) |
| `domain/tenancy/dto/RoomAssignmentResponse.java` | Exposes `monthlyRent` |
| `domain/tenancy/dto/CreateRoomAssignmentRequest.java` | `monthlyRent` required (`@NotNull` + `@DecimalMin(0.00)`) |
| `domain/tenancy/service/MembershipService.java` | Threads `monthlyRent` from request → entity in `assignRoom()` |

---

## Schema (V11) — key details

- **`room_assignments.monthly_rent NUMERIC(10,2) NULL`** — piggyback ALTER. Nullable so tenancy-pass fixture rows (from the earlier commit) don't fail validation on Hibernate startup. The API-level requirement lives in `CreateRoomAssignmentRequest.@NotNull`.
- **`tenant_deposits`** — `UNIQUE (membership_id)` at the table level, one deposit per tenancy. `chk_deposit_amount_nonneg CHECK (amount >= 0)`. Status enum enforced by CHECK constraint (`HELD | REFUNDED_PARTIAL | REFUNDED_FULL | FORFEITED | APPLIED_TO_BALANCE`).
- **`tenant_advance_rent`** — multiple rows per membership; `chk_advance_amount_positive CHECK (amount > 0)`, `chk_advance_months_positive CHECK (months_covered > 0)`.
- **`tenant_opening_balances`** — `UNIQUE (membership_id)` — one-shot. `direction` CHECK constraint (`OWED_BY_TENANT | CREDIT_TO_TENANT`).
- **`rent_increments`** — append-only, `previous_amount` nullable (for the rare case an assignment had NULL rent from the tenancy pass), `new_amount NUMERIC(10,2) NOT NULL CHECK (new_amount >= 0)`. Three indexes for common query shapes: by membership, by assignment, by `(membership, effective_bs)` — the last serves the Billing engine's segment-boundary reads.

---

## Per-endpoint logic

### Deposits (`/memberships/{mid}/deposit`)

| Endpoint | Rules |
|---|---|
| `POST` | Membership must exist (404) and be ACTIVE (400 if TERMINATED). One deposit per membership → 409 duplicate. Currency defaults to `NPR`. |
| `GET` | 404 if never recorded. |
| `PUT` | Correction path only — `amount`/`receivedAtBs`/`notes`. Status transitions (REFUNDED_*, FORFEITED, APPLIED_TO_BALANCE) belong to Vacancy (Phase 7). Only HELD deposits are correctable — 400 otherwise. |

### Advance rent (`/memberships/{mid}/advance-rent`)

| Endpoint | Rules |
|---|---|
| `POST` | Membership must be ACTIVE. `coveredFromBs <= coveredToBs` → 400. Bean validation: amount > 0, monthsCovered ≥ 1, BS-date format. Multiple rows per membership allowed. |
| `GET /` (list) | Ordered by `coveredFromBs ASC`. |
| `GET /advance-rent/{id}` | Top-level lookup — used by Billing to fetch a specific advance row. |

### Opening balance (`/memberships/{mid}/opening-balance`)

| Endpoint | Rules |
|---|---|
| `POST` | Membership must exist. One-shot: 409 if already recorded. `direction` required (`OWED_BY_TENANT` or `CREDIT_TO_TENANT`). |
| `GET` | 404 if never recorded. |
| **No PUT / DELETE** | Immutable after create by design. Corrections require admin intervention once audit_log lands (Phase 8). |

### Rent increments (`/memberships/{mid}/rent-increments`)

| Endpoint | Rules |
|---|---|
| `POST` | Full atomic transaction: (1) resolve assignment → 404 unknown, (2) verify cross-ownership (`assignment.membership_id == URL mid`) → 400, (3) verify assignment currently active (`effective_to_bs IS NULL`) → 400, (4) verify `effectiveBs >= assignment.effectiveFromBs` → 400 (can't set rent before the assignment started), (5) capture `previousAmount` from `assignment.monthlyRent` — never accepted from the client, (6) append `rent_increments` row, (7) update `room_assignments.monthly_rent`. All in `@Transactional`. Blocked if the membership is TERMINATED (400). |
| `GET /` (list) | Ordered by `effectiveBs DESC`. |
| `GET /rent-increments/{id}` | Top-level lookup, used by Billing for cross-membership segment resolution. |

---

## §14.3 edge cases handled

- **T7 (existing tenant, pre-app history)** — direct-create membership from the tenancy pass, plus opening_balance + advance_rent + deposit here complete the T7 onboarding. Billing will read `tenant_opening_balances` on first bill (spec §10.3).
- **T14 (rent increment)** — atomic apply flow. Historical trail preserved in `rent_increments`; current rent kept on `room_assignments.monthly_rent` for cheap "what is the current rent?" queries.

---

## Explicitly deferred

- **T8** mid-month join proration — Billing engine (Phase 5) reads `tenant_advance_rent` and prorates.
- **T9** shared-charge denominator changes — Billing engine.
- **T15** ownership transfer — `property_ownership_transfers` under PropertyController's future surface.
- **Deposit status transitions** (`REFUNDED_*`, `FORFEITED`, `APPLIED_TO_BALANCE`) — Vacancy (Phase 7).
- **Advance-rent consumption** — Billing engine.
- **Opening-balance application** to first bill — Billing engine.
- **Payment/gateway** — PaymentController (Phase 6). This pass records amounts held, not the flow of money in.
- **Tenant notification on rent increment** — we store `notified_tenant` boolean; the actual push/SMS ships in Phase 8.

---

## Design decisions

1. **Rent lives per-`room_assignment` (Option A).** Sum of an ACTIVE membership's assignments = total rent. T11 (add room) opens a new assignment with its own rent; T12 (partial vacate) ends an assignment, dropping that room's rent. `rent_increments.room_assignment_id` gives per-room historical granularity. Alternative "one rent per membership" fails T11/T12 semantics.
2. **`previous_amount` captured server-side, not client-provided.** History accuracy shouldn't depend on the client sending the right value — the service reads `assignment.monthlyRent` at apply time.
3. **Opening balance is one-shot, no PUT/DELETE.** Corrections require an admin path once `audit_log` (Phase 8) is in place. Landlord attempting to "fix" opening balance repeatedly would corrupt the pre-app position that Billing depends on.
4. **Deposit `PUT` is correction-only, not status transitions.** Vacancy owns the transition semantics (spec §11 / §V10–V11). This pass keeps the API surface narrow so a hypothetical UI can't accidentally jump statuses.
5. **All finance surfaces refuse operations on TERMINATED memberships.** Post-vacancy financial state is Vacancy's to reconcile, not additive.
6. **Rent-increment is atomic (single transaction).** Appending the audit row and mutating the assignment must happen together — a half-applied increment leaves the chain inconsistent.
7. **One combined `TenantFinanceController` instead of four sub-controllers.** All resources are namespaced under `/memberships/{mid}` and share the same authz shape (once auth lands). Splitting into four controllers would triple the boilerplate for no clarity gain.
8. **`monthly_rent` piggyback in V11 (not a separate V12).** The rent-storage decision is inseparable from this pass — `rent_increments` needs somewhere to update. Combining keeps the migration graph coherent.

---

## Our-own rules (not spec-mandated) — enumerated for audit

Per user directive, every rule we added beyond what the spec strictly requires:

| # | Rule | Rationale |
|---|------|-----------|
| 1 | **`room_assignments.monthly_rent`** column added (piggyback in V11) | Spec never says where rent lives; we chose per-assignment. Alternative options B (per-membership) and C (separate table) rejected because they don't support T11/T12 cleanly. |
| 2 | **`monthly_rent` column is nullable in DB, but required in `CreateRoomAssignmentRequest`** | Nullable so tenancy-pass fixture rows survive Hibernate validation. New creates must specify it. |
| 3 | **`tenant_deposits.membership_id UNIQUE`** — one deposit per tenancy | Spec §8.4 mandates per-tenancy (not per-room); we enforce it via UNIQUE at the table level. Beta explicitly rejects per-room deposits. |
| 4 | **`tenant_opening_balances.membership_id UNIQUE`** — one-shot per tenancy | Pre-app position is captured once. Multiple opening balances would ambiguate the first-bill starting position. |
| 5 | **No PUT / DELETE on `/opening-balance`** — immutable after create | Prevents landlord from "adjusting" the pre-app position after billing has begun to use it. Corrections require an admin/audit_log path (Phase 8). |
| 6 | **`PUT /deposit` is correction-only, not status transitions** | Vacancy (Phase 7) owns status transitions. Restricting this endpoint prevents accidental status jumps. |
| 7 | **All finance surfaces refuse operations on TERMINATED memberships** | Once a tenancy is closed, its financial state belongs to Vacancy for reconciliation. Additive writes after termination would corrupt settlement math. |
| 8 | **`RentIncrementService` captures `previousAmount` from the assignment, never from the client** | Ensures historical accuracy independent of client correctness. |
| 9 | **`rent_increments.room_assignment_id` must belong to the URL's membership** — cross-membership refused with 400 | Reference integrity: an assignment logically belongs to one tenancy; a rent change is scoped to that tenancy. |
| 10 | **`rent_increments.effectiveBs >= assignment.effectiveFromBs`** — retroactive-before-assignment-start refused | Rent for a period the tenant didn't yet occupy has no billing meaning. |
| 11 | **Rent increments blocked on ended (`effective_to_bs != NULL`) assignments** | Ended assignments are frozen historical records; changing their rent would rewrite past bills. |
| 12 | **`Advance rent`: coveredFromBs must be ≤ coveredToBs** | The window is a real range; inverting it makes billing consumption nonsensical. |
| 13 | **`Deposit.PUT` only permitted on HELD deposits** | Prevents corrections mid-vacancy transition. |
| 14 | **`Currency` defaults to NPR at DB and DTO layer** | Multi-currency out of scope for beta (spec launch-market rule); enforcing NPR keeps a stray currency from a mis-crafted request from silently landing. |

---

## Open items

- [ ] **Deposit status transitions** (REFUND_PARTIAL, REFUND_FULL, FORFEIT, APPLY_TO_BALANCE) — Vacancy (Phase 7).
- [ ] **Advance-rent consumption** during billing — Billing engine.
- [ ] **Opening-balance application** to first bill — Billing engine.
- [ ] **Admin RBAC** on all endpoints — waits for JWT auth.
- [ ] **Notification of tenant on rent increment** — `notified_tenant` flag stored; actual delivery in Phase 8.
- [ ] **BS-date range validation** — regex enforces `YYYY-MM-DD` shape; day/month-range enforcement waits on the BS-calendar library.

---

## Test coverage

33/33 scenarios pass — see [`TEST_RESULTS.md`](../api-tests/TenantFinanceController/TEST_RESULTS.md). Explicit coverage:

- **Piggyback `monthly_rent`**: required-on-create, negative rejected, second-room shape.
- **Deposit**: create + duplicate 409 + update correction + negative rejected + unknown membership 404.
- **Advance rent**: create + multiple rows + zero-months rejected + inverted window rejected + list + top-level GET.
- **Opening balance**: create + immutable one-shot 409 + missing-direction 400 + GET.
- **Rent increments (T14, the most complex)**: atomic apply verified live (assignment's `monthlyRent` mutates in same txn), unknown assignment 404, cross-membership 400, before-assignment-start 400, negative 400, ended-assignment 400, list, top-level GET.
- **TERMINATED gate**: all three write surfaces (deposit, advance, rent increment) refuse operations on a terminated membership.
