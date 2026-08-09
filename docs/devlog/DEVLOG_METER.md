# DEVLOG — MeterController

**Phase:** 3 — Meters
**Date:** 2026-07-31
**Status:** ✅ Complete — 35/35 live Postgres scenarios passed
**Scope tables (this pass):** `meters`, `meter_room_coverage`, `infrastructure_meter_scope` (FLOOR-scoped rows only)
**Scope tables (deferred to `TenancyController`, Phase 4):** `meter_tenant_assignments` (whole table), `infrastructure_meter_scope` TENANT-scoped rows, `meters.designated_tenant_id` (nullable column now, populated later)

---

## Why this scope cut

Per [CONTROLLER_TABLE_MAP.md](../CONTROLLER_TABLE_MAP.md) MeterController owns 4 tables, but two of them — `meter_tenant_assignments` and the TENANT scope kind on `infrastructure_meter_scope` — reference a `tenants` table that doesn't exist until Phase 4. Building them now would either need a dangling-FK column (bad — silently drops referential integrity) or would require inventing tenant IDs from an unbuilt controller (worse). Split confirmed with the user in-session before starting.

The reading-chain tables (`meter_reading_log`, `meter_replacement_events`, `meter_coverage_events`, `meter_coverage_event_changes`) belong to `MeterReadingController` per the CONTROLLER_TABLE_MAP, not this controller.

---

## Files created

| File | Purpose |
|------|---------|
| `db/migration/V8__meters.sql` | Creates `meters`, `meter_room_coverage`, `infrastructure_meter_scope` |
| `domain/meter/entity/Meter.java` | JPA entity + 6 enums (MeterPurpose, MeterType, InfrastructureScopeType, SplitRule, ReadingResponsibility, MeterStatus) |
| `domain/meter/entity/MeterRoomCoverage.java` | JPA entity for `meter_room_coverage` |
| `domain/meter/entity/InfrastructureMeterScope.java` | JPA entity + `ScopeBy` enum |
| `domain/meter/dto/CreateMeterRequest.java` | Request body for POST /meters |
| `domain/meter/dto/UpdateMeterRequest.java` | Request body for PUT /meters/{id} (deliberately excludes `meterType`, `meterPurpose`, `infrastructureScopeType`) |
| `domain/meter/dto/MeterResponse.java` | Public API contract |
| `domain/meter/dto/CreateMeterRoomCoverageRequest.java` | Body for POST coverage row (BS-date validated) |
| `domain/meter/dto/EndMeterRoomCoverageRequest.java` | Body for PATCH end-coverage |
| `domain/meter/dto/MeterRoomCoverageResponse.java` | Coverage response DTO |
| `domain/meter/dto/CreateInfrastructureMeterScopeRequest.java` | Body for POST infra-scope |
| `domain/meter/dto/InfrastructureMeterScopeResponse.java` | Scope response DTO |
| `domain/meter/repository/MeterRepository.java` | Spring Data repo — property/type/purpose filters + §6.5 existence check |
| `domain/meter/repository/MeterRoomCoverageRepository.java` | Coverage repo with active-row helpers |
| `domain/meter/repository/InfrastructureMeterScopeRepository.java` | Scope repo |
| `domain/meter/service/MeterService.java` | Business logic — cross-field validation, edge-case handling, coverage/scope sub-resources |
| `domain/meter/controller/MeterController.java` | HTTP layer — 5 top-level endpoints + 6 sub-resource endpoints |

### Files modified

| File | Change |
|------|--------|
| `domain/property/service/PropertyService.java` | Closed the deferred §6.5 gate — switch to `SUB_METERED`/`MAIN_METER_ONLY` now requires an existing active meter; injected `MeterRepository` |

---

## Schema (V8)

Full DDL in [V8__meters.sql](../../backend/src/main/resources/db/migration/V8__meters.sql). Key points:

- **UUID PKs, TIMESTAMPTZ audit columns, soft delete** — same conventions as V1–V7.
- **`meters.serial_number`** is optional (`NULL` allowed); property-scoped uniqueness enforced via a *partial* unique index (`WHERE serial_number IS NOT NULL`) so multiple no-serial meters in one property don't collide.
- **`meters.status`** enum column (`ACTIVE`/`INACTIVE`) is redundant with `is_active` for now — kept explicit because the spec models multiple lifecycle transitions (spec §7.8) and future states (e.g. `PENDING_REPLACEMENT`) may differ from the "any inactive row" semantic that `is_active` carries elsewhere.
- **CHECK constraints** — `chk_infra_scope_matches_type` enforces `infrastructure_scope_type` iff `meter_type=INFRASTRUCTURE`; `chk_infra_scope_target` on `infrastructure_meter_scope` ensures a row is exactly one of FLOOR-scoped or TENANT-scoped, never both/neither.
- **`meter_room_coverage.effective_from_bs` / `effective_to_bs`** are `VARCHAR(20)` BS dates — never Postgres DATE (project convention). `effective_to_bs IS NULL` = currently active. Ending a coverage row sets this column rather than deleting the row (spec §14.1 M9 — historical bills read coverage as-of billing date).

