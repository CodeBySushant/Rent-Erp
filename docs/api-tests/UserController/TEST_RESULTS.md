# UserController — API Test Results

**Date:** 2026-07-30
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 10/10 passed

Raw responses are saved in [`responses/`](responses/). Import [`RentERP-UserController.postman_collection.json`](RentERP-UserController.postman_collection.json) into the Postman VS Code extension to re-run.

---

## How to re-run in Postman (VS Code extension)

1. Open the Postman panel in VS Code → **Collections** → **Import**
2. Select `RentERP-UserController.postman_collection.json`
3. Make sure the backend is running (`mvn spring-boot:run`)
4. Run request **01** first — it auto-saves the new `userId` into a collection variable
5. Requests 04, 08, 09, 10 reuse that `{{userId}}` automatically

Collection variables:
- `baseUrl` = `http://localhost:8080`
- `userId` = set automatically by request 01

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | Create landlord | POST | `/api/v1/users` | 201 | 201 | ✅ |
| 1b | Create tenant | POST | `/api/v1/users` | 201 | 201 | ✅ |
| 2 | Duplicate phone | POST | `/api/v1/users` | 409 | 409 | ✅ |
| 3 | Invalid phone | POST | `/api/v1/users` | 400 | 400 | ✅ |
| 4 | Get single | GET | `/api/v1/users/{id}` | 200 | 200 | ✅ |
| 5 | Get non-existent | GET | `/api/v1/users/{bad-id}` | 404 | 404 | ✅ |
| 6 | Get all (default page) | GET | `/api/v1/users` | 200 | 200 | ✅ |
| 7 | Get all (page & size) | GET | `/api/v1/users?page=0&size=1` | 200 | 200 | ✅ |
| 8 | Update | PUT | `/api/v1/users/{id}` | 200 | 200 | ✅ |
| 9 | Soft delete | DELETE | `/api/v1/users/{id}` | 200 | 200 | ✅ |
| 10 | Get after delete | GET | `/api/v1/users/{id}` | 200, active=false | 200, active=false | ✅ |

---

## Key responses

### 1 — Create user → `201 Created`
```json
{
  "data": {
    "id": "7e339009-29a2-4c34-83fa-e844d19db719",
    "phone": "9812345678",
    "name": "Ram Shrestha",
    "role": "LANDLORD",
    "kycStatus": "PENDING",
    "active": true,
    "preferredLanguage": "en",
    "createdAt": "2026-07-30T11:11:30.464358Z",
    "updatedAt": "2026-07-30T11:11:30.464360Z"
  },
  "message": "User created successfully",
  "success": true,
  "timestamp": "2026-07-30T11:11:30.695169Z"
}
```

### 2 — Duplicate phone → `409 Conflict`
```json
{
  "message": "User already exists with phone = 9812345678",
  "success": false,
  "timestamp": "2026-07-30T11:12:01.044173Z"
}
```

### 3 — Invalid phone → `400 Bad Request`
```json
{
  "message": "Phone must be a valid 10-digit Nepal number starting with 97 or 98",
  "success": false,
  "timestamp": "2026-07-30T11:12:01.101746Z"
}
```

### 5 — Non-existent id → `404 Not Found`
```json
{
  "message": "User not found with id = 00000000-0000-0000-0000-000000000000",
  "success": false,
  "timestamp": "2026-07-30T11:12:01.326144Z"
}
```

### 6 — Paginated list → `200 OK`
```json
{
  "data": {
    "content": [ /* 2 users */ ],
    "last": true,
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "message": "Users fetched successfully",
  "success": true
}
```

### 7 — Pagination with size=1 → `200 OK`
Shows pagination splitting: `totalElements: 2`, `totalPages: 2`, `last: false` on page 0.
```json
{
  "data": {
    "content": [ /* 1 user */ ],
    "last": false,
    "page": 0,
    "size": 1,
    "totalElements": 2,
    "totalPages": 2
  }
}
```

### 9 — Soft delete → `200 OK`
```json
{
  "message": "User deactivated successfully",
  "success": true
}
```

### 10 — Get after delete → `active: false`
```json
{
  "data": {
    "id": "7e339009-29a2-4c34-83fa-e844d19db719",
    "name": "Ram Bahadur Shrestha",
    "active": false,
    "preferredLanguage": "ne"
  },
  "message": "User fetched successfully",
  "success": true
}
```
Confirms the row still exists (soft delete) with `active=false`.

---

## Issue found during testing

**PUT / write responses can return a stale `updatedAt`.**
In the PUT response (test 8) `updatedAt` still showed the create-time value, even though the DB was updated correctly (test 10 later showed the new timestamp).

**Cause:** The entity's `@PreUpdate` callback that sets `updatedAt` fires at transaction **commit / flush time**, which happens *after* the `UserResponse` DTO is constructed inside the service method. So the DTO captures the pre-update value.

**Impact:** Cosmetic only — the database value is always correct. But a client reading `updatedAt` straight from a write response would see a stale value.

**Fix options (deferred, logged as open item):**
- Set `createdAt` / `updatedAt` explicitly in the service instead of relying on `@PrePersist`/`@PreUpdate`, or
- Call `saveAndFlush()` and re-read, or
- Switch to Spring Data JPA Auditing (`@CreatedDate` / `@LastModifiedDate` with `@EnableJpaAuditing`).

Recommended: adopt JPA Auditing globally before we replicate this pattern across all 14 controllers.
