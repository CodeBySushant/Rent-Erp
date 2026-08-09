# DEVLOG — MeterReadingController (+ CoverageEventController)

**Phase:** 3 — Meters
**Date:** 2026-07-31
**Status:** ✅ Complete — 35/35 live PostgreSQL scenarios passed
**Scope tables (this pass):** `meter_reading_log`, `meter_replacement_events`, `meter_coverage_events`, `meter_coverage_event_changes`; adds `meters.max_reading_value` column
**Scope tables deferred:** none — the last four Phase 3 tables land in this pass

---

## Scope confirmed with user (Cut A)

- Two-state lifecycle: `PENDING` → `CONFIRMED`. Once confirmed, the row is immutable (§7.5). Corrections are new `CORRECTION` rows pointing back at the wrong-but-confirmed original via `corrects_reading_id`.
- `meters.max_reading_value` column added (default 99999) — rollover math (§14.1 M1) needs a per-meter cap; storing it beats fiddling per-request.
- Replacement is one atomic endpoint (POST /meters/{id}/replacement) — never "create new meter, then submit two readings".
- Coverage events sit at top-level `/coverage-events` because SPLIT/MERGE touches ≥2 meters.
- Estimation helper (`GET /meters/{id}/estimation-hint`) included this pass; the actual "apply the estimate" step lives inside the replacement endpoint (`estimated=true` + `estimationBasis`).

---

## Files created

| File | Purpose |
|------|---------|
| `db/migration/V9__meter_readings.sql` | Adds `meters.max_reading_value`; creates the four reading/event tables + indexes + CHECK constraints |
| `domain/meterreading/entity/MeterReading.java` | Append-only chain row (10 reading types × PENDING/CONFIRMED × rollover/estimated/backdated/gap flags) |
| `domain/meterreading/entity/MeterReplacementEvent.java` | One row per replacement (§14.1 M4) |
| `domain/meterreading/entity/MeterCoverageEvent.java` | Header for a structural coverage change |
| `domain/meterreading/entity/MeterCoverageEventChange.java` | Per-meter delta belonging to a coverage event (rooms added/removed + anchor reading), rooms stored as JSONB via Hibernate 6's `@JdbcTypeCode(SqlTypes.JSON)` |
| `domain/meterreading/dto/*` | Request + response DTOs — SubmitReading, Correction, CreateReplacement, CreateCoverageEvent, MeterReading/Replacement/CoverageEvent responses, EstimationHint |
| `domain/meterreading/repository/*` | Spring Data repos + `findLatestConfirmed` and `findRecentConsumption` chain helpers |
| `domain/meterreading/service/MeterReadingService.java` | Reading log logic — submit/confirm/discard/correct + M1/M2/M3/M16 rules + estimation helper. Exposes `insertConfirmedInternal` for the event services. |
| `domain/meterreading/service/MeterReplacementService.java` | Atomic §14.1 M4 flow — new-meter creation + coverage carry-forward + close/open readings + event row |
| `domain/meterreading/service/CoverageEventService.java` | §14.1 M9/M10 event flow — atomic multi-meter coverage change + anchor readings + per-meter change rows |
| `domain/meterreading/controller/MeterReadingController.java` | 8 endpoints: submit / list / get / confirm / discard / correct / estimation-hint / replacement + replacement-events list |
| `domain/meterreading/controller/CoverageEventController.java` | Top-level 3 endpoints: POST / GET by id / list by property |

### Files modified

| File | Change |
|------|--------|
| `domain/meter/entity/Meter.java` | Added `maxReadingValue` (default 99999) — mapped to the new column |
| `domain/meter/dto/CreateMeterRequest.java`, `UpdateMeterRequest.java`, `MeterResponse.java` | Expose `maxReadingValue` (optional in requests, always in responses) |
| `domain/meter/service/MeterService.java` | Pass `maxReadingValue` through create/update |

---

## Schema (V9)

Full DDL in [`V9__meter_readings.sql`](../../backend/src/main/resources/db/migration/V9__meter_readings.sql). Highlights:

