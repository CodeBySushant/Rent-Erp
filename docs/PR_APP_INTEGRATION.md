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

## Breaking changes / migration notes

* **Every endpoint except `/api/v1/auth/*` and `/actuator/health` now needs a bearer token.** Set `AUTH_ENFORCED=false` in a local `.env` to run the earlier Postman collections unchanged.
* `POST /properties`: the owner is the caller; `ownerUserId` in the body is used only by an admin.
* `GET /properties?ownerUserId=` is ignored for non-admins (they see their own properties).
* `POST /users` and `GET /users` are admin-only (accounts are created by `/auth/register`).
* Error bodies gained a `code` field (additive).

## How to test

1. Reset the database (README → "Resetting the database") and start the app.
2. Run the Postman collections in `docs/api-tests/` in README order, plus any
   collections added on this branch.
3. Run `mvn test`.

## Commits

See `docs/BRANCH_COMMITS.md` (regenerate with `scripts/update_branch_log.ps1`).
