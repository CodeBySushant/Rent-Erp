# Join by property code — Detailed Dev Log

**Endpoints:** `GET /api/v1/join/{code}`, `POST /api/v1/join/{code}`; `joinCode` on every property response
**Migration:** V17 — `properties.join_code` (unique, `^[A-Z]{2}-[A-Z]{3}-[0-9]{4}$`, back-filled for existing rows)
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/JoinByCode/join_test.ps1`

## Flow

1. Owner shares the code (Property Access screen: text, copy, QR).
2. Tenant enters or scans it → `GET /join/{code}`: property name, address, city, landlord name, and whether they already live there or already asked. Never the landlord's phone.
3. Tenant confirms → `POST /join/{code}` (optional message). If the account has no tenant profile, one is created, linked to it and filled from it. A PENDING join request is created through the existing `JoinRequestService` (blocked-tenant rule, expiry).
4. Owner sees it in the property's join requests and accepts (`/join-requests/{id}/accept`) → membership. Nothing becomes active before that.

## Rules

| Case | Answer |
|---|---|
| Unknown or deactivated property | 404 `JOIN_CODE_NOT_FOUND` |
| Already an active member | 409 `ALREADY_MEMBER` |
| Request already pending | 409 `REQUEST_PENDING` |
| Owner/manager of that property | 400 `OWN_PROPERTY` |
| More than 20 look-ups in 10 minutes (per account) | 429 `JOIN_LOOKUP_LIMIT` |
| Not signed in | 401 |

## Codes

`JoinCodeGenerator`: two letters from the name, three from the city (first letter + consonants), four random digits — "Shrestha Residency", "Kathmandu" → `SR-KTH-4821`. Accents are stripped; names without Latin letters get random letters. Codes are unique (a clash draws new digits). The code is unrelated to the UUID and never changes.

## Notes

The look-up limit is in memory (per backend instance); with several instances behind a load balancer it would need a shared store.