- `meter_reading_log` — 10-value `reading_type` CHECK constraint; `chk_confirmed_at_matches_status` DB-enforces that CONFIRMED rows carry a `confirmed_at` and PENDING rows don't; `consumption` is NULL until confirmed and stays NULL forever on chain-anchor rows (`INITIAL`, `REPLACEMENT_OPEN`).
- Seven indexes: composite `(meter_id, reading_date_bs)` powers chain lookups; `(meter_id, status)` powers PENDING drafts UI; three partial indexes on `corrects_reading_id`, `replacement_event_id`, `coverage_event_id` since most rows carry none of those.
- `meter_replacement_events` — hard-linked to both readings + both meters via FKs (no soft references).
- `meter_coverage_events` + `meter_coverage_event_changes` — header + one row per affected meter. Rooms live in JSONB arrays on the changes row (`rooms_added`, `rooms_removed`) because the authoritative structural record is still `meter_room_coverage`; the JSONB is display/audit only.
- `meters.max_reading_value` NUMERIC(12,2), default 99999 — added by ALTER as a piggyback.

---

## Per-endpoint logic

### Reading log

| Endpoint | Rules |
|---|---|
| `POST /meters/{id}/readings` | Meter must be ACTIVE. `readingType` gated to `INITIAL` and `BILLING_RUN` only — the four tenant/vacancy types are deferred, and the four event types (`REPLACEMENT_*`, `COVERAGE_ANCHOR`, `CORRECTION`) are created only by their event flows. §14.1 M1 rollover: `readingValue < prevConfirmed` requires `rolloverConfirmed=true` (else 400 for M2). §14.1 M3: value 0 blocked unless chain-anchor or confirmed rollover. §14.1 M16: `readingDateBs < prevConfirmed.readingDateBs` blocked; `submissionDateBs > readingDateBs` flags `is_backdated=true`. Creates a PENDING row; confirming is a separate step. |
| `POST /readings/{id}/confirm` | Stamps `consumption` = `current − prevConfirmed` (or `(max − prev) + current` for rollover), sets `confirmed_at` and `status=CONFIRMED`. Chain-anchor rows get NULL consumption. Idempotent — re-confirming a CONFIRMED row is a 200 no-op. |
| `DELETE /readings/{id}` | Only allowed while PENDING (draft discard). CONFIRMED → 400 with the spec §7.5 immutability quote. |
| `POST /readings/{id}/corrections` | Original must be CONFIRMED (PENDING drafts should be discarded + resubmitted). CORRECTION rows can't be re-corrected (chain integrity — issue a new correction against the ORIGINAL). Notes are required (audit trail). Correction row is auto-CONFIRMED and stamps its own consumption from the previous confirmed reading *before* the original (`latestConfirmedExcluding`). |
| `GET /meters/{id}/readings` | Paginated, filters: `type`, `status`. Default sort: `readingDateBs DESC`. |
| `GET /readings/{id}` | 404 if not found. |
| `GET /meters/{id}/estimation-hint` | §14.1 M5 — rolling mean of the last 3 CONFIRMED consumption-bearing readings (excludes chain-anchor rows where consumption is NULL). Returns 0-length samples list if the meter has no history yet. Purely read-only. |

### Replacement (§14.1 M4)

| Endpoint | Rules |
|---|---|
| `POST /meters/{id}/replacement` | Old meter must be ACTIVE and not already replaced. Atomic: (1) create new meter carrying `meterType`, `meterPurpose`, `infrastructureScopeType`, `splitRuleOverride`, `readingResponsibility`, `designatedTenantId`, `maxReadingValue` forward; (2) end every active coverage row on the old meter on `replacementDateBs`, open a fresh coverage row on the new meter for the same room from the same date; (3) `REPLACEMENT_CLOSE` reading on old chain (consumption stamped, may be estimated per §14.1 M5); (4) mark old meter INACTIVE + `replaced_by_meter_id`; (5) `REPLACEMENT_OPEN` on new chain (chain-anchor, no consumption); (6) persist event row and back-fill both readings' `replacement_event_id`. Cross-field: `estimated=true` requires `estimationBasis`, `estimated=false` forbids it. |
| `GET /meters/{id}/replacement-events` | Lists events where this meter is either side (old or new) — old side after being replaced, new side after replacing a predecessor. |

