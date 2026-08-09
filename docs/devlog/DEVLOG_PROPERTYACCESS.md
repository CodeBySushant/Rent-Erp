# PropertyAccessController — Detailed Dev Log

**Controller:** `PropertyAccessController`
**Base URL:** `/api/v1/property-access`
**Table:** `property_access`
**Status:** Complete

---

## Files Created

| File | Purpose |
|------|---------|
| `db/migration/V5__property_access.sql` | Creates `property_access` table |
| `domain/propertyaccess/entity/PropertyAccess.java` | JPA entity + `AccessRole` enum (OWNER/MANAGER/VIEW_ONLY) |
| `domain/propertyaccess/dto/CreatePropertyAccessRequest.java` | Request body for POST /property-access |
| `domain/propertyaccess/dto/UpdatePropertyAccessRequest.java` | Request body for PUT /property-access/{id} — role only |
| `domain/propertyaccess/dto/PropertyAccessResponse.java` | What the API returns — never the raw entity |
| `domain/propertyaccess/repository/PropertyAccessRepository.java` | Spring Data repo + `findByPropertyId`, `findByUserId`, `existsByPropertyIdAndUserId` |
| `domain/propertyaccess/service/PropertyAccessService.java` | All business logic + `createOwnerGrant()` used by `PropertyService` |
| `domain/propertyaccess/controller/PropertyAccessController.java` | HTTP layer — 5 endpoints |
| `common/exception/InvalidOperationException.java` | New — 400 for a well-formed request that violates a business rule (the OWNER-fencing rules below) |

Also modified: `PropertyService.createProperty()` now calls `propertyAccessService.createOwnerGrant()` in the same transaction — see "Design decisions" below.

---

## Schema — `property_access`

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `id` | UUID PK | `gen_random_uuid()` | |
| `property_id` | UUID FK → `properties.id` | — | |
| `user_id` | UUID FK → `users.id` | — | |
| `role` | VARCHAR(50) | — | `OWNER \| MANAGER \| VIEW_ONLY` |
| `granted_by` | UUID FK → `users.id`, nullable | — | who invited them; `NULL` for the auto-created OWNER row (nobody "granted" it) |
| `is_active` | BOOLEAN | `TRUE` | soft delete / revoke flag |
| `created_at` / `updated_at` | TIMESTAMPTZ | `NOW()` | JPA-audited |
| — | `UNIQUE (property_id, user_id)` | | one grant per user per property — spec §20.2 |

---

## APIs

### POST /api/v1/property-access
**Purpose:** Grant MANAGER or VIEW_ONLY access to a property.

**Request body:**
```json
{
  "propertyId": "a741187f-9e7e-456c-b1c8-17ca9566e9bd",
  "userId": "e4758429-5021-410e-94f3-6ada5113be2b",
  "role": "MANAGER",
  "grantedBy": "7e339009-29a2-4c34-83fa-e844d19db719"
}
```

**Rules:**
- `propertyId` and `userId` must reference existing rows — checked explicitly in the service (same FK-existence pattern as `PropertyController`), never left to the DB constraint.
- `role` cannot be `OWNER` — rejected with 400. OWNER grants are only ever created by `PropertyService.createProperty()`.
- Duplicate `(propertyId, userId)` → 409, matching the DB's unique constraint.
- `grantedBy` is optional; if provided, must also reference an existing user.

**Response:** `201 Created` with the created grant.

---

### GET /api/v1/property-access/{id}
**Purpose:** Fetch a single access grant by UUID. 404 if not found. Returns revoked grants too (`is_active=false`).

---

### GET /api/v1/property-access
**Purpose:** List access grants, filtered by property or by user.

**Query params:**
```
?page=0&size=20&sort=createdAt,desc&propertyId=<uuid>   → everyone with access to this property
?page=0&size=20&sort=createdAt,desc&userId=<uuid>       → every property this user has access to
```

