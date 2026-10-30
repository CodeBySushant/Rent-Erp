# Sign-up without phone OTP (temporary, branch `temp/signup-no-otp`)

**Date:** 2026-10-30
**Branch:** `temp/signup-no-otp`, cut from `feature/app-integration`. Not to be merged into `feature/app-integration` or `main`.
**Why:** the company is not registered with an SMS provider yet, so sign-up codes cannot be texted. For client testing, sign-up creates the account straight away.

## What changed

| File | Change |
|---|---|
| `domain/auth/dto/RegisterRequest.java` | `verificationToken` is no longer `@NotBlank`. It is optional and ignored. The field stays so older app builds and the `docs/api-tests/*` scripts, which still send it, are not rejected. |
| `domain/auth/service/AuthService.java` | `register` no longer calls `otpService.consumeSignupToken(...)`. The new user is saved with `phone_verified_at = null`, because the number was not verified. |
| `src/test/.../AuthServiceRegisterTest.java` | 4 tests: register works with no token, a stale token is ignored, and a taken phone or email is still refused (409 `PHONE_TAKEN` / `EMAIL_TAKEN`). |

## What did not change

- Every other `/auth/register` rule still applies: name/phone/email format, password policy, LANDLORD or TENANT role, the 10-per-hour-per-IP limit, unique phone and unique email.
- `/auth/otp/request` and `/auth/otp/verify` still exist. The app's sign-up no longer calls them. Phone **login** still uses OTP and still needs SMS. Owners and tenants sign in with email + password, which needs no SMS.
- No migration and no schema change. `phone_verified_at` was already nullable. A later phone login or phone password reset sets it.

## API now

`POST /api/v1/auth/register`
`{name, phone, email, password, role, preferredLanguage}` → 201 `AuthResponse` (logged in).
A `verificationToken` in the body is accepted and ignored.

## Turning OTP back on

Use `feature/app-integration`, which never had this change, or revert this branch's commit. Accounts created on this branch keep `phone_verified_at = null` until the user verifies by phone login or phone reset.
