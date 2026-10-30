# No phone OTP (temporary, branch `temp/signup-no-otp`)

**Date:** 2026-10-30, extended 2026-10-31
**Branch:** `temp/signup-no-otp`, cut from `feature/app-integration`. Not to be merged. `feature/app-integration` keeps the full OTP flow.
**Why:** the company is not registered with an SMS provider yet. For client testing, this branch has no phone OTP anywhere: accounts are email + password.

## What this branch has

| Flow | Endpoint(s) | Notes |
|---|---|---|
| Sign up | `POST /auth/register {name, phone, email, password, role, preferredLanguage}` | No code, no `verificationToken`. The phone is stored but never verified (`phone_verified_at = null`). Phone and email must still be unique. |
| Log in | `POST /auth/login/password {email, password}` | The only login. |
| Forgot password | `POST /auth/password/forgot {email}` → `POST /auth/password/reset` | **Email only.** The code is emailed (MAIL_* set). With `APP_ENV=local` and no MAIL_HOST it is written to the backend log as `DEV email code for …`; elsewhere 503 `EMAIL_NOT_CONFIGURED`. |
| Account | `/me/password`, `/me/photo`, `/me/sessions…`, `DELETE /me` | Unchanged. |

## Removed

| Removed | Was |
|---|---|
| `POST /auth/otp/request`, `POST /auth/otp/verify` | sign-up / login codes by SMS |
| `POST /auth/login` | phone + OTP login |
| `POST /me/phone/otp`, `/me/phone`, `/me/email/otp`, `/me/email` | change phone / email with a code |
| `{phone}` in `/auth/password/forgot` | reset code by SMS |
| `domain/auth/otp/*` (`OtpSender`, `LocalLogOtpSender`, `UnconfiguredOtpSender`) | the SMS sender seam |
| `RequestOtpRequest`, `VerifyOtpRequest`, `VerificationTokenResponse`, `RegisterRequest.verificationToken`, `AccountRequests.NewPhone/NewEmail/ConfirmCode` | their DTOs |
| `OtpService.issueVerificationToken` / `consumeSignupToken`, `app.auth.otp.verification-token-minutes` | the sign-up proof token |

`OtpService` now only issues Forgot Password's email code. Phone numbers get no code: `issue` rejects them, and `canDeliver` returns false for them.

## Kept on purpose

- **No migration and no schema change.** `otp_attempts` and its columns, and every `OtpAttempt.Purpose` value, stay. That way this branch and `feature/app-integration` can share one local database: rows written by the OTP branch still load here.
- The housekeeping job still prunes `otp_attempts`.
- `docs/api-tests/AuthController/auth_test.ps1` and `Account/account_test.ps1` test the removed endpoints. Each is marked at the top to run on `feature/app-integration`. Every other script's `Register` helper now calls `/auth/register` directly.

## Tests

- `AuthServiceRegisterTest` (3): register with no verification at all, taken phone, taken email.
- `OtpServiceDeliveryTest` (6): email only, local log fallback, 503 in production without mail, phones refused.
- `OtpServiceFixedCodeTest` (4): the dev fixed code.
- `PasswordResetServiceTest` (8): email only, phones never looked up.

## Turning OTP back on

Use `feature/app-integration`. Accounts made here keep `phone_verified_at = null` until the user verifies by phone there.