---

## Per-endpoint logic

### Meter CRUD (`/api/v1/meters`)

| Endpoint | Rules |
|---|---|
| `POST /meters` | Property existence → 404. §14.1 M12 property-scoped dup serial → 409. Cross-field `infrastructureScopeType` iff `meterType=INFRASTRUCTURE` → 400. §14.1 M18 default: infra meters default `readingResponsibility=LANDLORD_ONLY` when omitted; non-infra meters *require* it explicitly → 400 if missing. |
| `GET /meters/{id}` | 404 on unknown id. |
| `GET /meters` | Optional filters: `propertyId`, `meterType`, `meterPurpose`. Uses 4-way branching in the service, one query per combination — avoids Specifications/QueryDSL complexity for a stable, finite filter surface. |
| `PUT /meters/{id}` | `serialNumber` re-checked for property-scoped uniqueness only when the value actually changes (so `PUT` with the current serial doesn't collide against itself). `meterType`, `meterPurpose`, `infrastructureScopeType` deliberately not exposed — changing them mid-life invalidates every downstream billing calculation; the correct operation is a formal replacement via `MeterReadingController`. `designatedTenantId` waits for Phase 4. |
| `DELETE /meters/{id}` | Soft-deactivates (§7.8 / §14.1 M11). Blocked while any active coverage row exists (`InvalidOperationException` → 400) — the landlord must end coverage first, which is the moment the UI can display "who loses coverage before you confirm". Idempotent on already-INACTIVE meters. |

### Coverage sub-resource (`/api/v1/meters/{id}/coverage`)

| Endpoint | Rules |
|---|---|
| `POST /coverage` | Only `TENANT_SUPPLY` meters accept coverage rows → 400 for MAIN/INFRASTRUCTURE. Meter must be ACTIVE. Room must exist. Blocked if the `(meter, room)` pair already has an active row (`effectiveToBs IS NULL`) → 409 — no overlapping periods on the same physical link. |
| `GET /coverage` | Returns the full dated history ordered by `effectiveFromBs ASC`. |
| `PATCH /coverage/{coverageId}/end` | Sets `effectiveToBs`. PATCH not DELETE — the row is retained forever for historical bill reconstruction. Cross-parent-id 404 if `coverageId` doesn't belong to the URL's meter. |

### Infrastructure scope sub-resource (`/api/v1/meters/{id}/infra-scope`)

| Endpoint | Rules |
|---|---|
| `POST /infra-scope` | Only meaningful when `meterType=INFRASTRUCTURE AND infrastructureScopeType=SCOPED`. GLOBAL infra covers everyone by default (400 — nothing to scope). Non-infra meters have no scope surface (400). `scopeBy=FLOOR` requires `floorId`, forbids `tenantId`; floor must belong to the meter's property (400 otherwise). `scopeBy=TENANT` deferred to Phase 4 (400 with an explanatory message). |
| `GET /infra-scope` | Ordered by `createdAt ASC` — scope is a live set, not a chain, so no dated ordering. |
| `DELETE /infra-scope/{scopeId}` | Hard delete — scope is a live-membership set, not a historical ledger. Removing a floor from an infra meter's scope has no "as-of" semantic on the row itself. |

---

## Edge cases handled (§14.1)

| Case | Handling |
|------|---------|
| **M11** — meter permanently removed | `DELETE /meters/{id}` soft-marks `status=INACTIVE`, blocks while any active coverage row exists (the "show affected before confirm" UX gate). Idempotent re-DELETE. |
| **M12** — duplicate meter serial in a property | Partial unique index `uq_meters_property_serial` + service pre-check → 409 on both create and update. Optional serial is exempt. |
| **M18** — reading responsibility per meter | Enum column enforced; infra meters default to `LANDLORD_ONLY` when the caller omits the field; non-infra meters must specify explicitly (surfaces the decision in the property config rather than defaulting silently). |

## Edge cases deferred (§14.1) — belong to `MeterReadingController`

M1 (rollover), M2 (current < previous), M3 (zero reading), M4 (replacement), M5 (estimated final reading), M6 (non-zero replacement opening), M7 (multiple replacements/period), M8 (replacement on billing day), M9 (coverage-change historical read), M10 (merge), M13 (atomic photo+reading), M14 (vacant gap absorbed), M15 (new tenant before prior vacancy reading), M16 (backdated reading), M17 (sum-of-submeters > main). All of these are reading-chain mechanics that live in the reading log and event tables, none of which exist yet.

---

## Related change — §6.5 gate closed in PropertyService

`PropertyService.updateProperty()` previously carried a TODO from the 2026-07-30 spec audit. Now closed: switching electricity billing mode TO `SUB_METERED` or `MAIN_METER_ONLY` requires at least one active meter to already exist for the property. Uses `MeterRepository.existsByPropertyIdAndActive(id, true)`, throws `InvalidOperationException` → 400. The reciprocal switch (metered → non-metered) is deliberately not gated; meters just become inert. Verified live via scenarios 32/33/34 in `TEST_RESULTS.md`.

The *second half* of §6.5 — "and at least one opening reading exists" — still can't be enforced until `MeterReadingController` builds `meter_reading_log`. TODO note relocated to this file's Open Items below.

---

## Design decisions

1. **`meterType`, `meterPurpose`, `infrastructureScopeType` are immutable via PUT.** Changing them mid-life invalidates every downstream billing calculation. The correct operation is a formal replacement (`MeterReadingController` replacement event, spec §7.8) or a new meter — never a plain field edit.
2. **Coverage is a `TENANT_SUPPLY`-only concept.** MAIN sits upstream of all rooms and INFRASTRUCTURE covers pumps/pipes, not rooms — enforced at the service layer with a 400.
3. **Serial-number scoping.** Property-scoped uniqueness (not global) — two landlords can independently own meters that happen to share a serial from different manufacturers. Serial is also optional, so uniqueness lives in a partial unique index. Confirmed with user before adding.
4. **Split-rule enum duplicated between `Meter.SplitRule` and `Property.SplitRule`.** Same three values, but the meter domain does not force a compile-time dependency on the property domain for a shared enum — mirrors how Charge duplicates enums for the same reason. Any future divergence (a meter-specific rule) lands cleanly on `Meter.SplitRule` alone.
5. **`meters.status` retained alongside `is_active`.** Redundant today, kept for the spec-documented future lifecycle transitions (§7.8) — `PENDING_REPLACEMENT` and similar don't fit the boolean `is_active` shape. Cheap forward-compat, no runtime cost.
6. **Scope row hard-delete.** `infrastructure_meter_scope` rows are live membership, not historical ledger — no "as-of" semantic. Ending a scope entry is functionally identical to removing it. Distinct from `meter_room_coverage`, which is time-dated because historical bills read it as-of billing date.

---

## Open items

- [ ] **Second half of §6.5 gate** — "switch requires at least one opening reading" — waits on `MeterReadingController` and `meter_reading_log`. Add a `MeterReadingRepository.existsByMeterIdAndReadingTypeInitial()` check alongside the meter-existence check the moment Phase 3's next controller lands.
- [ ] **`meters.designated_tenant_id`** — column exists (nullable), but there's no way to populate it until Phase 4. Add a `PATCH /meters/{id}/designated-tenant` endpoint (or extend `UpdateMeterRequest`) once tenants exist, and re-check the assignment against the meter's `readingResponsibility=DESIGNATED_TENANT`.
- [ ] **`meter_tenant_assignments`** — whole table not yet built; will be added in TenancyController's pass (Phase 4).
- [ ] **`infrastructure_meter_scope` TENANT-scoped rows** — service currently returns 400 with an explanatory message; flip to accept once tenants exist. The `tenant_id` column already accepts NULL and matches the DB CHECK constraint that gates FLOOR vs TENANT shapes.
- [ ] **RBAC / auth** — no authorization enforcement anywhere; deferred until JWT auth lands. Expected and known.
- [ ] **RoomService.deleteRoom() active-coverage gate** — RoomService's existing TODO ("block room deletion while it has active `meter_room_coverage`") is now unblockable — `MeterRoomCoverageRepository` exists. Follow-up pass, tracked in `DEVLOG_STRUCTURE.md`.
- [ ] **BS-date validation** — `effective_from_bs` / `effective_to_bs` are regex-validated to `YYYY-MM-DD` shape only. Day/month range enforcement (BS months are 29–32 days) waits on the BS-calendar library wire-in — that library also unblocks proration in the Billing engine (§14.2 B14).

---

## Test coverage

35 scenarios, all passing against a live local PostgreSQL 16 — see [TEST_RESULTS.md](../api-tests/MeterController/TEST_RESULTS.md). Coverage across:

- CRUD success paths for all 3 meter types × both purposes
- All cross-field validation combinations (§14.1 M18 default, INFRA scope-type match, non-infra responsibility required)
- §14.1 M12 duplicate-serial on create AND update
- §14.1 M11 deactivation gate + idempotent re-DELETE
- Coverage add/list/end + type restriction + duplicate-active block
- Infra scope add/list/delete + all four "wrong meter type" refusals + TENANT-deferral
- Pagination + property/type/purpose filter matrix
- §6.5 gate in both directions (block and reverse-allow)
- 404 on unknown property, meter, coverage-under-wrong-meter, scope-under-wrong-meter
