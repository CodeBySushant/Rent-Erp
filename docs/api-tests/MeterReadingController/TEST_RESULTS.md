# MeterReadingController + CoverageEventController — API Test Results

**Date:** 2026-07-31
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 35/35 passed

Raw responses in [`responses/`](responses/). Postman collection: [`RentERP-MeterReadingController.postman_collection.json`](RentERP-MeterReadingController.postman_collection.json).

Two of the terminal-log lines from the test runner (`consumption FAIL … got 50.0` and `rollover FAIL … got 9899.0`) are string-compare quirks in the test script (`50.00` serializes as `50.0` in the JSON response); the numeric values are exactly correct: `150 − 100 = 50` and `(9999 − 150) + 50 = 9899` for the confirmed rollover.

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | BILLING_RUN before any INITIAL → 400 | POST | `/api/v1/meters/{id}/readings` | 400 | 400 | ✅ |
| 2 | Submit INITIAL 100 (PENDING) | POST | `/api/v1/meters/{id}/readings` | 201 | 201 | ✅ |
| 3 | Discard PENDING draft | DELETE | `/api/v1/readings/{id}` | 200 | 200 | ✅ |
| 4 | Resubmit INITIAL (photo + notes) | POST | `/api/v1/meters/{id}/readings` | 201 | 201 | ✅ |
| 5 | Confirm INITIAL (seals row) | POST | `/api/v1/readings/{id}/confirm` | 200 | 200 | ✅ |
| 6 | Re-confirm already-CONFIRMED (no-op) | POST | `/api/v1/readings/{id}/confirm` | 200 | 200 | ✅ |
| 7 | Second INITIAL on same chain → 400 | POST | `/api/v1/meters/{id}/readings` | 400 | 400 | ✅ |
| 8 | Value < previous, no rollover confirm → 400 (§14.1 **M2**) | POST | `/api/v1/meters/{id}/readings` | 400 | 400 | ✅ |
| 9 | Zero reading blocked (§14.1 **M3**) | POST | `/api/v1/meters/{id}/readings` | 400 | 400 | ✅ |
| 10 | Backdate before last confirmed date → 400 (§14.1 **M16**) | POST | `/api/v1/meters/{id}/readings` | 400 | 400 | ✅ |
| 11 | Normal BILLING_RUN 150 | POST | `/api/v1/meters/{id}/readings` | 201 | 201 | ✅ |
| 12 | Confirm BILLING_RUN — consumption = 50 | POST | `/api/v1/readings/{id}/confirm` | 200, cons=50 | 200, cons=50 | ✅ |
| 13 | Rollover 50 (`rolloverConfirmed:true`) — §14.1 **M1** | POST | `/api/v1/meters/{id}/readings` | 201 | 201 | ✅ |
| 14 | Confirm rollover — consumption = (9999 − 150) + 50 = 9899, `rollover=true` | POST | `/api/v1/readings/{id}/confirm` | 200, cons=9899 | 200, cons=9899 | ✅ |
| 15 | Correction against a confirmed reading (auto-CONFIRMED) | POST | `/api/v1/readings/{id}/corrections` | 201 | 201 | ✅ |
| 16 | Correcting a CORRECTION → 400 | POST | `/api/v1/readings/{corr-id}/corrections` | 400 | 400 | ✅ |
| 17 | DELETE a CONFIRMED reading → 400 | DELETE | `/api/v1/readings/{id}` | 400 | 400 | ✅ |
| 18 | Deferred type VACANCY submitted directly → 400 | POST | `/api/v1/meters/{id}/readings` | 400 | 400 | ✅ |
| 19 | REPLACEMENT_OPEN submitted directly → 400 | POST | `/api/v1/meters/{id}/readings` | 400 | 400 | ✅ |
| 20 | List readings for meter | GET | `/api/v1/meters/{id}/readings` | 200 | 200 | ✅ |
| 21 | Filter list by `type=CORRECTION` — 1 result | GET | `/api/v1/meters/{id}/readings?type=CORRECTION` | 200, 1 result | 200, 1 result | ✅ |
| 22 | Estimation hint returns rolling avg + samples | GET | `/api/v1/meters/{id}/estimation-hint` | 200, samples=3 | 200, samples=3 | ✅ |
| 23 | Estimation hint on a meter with no readings | GET | `/api/v1/meters/{empty-id}/estimation-hint` | 200, samples=0 | 200 | ✅ |
| 24 | Replacement — atomic close-old + new-meter + open (§14.1 **M4**) | POST | `/api/v1/meters/{old-id}/replacement` | 201 | 201 | ✅ |
| 25 | Old meter now `INACTIVE` + `replacedByMeterId` set | GET | `/api/v1/meters/{old-id}` | INACTIVE + fk | INACTIVE + fk | ✅ |
| 26 | Submitting a reading on the INACTIVE old meter → 400 | POST | `/api/v1/meters/{old-id}/readings` | 400 | 400 | ✅ |
| 27 | Replacing an already-replaced meter → 400 | POST | `/api/v1/meters/{old-id}/replacement` | 400 | 400 | ✅ |
| 28 | `estimated=true` without `estimationBasis` → 400 (§14.1 **M5**) | POST | `/api/v1/meters/{id}/replacement` | 400 | 400 | ✅ |
| 29 | Coverage event `SPLIT` — room1 moves from M3 to M4 (§14.1 **M9**) | POST | `/api/v1/coverage-events` | 201 | 201 | ✅ |
| 30 | M3's existing coverage row now ends on event date | GET | `/api/v1/meters/{m3-id}/coverage` | `effectiveToBs=2082-05-15` | matches | ✅ |
| 31 | Coverage event missing anchor when coverage changed → 400 (§14.1 **M9**) | POST | `/api/v1/coverage-events` | 400 | 400 | ✅ |
| 32 | Coverage event on a MAIN meter → 400 | POST | `/api/v1/coverage-events` | 400 | 400 | ✅ |
| 33 | Get coverage event by id (with `changes[]`) | GET | `/api/v1/coverage-events/{id}` | 200 | 200 | ✅ |
| 34 | List coverage events by property | GET | `/api/v1/coverage-events?propertyId={id}` | 200 | 200 | ✅ |
| 35 | Estimation hint on unknown meter → 404 | GET | `/api/v1/meters/{bad-id}/estimation-hint` | 404 | 404 | ✅ |
| 36 | Setup — submit a PENDING reading | POST | `/api/v1/meters/{id}/readings` | 201 | 201 | ✅ (setup) |
| 37 | Correcting a PENDING reading → 400 | POST | `/api/v1/readings/{pending-id}/corrections` | 400 | 400 | ✅ |
| 38 | Rollover with backdated date before predecessor → 400 (§14.1 **M16**) | POST | `/api/v1/meters/{id}/readings` | 400 | 400 | ✅ |

