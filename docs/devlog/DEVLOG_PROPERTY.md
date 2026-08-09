# PropertyController — Detailed Dev Log

**Controller:** `PropertyController`
**Base URL:** `/api/v1/properties`
**Table:** `properties`
**Status:** Complete

---

## Files Created

| File | Purpose |
|------|---------|
| `db/migration/V3__property.sql` | Creates `properties` table |
| `domain/property/entity/Property.java` | JPA entity + 5 enums (electricity/water/split/NEA/mid-month modes) |
| `domain/property/dto/CreatePropertyRequest.java` | Request body for POST /properties |
| `domain/property/dto/UpdatePropertyRequest.java` | Request body for PUT /properties/{id} |
| `domain/property/dto/PropertyResponse.java` | What the API returns — never the raw entity |
| `domain/property/repository/PropertyRepository.java` | Spring Data repo + `findByOwnerUserId` |
| `domain/property/service/PropertyService.java` | All business logic |
| `domain/property/controller/PropertyController.java` | HTTP layer — 5 endpoints |

`property_ownership_transfers`, `property_mode_changes`, `property_blocked_tenants` — mapped to this controller in [../CONTROLLER_TABLE_MAP.md](../CONTROLLER_TABLE_MAP.md) but **not built in this pass**. They're event-log tables written by dedicated workflows (ownership transfer, mode-change approval, tenant blocking), not plain CRUD — same reasoning that kept `otp_attempts`/`user_sessions` out of `UserController`'s endpoint surface. They'll be added alongside those features.

---

## Schema — `properties`

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `id` | UUID PK | `gen_random_uuid()` | |
| `owner_user_id` | UUID FK → `users.id` | — | landlord who owns this property |
| `name` | VARCHAR(255) | — | required |
| `address` | VARCHAR(500) | — | optional |
| `city` | VARCHAR(100) | — | optional |
| `electricity_billing_mode` | VARCHAR(50) | `SUB_METERED` | one of the 4 modes from the spec |
| `water_mode` | VARCHAR(50) | `INCLUDED_IN_RENT` | one of the 5 modes from the spec |
| `default_split_rule` | VARCHAR(50) | `EQUAL` | property-level default, overridable per meter later |
| `nea_tariff_mode` | VARCHAR(50) | `FLAT_RATE` | flat vs blended-rate NEA compliance mode |
| `mid_month_departure_rate_mode` | VARCHAR(50) | `PREVIOUS_MONTH` | which rate a mid-month leaver is billed at |
| `billing_day` | SMALLINT | `1` | 1–32, day of month billing runs generate (was wrongly capped at 1–28 — fixed 2026-07-30, see below) |
| `grace_period_days` | SMALLINT | `7` | was `5` — fixed to match spec default 2026-07-30, see below |
| `is_active` | BOOLEAN | `TRUE` | soft delete flag |
| `created_at` / `updated_at` | TIMESTAMPTZ | `NOW()` | JPA-audited |

**Why enums are stored as VARCHAR, not Postgres native ENUM:** consistent with how `role`/`kyc_status` are stored on `users` in V1 — adding a new mode later is an app deploy, not a migration that alters a Postgres enum type.

**Why `billing_day` caps at 32, not 28:** BS months run 29–32 days, not the Gregorian 28–31 — capping at 28 was a Gregorian-calendar assumption that directly contradicted the project's own BS-calendar rule (spec §18.2). Fixed 2026-07-30 (see audit entry below); the original 1–28 reasoning was wrong and is kept here only as a record of the mistake.

---

## APIs

### POST /api/v1/properties
**Purpose:** Register a new property under a landlord.

**Request body:**
```json
{
  "ownerUserId": "412eb8f3-c913-4ef1-b60f-b7036bfddd39",
  "name": "Baneshwor Apartment",
  "address": "Baneshwor-10",
  "city": "Kathmandu",
  "electricityBillingMode": "SUB_METERED",
  "waterMode": "KUKL_SPLIT",
  "billingDay": 5,
  "gracePeriodDays": 7
}
```

**Rules:**
- `ownerUserId` is required and must reference an existing user — checked explicitly in the service (see "Issue found during testing" below), not left to the DB FK constraint.
- `name` is required, max 255 chars.
- All billing-mode fields are optional and default per the schema table above.
- `billingDay` 1–28, `gracePeriodDays` 0–90 — enforced by `@Min`/`@Max`.

