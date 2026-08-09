# PropertyAccessController — API Test Results

**Date:** 2026-07-30
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 18/18 passed

Raw responses are saved in [`responses/`](responses/). Import [`RentERP-PropertyAccessController.postman_collection.json`](RentERP-PropertyAccessController.postman_collection.json) into the Postman VS Code extension to re-run.

---

## How to re-run in Postman (VS Code extension)

1. Open the Postman panel in VS Code → **Collections** → **Import**
2. Select `RentERP-PropertyAccessController.postman_collection.json`
3. Make sure the backend is running (`mvn spring-boot:run`)
4. Create 4 users via the UserController collection first; set collection variables `ownerUserId`, `managerUserId`, `viewerUserId`, `otherUserId`
5. Run request **01** — it auto-saves `propertyId`; request **02** auto-saves `ownerGrantId`; requests 03/04 auto-save `managerGrantId`/`viewerGrantId`
6. Run top to bottom — later requests depend on ids captured by earlier ones

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | Create property → auto-creates OWNER grant | POST | `/api/v1/properties` | 201 | 201 | ✅ |
| 2 | List access by `propertyId`, confirm OWNER grant exists | GET | `/api/v1/property-access?propertyId={id}` | 200, 1 result, role=OWNER | 200, 1 result, role=OWNER | ✅ |
| 3 | Grant MANAGER access | POST | `/api/v1/property-access` | 201 | 201 | ✅ |
| 4 | Grant VIEW_ONLY access | POST | `/api/v1/property-access` | 201 | 201 | ✅ |
| 5 | Duplicate grant, same property+user | POST | `/api/v1/property-access` | 409 | 409 | ✅ |
| 6 | Grant role=OWNER directly | POST | `/api/v1/property-access` | 400 | 400 | ✅ |
| 7 | List access by `userId` | GET | `/api/v1/property-access?userId={id}` | 200, 1 result | 200, 1 result | ✅ |
| 8 | Get single grant | GET | `/api/v1/property-access/{id}` | 200 | 200 | ✅ |
| 9 | Update MANAGER → VIEW_ONLY | PUT | `/api/v1/property-access/{id}` | 200, role updated, `updatedAt` fresh | 200, role updated, `updatedAt` fresh | ✅ |
| 10 | Update a grant's role to OWNER | PUT | `/api/v1/property-access/{id}` | 400 | 400 | ✅ |
| 11 | Update the OWNER grant itself | PUT | `/api/v1/property-access/{id}` | 400 | 400 | ✅ |
| 12 | Revoke (DELETE) the OWNER grant | DELETE | `/api/v1/property-access/{id}` | 400 | 400 | ✅ |
| 13 | Revoke the VIEW_ONLY grant | DELETE | `/api/v1/property-access/{id}` | 200 | 200 | ✅ |
| 14 | Get the revoked grant, confirm `active=false` | GET | `/api/v1/property-access/{id}` | 200, active=false | 200, active=false | ✅ |
| 15 | Get non-existent grant | GET | `/api/v1/property-access/{bad-id}` | 404 | 404 | ✅ |
| 16 | Create grant, non-existent `propertyId` | POST | `/api/v1/property-access` | 404 | 404 | ✅ |
| 17 | Create grant, non-existent `userId` | POST | `/api/v1/property-access` | 404 | 404 | ✅ |
| 18 | Create grant, missing `role` | POST | `/api/v1/property-access` | 400 | 400 | ✅ |

---

## Notes

- Test 1/2 together verify the core design decision from this pass: creating a property auto-inserts an `OWNER` row into `property_access` in the same transaction (`PropertyService.createProperty()` → `PropertyAccessService.createOwnerGrant()`), so `properties.owner_user_id` and `property_access` never disagree.
- Tests 6, 10, 11, 12 verify the `OWNER` role is fully fenced off from this controller: it can't be granted directly, a grant can't be changed *to* OWNER, and the OWNER row itself can't be edited or revoked — all via the new `InvalidOperationException` → 400.
- Test data (the test property + its access rows) was deleted directly from Postgres after the run; the two properties from the earlier `PropertyController` pass were left untouched.
