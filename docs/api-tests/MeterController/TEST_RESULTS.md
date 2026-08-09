# MeterController — API Test Results

**Date:** 2026-07-31
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 35/35 passed

Raw responses are saved in [`responses/`](responses/). Import [`RentERP-MeterController.postman_collection.json`](RentERP-MeterController.postman_collection.json) into the Postman VS Code extension to re-run.

---

## How to re-run

1. Open the Postman panel → **Collections** → **Import**
2. Select `RentERP-MeterController.postman_collection.json`
3. Make sure the backend is running (`mvn spring-boot:run`) and PostgreSQL is up
4. Run top to bottom — request 01 (Create user) → 02 (Property) → 03 (Floor) → 04 (Room) seed the collection variables (`userId`, `propertyId`, `floorId`, `roomId`); every subsequent request reads from those

Alternatively, `/tmp/meter_test.sh` (regeneratable from this file's scenarios) drives the same flow with `curl`.

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | Create MAIN electricity meter | POST | `/api/v1/meters` | 201 | 201 | ✅ |
| 2 | Create TENANT_SUPPLY meter (`splitRuleOverride=ROOM_WEIGHTED`) | POST | `/api/v1/meters` | 201 | 201 | ✅ |
| 3 | Create INFRASTRUCTURE meter (`scopeType=SCOPED`, no `readingResponsibility` supplied) | POST | `/api/v1/meters` | 201 | 201 | ✅ |
| 3b | Verify §14.1 **M18** default — infra meter's `readingResponsibility` defaulted to `LANDLORD_ONLY` | — | (verify response body) | `LANDLORD_ONLY` | `LANDLORD_ONLY` | ✅ |
| 4 | Create INFRASTRUCTURE meter (`scopeType=GLOBAL`) | POST | `/api/v1/meters` | 201 | 201 | ✅ |
| 5 | Duplicate `serialNumber` in same property (§14.1 **M12**) | POST | `/api/v1/meters` | 409 | 409 | ✅ |
| 6 | INFRASTRUCTURE meter without `infrastructureScopeType` | POST | `/api/v1/meters` | 400 | 400 | ✅ |
| 7 | MAIN meter *with* `infrastructureScopeType` | POST | `/api/v1/meters` | 400 | 400 | ✅ |
| 8 | Non-infra meter without `readingResponsibility` | POST | `/api/v1/meters` | 400 | 400 | ✅ |
| 9 | Unknown `propertyId` | POST | `/api/v1/meters` | 404 | 404 | ✅ |
| 10 | Get meter by id | GET | `/api/v1/meters/{id}` | 200 | 200 | ✅ |
| 11 | Get unknown id | GET | `/api/v1/meters/{bad-id}` | 404 | 404 | ✅ |
| 12 | List by property (4 seeded meters) | GET | `/api/v1/meters?propertyId={id}` | 200, 4 results | 200, 4 results | ✅ |
| 13 | Filter list by `meterType=MAIN` | GET | `/api/v1/meters?propertyId=...&meterType=MAIN` | 200, 1 result | 200, 1 result | ✅ |
| 14 | Filter list by `meterPurpose=WATER` | GET | `/api/v1/meters?propertyId=...&meterPurpose=WATER` | 200, 1 result | 200, 1 result | ✅ |
| 15 | Update `label` + `readingResponsibility` | PUT | `/api/v1/meters/{id}` | 200 | 200 | ✅ |
| 16 | Update `serialNumber` to a value already used in the property | PUT | `/api/v1/meters/{id}` | 409 | 409 | ✅ |
| 17 | Add TENANT_SUPPLY coverage row for room `G-1` | POST | `/api/v1/meters/{id}/coverage` | 201 | 201 | ✅ |
| 18 | Add coverage on a MAIN meter (only TENANT_SUPPLY has coverage) | POST | `/api/v1/meters/{main-id}/coverage` | 400 | 400 | ✅ |
| 19 | Duplicate active coverage row (same meter+room, no `effectiveToBs`) | POST | `/api/v1/meters/{id}/coverage` | 409 | 409 | ✅ |
| 20 | List coverage | GET | `/api/v1/meters/{id}/coverage` | 200 | 200 | ✅ |
| 21 | Deactivate meter while active coverage exists (§14.1 **M11**) | DELETE | `/api/v1/meters/{id}` | 400 | 400 | ✅ |
| 22 | End the coverage row | PATCH | `/api/v1/meters/{id}/coverage/{covId}/end` | 200 | 200 | ✅ |
| 23 | Now deactivate — coverage no longer active | DELETE | `/api/v1/meters/{id}` | 200 | 200 | ✅ |
| 24 | Re-DELETE (idempotent no-op on already-inactive meter) | DELETE | `/api/v1/meters/{id}` | 200 | 200 | ✅ |
| 25 | Add FLOOR scope to SCOPED infra meter | POST | `/api/v1/meters/{id}/infra-scope` | 201 | 201 | ✅ |
| 26 | Duplicate FLOOR scope on same infra meter | POST | `/api/v1/meters/{id}/infra-scope` | 409 | 409 | ✅ |
| 27 | Add scope on a GLOBAL infra meter (nothing to scope) | POST | `/api/v1/meters/{global-id}/infra-scope` | 400 | 400 | ✅ |
| 28 | Add scope on a MAIN meter (not infrastructure) | POST | `/api/v1/meters/{main-id}/infra-scope` | 400 | 400 | ✅ |
| 29 | TENANT-scoped scope row (deferred to Phase 4) | POST | `/api/v1/meters/{id}/infra-scope` | 400 | 400 | ✅ |
| 30 | List infra scope rows | GET | `/api/v1/meters/{id}/infra-scope` | 200 | 200 | ✅ |
| 31 | Delete scope row | DELETE | `/api/v1/meters/{id}/infra-scope/{scopeId}` | 200 | 200 | ✅ |
| 32 | **§6.5 gate** — switch a meterless property to `SUB_METERED` | PUT | `/api/v1/properties/{no-meter-property-id}` | 400 | 400 | ✅ |
| 33 | §6.5 gate — switch a metered property to `SUB_METERED` | PUT | `/api/v1/properties/{metered-property-id}` | 200 | 200 | ✅ |
| 34 | Reverse switch (metered → non-metered) is never gated | PUT | `/api/v1/properties/{id}` | 200 | 200 | ✅ |

---

## Notes

- **Spec §14.1 M12** (duplicate meter serial): scenario 5. Serial is optional, so uniqueness is scoped by a **partial** unique index on `(property_id, serial_number) WHERE serial_number IS NOT NULL` — multiple no-serial rows in the same property are allowed.
- **Spec §14.1 M18** (reading responsibility): scenarios 3/3b. Infra meters default to `LANDLORD_ONLY` when the caller omits `readingResponsibility` — matches the "pump rooms are physically locked" reality. Non-infra meters require an explicit choice (scenario 8) so the property config surfaces the decision rather than silently defaulting.
- **Spec §14.1 M11** (meter deactivation): scenarios 21–24. Deactivation is blocked while any active coverage row exists (`effective_to_bs IS NULL`). The landlord must end coverage first, which is the moment the UI can display "who loses coverage before you confirm". Deactivation itself soft-marks (`status=INACTIVE`, `is_active=false`); the row is never removed, and repeat DELETEs are idempotent no-ops (scenario 24).
- **Spec §6.5 gate** (previously deferred TODO in `PropertyService.updateProperty()`): scenarios 32–34. Switching an electricity mode to `SUB_METERED` or `MAIN_METER_ONLY` now requires at least one active meter to already exist for the property. The reciprocal switch (metered → non-metered) is not gated — meters just become inert. Opening-reading enforcement (the second half of §6.5) still waits on `MeterReadingController`, tracked in `DEVLOG_METER.md` open items.
- **Cross-meter-type invariants:** scenarios 6/7 enforce that `infrastructureScopeType` is present iff `meterType=INFRASTRUCTURE`, matching the V8 check constraint (`chk_infra_scope_matches_type`). The service throws `InvalidOperationException` before the DB, so the client gets a clean 400 rather than a generic 500 from a constraint-violation exception.
- **Coverage semantics:** scenarios 17–20. Coverage is a `TENANT_SUPPLY`-only concept (scenario 18 blocks it on MAIN); MAIN sits upstream and INFRASTRUCTURE covers pumps/pipes, not rooms. Ending a coverage row (`effectiveToBs`) is a PATCH, not a DELETE, because the row is retained forever for historical bill reconstruction (§14.1 M9).
- **Infra scope semantics:** scenarios 25–31. Only meaningful when `meterType=INFRASTRUCTURE AND infrastructureScopeType=SCOPED` — GLOBAL infra covers everyone by default (scenario 27), and non-infra meters have no scope surface (scenario 28). TENANT-scoped rows are deferred to `TenancyController` (scenario 29) — the `tenant_id` column exists in the migration but MeterController refuses to insert into it until the tenants table exists.
- Test data was created under an isolated landlord (`9800000801`) and left in place for later regression runs; teardown is a manual delete or a DB truncate.