**Response:** `201 Created` with the created property.

**Logic flow:**
1. `@Valid` triggers Bean Validation on the request body
2. `propertyService.createProperty()` checks `ownerUserId` exists via `UserRepository.existsById()`
3. If missing → throws `ResourceNotFoundException` → `GlobalExceptionHandler` returns 404
4. Builds `Property` entity with builder defaults for any omitted mode field, saves, returns `PropertyResponse`

---

### GET /api/v1/properties/{id}
**Purpose:** Fetch a single property by UUID.

**Rules:**
- If property does not exist → 404 Not Found.
- Returns inactive properties too (`is_active=false`).

**Response:** `200 OK` with property data.

---

### GET /api/v1/properties
**Purpose:** Fetch properties with pagination, optionally scoped to one owner.

**Query params:**
```
?page=0&size=20&sort=createdAt,desc&ownerUserId=<uuid>
```

**Rules:**
- `ownerUserId` is optional. Omitted → returns every property across all owners (admin-style view). Provided → scoped to that landlord's own properties via `PropertyRepository.findByOwnerUserId`.
- This is the query the mobile app's "my properties" screen will call once auth resolves the current user.

**Response:** `200 OK` with `PagedResponse`.

---

### PUT /api/v1/properties/{id}
**Purpose:** Update a property's name/address/billing-mode settings.

**Request body (all fields optional — only provided fields are updated):**
```json
{
  "name": "Baneshwor Apartment - Block A",
  "gracePeriodDays": 10,
  "neaTariffMode": "BLENDED_RATE"
}
```

**Rules:**
- Every field is optional; null fields are ignored.
- `ownerUserId` is **not** updatable here — ownership transfer is a distinct workflow (`property_ownership_transfers`) with its own audit trail, not a plain field edit.
- If property not found → 404.

**Response:** `200 OK` with updated property.

---

### DELETE /api/v1/properties/{id}
**Purpose:** Deactivate a property (soft delete).

**Rules:**
- Sets `is_active = false`. Row is NEVER deleted from the database.
- Idempotent — already-inactive property silently succeeds.
- If property not found → 404.

**Response:** `200 OK` with success message, no data body.

---

## Exception handling

| Exception | HTTP Status | When thrown |
|-----------|-------------|-------------|
| `ResourceNotFoundException` | 404 | Property ID doesn't exist, or `ownerUserId` on create doesn't reference a real user |
| `MethodArgumentNotValidException` | 400 | `@Valid` fails (e.g. blank name, billingDay out of 1–28 range) |
| `Exception` (catch-all) | 500 | Anything unexpected |

No `DuplicateResourceException` case here — unlike `phone` on `users`, nothing on `properties` is required to be globally unique (two landlords can both name a building "Sunrise Apartment").

---

## Design decisions

**Why explicit `existsById` check instead of relying on the FK constraint?**
Without it, a bad `ownerUserId` reaches Postgres, fails the FK constraint, and Spring's `DataIntegrityViolationException` falls into `GlobalExceptionHandler`'s generic 500 handler — a real client-input error masquerading as a server bug. See "Issue found during testing" in [../api-tests/PropertyController/TEST_RESULTS.md](../api-tests/PropertyController/TEST_RESULTS.md). Adopted as the pattern for any FK reference in a create/update DTO going forward.

**Why billing-mode config lives on `properties` directly instead of a separate settings table?**
These fields are 1:1 with the property, always read together by the billing engine, and rarely change after setup. A join for something read on every billing run would be pure overhead with no benefit — same reasoning as `preferred_language` living directly on `users` instead of a `user_settings` table.

**Why soft delete?**
Same reasoning as `UserController` — a property's history (rooms, meters, bills) must survive even if a landlord archives it. `is_active=false` keeps the row while excluding it from active queries.

**Why no unique constraint on `name`?**
Property names aren't an identity — two different landlords, or the same landlord, can legitimately use the same name. Uniqueness would be a false constraint.

---

## Testing

All 5 endpoints (12 scenarios incl. two owners for the filter test, 404/400 error paths, FK-violation-turned-404) tested live against PostgreSQL — **12/12 scenarios passed**.