**Rules:**
- `propertyId` is checked before `userId` if both are somehow provided.
- Neither provided → returns every grant across the platform (admin-style view, same convention as `PropertyController`'s unfiltered list).
- The `userId` variant is what will drive the property-switcher UX (spec §15.1) once a manager/viewer with access to multiple properties needs to pick one.

**Response:** `200 OK` with `PagedResponse`.

---

### PUT /api/v1/property-access/{id}
**Purpose:** Change a grant's role (e.g. Manager → View only).

**Request body:**
```json
{ "role": "VIEW_ONLY" }
```

**Rules:**
- Only `role` is editable — `propertyId`/`userId` are the grant's identity; revoke + re-grant if either needs to change.
- Rejected with 400 if the *target* grant's current role is `OWNER`, or if the *new* role being set is `OWNER`. Ownership only changes via the (still-deferred) ownership-transfer workflow, never a role edit here.

**Response:** `200 OK` with the updated grant.

---

### DELETE /api/v1/property-access/{id}
**Purpose:** Revoke access (soft delete — `is_active = false`).

**Rules:**
- Rejected with 400 if the grant's role is `OWNER` — a property can never end up with no owner through this endpoint.
- Idempotent — already-revoked grant silently succeeds, same as `PropertyController`'s delete.

**Response:** `200 OK` with success message, no data body.

---

## Exception handling

| Exception | HTTP Status | When thrown |
|-----------|-------------|-------------|
| `ResourceNotFoundException` | 404 | Grant id doesn't exist, or `propertyId`/`userId`/`grantedBy` on create doesn't reference a real row |
| `DuplicateResourceException` | 409 | A grant already exists for this `(propertyId, userId)` pair |
| `InvalidOperationException` **(new)** | 400 | Granting/setting role to `OWNER` directly, or modifying/revoking an existing `OWNER` grant |
| `MethodArgumentNotValidException` | 400 | `@Valid` fails (e.g. missing `role`) |
| `Exception` (catch-all) | 500 | Anything unexpected |

`InvalidOperationException` is a new common exception type, added because none of the existing three fit "the request is well-formed and every id is real, but the operation itself breaks a business rule." It's registered in `GlobalExceptionHandler` alongside the others and is written to be reused by future controllers for the same shape of problem (e.g. billing immutability rules in `BillingController`, later).

---

## Design decisions

**Why does creating a property auto-create an OWNER grant?**
The spec's capability matrix (§4.2) and table inventory (§20.1: "`property_access` | Owner, manager and view-only grants") both frame permissions entirely in terms of this table — not `properties.owner_user_id`. If the actual owner never got a row here, every future permission check would need to special-case "check `owner_user_id` OR check `property_access`" — two sources of truth for the same fact. Instead, `PropertyService.createProperty()` calls `propertyAccessService.createOwnerGrant()` in the same transaction, so `property_access` is always the single place a permission check needs to look. Confirmed by discussion with the user before implementation (2026-07-30).

**Why is the OWNER role fenced off from every mutation this controller exposes?**
An `OWNER` grant is a structural fact about the property (mirrors `properties.owner_user_id`), not a delegated permission — it should only ever change via a dedicated, audited ownership-transfer flow (spec §14.3 T15, backed by the still-deferred `property_ownership_transfers` table), never a generic role edit or revoke. Blocking it here now means there's no path to accidentally orphan a property before that workflow exists.

**Why can a user hold at most one grant per property?**
Spec §20.2 states the constraint directly: `UNIQUE (property_id, user_id)`. A role change is a `PUT`, not a second `POST` — this also keeps "does this user have access, and at what level" a single-row lookup.

**Why no endpoint to look up "am I the owner of this property"?**
Not needed yet — there's no authenticated-principal concept until JWT auth lands (same open item as `ownerUserId`/`grantedBy` being passed explicitly in request bodies). `GET ?propertyId=` already returns the full list including the OWNER row; a convenience "current user's role" endpoint can be added once a principal exists to resolve "current user" from.

---

## Testing

All 5 endpoints (18 scenarios incl. the OWNER auto-grant verification, all 4 OWNER-fencing rules, duplicate-grant 409, and 404/400 error paths) tested live against PostgreSQL — **18/18 scenarios passed**.

- Full results: [../api-tests/PropertyAccessController/TEST_RESULTS.md](../api-tests/PropertyAccessController/TEST_RESULTS.md)
- Postman collection: [../api-tests/PropertyAccessController/RentERP-PropertyAccessController.postman_collection.json](../api-tests/PropertyAccessController/RentERP-PropertyAccessController.postman_collection.json)
- Raw responses: [../api-tests/PropertyAccessController/responses/](../api-tests/PropertyAccessController/responses/)

No issues found during testing — the OWNER-fencing rules and duplicate-grant constraint all behaved as designed on the first pass, likely because the FK-existence-check and soft-delete patterns were already established by `UserController`/`PropertyController` before this one was written.

`updatedAt` freshness confirmed correct (test 9 shows `updatedAt` newer than `createdAt` after the role-change `PUT`) — `PropertyAccess` extends `BaseAuditEntity` and the service uses `saveAndFlush()` on update/revoke, per the established convention.

---

## Edge cases handled

Per the DEVLOG.md SOP (read the matching Edge Case Register subsection before writing the service layer): this controller's domain doesn't map to a specific §14 subsection — it's platform/access plumbing rather than meter/billing/tenancy/vacancy/payment logic. The relevant register entries are **T15** (ownership transfer — deliberately *not* handled here, see below) and the general §4.2 capability matrix, which this table exists to eventually enforce once auth lands.

- **T15 (ownership transfer)** — explicitly deferred. This controller only ever manages MANAGER/VIEW_ONLY grants plus the auto-created OWNER row; changing who holds OWNER is out of scope until `property_ownership_transfers` and its dedicated workflow are built.

---

## Open items
- [ ] No RBAC enforcement yet — `POST`/`PUT`/`DELETE` here aren't restricted to callers who themselves have OWNER access on the target property. Same open item as `UserController`'s role self-assignment and `PropertyController`'s update/delete authorization — all three need the same JWT-auth-derived principal once it exists.
- [ ] `property_ownership_transfers` — the only sanctioned path to change an OWNER grant — still not built (tracked in `CONTROLLER_TABLE_MAP.md` against `PropertyController`).
- [ ] Add `GET /api/v1/property-access?active=true` filter when frontend needs it (same open item pattern as `UserController`/`PropertyController`).
- [ ] A "current user's access level for property X" convenience lookup once an authenticated principal exists — see "Design decisions" above.
