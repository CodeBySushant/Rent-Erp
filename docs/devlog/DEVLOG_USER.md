# UserController — Detailed Dev Log

**Controller:** `UserController`
**Base URL:** `/api/v1/users`
**Table:** `users`
**Status:** Complete

---

## Files Created

| File | Purpose |
|------|---------|
| `common/response/ApiResponse.java` | Universal response envelope for all APIs |
| `common/response/PagedResponse.java` | Pagination metadata wrapper |
| `common/exception/ResourceNotFoundException.java` | Thrown when a row is not found → 404 |
| `common/exception/DuplicateResourceException.java` | Thrown when a unique field already exists → 409 |
| `common/exception/GlobalExceptionHandler.java` | Catches all exceptions app-wide, returns consistent ApiResponse |
| `domain/auth/dto/CreateUserRequest.java` | Request body for POST /users |
| `domain/auth/dto/UpdateUserRequest.java` | Request body for PUT /users/{id} |
| `domain/auth/dto/UserResponse.java` | What the API returns — never the raw entity |
| `domain/auth/service/UserService.java` | All business logic |
| `domain/auth/controller/UserController.java` | HTTP layer — 5 endpoints |

---

## ApiResponse — how every response is shaped

Every single API in this project returns this structure:

**Success:**
```json
{
  "success": true,
  "message": "User created successfully",
  "data": { ... },
  "timestamp": "2026-07-30T10:00:00Z"
}
```

**Failure:**
```json
{
  "success": false,
  "message": "User not found with id = abc-123",
  "data": null,
  "timestamp": "2026-07-30T10:00:00Z"
}
```

`@JsonInclude(NON_NULL)` on ApiResponse means `data: null` is omitted from the JSON on failure responses, keeping it clean.

---

## Pagination — line by line explanation

**Endpoint:** `GET /api/v1/users?page=0&size=20&sort=createdAt,desc`

### What each query param means
| Param | Default | Meaning |
|-------|---------|---------|
| `page` | 0 | Which page to fetch. 0 = first page, 1 = second page, etc. |
| `size` | 20 | How many records to return per page |
| `sort` | createdAt,desc | Which column to sort by, and in which direction |

### What PagedResponse returns
```json
{
  "content": [ ...20 users... ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "last": false
}
```
- `content` — the actual list of users for this page
- `page` — which page this is (0-based)
- `size` — how many per page
- `totalElements` — total users in the database
- `totalPages` — 150 users ÷ 20 per page = 8 pages
- `last` — false means there are more pages after this one

### How Spring handles this automatically
`@PageableDefault(size = 20, sort = "createdAt", direction = DESC)` on the controller method tells Spring: if the caller doesn't pass `?page` or `?size`, use these defaults. Spring reads the query params, builds a `Pageable` object, and passes it to the repository. `findAll(pageable)` in Spring Data JPA automatically adds `LIMIT`, `OFFSET`, and `ORDER BY` to the SQL query.

---

## APIs

### POST /api/v1/users
**Purpose:** Register a new user in the system.

**Request body:**
```json
{
  "phone": "9812345678",
  "name": "Ram Shrestha",
  "role": "LANDLORD",
  "preferredLanguage": "en"
}
```

**Rules:**
- `phone` is required. Must be a 10-digit Nepal number starting with 97 or 98.
- `name` is optional.
- `role` defaults to `LANDLORD` if not provided. Accepted: `LANDLORD`, `TENANT`, `ADMIN`.
- `preferredLanguage` defaults to `en`. Accepted: `en`, `ne`.
- Phone must be unique — duplicate triggers 409 Conflict.

**Response:** `201 Created` with the created user (no fcmToken exposed).

**Logic flow:**
1. `@Valid` triggers Bean Validation on the request body
2. `userService.createUser()` checks if phone already exists
3. If duplicate → throws `DuplicateResourceException` → `GlobalExceptionHandler` returns 409
4. Builds `User` entity, saves, returns `UserResponse`

---

### GET /api/v1/users/{id}
**Purpose:** Fetch a single user by UUID.

**Rules:**
- If user does not exist → 404 Not Found.
- Returns inactive users too (is_active=false). Filtering by active status is a separate concern.

**Response:** `200 OK` with user data.

---

### GET /api/v1/users
**Purpose:** Fetch all users with pagination.

**Query params:**
```
?page=0&size=20&sort=createdAt,desc
```

**Response:** `200 OK` with `PagedResponse` containing content + pagination metadata.

**Note:** Returns ALL users including inactive ones. Frontend can filter by `active` field. A dedicated `GET /api/v1/users?active=true` filter can be added later when needed.

---

### PUT /api/v1/users/{id}
**Purpose:** Update a user's profile fields.

**Request body (all fields optional — only provided fields are updated):**
```json
{
  "name": "Updated Name",
  "preferredLanguage": "ne",
  "fcmToken": "firebase-token-here"
}
```

