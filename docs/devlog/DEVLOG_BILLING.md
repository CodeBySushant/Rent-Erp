# DEVLOG — BillingController (Phase 5)

**Phase:** 5 — Billing engine (the "switch to Claude Opus" phase per RENT_ERP_CONTEXT.md)
**Date:** 2026-07-31 (pass 1), 2026-08-01 (pass 2)
**Status:** ✅ Pass 1 + Pass 2 complete — 35/35 (pass 1) + 31/31 (pass 2) live PostgreSQL API scenarios + 14/14 unit tests (9 BsCalendar + 5 pass-2 math). See the Pass 2 section at the bottom.
**Scope tables (this pass):** all 7 billing tables landed in `V12__billing.sql` — `tariff_versions`, `billing_runs`, `billing_run_segments`, `billing_run_progress`, `tenant_bills`, `tenant_bill_adjustments`, `bill_corrections`. Pass 1 wires the engine for the **non-metered** subset; pass 2 wires the metered/NEA/segment/async/correction paths.

Also resolves **Open Verification Item #1** ("confirm a maintained BS-calendar library covers 2082+") — see the BS-calendar decision below.

---

## Two-pass cut (confirmed with user)

Billing is the largest domain in the system, so it's split into two commits rather than one:

- **Pass 1 (this commit):** scaffolding + non-metered engine end-to-end — `tariff_versions` CRUD, `billing_runs` create/get/list/confirm/cancel, `tenant_bills` reads, one-time adjustments. Modes handled: `FIXED_PER_TENANT` + `INCLUDED_IN_RENT` electricity, `MAIN_METER_ONLY` (equal + room-weighted), INCLUDED/FIXED water, charge templates, rent, opening balance (T7), advance-rent consumption (T8), rounding + remainder reconciliation (B9), grace-from-generation (B4), idempotency + row-lock + unique index (B10), oldest-first confirm (B13), empty run (B11).
- **Pass 2 (next commit):** `SUB_METERED` electricity, NEA `BLENDED_RATE` (tariff slabs → `billing_runs.nea_*`), KUKL/boring water, mid-period shared-denominator segment engine (T9, M9 coverage-as-of-date), overage threshold (M17), async db-scheduler workers + progress polling (B15), `bill_corrections` POST both paths (B8), penalty accrual, B7 prorate-this-cycle deactivated-charge handling, CUSTOM split.

Deferred modes are **rejected loudly** (400 `InvalidOperationException`) in pass 1 rather than silently mis-billed — see guardrail scenarios 24–26.

---

## BS calendar decision (Open Verification Item #1 — RESOLVED)

The spec (B14 / §18.2) mandates "all proration from a maintained calendar library, never hardcode 30" — BS months run 29–32 days. Investigation found **no maintained Java BS library on Maven Central**: the candidates (`medic/bikram-sambat`, `nepali-bhasa/nepali-date-conversion`, `bahadurbaniya/...`) publish only to GitHub Packages or private staging repos, which would force auth tokens into the build — a non-starter for a reproducible build.

**Decision (chosen by user: "wire a BS lib now"):** embed the canonical month-length table directly in `common/util/BsCalendar.java`. The dataset is `medic/bikram-sambat`'s tested `daysInMonth.json` (a widely-deployed dataset used in production health systems), covering **BS 1970–2090** — well past the 2082+ launch need. This satisfies B14's real intent (authoritative per-month day counts, never 30) with zero external dependency or auth, and is fully reproducible.