### Coverage events (§14.1 M9/M10)

| Endpoint | Rules |
|---|---|
| `POST /coverage-events` | Top-level (not nested) because SPLIT touches ≥2 meters. Every affected meter must be ACTIVE, `TENANT_SUPPLY` (same rule as raw coverage — MAIN and INFRASTRUCTURE reject with 400), and belong to `propertyId`. For each meter: `roomsRemoved` end existing active rows on `eventDateBs`; `roomsAdded` open fresh rows and re-check the same duplicate-active-pair 409 the raw endpoint enforces. **Anchor reading required** whenever the meter's coverage changed on this event — otherwise 400 with the §14.1 M9 quote. |
| `GET /coverage-events/{id}` | Returns the header + full `changes[]` list. |
| `GET /coverage-events?propertyId=...` | Paginated events for a property. |

---

## §14.1 edge cases handled

| Case | Handling |
|------|---------|
| **M1** rollover | `rolloverConfirmed=true` → consumption uses `(max − previous) + current`. `is_rollover` flag stored. Verified live (test 13/14: 9899 for 9999-max, prev 150, current 50). |
| **M2** current < previous, no rollover | 400 with a message that names both `rolloverConfirmed` and §14.1 M1/M2. |
| **M3** zero reading | Blocked unless chain-anchor type or confirmed rollover. |
| **M4** replacement | One atomic endpoint. Coverage carries forward. Both close/open readings persisted with their event id. |
| **M5** failed meter estimation | Close-reading `estimated=true` + `estimationBasis`. `GET /estimation-hint` returns the rolling avg the landlord can use as the manual entry. |
| **M6** replacement doesn't start at 0 | No special code — open reading is a chain anchor, consumption stays NULL, absolute value irrelevant. |
| **M7** multiple replacements in one period | Same endpoint invoked twice → two events, two sub-chains. Billing engine (later) sums sub-periods; no code needed here. |
| **M8** replacement on billing day | Same endpoint; the replacement executes first because it's the transactionally atomic operation, then billing can run against the new chain. Nothing to enforce here — billing engine will read the chain and see the anchor. |
| **M9** coverage change adds/removes rooms | `POST /coverage-events` with `roomsAdded`/`roomsRemoved` + mandatory `anchorReading`. Missing anchor → 400. |
| **M10** two meters merged | Model matches SPLIT symmetrically — the surviving meter's `AffectedMeter` has `roomsAdded` (from the removed meter); the removed meter's `AffectedMeter` has `roomsRemoved`. Event type `MERGE`. |
| **M13** atomic photo + reading | `photo_url` column NULL-able; landlord submissions accept photo optionally (matches spec — tenant-required-photo path is Phase 4). |
| **M16** backdated reading | `submissionDateBs > readingDateBs` → `is_backdated=true`. `readingDateBs < prevConfirmed.readingDateBs` → 400 regardless of rollover flag. |

## Deferred

- **M14** vacant gap absorbed — column `is_gap_absorbed` exists; setter (and the whole `GAP_ABSORBED` reading type) waits on `TenancyController` and `room_assignments`.
- **M15** blocked new-tenant assignment before prior vacancy reading — belongs to `TenancyController`, not here.
- **M17** sum-of-subs > main threshold — bill-time reconciliation, `properties.overage_threshold_percent` is already populated; the check lives in the Billing engine.
- `VACANCY`, `TENANT_JOIN`, `DEPARTURE_TOPUP` reading types — deferred by controller-level gate; enum values exist so schema is stable.
- **M18** already enforced at MeterController (who may submit); wiring "who is currently allowed by this meter's `readingResponsibility`" to an actual submitter identity waits on JWT auth.

---

## Design decisions

