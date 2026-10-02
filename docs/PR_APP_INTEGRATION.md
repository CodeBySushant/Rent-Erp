# PR: App integration (`feature/app-integration` → `main`)

> Draft, kept current while the branch is open. Paste into the pull request
> when the work is complete.
>
> **Status (2026-10-02):** feature work complete; all live scripts pass.
> Remaining before opening the PR: see *Before merging* below.

## Summary

Makes the backend serve the Rentlo Flutter app: authentication, the data the
app's screens need, and fixes found while integrating. Existing behaviour on
`main` is preserved unless a change below says otherwise.

## Changes

| Area | Change | Migration | Tests |
|---|---|---|---|
| Docs | Branch log, PR draft, branch commit log generator; docs pass (controller log, table map, README, context file) | — | — |
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
| Billing flow (app) | No API change; billing-run endpoints exercised as the app's Create Bill / Bills screens use them | — | `billing_flow_test.ps1` |
| Payments | owner record, tenant proof, approve / reject / withdraw, histories; applied once; never above owed; runs with payments not cancellable; `pendingPayments` in summary | V18 | `payments_test.ps1` |
| Readings due | owner due list; tenant meters and own submission (responsibility rules, first reading required, one per month, own photo) | — | `readings_test.ps1` |
| Move-out | notice / withdraw / settle; deposit pays bills then charges, rest refunded; tenancy ended, rooms freed | V19 | `moveout_test.ps1` |
| Room transfer | old room closed, new room opened in one transaction; both rooms locked | — | `transfer_test.ps1` |
| Requests | room change / vacate / maintenance / other; approve, reject, complete, withdraw; vacate → move-out notice, room change → transfer | V20 | `requests_test.ps1` |
| Payment details | QR / wallet / bank per property; owners edit, active tenants read | V21 | `payment_details_test.ps1` |
| Account | change password (other devices signed out), phone / email with a code to the new one, devices, delete account | V22 | `account_test.ps1` |
| Notifications | in-app inbox; created with bills, payments, readings, requests, joins, move-out; device tokens for push later | V23 | `notifications_test.ps1` |

## Breaking changes / migration notes

* Every endpoint except `/api/v1/auth/*` and `/actuator/health` needs `Authorization: Bearer <token>` unless `AUTH_ENFORCED=false` (developer `.env` only). The pre-auth Postman collections run with it set to false.
* With a token, non-admin callers must pass `propertyId` to `/floors`, `/rooms`, `/charge-templates`, `/meters`, `/memberships`, `/join-requests` lists (memberships / join requests also accept the caller's own `tenantProfileId`).
* `ownerUserId` on `POST/GET /properties` is ignored for non-admins (the caller is the owner).

## How to test

1. Reset the database (README → "Resetting the database"), set `APP_ENV=local`
   in `backend/.env`, and start the app (stop any older backend on port 8080 first).
2. `mvn clean install` — 44 unit tests.
3. Run the live PowerShell scripts listed in README → "Live API scripts"
   (16 scripts, all passing on 2026-10-02).
4. The pre-auth Postman collections in `docs/api-tests/` run with
   `AUTH_ENFORCED=false`.

## Known limitations

* No final bill is generated for a partial last month at move-out; the owner
  enters it as "final charges" (or runs that month's billing first).
* Payments are recorded manually or from tenant proofs; no Khalti / eSewa gateway.
* OTP codes are only delivered locally (log); email-change codes need an email sender.
* Files are stored on the local disk (`FileStorage` interface, ready for S3).
* Notifications are in-app only; device tokens are stored for Firebase push later.

## Before merging

1. App clean-up (remaining sample-only screens) and an end-to-end check on a phone.
2. Production settings: SMS and email senders, file storage and backups,
   secrets, CORS, Firebase.
3. Decide: partial-month final bill, billing only some tenants, one-off
   charges in Create Bill.

## Commits

See `docs/BRANCH_COMMITS.md` (regenerate with `scripts/update_branch_log.ps1`).
