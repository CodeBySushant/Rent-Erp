# StructureController (Floor + Room) — Detailed Dev Log

**Controllers:** `FloorController`, `RoomController`
**Base URLs:** `/api/v1/floors`, `/api/v1/rooms`
**Tables:** `floors`, `rooms`
**Status:** Complete

---

## Files Created

| File | Purpose |
|------|---------|
| `db/migration/V6__structure.sql` | Creates `floors` and `rooms` tables |
| `domain/structure/entity/Floor.java` | JPA entity |
| `domain/structure/entity/Room.java` | JPA entity |
| `domain/structure/dto/CreateFloorRequest.java` / `UpdateFloorRequest.java` / `FloorResponse.java` | Floor DTOs |
| `domain/structure/dto/CreateRoomRequest.java` / `UpdateRoomRequest.java` / `RoomResponse.java` | Room DTOs |
| `domain/structure/repository/FloorRepository.java` | Spring Data repo + property/floorNumber finders |
| `domain/structure/repository/RoomRepository.java` | Spring Data repo + floor/name finders |
| `domain/structure/service/FloorService.java` | Business logic + the active-rooms deletion gate |
| `domain/structure/service/RoomService.java` | Business logic + the propertyId→floors→rooms resolution |
| `domain/structure/controller/FloorController.java` | HTTP layer — 5 endpoints |
| `domain/structure/controller/RoomController.java` | HTTP layer — 5 endpoints |

One domain package, two controllers — matches the single `StructureController (Floor + Room)` row in `CONTROLLER_TABLE_MAP.md`, but Floor and Room have distinct base paths and lifecycles, so they're separate `@RestController` classes rather than one class handling two resource types.

`room_details`, `room_amenities`, `room_photos` (and their property-level equivalents) are **future-only, schema-not-built** per `CONTROLLER_TABLE_MAP.md` — intentionally out of scope here, not an oversight.

---

## Schema

### `floors`

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `id` | UUID PK | `gen_random_uuid()` | |
| `property_id` | UUID FK → `properties.id` | — | |
| `name` | VARCHAR(255) | — | e.g. "Ground Floor" |
| `floor_number` | SMALLINT | — | ordering key, not a label — ground = 0, basements negative, bounded -5..200 |
| `is_active` | BOOLEAN | `TRUE` | soft delete |
| `created_at` / `updated_at` | TIMESTAMPTZ | `NOW()` | JPA-audited |
| — | `UNIQUE (property_id, floor_number)` | | **our own rule**, not in spec §20.2 — prevents two floors colliding on the same structural position |

### `rooms`

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `id` | UUID PK | `gen_random_uuid()` | |
| `floor_id` | UUID FK → `floors.id` | — | |
| `name` | VARCHAR(100) | — | e.g. "301" |
| `is_active` | BOOLEAN | `TRUE` | soft delete |
| `created_at` / `updated_at` | TIMESTAMPTZ | `NOW()` | JPA-audited |
| — | `UNIQUE (floor_id, name)` | | **our own rule**, not in spec §20.2 — scoped per floor, not per property (same room name is fine on two different floors) |

**No `status`/`occupancy` column on `rooms`, deliberately.** Spec §20.1 states rooms "deliberately hold no meter or tenant reference" — vacancy is always derived from `room_assignments` (Tenancy phase, not yet built), never stored here. Same principle as meter logic being derived, never hand-entered (P1).

---

## APIs

### POST /api/v1/floors
Creates a floor under a property. `propertyId` must reference an existing property (checked in-service, not left to the FK). `floorNumber` must be unique within that property — duplicate → 409.

### GET /api/v1/floors/{id}
Fetch one floor. 404 if missing. Returns inactive floors too.

### GET /api/v1/floors?propertyId=
Lists floors, optionally scoped to a property. **Default sort is `floorNumber` ascending**, not `createdAt` — deliberate deviation from every other controller so far, since floors are read in physical top-to-bottom order, not creation order.

