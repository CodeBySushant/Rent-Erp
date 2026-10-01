# AuthController — Detailed Dev Log

**Controller:** `AuthController` (+ security filter chain, `AccessGuard`)
**Base URL:** `/api/v1/auth`
**Tables:** `users`, `otp_attempts`, `user_sessions` (V1, extended by V13)
**Status:** Implemented on `feature/app-integration`; live test script `docs/api-tests/AuthController/auth_test.ps1`

---

## Files

| File | Purpose |
|------|---------|
| `db/migration/V13__auth_sessions_and_hindi.sql` | users: email, password_hash, verified-at, lockout; otp_attempts: purpose, verification token, user_id; user_sessions: last_used_at; Hindi allowed for users and tenant_profiles |
| `domain/auth/controller/AuthController.java` | 8 endpoints |
| `domain/auth/service/AuthService.java` | Sign-up, OTP login, password login, refresh, logout, me |
| `domain/auth/service/OtpService.java` | Issue / check codes, sign-up verification token |
| `domain/auth/service/AuthTokenService.java` | Sessions, refresh-token rotation, revocation |
| `domain/auth/service/LoginAttemptService.java` | Password lockout counter (own transactions) |
| `domain/auth/service/PasswordPolicy.java` | 8–72 chars, a letter and a digit |
| `domain/auth/otp/*` | `OtpSender`; `LocalLogOtpSender` (APP_ENV=local only); `UnconfiguredOtpSender` (503 elsewhere) |
| `domain/auth/security/JwtService.java` | HS256 access tokens, JDK crypto only |
| `domain/auth/security/JwtAuthenticationFilter.java` | Bearer token → `AuthUser`, session checked per request |
| `domain/auth/security/AccessGuard.java` | Property / account authorization from the token |
| `domain/auth/security/ResourceAccess.java` | Per-resource checks used by every controller |
| `db/migration/V14__tenant_profile_created_by.sql` | `tenant_profiles.created_by` |
| `domain/auth/security/Rest*`, `JsonErrorWriter` | 401 / 403 bodies in the standard shape |
| `config/SecurityConfig.java` | Public vs protected routes, `PasswordEncoder` |
| `common/exception/ApiException.java`, `GlobalExceptionHandler.java` | Status + `code` on every error |

## Endpoints

| Method | Path | Auth | Body → Response |
|---|---|---|---|
| POST | `/otp/request` | public | `{phone, purpose: SIGNUP\|LOGIN}` → `{verificationId, expiresInSeconds, resendAfterSeconds}` |
| POST | `/otp/verify` | public | `{verificationId, code}` → `{verificationToken, expiresInSeconds}` (sign-up) |
| POST | `/register` | public | `{name, phone, email, password, role, preferredLanguage, verificationToken}` → 201 `AuthResponse` |
| POST | `/login` | public | `{verificationId, code}` → `AuthResponse` |
| POST | `/login/password` | public | `{email, password}` → `AuthResponse` |
| POST | `/refresh` | public | `{refreshToken}` → `AuthResponse` (rotated) |
| POST | `/logout` | token | → ends this session |
| GET | `/me` | token | → `UserResponse` |

`AuthResponse = {user, accessToken, refreshToken, tokenType:"Bearer", accessTokenExpiresInSeconds, refreshTokenExpiresInSeconds}`.
`UserResponse` gained `email`; it never contains the password hash.

## How it works

**Tokens.** Access token: HS256 JWT `{sub, sid, role, iat, exp}`, 15 minutes, signed with `JWT_SECRET` (≥ 32 chars, startup fails otherwise). Refresh token: 32 random bytes, stored as SHA-256 in `user_sessions.token_hash`, 30 days, **rotated on every refresh** under a row lock — the old one stops working immediately. The filter checks the session on every request, so logout/revocation is immediate rather than waiting for the JWT to expire.

**OTP.** 6 digits, BCrypt-hashed, 5-minute life. Per phone + purpose: one new code per 45 s, five per hour. Five wrong tries burn the code. Checking runs in its own transaction (`REQUIRES_NEW`, no rollback on rejection) so wrong-try counts persist even though the request fails. Codes are never valid across purposes. Sign-up returns a single-use verification token (15 minutes) that `/register` spends inside its transaction — a failed registration leaves it unspent.

**SMS.** No provider yet. `APP_ENV=local`: the code is written to the backend log (`DEV OTP for <phone> (<purpose>): <code>`). Any other environment: `/otp/request` answers 503 `SMS_NOT_CONFIGURED`. Codes are never returned by the API and never logged outside local.

