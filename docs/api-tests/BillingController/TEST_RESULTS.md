# BillingController (Phase 5, Pass 1) — Test Results

**Date:** 2026-07-31
**Result:** 35/35 API scenarios PASS + 9/9 BsCalendar unit tests PASS
**Environment:** Spring Boot 4.1 / Java 25, PostgreSQL 16 (`renterp`), Flyway V12 applied, Hibernate `validate` green.

The full runnable script is `billing_test.sh` (idempotent — uses a run-unique phone base); raw output in `RAW_TEST_OUTPUT.txt`; sample JSON bodies in `SAMPLE_RESPONSES.json`; Postman collection `BillingController.postman_collection.json`.

## Unit tests — `BsCalendarTest` (9)

| # | Test | Result |
|---|------|--------|
| U1 | BS 2082 month lengths match authoritative dataset | PASS |
| U2 | every supported year (1970–2090) totals 365/366 | PASS |
| U3 | covers launch operating range (32- and 29-day months) | PASS |
| U4 | parse valid + reject invalid day/month/format | PASS |
| U5 | year outside range fails loudly (never assumes 30) | PASS |
| U6 | inclusiveDays over a whole month = month length | PASS |
| U7 | inclusiveDays mid-month span | PASS |
| U8 | daysBetween across month + year boundaries | PASS |
| U9 | inclusiveDays rejects reversed range | PASS |

## API scenarios (35)

### Tariff versions (6)
| # | Scenario | Expect | Result |
|---|----------|--------|--------|
| 1 | Create tariff version | 201 success | PASS |
| 2 | Get by id | matches | PASS |
| 3 | `effective?asOfBs=` returns in-force version | matches | PASS |
| 4 | List (paginated) | ≥1 | PASS |
| 5 | Duplicate active `effectiveFromBs` | 409 | PASS |
| 6 | Update (not referenced by CONFIRMED run) | 200 | PASS |

### Generate / engine (7)
| # | Scenario | Expect | Result |
|---|----------|--------|--------|
| 7 | Generate MAIN_METER_ONLY run, 3 tenants | DRAFT | PASS |
| 8 | tenantCount = 3 | 3 | PASS |
| 9 | **B9** electricity shares sum == 1000 exactly | 1000.00 | PASS |
| 10 | **T8/proration** mid-month join prorated flag | true | PASS |
| 11 | mid-month join daysOccupied = 16 | 16 | PASS |
| 12 | **B4** due date = generated + grace(7) via BsCalendar | 2082-05-08 | PASS |

### Concurrency & lifecycle (10)
| # | Scenario | Expect | Result |
|---|----------|--------|--------|
| 13 | **B10** idempotent replay returns same run | same id | PASS |
| 14 | **B10** duplicate live run same period | 409 | PASS |
| 15 | **B13** confirm later period while earlier draft exists | 400 | PASS |
| 16 | Confirm oldest (Apr) run | CONFIRMED | PASS |
| 17 | Confirmed run's bills flip to ISSUED | ISSUED | PASS |
| 18 | Confirm later (May) run after Apr confirmed | CONFIRMED | PASS |
| 19 | Re-confirm already-confirmed run | 400 | PASS |
| 20 | Segments recorded (FULL_PERIOD per tenant) | 3 | PASS |
| 21 | **B8** cancel run → run CANCELLED | CANCELLED | PASS |
| 22 | **B8** cancelled run's bills CANCELLED | CANCELLED | PASS |
| 23 | **B8** regenerate same period after cancel | DRAFT | PASS |

### Guardrails — deferred modes fail loudly (4)
| # | Scenario | Expect | Result |
|---|----------|--------|--------|
| 24 | Invalid BS date in request | 400 | PASS |
| 25 | MAIN_METER_ONLY without electricityTotalAmount | 400 | PASS |
| 26 | SUB_METERED property (pass 2) | 400 | PASS |
| 27 | **B11** empty run (no tenants) tenantCount | 0 | PASS |
| 28 | **B11** empty run totalBilled | 0 | PASS |