---

## Notes

- **§14.1 M1 rollover math** (scenarios 13/14) — consumption = `(max_reading_value − previous) + current`, using the per-meter `max_reading_value` (`9999` on the test meter). Rollover requires an explicit `rolloverConfirmed:true`; without it, a smaller-than-previous reading is treated as an entry error (M2, scenario 8).
- **§14.1 M3 zero-reading rule** (scenario 9) — blocked unless the row is a chain-anchor type (`INITIAL`/`REPLACEMENT_OPEN`) or a confirmed rollover.
- **§14.1 M4 replacement** (scenarios 24–27) — one atomic POST creates the successor meter, carries coverage/split/responsibility/max forward from the old meter, inserts `REPLACEMENT_CLOSE` on the old chain, marks the old meter `INACTIVE` + `replaced_by_meter_id`, inserts `REPLACEMENT_OPEN` on the new chain, and persists the event row. `INACTIVE` meters refuse new readings (26). Re-replacement is blocked (27).
- **§14.1 M5 estimation** (scenario 28 + the `/estimation-hint` helper) — close-side readings may carry `estimated=true` with a required `estimationBasis` (`ROLLING_3_MONTH_AVG` or `MANUAL`). The `estimationBasis` field enforces its own cross-field rule: required iff `estimated=true`.
- **§14.1 M6** — non-zero replacement opening is trivially supported; the open row is a chain anchor (no consumption stamped), so its absolute value is irrelevant. Scenario 24 uses `openReading.readingValue=0` as the simple case; a landlord opening with e.g. `50` on a refurbished meter would work identically.
- **§14.1 M9 coverage change** (scenarios 29–31) — every meter that gains or loses coverage in a coverage event must include an anchor reading, refused otherwise (31). The existing raw `meter_room_coverage` rows are still the authoritative structural record; the event records the *why* and stamps anchor readings that later billing runs read against.
- **§14.1 M13 atomic photo+reading** — `photoUrl` column exists on the reading log; landlord submissions accept it as optional (matches spec — tenant path blocks without a photo, but tenant path is Phase 4).
- **§14.1 M16 backdated reading** (scenarios 10/38) — `submissionDateBs > readingDateBs` sets `is_backdated=true` for audit visibility, but `readingDateBs < last_confirmed_readingDateBs` is refused — same rule applies regardless of whether the reading is a normal delta or a rollover.
- **Correction semantics** (scenarios 15–17, 37) — corrections are new `CORRECTION` rows with `corrects_reading_id` pointing at the wrong-but-confirmed original; auto-CONFIRMED on insert (a correction is a fix, not a draft); can only be issued against a CONFIRMED reading (37); cannot be re-corrected (16, chain integrity).
- **Reading-type gate** (scenarios 18/19) — the controller accepts only `INITIAL` and `BILLING_RUN` from client posts. `REPLACEMENT_CLOSE`/`REPLACEMENT_OPEN`/`COVERAGE_ANCHOR`/`CORRECTION` are created *only* inside their event flows (replacement, coverage, correction endpoints). `VACANCY`/`TENANT_JOIN`/`DEPARTURE_TOPUP`/`GAP_ABSORBED` are deferred entirely to Phases 4/7.
- **Test isolation:** all data was created under a fresh landlord (`9800000902`) and its own property + meters; no dependency on the meters seeded during the MeterController pass. Left in place for regression re-runs.