**Passwords.** BCrypt. Email login is case-insensitive (`uq_users_email_lower`). Five wrong passwords in a row lock password login for 15 minutes (phone OTP still works). Unknown emails take as long as wrong passwords (dummy hash compare) and get the same 401, so accounts cannot be enumerated through the password endpoint.

**Authorization.** `AccessGuard` decides from the token: property rights from `property_access` (OWNER > MANAGER > VIEW_ONLY), self-or-admin for user records, admin for user listing/creation. Applied in this pass to `UserController`, `PropertyService`, `PropertyAccessService`. `GET /properties` returns only the caller's properties; `ownerUserId` is honoured for admins only; `POST /properties` makes the caller the owner.

**Every other domain (2026-10-02).** `ResourceAccess` resolves each resource to its property and asks `AccessGuard` — READ needs VIEW_ONLY, WRITE needs MANAGER. Controllers call it before the service, so services keep calling each other without re-checking. Applied to floors, rooms, charge templates, meters (+ coverage, infra scope), readings (+ corrections, replacement), coverage events, tenant profiles (+ KYC), join requests, memberships (+ room assignments), blocked tenants, deposits, advance rent, opening balances, rent increments, billing runs, bills, adjustments, corrections; tariffs are readable by anyone signed in and writable by admins only.
* A tenant with an app account reads their own membership and everything under it (bills, deposit, advance rent, increments, assignments) and never writes through these endpoints; they can create their own linked profile, ask to join, and withdraw their request.
* Tenant profiles belong to no single property: visible to their own account, their creator (`tenant_profiles.created_by`, V14) and anyone with access to a property where they have a membership or join request. `GET /tenant-profiles` returns only those. KYC decisions are never the tenant's own.
* List endpoints with an optional property filter (`/floors`, `/rooms`, `/charge-templates`, `/meters`, `/memberships`, `/join-requests`) require `propertyId` from a non-admin (400 `PROPERTY_ID_REQUIRED`); memberships / join requests may instead filter by the caller's own `tenantProfileId`.
* An unknown id still passes the check and gets the service's 404; an existing resource of someone else is 403.

**`AUTH_ENFORCED`.** Default true. A developer can set `AUTH_ENFORCED=false` in `backend/.env` to run the pre-auth Postman collections: requests without a token then pass as before, but any request that does carry a token is still checked. Startup logs a warning when off.

## Error codes

| Status | Codes |
|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `MISSING_PARAMETER`, `OTP_INCORRECT`, `PASSWORD_WEAK`, `ROLE_NOT_ALLOWED`, `VERIFICATION_PHONE_MISMATCH`, `INVALID_OPERATION` |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `TOKEN_INVALID`, `SESSION_REVOKED`, `INVALID_CREDENTIALS`, `REFRESH_INVALID`, `ACCOUNT_DISABLED` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND`, `ACCOUNT_NOT_FOUND` |
| 409 | `PHONE_TAKEN`, `EMAIL_TAKEN`, `DUPLICATE`, `DATA_CONFLICT` |
| 410 | `OTP_EXPIRED`, `VERIFICATION_INVALID`, `VERIFICATION_EXPIRED` |
| 429 | `OTP_RESEND_TOO_SOON`, `OTP_RATE_LIMITED`, `OTP_TOO_MANY_ATTEMPTS`, `LOGIN_LOCKED` |
| 503 | `SMS_NOT_CONFIGURED` |

## Tests

* Unit: `JwtServiceTest` (round trip, expiry, tampered payload, wrong secret, garbage, short secret), `TokenHasherTest`, `PasswordPolicyTest`, `AccessGuardTest` (owner vs other owner, viewer vs manager, self vs other account, 401 without token, enforcement off, admin).
* Live: `docs/api-tests/Authorization/authz_test.ps1` — owner vs owner and tenant vs tenant across every domain, the join flow, list scoping.
* Live: `docs/api-tests/AuthController/auth_test.ps1` — sign-up, rate limits, wrong / reused codes, register, me, tampered token, password login + case, refresh rotation + reuse, logout + revoked session, OTP login, IDOR on properties / users / access grants, malformed JSON, bad UUID, password lockout. Results: `TEST_RESULTS.md`.

## Open items

* Sparrow SMS sender (replaces `UnconfiguredOtpSender` outside local).
* An unlinked tenant profile (added by an owner) is linked to the account when that tenant registers — tenancy pass.