**Rules:**
- Only `name`, `preferredLanguage`, `fcmToken` can be updated via this endpoint.
- `phone` and `role` are not updatable here (phone = identity, role = admin concern).
- Null fields are ignored — only non-null fields are applied.
- If user not found → 404.

**Response:** `200 OK` with updated user.

---

### DELETE /api/v1/users/{id}
**Purpose:** Deactivate a user (soft delete).

**Rules:**
- Sets `is_active = false`. Row is NEVER deleted from the database.
- Idempotent — if user is already inactive, silently succeeds (no error).
- If user not found → 404.

**Response:** `200 OK` with success message, no data body.

---

## Exception handling

| Exception | HTTP Status | When thrown |
|-----------|-------------|-------------|
| `ResourceNotFoundException` | 404 | User ID does not exist |
| `DuplicateResourceException` | 409 | Phone number already registered |
| `MethodArgumentNotValidException` | 400 | `@Valid` fails (e.g. bad phone format) |
| `Exception` (catch-all) | 500 | Anything unexpected |

All caught by `GlobalExceptionHandler` — controllers have zero try/catch blocks.

---

## Design decisions

**Why DTOs instead of returning the entity directly?**
The `User` entity is a database object — it contains internal fields (`fcmToken`, JPA annotations, etc.) that should not leak to the API consumer. `UserResponse` is the public contract. If the DB schema changes, the API shape can stay the same.

**Why soft delete?**
Audit compliance. A user's history (bills, readings, payments) must remain intact even after they leave. Hard delete would orphan those records. `is_active = false` keeps the row while excluding the user from active queries.

**Why only name/language/fcmToken updatable?**
- `phone` = identity — changing it is an auth operation, not a profile update
- `role` = admin assignment — will be a separate privileged endpoint
- `kycStatus` = set by the admin KYC flow, not by a generic update

---

## Testing

All 5 endpoints tested live against PostgreSQL — **10/10 scenarios passed** (incl. 409/400/404 error paths and pagination).

- Full results: [../api-tests/UserController/TEST_RESULTS.md](../api-tests/UserController/TEST_RESULTS.md)
- Postman collection: [../api-tests/UserController/RentERP-UserController.postman_collection.json](../api-tests/UserController/RentERP-UserController.postman_collection.json)
- Raw responses: [../api-tests/UserController/responses/](../api-tests/UserController/responses/)

## [2026-07-30] Fix — stale `updatedAt` in write responses (JPA Auditing)

**Status:** ✅ Fixed & verified

The stale-`updatedAt` issue found during testing is resolved by adopting Spring Data JPA Auditing globally.

**Files added:**
| File | Purpose |
|------|---------|
| `config/JpaAuditingConfig.java` | `@EnableJpaAuditing` — turns on auditing app-wide |
| `common/entity/BaseAuditEntity.java` | `@MappedSuperclass` with `@CreatedDate createdAt` + `@LastModifiedDate updatedAt`, `@EntityListeners(AuditingEntityListener.class)` |

**Files changed:**
- `User.java` — now `extends BaseAuditEntity`; removed its own `createdAt`/`updatedAt` fields and the manual `@PrePersist`/`@PreUpdate`. Switched `@Builder` → `@SuperBuilder` (required for Lombok builder across an inheritance hierarchy; `BaseAuditEntity` also gets `@SuperBuilder` + `@NoArgsConstructor`).
- `OtpAttempt.java`, `UserSession.java` — append-only (created_at only, no updated_at column), so they don't extend BaseAuditEntity. Instead annotated with `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate` and dropped their manual `@PrePersist`.
- `UserService.java` — `updateUser()` and `deleteUser()` now use `saveAndFlush()` instead of `save()`.

**Why `saveAndFlush` matters (the actual fix):**
Auditing alone does NOT fix the timing — `@LastModifiedDate` is still applied by the `AuditingEntityListener` via the `@PreUpdate` lifecycle hook, which fires at flush/commit. With a plain `save()` inside a `@Transactional` method, that flush happens at commit — *after* the `UserResponse` DTO is built — so the response would still carry the old value. `saveAndFlush()` forces the flush (and thus the listener) to run *before* we build the DTO, so the managed entity already holds the fresh `updatedAt`. Create stays on `save()` — `@PrePersist` runs synchronously during `persist()`, so createdAt is already correct there.

**Verified:** create → updatedAt == createdAt; then update → updatedAt is ~1s later than createdAt. See [responses/11_update_after_auditing_fix.txt](../api-tests/UserController/responses/11_update_after_auditing_fix.txt).

**Pattern for future controllers:** mutable entities extend `BaseAuditEntity`; append-only entities use `@CreatedDate` + `AuditingEntityListener` directly; services use `saveAndFlush()` on update/delete when the response must reflect the new `updatedAt`.

## Open items
- [ ] Add `GET /api/v1/users?active=true` filter when frontend needs it
- [ ] Add role-based access control once auth is implemented (only ADMIN can list all users)
- [ ] Add `GET /api/v1/users/phone/{phone}` lookup for internal use by auth service