- **Pure-BS arithmetic only** — proration needs day counts and BS-date diffs, both derivable from the month-length table alone. No AD conversion is implemented (that's a display/scheduling concern for later).
- `BsCalendar` throws `InvalidOperationException` for any year outside [1970, 2090] — it **fails loudly** rather than assuming 30, which is the whole point of B14.
- **Maintenance checkpoint** documented in the class javadoc: the table ends at BS 2090 (≈2033 AD); extend before then from an updated authoritative source.
- Verified by `BsCalendarTest` (9 tests): 2082 month lengths match the dataset, every year totals 365/366, parse/validation, mid-month and cross-boundary diffs.

---

## Files created

| File | Purpose |
|------|---------|
| `common/util/BsCalendar.java` | Embedded BS month-length table (1970–2090) + `daysInMonth`, `parse`, `isValid`, `daysBetween`, `inclusiveDays`, `addDays` |
| `db/migration/V12__billing.sql` | 7 billing tables, CHECK constraints, partial unique indexes (idempotency, one-live-run-per-period, one-live-bill-per-membership-per-run) |
| `domain/billing/entity/TariffVersion.java` | NEA tariff schedule + JSONB `slabs` |
| `domain/billing/entity/BillingRun.java` | Run header, DRAFT→CONFIRMED/CANCELLED, NEA reconciliation fields (pass 2) |
| `domain/billing/entity/BillingRunSegment.java` | Per-membership sub-period (FULL_PERIOD in pass 1) |
| `domain/billing/entity/TenantBill.java` | Immutable per-tenant bill + JSONB `line_items` snapshot |
| `domain/billing/entity/TenantBillAdjustment.java` | Carry-forward charge/credit (P9/B8) |
| `domain/billing/dto/*` | 11 DTOs incl. `BillLineItem` (JSONB element) |
| `domain/billing/repository/*` | 5 Spring Data repos |
| `domain/billing/service/BillingMath.java` | Money split + rounding + **B9 remainder reconciliation** |
| `domain/billing/service/TariffVersionService.java` | Tariff CRUD + edit/delete guard vs CONFIRMED runs (B5) |
| `domain/billing/service/BillingRunService.java` | **The engine** — generate / confirm / cancel / reads |
| `domain/billing/service/TenantBillService.java` | Bill reads (by id / by run / by membership) |
| `domain/billing/service/BillAdjustmentService.java` | One-time adjustments (P9) |
| `domain/billing/controller/TariffController.java` | `/api/v1/tariffs` |
| `domain/billing/controller/BillingRunController.java` | `/api/v1/properties/{id}/billing-runs`, `/api/v1/billing-runs/{id}/...` |
| `domain/billing/controller/TenantBillController.java` | `/api/v1/tenant-bills/{id}`, `/api/v1/memberships/{mid}/bills`, `.../adjustments` |
| `docs/api-tests/BillingController/*` | test script, results, sample responses, Postman collection |
| `src/test/java/com/renterp/common/util/BsCalendarTest.java` | 9 unit tests for the BS table |

### Files modified

| File | Change |
|------|--------|
| `domain/property/repository/PropertyRepository.java` | Added `findByIdForUpdate` (PESSIMISTIC_WRITE) — B10 concurrency layer 2 |
| `domain/tenancy/repository/RoomAssignmentRepository.java` | Added `findByMembershipIdAndEffectiveToBsIsNull` (active assignments for rent + room weight) |
| `domain/tenancy/repository/TenantPropertyMembershipRepository.java` | Added non-paged `findByPropertyIdAndStatus` (engine iterates active memberships) |
| `domain/charge/repository/ChargeTemplateRepository.java` | Added `findByPropertyIdAndActiveTrue` (engine bills active charges) |

---

## Schema (V12) — key details

- **Money scales:** `NUMERIC(10,2)` for per-tenant amounts, `NUMERIC(12,2)` for run totals (a large property's total can exceed one tenant's ceiling), `NUMERIC(10,4)` for `nea_blended_rate`. BS dates `VARCHAR(20)`. JSONB (`slabs`, `line_items`, segment `details`) is display/audit snapshot only, never filtered on.
- **`billing_runs`** — 3 of B10's 4 concurrency layers live in the schema/service:
  - `uq_billing_run_idempotency (property_id, idempotency_key) WHERE key IS NOT NULL` — layer 1.
  - `PropertyRepository.findByIdForUpdate` PESSIMISTIC_WRITE on the property row — layer 2.
  - `uq_billing_run_property_period_live (property_id, billing_month_bs) WHERE status <> 'CANCELLED'` — layer 3. A cancelled run frees the slot so a period can be regenerated (B8).
  - (Layer 4, the UI-disable, is the frontend's.)
- **`tenant_bills`** — `uq_tenant_bill_run_membership_live (billing_run_id, membership_id) WHERE status <> 'CANCELLED'`. `chk_bill_days CHECK (days_occupied > 0 AND days_in_period > 0 AND days_occupied <= days_in_period)`. Correction chain via `supersedes_bill_id` / `superseded_by_bill_id` self-FKs.
- **`tenant_bill_adjustments`** — partial index `WHERE status = 'PENDING'` for the engine's hot lookup; `chk_adjustment_amount CHECK (amount > 0)` (direction carried by `adjustment_type`).
- **`billing_run_progress`** / **`bill_corrections`** — tables created now (stable FKs), write paths land in pass 2. Left **unmapped by JPA** intentionally, which `ddl-auto=validate` tolerates (it validates mapped entities only).

---

## The engine (`BillingRunService.generate`) — how it works

1. Validate all BS dates (`BsCalendar.parse` → 400 on malformed/out-of-range); reject `periodEnd < periodStart`.
2. `findByIdForUpdate(propertyId)` — pessimistic lock (B10 layer 2), serialises concurrent generates per property.
3. Idempotent replay (B10 layer 1): key hit → return existing run, no new insert.
4. One-live-run-per-period guard (B10 layer 3) → 409 if a non-cancelled run for the period exists.
5. Mode guardrails: SUB_METERED / BLENDED_RATE / KUKL/boring / CUSTOM split → 400 (pass 2).
6. Persist the DRAFT run (unique index fires here).
7. Build the **billable set**: active memberships whose occupancy window overlaps the period. Per membership: occupancy days (`BsCalendar.inclusiveDays`), rent = Σ active assignments' `monthly_rent`, room weight = active assignment count.
8. **B11 empty run** — no billable members → persist a 0-tenant run and return (no timeline gap).
9. **Shared electricity** (MAIN_METER_ONLY): `BillingMath.split(total, weights, method, remainderTo)` — weights are all-ones (EQUAL) or room counts (ROOM_WEIGHTED). **Shared charges** (SHARED split_basis): equal split. Both reconcile to the paisa (B9).
10. Per bill: rent (prorated by occupancy), electricity, water, charges, previous balance (T7 first-bill opening balance), one-time adjustments (P9, consumed here), TDS deduction, advance-rent credit (T8), rounding delta. `total_due = subtotal − tds + previousBalance + adjustments − advance + roundingDelta`. Due date = `addDays(generatedAt, gracePeriodDays)` (B4). Line items captured in the JSONB snapshot.
11. Persist bill (DRAFT), consume its adjustments (→ APPLIED, `applied_bill_id`), write a FULL_PERIOD segment.
12. Set `run.tenantCount` + `run.totalBilled = Σ bill totals`.

**Confirm:** blocks if an earlier-period draft exists (B13), asserts Σ non-cancelled bill totals == run total (B9 commit guard), flips run→CONFIRMED and bills→ISSUED.

**Cancel:** flips run + bills→CANCELLED and **releases** consumed adjustments back to PENDING (so a regenerate re-applies them). Supports the B8 cancel+regenerate path (a cancelled period slot is free to regenerate).

---

## Edge cases handled (spec §14.2 Billing + cross-refs)

| Case | How |
|------|-----|
| **B4** | Grace period counts from `generated_at_bs` (snapshotted on each bill), `due_date_bs = addDays(generatedAt, grace)`. |
| **B5** | Amounts are snapshotted at run time; changing a charge/tariff later never reprices a CONFIRMED bill. Tariff edit/delete blocked once referenced by a CONFIRMED run. |
| **B9** | `BillingMath.split` allocates the rounding remainder per `rounding_remainder_to` so shares sum to the split total to the paisa; hard assertion inside `split` **and** a Σ-bills==run-total guard at confirm. |
| **B10** | 4 layers — idempotency key, pessimistic row lock, unique DB index, (UI). |
| **B11** | Empty run recorded with 0 tenants / 0 total, no bills. |
| **B13** | Confirm refuses while an earlier-period draft exists; multiple drafts for different periods coexist. |
| **B14** | All proration via `BsCalendar` actual month lengths, never 30. |
| **T7** | Opening balance applied as `previous_balance` on the first (only) bill of a membership. |
| **T8** | Advance rent consumed against rent, ledger-based (held − already-applied on non-cancelled bills). |
| **P9** | One-time charge/credit follows the tenant onto the next bill (and will flow to the vacancy bill in Phase 7). |
| **B8** (partial) | Cancel+regenerate path proven; the paid/partial next-bill-adjustment POST is pass 2. |

---

## Our-own rules (rules added beyond spec-strict requirements — enumerated per standing SOP)

| # | Rule | Rationale |
|---|------|-----------|
| 1 | **Embedded BS month-length table** rather than a Maven dependency | No maintained Java BS lib exists on Maven Central without auth-gated repos; the dataset is public/finite. Satisfies B14's intent with a reproducible build. (See BS-calendar decision.) |
| 2 | **Covered window supplied explicitly** in the request (`periodStartBs`/`periodEndBs`) rather than derived from `billing_day` | Billing-day-window derivation is coupled to the segment engine; deferred to pass 2. Explicit window keeps pass-1 proration correct and testable. |
| 3 | **Deferred modes rejected with 400**, not silently zeroed | A property misconfigured for a pass-2 mode must fail loudly, never emit a wrong (e.g. zero-electricity) bill. |
| 4 | **`tariff_versions` is national/global**, not property-scoped | Spec models NEA slabs as one national schedule; the property only picks FLAT_RATE vs BLENDED_RATE. Unique active `effective_from_bs` keeps "the tariff as of date X" unambiguous. |
| 5 | **Tariff edit/delete blocked once a CONFIRMED run references it** | Enforces B5 (no retroactive repricing) at the schema-of-record level. |
| 6 | **Advance-rent consumption is ledger-based** (held − Σ applied on non-cancelled bills), not a status flip | Idempotent, reversible on cancel, no schema mutation, no double-apply. The advance row's HELD→CONSUMED display flip is deferred to pass 2. |
| 7 | **Adjustments consumed at generation, released on cancel** | Treats a DRAFT run as a real (if uncommitted) claim on the adjustment, so totals are stable between generate and confirm; cancel returns them to PENDING for the regenerate. |
| 8 | **SHARED charge templates split equally** (not by property split rule) | Charge templates have no per-charge split-rule column; equal is the conventional default. Room-weighted charge splits can be added when a column exists. |
| 9 | **Opening balance applied only on the first non-cancelled bill** of a membership | T7 pre-app position belongs on the first bill; subsequent bills must not re-apply it. First-bill detection = zero prior non-cancelled bills. |
| 10 | **Penalty = 0 in pass 1** | Penalty accrual needs paid/overdue state, which is the Payment phase's ledger; computing it at first generation (before grace even starts) would be wrong. |
| 11 | **Mid-period proration is per-tenant only** in pass 1 (individual amounts prorated; shared denominators are NOT re-segmented) | True shared-denominator re-segmentation on mid-period join/exit is T9/M9 — the pass-2 segment engine. Pass 1 still prorates a joiner's own rent/fixed charges correctly. |
| 12 | **Confirm re-asserts reconciliation** (Σ bills == run total) as a commit guard | Defence in depth on top of the per-split B9 assertion. |

---

## Open items / deferred to pass 2 (and beyond)

- SUB_METERED electricity (reads `meter_reading_log` consumption per segment).
- NEA `BLENDED_RATE` — slabs → blended rate → `billing_runs.nea_*` reconciliation fields.
- Mid-period shared-denominator **segment engine** (T9), coverage-as-of-billing-date (M9).
- KUKL/boring water modes; overage threshold action (M17).
- Async db-scheduler workers + `billing_run_progress` polling (B15).
- `bill_corrections` POST — both B8 paths (unpaid cancel+regenerate record; paid/partial → adjustment).
- Penalty accrual (Payment phase state), B7 prorate-this-cycle deactivated-charge handling, CUSTOM split.
- **RBAC/auth** — still none anywhere (deferred until JWT auth lands; repeated across every controller's DEVLOG).
- Advance-rent HELD→CONSUMED status flip (display convenience).
- Second-half of §6.5 gate (require INITIAL reading on SUB_METERED switch) — now unblockable, small follow-up (`readingRepository.existsInitialForMeter`).

---

# PASS 2 — metered engine, NEA blended rate, segments, async, corrections (2026-08-01)

**Status:** ✅ 31/31 live PostgreSQL API scenarios + 5/5 pass-2 math unit tests. Pass-1's 35/35 re-run green as regression (3 assertions updated for intended pass-2 behavior changes — see below). No new migration: V12 already provisioned every pass-2 column/table.

## What landed

| Area | How |
|------|-----|
| **SUB_METERED electricity** | `ElectricityEngine.computeSubMetered` — per-meter units = Σ confirmed consumption in the half-open window `(periodStartBs, periodEndBs]` (opening reading excluded, closing included; per-reading deltas already handle rollover M1). Payer resolved via coverage-as-of-period-end (M9) → room → billable membership. Vacant/landlord-covered rooms counted for overage but billed to nobody (M14). |
| **NEA BLENDED_RATE** | Main-meter units → tariff slabs (`applySlabs`) → energy cost + demand + service, floored at minimum charge, + VAT → NEA total bill; blended rate = total ÷ units (NUMERIC 10,4). All sub-meters billed at the blended rate; the full `nea_*` snapshot is stored on the run. With `commonUnitsChargedToTenants`, the common units (main − Σsub) are allocated to tenants at the blended rate so Σ(billed electricity) reconciles **exactly** to the NEA bill (verified: 1350.35). |
| **Segment engine (T9/M9)** | `SegmentEngine.splitShared` spreads a shared monthly total evenly across the period's days and splits each day among the tenants present, weighted by split rule — a day-granular denominator. A mid-period joiner therefore pays nothing for pre-join days and pre-join days fall entirely on the earlier group. Reconciles to the paisa. `buildSegments` writes real per-membership `billing_run_segments` rows (MID_MONTH_JOIN / MID_MONTH_EXIT / FULL_PERIOD) split at every denominator change. Applied to MAIN_METER_ONLY electricity, SHARED charges, and KUKL/boring water. |
| **KUKL / boring water** | `KUKL_SPLIT`, `BORING_PUMP_ONLY`, `KUKL_AND_BORING` — variable monthly amounts (`waterKuklAmount` / `waterBoringAmount` in the request) split segment-aware across tenants. |
| **CUSTOM split** | `defaultSplitRule = CUSTOM` reads per-membership weights from `customWeights` in the request; missing a weight for any billable membership → 400. |
| **M17 overage** | Σ sub-meter units vs main × (1 + `overageThresholdPercent`). Over-threshold always logs (notify); `overageAction = BLOCK` → 400; NOTIFY_* proceeds. `commonUnits` floored at 0. |
| **B8 corrections** | `POST /tenant-bills/{id}/correct`. Unpaid → CANCEL_REGENERATE: original cancelled (first, to free the one-live-bill slot), replacement issued with the corrected total, supersedes-chain linked, run total adjusted. Paid/partial → NEXT_BILL_ADJUSTMENT: a PENDING `tenant_bill_adjustments` row (CHARGE/CREDIT) the engine carries onto the next bill. Every correction writes a `bill_corrections` audit row. |
| **B15 async** | Runs over `ASYNC_TENANT_THRESHOLD` (25) tenants — or `"async": true` — persist DRAFT + a RUNNING `billing_run_progress` row and hand off to a db-scheduler one-time task (`BillingAsyncService` / `BillingSchedulerConfig`, explicit `Scheduler` bean, 5 worker threads). The worker builds bills off-thread, incrementing progress; `GET /billing-runs/{id}/progress` is the poll endpoint. Confirm is blocked until progress is COMPLETED. On failure the progress flips FAILED (separate tx) leaving the run DRAFT to cancel/regenerate. |

## Correctness fix carried from pass 1

**B9 remainder double-count (fixed).** Pass 1 added the split's rounding-remainder delta to `total_due` **on top of** the electricity/water/charge component, which was already the final reconciled share — so a shared-cost tenant's total was overstated by the remainder. It escaped pass-1 tests because the only `total_due` assertion used a single FIXED_PER_TENANT tenant (no shared split) and the multi-tenant test asserted only Σ electricity. Pass 2 removes the separate `roundingAdjustment` term: component amounts are the final shares, `total_due = subtotal − tds + previousBalance + adjustments − advance`. `rounding_adjustment` column now stores 0 (the remainder is baked into the component share).

## Our-own rules (pass 2 additions to the pass-1 table)

| # | Rule | Rationale |
|---|------|-----------|
| 13 | **Variable amounts (sub-metered flat rate, KUKL, boring) come from the request**, not a property column | No such columns exist on `properties`; consistent with pass-1's `electricityTotalAmount` pattern. The landlord enters the period's actual figures. |
| 14 | **Consumption window is half-open `(periodStart, periodEnd]`** | Excludes the opening reading (its consumption is the prior period's) and includes the closing reading. Unambiguous, and matches how a closing BILLING_RUN reading is dated at period end. |
| 15 | **Blended common-unit allocation only when `commonUnitsChargedToTenants`** | With the flag on, common units are billed to tenants at the blended rate so Σ reconciles to the full NEA bill; off, the landlord absorbs common-area consumption (correct — common areas are the landlord's). |
| 16 | **Penalty stays 0 (deferred to Payment phase)** | Confirmed with the user for pass 2. Penalty requires real paid/overdue state; with no payments recorded every bill looks unpaid, so accruing now would over-penalize. Lands in Phase 6. |
| 17 | **B7 realized via adjustments, not billed from inactive charges** | `ChargeTemplate` has `deactivationMode` but no deactivation-date, so the engine can't scope "this cycle" across runs. A `THIS_CYCLE_PRORATED` deactivation should emit a P9 one-time adjustment at deactivation time (Charge domain), which the engine already consumes. The engine bills active charges + consumes adjustments — it never bills inactive charges (which would bill forever). |
| 18 | **Async threshold = 25 tenants; overridable per request** | A sensible default so small runs stay synchronous (simpler, immediate) and large ones don't block the request thread (B15). `"async": true/false` overrides for testing/explicit choice. |
| 19 | **Explicit `Scheduler` bean (fallback)** | The db-scheduler starter did not auto-create a `Scheduler`/`SchedulerClient` in this app; `BillingSchedulerConfig` defines one (`@ConditionalOnMissingBean`) and starts it, guaranteeing the async path has a client. |

## Pass-2 edge cases handled

| Case | How |
|------|-----|
| **B3** | Sub-meter with no confirmed reading in the window → that tenant's electricity marked "reading pending", not blocked; other tenants still billed. |
| **B8** | Both paths — unpaid cancel+regenerate (supersedes chain) and paid/partial next-bill adjustment. |
| **B15** | Async db-scheduler workers + `billing_run_progress` polling; confirm gated on COMPLETED. |
| **M9** | Coverage-as-of-period-end (`MeterRoomCoverageRepository.findActiveAsOf`) resolves which rooms a meter served for the historical period. |
| **M14** | A sub-meter's vacant/landlord rooms are never billed to a tenant (counted only for overage). |
| **M17** | Overage threshold + action (notify always; BLOCK → 400); common units floored at 0. |
| **T9** | Segment engine — shared-cost denominator changes on join/exit, reconciles exactly. |
| **CUSTOM** | Per-membership request weights. |

## Regression: 3 pass-1 assertions updated (intended behavior changes, not fixes)

1. **Segments count 3 → 5** — the T9 engine now splits the mid-month-join scenario at the denominator change (was 3 flat FULL_PERIOD rows).
2. **SUB_METERED generate 400 → 400 (re-scoped)** — SUB_METERED is now implemented, so an *empty* property short-circuits to a B11 empty run (201); the assertion now uses a property **with a tenant but no sub-meter**, which correctly 400s on the "requires a TENANT_SUPPLY meter" guard.
3. **Tariff-effective as-of date** — moved from `2082-06-01` to `2082-01-03` to isolate pass-1's own tariff from the national/global tariffs the pass-2 suite adds (`tariff_versions` is not property-scoped).

## Still deferred (beyond pass 2)

- **Penalty accrual** — Phase 6 (Payment), owns paid/overdue state.
- **Mid-month departure rate mode `DEFERRED`** — `MidMonthDepartureRateMode.PREVIOUS_MONTH` is the effective behavior; the deferred-to-billing-day exact-rate variant for blended departures is a follow-up.
- **B7 proration adjustment emission** — the Charge domain should create the one-time proration adjustment on `THIS_CYCLE_PRORATED` deactivation (billing already consumes it).
- **§6.5 second-half gate** — require an INITIAL reading when switching to SUB_METERED (`readingRepository.existsInitialForMeter`) — small Meter-domain follow-up.
- **RBAC/auth** — still none anywhere (JWT phase).
