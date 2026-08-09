# StructureController (Floor + Room) — API Test Results

**Date:** 2026-07-31
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 30/30 passed

Raw responses are saved in [`responses/`](responses/). Import [`RentERP-StructureController.postman_collection.json`](RentERP-StructureController.postman_collection.json) into the Postman VS Code extension to re-run.

---

## How to re-run in Postman (VS Code extension)

1. Open the Postman panel → **Collections** → **Import**
2. Select `RentERP-StructureController.postman_collection.json`
3. Make sure the backend is running (`mvn spring-boot:run`)
4. Create a landlord user via the UserController collection first; set collection variable `ownerUserId`
5. Run top to bottom — request 01 saves `propertyId`; 02/03 save `floor0Id`/`floor1Id`; 07/08/10 save `room1Id`/`room2Id`/`room3Id`

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | Create property | POST | `/api/v1/properties` | 201 | 201 | ✅ |
| 2 | Create floor 0 (Ground Floor) | POST | `/api/v1/floors` | 201 | 201 | ✅ |
| 3 | Create floor 1 (1st Floor) | POST | `/api/v1/floors` | 201 | 201 | ✅ |
| 4 | Duplicate `floorNumber` in same property | POST | `/api/v1/floors` | 409 | 409 | ✅ |
| 5 | Create floor, non-existent `propertyId` | POST | `/api/v1/floors` | 404 | 404 | ✅ |
| 6 | Create floor, `floorNumber` out of range (300) | POST | `/api/v1/floors` | 400 | 400 | ✅ |
| 7 | Create room "301" on floor 1 | POST | `/api/v1/rooms` | 201 | 201 | ✅ |
| 8 | Create room "302" on floor 1 | POST | `/api/v1/rooms` | 201 | 201 | ✅ |
| 9 | Duplicate room name on same floor | POST | `/api/v1/rooms` | 409 | 409 | ✅ |
| 10 | Same room name allowed on a **different** floor | POST | `/api/v1/rooms` | 201 | 201 | ✅ |
| 11 | Create room, non-existent `floorId` | POST | `/api/v1/rooms` | 404 | 404 | ✅ |
| 12 | List floors by `propertyId`, ordered by `floorNumber` asc | GET | `/api/v1/floors?propertyId={id}` | 200, ordered 0 then 1 | 200, ordered 0 then 1 | ✅ |
| 13 | List rooms by `floorId` | GET | `/api/v1/rooms?floorId={id}` | 200, 2 results | 200, 2 results | ✅ |
| 14 | List rooms by `propertyId` (across both floors) | GET | `/api/v1/rooms?propertyId={id}` | 200, 3 results | 200, 3 results | ✅ |
| 15 | Get single floor | GET | `/api/v1/floors/{id}` | 200 | 200 | ✅ |
| 16 | Get single room | GET | `/api/v1/rooms/{id}` | 200 | 200 | ✅ |
| 17 | Update floor — rename + renumber | PUT | `/api/v1/floors/{id}` | 200, updated, `updatedAt` fresh | 200, updated, `updatedAt` fresh | ✅ |
| 18 | Update floor to a `floorNumber` already taken | PUT | `/api/v1/floors/{id}` | 409 | 409 | ✅ |
| 19 | Update room — rename | PUT | `/api/v1/rooms/{id}` | 200, updated, `updatedAt` fresh | 200, updated, `updatedAt` fresh | ✅ |
| 20 | Update room to a name already taken on same floor | PUT | `/api/v1/rooms/{id}` | 409 | 409 | ✅ |
| 21 | Delete floor 1 while it still has active rooms | DELETE | `/api/v1/floors/{id}` | 400 | 400 | ✅ |
| 22 | Delete room 1 | DELETE | `/api/v1/rooms/{id}` | 200 | 200 | ✅ |
| 23 | Delete room 2 | DELETE | `/api/v1/rooms/{id}` | 200 | 200 | ✅ |
| 24 | Delete floor 1 now that its rooms are inactive | DELETE | `/api/v1/floors/{id}` | 200 | 200 | ✅ |
| 25 | Get floor 1, confirm `active=false` | GET | `/api/v1/floors/{id}` | 200, active=false | 200, active=false | ✅ |
| 26 | Delete already-inactive floor again | DELETE | `/api/v1/floors/{id}` | 200 (idempotent) | 200 (idempotent) | ✅ |
| 27 | Get non-existent floor | GET | `/api/v1/floors/{bad-id}` | 404 | 404 | ✅ |
| 28 | Get non-existent room | GET | `/api/v1/rooms/{bad-id}` | 404 | 404 | ✅ |
| 29 | Create floor, missing name | POST | `/api/v1/floors` | 400 | 400 | ✅ |
| 30 | Create room, blank name | POST | `/api/v1/rooms` | 400 | 400 | ✅ |

---

## Notes

- Tests 4/18 and 9/20 verify the two "our own rule, not spec-mandated" uniqueness constraints: `(property_id, floor_number)` and `(floor_id, name)`. Test 10 specifically verifies the room-name constraint is scoped **per floor**, not global — the same room name "301" was allowed on a second floor of the same property.
- Tests 21/22/23/24 verify the floor-deletion gate: deleting a floor with active rooms is blocked (400), and only succeeds once every room under it has been deactivated first.
- Test 12 verifies `GET /api/v1/floors` defaults to sorting by `floorNumber` ascending, not `createdAt` — a deliberate deviation from every other controller's default sort, since floors are read in physical order.
- Test data (the test property, its floors, and rooms) was deleted directly from Postgres after the run; the two properties from earlier passes were left untouched.