1. **Two-state PENDING/CONFIRMED lifecycle.** Draft save for meter-walking (spec §15.2) maps cleanly to PENDING. Confirming stamps `consumption` in one place; before that, the row has no downstream billing meaning. Landlord flow can auto-confirm-on-submit at the controller layer if you want single-step UX — the seam is preserved either way.
2. **Corrections are new rows, auto-CONFIRMED.** Matches spec §9.6. Correction row's consumption is computed against the row *before* the original (via `latestConfirmedExcluding`), so the original stays in the audit trail without polluting the downstream chain math.
3. **`insertConfirmedInternal` is package-visible.** Event services (replacement, coverage) bypass the client-facing type gate because those readings are legitimately created by the platform, not submitted by a user. Marked package-private-via-doc but Java `public` because the services live in the same package.
4. **Replacement is atomic and coverage carries forward automatically.** Alternative was "create new meter separately, then submit close/open readings," which leaves a broken intermediate state (old chain closed, no new meter yet — bill runs would fail). Spec §7.8 mandates the carry-forward, and the atomic endpoint is the only way to guarantee it.
5. **Coverage events at top-level `/coverage-events`.** SPLIT/MERGE touches ≥2 meters — a URL nested under one `{meterId}` misrepresents the semantics.
6. **`meters.max_reading_value` piggyback on V9.** Adding a column mid-controller-pass is fine because no meter data prior to V8 could carry it, and V8 is one commit ago. Alternative was per-request `rolloverMaxValue` on every submission — clumsier and forces the client to know something that's a property of the meter, not the reading.
7. **JSONB via Hibernate 6 native** (`@JdbcTypeCode(SqlTypes.JSON)`) — no `hypersistence-utils` dependency needed. `rooms_added`/`rooms_removed` are audit-shape data; the authoritative record still lives in `meter_room_coverage`, so JSONB's non-queryable shape is fine here.
8. **Rollover math is deterministic per meter, not per property.** Each meter carries its own `max_reading_value` because meters in the same property can have different digit displays. Confirmed with user before adding.

---

## Open items

- [ ] **Second half of §6.5 gate** — "requires at least one *opening* reading" — now unblockable. Add a `readingRepository.existsInitialForMeter(meterId)` call to `PropertyService.updateProperty()`'s SUB_METERED/MAIN_METER_ONLY gate. Small follow-up commit.
- [ ] **RoomService.deleteRoom() active-coverage gate** — noted in `DEVLOG_METER.md`, still unblockable (MeterRoomCoverageRepository is imported into RoomService now that its cross-domain use is legit).
- [ ] **`GAP_ABSORBED` insert path** — waits on `TenancyController` / `room_assignments`. Column `is_gap_absorbed` in schema, service gate excludes the type.
- [ ] **`VACANCY` / `TENANT_JOIN` / `DEPARTURE_TOPUP` reading types** — deferred to Phase 4/7 by the same controller gate. When accepted, they'll go through analogous flows to `BILLING_RUN` (append + confirm), but validation rules differ per spec §14.3/§14.4.
- [ ] **Sum-of-subs vs main threshold (§14.1 M17)** — belongs to Billing engine (`properties.overage_threshold_percent` + `properties.overage_action` fields already exist).
- [ ] **RBAC / auth on submitter identity** — `submitted_by`/`confirmed_by` are populated to NULL right now; hook up once JWT auth lands.
- [ ] **BS-date range validation** — regex enforces `YYYY-MM-DD` shape; day/month range enforcement (BS months 29–32 days) waits on the BS-calendar library wire-in.

---

## Test coverage

35/35 scenarios green against live PostgreSQL 16 — see [`TEST_RESULTS.md`](../api-tests/MeterReadingController/TEST_RESULTS.md). Covers every §14.1 case landed this pass (M1, M2, M3, M4, M5, M6, M9, M13, M16), both directions of the reading-type gate (deferred and event-only), atomicity of replacement (old goes INACTIVE + coverage carries forward + both readings hard-linked to event), atomicity of coverage events (coverage rows ended and opened together with per-meter anchor readings), correction chain semantics (auto-confirm, no correction-of-correction, PENDING can't be corrected), estimation-hint on empty vs populated vs unknown meter.