### PUT /api/v1/floors/{id}
Renames and/or renumbers a floor. Renumbering re-checks the `(propertyId, floorNumber)` uniqueness against every *other* floor (excluding itself) — 409 on collision. `propertyId` itself is not editable (moving a floor to a different property isn't a field edit).

### DELETE /api/v1/floors/{id}
Soft delete. **Blocked (400) while the floor still has any active room** — the landlord must deactivate its rooms first. This mirrors the "show consequences before confirming" principle used for meter deactivation in the spec, applied here since Meter/Tenancy tables don't exist yet to check against directly. Idempotent once the floor is already inactive.

### POST /api/v1/rooms
Creates a room under a floor. `floorId` must reference an existing floor. `name` must be unique within that floor (not property-wide) — duplicate → 409; the same name on a different floor is fine (verified in test 10).

### GET /api/v1/rooms/{id}
Fetch one room. 404 if missing.

### GET /api/v1/rooms?floorId= / ?propertyId=
Two filter modes: `floorId` for rooms on one floor, or `propertyId` for every room across the whole property — needed for the "pick vacant rooms" multi-select during tenant assignment (spec §10.2). Since `rooms` only stores `floor_id` (no `property_id` column, matching the "no denormalized coupling" principle), the `propertyId` path resolves every floor under that property first (`FloorRepository.findAllByPropertyId`), then queries rooms across all of them (`RoomRepository.findByFloorIdIn`).

### PUT /api/v1/rooms/{id}
Renames a room, re-checking the `(floorId, name)` uniqueness. `floorId` is not editable — a room's physical floor doesn't change after construction (this is not the same thing as "Room Transfer," spec §12.3, which moves a *tenant* between two already-existing rooms).

### DELETE /api/v1/rooms/{id}
Soft delete. No active-reference gate yet (see Open items) — `meter_room_coverage` and `room_assignments` don't exist until later phases.

---

## Exception handling

| Exception | HTTP Status | When thrown |
|-----------|-------------|-------------|
| `ResourceNotFoundException` | 404 | Floor/room id doesn't exist, or `propertyId`/`floorId` on create doesn't reference a real row |
| `DuplicateResourceException` | 409 | `(propertyId, floorNumber)` or `(floorId, name)` collision, on create or on update |
| `InvalidOperationException` | 400 | Deleting a floor that still has active rooms |
| `MethodArgumentNotValidException` | 400 | `@Valid` fails (blank name, `floorNumber` out of -5..200) |
| `Exception` (catch-all) | 500 | Anything unexpected |

---

## Design decisions

**Why `(property_id, floor_number)` and `(floor_id, name)` uniqueness, when the spec doesn't mandate either?**
Confirmed with the user before implementation. Neither appears in spec §20.2's key-constraints list, but leaving them unconstrained would let a landlord create two "Floor 3"s or two "Room 301"s on the same floor, which is purely confusing, never intentional. Property-level room-name uniqueness was explicitly *not* chosen — "301" on Floor 1 and "301" on Floor 3 are different physical rooms and there's no reason to force them to have different labels.

**Why is floor deletion gated on active rooms, but room deletion has no gate at all yet?**
Floors gating on rooms is enforceable right now — `RoomRepository.existsByFloorIdAndActive()` is available in this same migration. Rooms gating on meter coverage or tenant assignment is *not* enforceable yet — `meter_room_coverage` and `room_assignments` don't exist until Phase 3/4. Rather than skip the floor-level gate too (for consistency) or fake a room-level gate that checks nothing, the floor gate ships now and the room gate is a tracked TODO for the moment those tables land — same shape as `PropertyService`'s electricity-mode-switch gate.

**Why does `floorNumber` allow negative values?**
Basements are common in the target buildings (spec §2.3: 10-40 room multi-storey residential). Ground floor = 0 is the natural anchor, so basements need negative numbers rather than an awkward offset scheme.

**Why is `GET /api/v1/floors`'s default sort `floorNumber` ascending instead of `createdAt` descending like every other controller?**
Every other list endpoint defaults to "most recently created first" because recency is what matters (newest property, newest access grant). Floors are different — a landlord viewing floor list wants to see the building top-to-bottom (or bottom-to-top with basements), never in the order they happened to be typed into the setup wizard.

---

## Testing

Both controllers (10 endpoints total) tested live against PostgreSQL — **30/30 scenarios passed**, including both uniqueness constraints (and the room-name constraint's per-floor scoping), the floor-deletion gate in both its blocked and unblocked states, idempotent delete, and the usual 404/409/400 paths.

- Full results: [../api-tests/StructureController/TEST_RESULTS.md](../api-tests/StructureController/TEST_RESULTS.md)
- Postman collection: [../api-tests/StructureController/RentERP-StructureController.postman_collection.json](../api-tests/StructureController/RentERP-StructureController.postman_collection.json)
- Raw responses: [../api-tests/StructureController/responses/](../api-tests/StructureController/responses/)

No issues found during testing — the FK-existence-check, soft-delete, and `saveAndFlush()`-for-fresh-`updatedAt` patterns were already established, and the two new uniqueness/gating rules behaved as designed on the first pass.

---

## Edge cases handled

Per the DEVLOG.md SOP: Structure doesn't map to a §14 subsection (Meter/Billing/Tenancy/Vacancy/Payment) — it's pure setup-time CRUD with no billing or tenancy consequences yet, confirmed before implementation. The register cases that *reference* rooms (T11 Room Addition, T12 Partial Vacate, M9 Coverage Change) all belong to later controllers (RoomOpsController, MeterController) that attach meters/tenants to rooms already created here — this controller only builds the shell those events will later act on.

---

## Open items
- [ ] `RoomService.deleteRoom()` should block deletion while a room has active meter coverage or an active tenant assignment, once `meter_room_coverage` (Phase 3) and `room_assignments` (Phase 4) exist. TODO comment left at the exact call site. **Do not ship `MeterController` or `TenancyController` without circling back to this.**
- [ ] No RBAC enforcement yet — same open item as every controller so far, deferred until JWT auth exists.
- [ ] Add `?active=true` filters on both list endpoints when frontend needs them (same recurring open item).
- [ ] Room's `RoomResponse` doesn't include a resolved `propertyId` (only `floorId`) — deliberately kept minimal since the frontend already knows `propertyId` from context when it queries by it. Revisit only if a real UI need for it emerges.