### Money application — T7/T8/P9 (6)
| # | Scenario | Expect | Result |
|---|----------|--------|--------|
| 29 | **P9** one-time adjustment created PENDING | PENDING | PASS |
| 30 | **T7** opening balance → previousBalance | 500 | PASS |
| 31 | **P9** adjustment applied to bill | 200 | PASS |
| 32 | **T8** advance rent consumed (min(rent, held)) | 3000 | PASS |
| 33 | total_due = 10000+1500+500+200−3000 = 9200 | 9200 | PASS |
| 34 | Adjustment consumed → APPLIED after generation | APPLIED | PASS |

### Cleanup (1)
| # | Scenario | Expect | Result |
|---|----------|--------|--------|
| 35 | Tariff soft-delete | 200 | PASS |

## Notes
- **B9 reconciliation** is asserted inside `BillingMath.split` (shares sum to the split total to the paisa) and again as a commit guard at confirm (Σ bill totals == run total). Both proven live (scenario 9, and the whole-rupee remainder allocation: 1000/3 → 334/333/333).
- **T8 advance** is ledger-based (held − already-applied on non-cancelled bills), so a cancelled run returns advance to the pool automatically — no schema mutation, no double-apply.
- All money is `BigDecimal`; all proration divides by the actual BS-month length from `BsCalendar` (B14), never 30.

---

# Pass 2 — metered engine (2026-08-01)

**Result:** 31/31 pass-2 API scenarios PASS + 5/5 pass-2 math unit tests PASS. Pass-1 re-run 35/35 (3 assertions updated for intended behavior changes). Script: `billing_pass2_test.sh`.

## Pass-2 math unit tests — `BillingPass2MathTest` (5)

| # | Test | Result |
|---|------|--------|
| U1 | applySlabs prices each band at its own rate (open-ended final slab) | PASS |
| U2 | applySlabs three bands (70u → 540) | PASS |
| U3 | T9 mid-join split: A=2300, B=800, reconciles to 3100 | PASS |
| U4 | T9 segment rows: joiner gets MID_MONTH_JOIN, group splits at join | PASS |
| U5 | splitShared weighted 3:1 → 750/250 exact | PASS |

## Pass-2 API scenarios (31)

| Group | Scenarios | Result |
|-------|-----------|--------|
| SUB_METERED FLAT_RATE | run DRAFT; M1 150u×15=2250; M2 60u×15=900 | 3/3 PASS |
| NEA BLENDED_RATE | tariffMode BLENDED; blended 13.5035; NEA total 1350.35; **B9 Σ elec reconciles to 1350.35** | 4/4 PASS |
| Segment engine (T9) | M1=2300, M2=800 (mid-join), Σ=3100; 3 segments; M2 MID_MONTH_JOIN | 5/5 PASS |
| KUKL/boring water | each 500; Σ=1000; missing amount → 400 | 3/3 PASS |
| CUSTOM split | 3:1 → 750/250; missing weights → 400 | 3/3 PASS |
| M17 overage | sub>main+3% with BLOCK → 400 | 1/1 PASS |
| B8 correction (unpaid) | CANCEL_REGENERATE; original CANCELLED; replacement +500 ISSUED; supersedes chain | 4/4 PASS |
| B8 correction (paid/partial) | NEXT_BILL_ADJUSTMENT; original stays ISSUED; BILL_CORRECTION adjustment PENDING | 3/3 PASS |
| B15 async | async=true; progress RUNNING→COMPLETED; 2/2 processed; 2 bills; confirmable after COMPLETED | 5/5 PASS |

## Notes
- **NEA blended reconciliation:** energy 20u@8 + 80u@12 = 1120; +demand 75 = 1195; VAT 13% = 155.35; total 1350.35; blended 13.5035/unit. With `commonUnitsChargedToTenants`, the 20 common units (main 100 − Σsub 80) are billed at blended so Σ tenant electricity = 1350.35 exactly.
- **T9 proof:** 31-day period, total 3100 (daily 100); tenant B joins day 16 → pays only 16 post-join days (800); the 15 pre-join days fall entirely on A (1500) + A's share of the shared post-join days (800) = 2300.
- **B8 paid path** uses a psql `UPDATE` to set `payment_status=PARTIAL` (the Payment phase isn't built yet) so the paid-path branch is exercised end-to-end.
- **B15 async** worker fires ~2s after enqueue via db-scheduler (5 worker threads); the test polls `GET /billing-runs/{id}/progress` until COMPLETED.