- Full results: [../api-tests/PropertyController/TEST_RESULTS.md](../api-tests/PropertyController/TEST_RESULTS.md)
- Postman collection: [../api-tests/PropertyController/RentERP-PropertyController.postman_collection.json](../api-tests/PropertyController/RentERP-PropertyController.postman_collection.json)
- Raw responses: [../api-tests/PropertyController/responses/](../api-tests/PropertyController/responses/)

### Issue found during testing — FK violation surfaced as 500, fixed same pass

**Status:** ✅ Fixed & verified

`POST /api/v1/properties` with a non-existent `ownerUserId` initially relied on the `owner_user_id` foreign key to reject the insert, which `GlobalExceptionHandler`'s catch-all turned into an unhelpful `500`. Fixed by adding an explicit `userRepository.existsById(ownerUserId)` check in `PropertyService.createProperty()` before the insert, throwing the existing `ResourceNotFoundException` (→ clean 404). Verified in test 2 — see raw response [responses/02_create_owner_not_found_404.txt](../api-tests/PropertyController/responses/02_create_owner_not_found_404.txt).

**Pattern for future controllers:** any FK reference in a create/update request body must be existence-checked in the service layer before insert/update — never rely on the DB constraint to surface as the client-facing error. Applies immediately to `PropertyAccessController` (`propertyId` + `userId`) and `StructureController` (`propertyId` on Floor, `floorId` on Room).

### `updatedAt` freshness — confirmed correct, no regression

`Property` extended `BaseAuditEntity` and `PropertyService` used `saveAndFlush()` on update/delete from the first version written (the global convention adopted after `UserController`'s stale-`updatedAt` fix), so no repeat of that bug — confirmed by test 8's response showing `updatedAt` ~49s after `createdAt` within the same response.

---

## Open items
- [ ] Add authorization so only the owner (or a `property_access` row with sufficient role) can update/delete a property — deferred until auth is implemented and `PropertyAccessController` exists
- [ ] Ownership transfer endpoint backed by `property_ownership_transfers`, mode-change workflow backed by `property_mode_changes`, tenant-blocking backed by `property_blocked_tenants` — build alongside those features, not as plain CRUD
- [ ] Add `GET /api/v1/properties?active=true` filter when frontend needs it (same open item pattern as `UserController`)
- [ ] `updateProperty()` switching `electricityBillingMode` to `SUB_METERED`/`MAIN_METER_ONLY` should be blocked until at least one meter with an opening reading exists (spec §6.5). Cannot be enforced yet — no `meters` table until Phase 3. TODO comment left at the call site in `PropertyService.updateProperty()`. **Do not ship `MeterController` without circling back to this.**
- [ ] Config changes to billing-critical fields (mode, rate, split rule) should be formal dated events per spec §6.5 ("never silent edits — historical bills must be reproducible using settings active on their billing date"). Currently a plain in-place update. Revisit once `property_mode_changes` is built (tracked above) and once the Billing phase needs to snapshot config per run.

---

## [2026-07-30] Spec audit — 4 gaps found and fixed

A full spec-vs-code audit (see [../../RENT_ERP_CONTEXT.md](../../RENT_ERP_CONTEXT.md) Edge Case Register) found `Property` was missing most of spec §6.4's policy settings. Fixed in `V4__property_policy_settings.sql`:

- Added 14 new columns: `payment_model_default`, `vacancy_notice_period_days`, `move_out_day_chargeable`, `common_units_charged_to_tenants`, `penalty_type`, `penalty_frequency`, `penalty_grace_days`, `penalty_cap_amount`, `late_departure_charge_type`, `late_departure_charge_amount`, `rounding_method`, `rounding_remainder_to`, `overage_threshold_percent`, `overage_action`, `tds_enabled`, `tds_rate_percent`, `dispute_window_days` — matching new enums on `Property` and fields on all 3 DTOs.
- Fixed `billing_day` validation range: was `1-28` (Gregorian-calendar assumption), spec requires `1-32` since BS months run 29-32 days and are never assumed 30 (project's own §18.2 rule). `CreatePropertyRequest`/`UpdatePropertyRequest` `@Min`/`@Max` updated; verified `32` accepted and `33` rejected live.
- Fixed `grace_period_days` default: was `5`, spec default is `7`.
- Verified live against Postgres: migration applied cleanly (schema now v4), created a property exercising the new fields, confirmed correct JSON round-trip, cleaned up test rows.

No endpoint signatures changed — additive only, no test regressions expected. Full 12/12 `TEST_RESULTS.md` scenarios remain valid.
