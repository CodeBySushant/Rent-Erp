# PR: App integration (`feature/app-integration` → `main`)

> Draft, kept current while the branch is open. Paste into the pull request
> when the work is complete.

## Summary

Makes the backend serve the Rentlo Flutter app: authentication, the data the
app's screens need, and fixes found while integrating. Existing behaviour on
`main` is preserved unless a change below says otherwise.

## Changes

| Area | Change | Migration | Tests |
|---|---|---|---|
| Docs | Branch log, PR draft, branch commit log generator | — | — |
| Auth | `/api/v1/auth/*`: OTP sign-up and login, email + password login, refresh (rotating), logout, me. Tokens required for every other endpoint. | V13 | unit + `auth_test.ps1` |
| Authorization | `AccessGuard`; users (self/admin), properties and property access (by grant). `GET /properties` scoped to the caller. | — | `AccessGuardTest`, `auth_test.ps1` |
| Errors | `code` on every error; 400 for malformed input / bad ids; 409 for constraint violations; 401/403/410/429/503 | — | `auth_test.ps1` |
| Hindi | `hi` accepted for users and tenant profiles | V13 | `auth_test.ps1` |
| Authorization (all domains) | `ResourceAccess` on every controller: property access for structure, charges, meters, readings, tenancy, finance, billing; tenants limited to their own membership and its bills/finance; tariffs admin-only to write; non-admin lists need `propertyId`; `tenant_profiles.created_by` | V14 | `authz_test.ps1` |
| Files | `POST/GET/DELETE /files`, protected `/files/{id}/content`; type from bytes, 5 MB, private by default; local storage behind `FileStorage` | V15 | unit + `files_test.ps1` |
| Dashboard | `GET /dashboard`, `/properties/{id}/summary`, `/properties/{id}/tenants`; `BsCalendar.today()` | — | `BsCalendarTest`, `dashboard_test.ps1` |
| Add Tenant | `POST /properties/{id}/tenants`: profile + membership + room + deposit in one transaction, room locked, vacancy / ownership / duplicate checks | V16 | `add_tenant_test.ps1` |
| Join by code | `properties.join_code`; `GET/POST /join/{code}` with member / pending / own-property checks and a look-up limit | V17 | `JoinCodeGeneratorTest`, `join_test.ps1` |
| My Stay | `GET /me/stay`: tenancies, landlord, rooms, rent, deposit, notice, current bill, owed; pending requests | — | `mystay_test.ps1` |

## Breaking changes / migration notes

* Every endpoint except `/api/v1/auth/*` and `/actuator/health` needs `Authorization: Bearer <token>` unless `AUTH_ENFORCED=false` (developer `.env` only). The pre-auth Postman collections run with it set to false.
* With a token, non-admin callers must pass `propertyId` to `/floors`, `/rooms`, `/charge-templates`, `/meters`, `/memberships`, `/join-requests` lists (memberships / join requests also accept the caller's own `tenantProfileId`).
* `ownerUserId` on `POST/GET /properties` is ignored for non-admins (the caller is the owner).

## How to test

1. Reset the database (README → "Resetting the database") and start the app.
2. Run the Postman collections in `docs/api-tests/` in README order, plus any
   collections added on this branch.
3. Run `mvn test`.

## Commits

See `docs/BRANCH_COMMITS.md` (regenerate with `scripts/update_branch_log.ps1`).
