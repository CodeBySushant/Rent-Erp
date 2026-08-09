# PropertyController — API Test Results

**Date:** 2026-07-30
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 12/12 passed

Raw responses are saved in [`responses/`](responses/). Import [`RentERP-PropertyController.postman_collection.json`](RentERP-PropertyController.postman_collection.json) into the Postman VS Code extension to re-run.

---

## How to re-run in Postman (VS Code extension)

1. Open the Postman panel in VS Code → **Collections** → **Import**
2. Select `RentERP-PropertyController.postman_collection.json`
3. Make sure the backend is running (`mvn spring-boot:run`)
4. Create two landlord users via the UserController collection first, set collection variables `ownerUserId1` / `ownerUserId2` from their responses
5. Run request **01** — it auto-saves the new `propertyId` into a collection variable
6. Requests 04, 08, 09, 10, 10b reuse `{{propertyId}}` automatically; request 07 reuses `{{ownerUserId1}}`

Collection variables:
- `baseUrl` = `http://localhost:8080`
- `ownerUserId1`, `ownerUserId2` = set manually from two UserController create responses
- `propertyId` = set automatically by request 01

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | Create property (owner1) | POST | `/api/v1/properties` | 201 | 201 | ✅ |
| 1b | Create second property (owner2) | POST | `/api/v1/properties` | 201 | 201 | ✅ |
| 2 | Owner user does not exist | POST | `/api/v1/properties` | 404 | 404 | ✅ |
| 3 | Blank name | POST | `/api/v1/properties` | 400 | 400 | ✅ |
| 4 | Get single | GET | `/api/v1/properties/{id}` | 200 | 200 | ✅ |
| 5 | Get non-existent | GET | `/api/v1/properties/{bad-id}` | 404 | 404 | ✅ |
| 6 | Get all (default page, no filter) | GET | `/api/v1/properties` | 200, 2 results | 200, 2 results | ✅ |
| 7 | Get all filtered by owner | GET | `/api/v1/properties?ownerUserId={id}` | 200, 1 result | 200, 1 result | ✅ |
| 8 | Update | PUT | `/api/v1/properties/{id}` | 200 | 200 | ✅ |
| 9 | Soft delete | DELETE | `/api/v1/properties/{id}` | 200 | 200 | ✅ |
| 10 | Get after delete | GET | `/api/v1/properties/{id}` | 200, active=false | 200, active=false | ✅ |
| 10b | Update non-existent | PUT | `/api/v1/properties/{bad-id}` | 404 | 404 | ✅ |

---

## Key responses

### 1 — Create property → `201 Created`
```json
{
  "data": {
    "id": "af6ac8f6-b82d-4042-9516-dc2f823cefbe",
    "ownerUserId": "412eb8f3-c913-4ef1-b60f-b7036bfddd39",
    "name": "Baneshwor Apartment",
    "address": "Baneshwor-10",
    "city": "Kathmandu",
    "electricityBillingMode": "SUB_METERED",
    "waterMode": "KUKL_SPLIT",
    "defaultSplitRule": "EQUAL",
    "neaTariffMode": "FLAT_RATE",
    "midMonthDepartureRateMode": "PREVIOUS_MONTH",
    "billingDay": 5,
    "gracePeriodDays": 7,
    "active": true,
    "createdAt": "2026-07-30T13:32:45.766249Z",
    "updatedAt": "2026-07-30T13:32:45.766249Z"
  },
  "message": "Property created successfully",
  "success": true
}
```

### 2 — Owner user does not exist → `404 Not Found`
```json
{
  "message": "User not found with id = 00000000-0000-0000-0000-000000000000",
  "success": false,
  "timestamp": "2026-07-30T13:33:03.730375Z"
}
```

### 3 — Blank name → `400 Bad Request`
```json
{
  "message": "Property name is required",
  "success": false,
  "timestamp": "2026-07-30T13:33:03.780929Z"
}
```

### 7 — Filtered list → `200 OK`, scoped to one owner
```json
{
  "data": {
    "content": [ /* only the 1 property owned by ownerUserId1 */ ],
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```
Confirms `findByOwnerUserId` actually scopes results — the unfiltered list (test 6) returned both owners' properties (`totalElements: 2`).

### 8 — Update → `200 OK`, fresh `updatedAt`
```json
{
  "data": {
    "name": "Baneshwor Apartment - Block A",
    "neaTariffMode": "BLENDED_RATE",
    "gracePeriodDays": 10,
    "createdAt": "2026-07-30T13:32:45.766249Z",
    "updatedAt": "2026-07-30T13:33:34.650889Z"
  }
}
```
`updatedAt` is correctly ~49s after `createdAt` in the same response — no stale-timestamp issue here, because `Property` extended `BaseAuditEntity` and `PropertyService` used `saveAndFlush()` from the start (the global convention adopted after the UserController fix).

### 10 — Get after delete → `active: false`
```json
{
  "data": {
    "id": "af6ac8f6-b82d-4042-9516-dc2f823cefbe",
    "name": "Baneshwor Apartment - Block A",
    "active": false
  }
}
```
Confirms the row still exists (soft delete) with `active=false`.

---

## Issue found during testing

**A non-existent `ownerUserId` on create fell through to a generic 500, not a clean error.**

Before the fix, `POST /api/v1/properties` with a UUID that isn't in `users` relied entirely on the `properties.owner_user_id` foreign-key constraint. Postgres rejected the insert, Spring wrapped it as `DataIntegrityViolationException`, and `GlobalExceptionHandler`'s catch-all turned it into a `500 An unexpected error occurred` — no useful message for the client, and a real bug logged as if it were unexpected.

**Fix:** `PropertyService.createProperty()` now checks `userRepository.existsById(ownerUserId)` before building the entity, and throws the existing `ResourceNotFoundException` (→ 404, same shape as any other "not found") if the owner doesn't exist. Verified in test 2 above.

**Pattern for future controllers:** any FK reference in a create/update DTO (e.g. `PropertyAccessController`'s `propertyId` + `userId`, `RoomController`'s `floorId`) must be existence-checked in the service before insert — don't rely on the DB constraint to surface as a client-facing error.

---

## Open items
- [ ] `PropertyController` doesn't yet enforce that only the owner (or a user with `property_access`) can update/delete — deferred until auth + `PropertyAccessController` exist
- [ ] `ownerUserId` is not transferable via `PUT /properties/{id}` — ownership transfer will be a dedicated endpoint backed by `property_ownership_transfers` once that workflow is built
- [ ] No dedicated validation that `billingDay` stays valid across BS/AD month-length differences beyond the 1–28 range enforced here — revisit when the billing engine (Phase 5) defines exact semantics
