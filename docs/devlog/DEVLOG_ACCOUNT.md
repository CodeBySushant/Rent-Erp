# Account self-service — Detailed Dev Log

**Endpoints (`AccountController`, signed-in user only):**

| Method | Path | |
|---|---|---|
| POST | `/api/v1/me/password` `{currentPassword?, newPassword}` | `{otherDevicesSignedOut}` |
| POST | `/api/v1/me/phone/otp` `{phone}` → POST `/api/v1/me/phone` `{verificationId, code}` | new phone, code sent to it |
| POST | `/api/v1/me/email/otp` `{email}` → POST `/api/v1/me/email` `{verificationId, code}` | new email, code sent to it |
| GET | `/api/v1/me/sessions` | signed-in devices, `current` marks this one |
| DELETE | `/api/v1/me/sessions/{id}` | sign out one device |
| POST | `/api/v1/me/sessions/end-others` | `{ended}` |
| DELETE | `/api/v1/me` `{confirm: "DELETE", password?}` | delete the account |

**Migration:** V22 — `otp_attempts.phone` widened to 254 (it now holds the code's destination, phone or email); purpose `CHANGE_EMAIL` added.
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/Account/account_test.ps1`

## Rules

* **Password:** current password required when one is set (400 `PASSWORD_INCORRECT`); `PasswordPolicy` (400 `WEAK_PASSWORD`); must differ (400 `PASSWORD_UNCHANGED`); clears the login lock; signs out every other session, this one keeps working.
* **Phone / email:** same value 400; already used by another account 409 `PHONE_TAKEN` / `EMAIL_TAKEN` (checked again at confirm). The code goes to the new destination (purpose `CHANGE_PHONE` / `CHANGE_EMAIL`, normal OTP limits). The challenge is bound to the user who asked: anyone else gets 410 *before* the code is tried, so it is not used up. The user's own tenant profile follows a phone change.
* **Devices:** only the user's own active sessions; ending another user's id → 404.
* **Delete:** `confirm` must be `DELETE`; password when one is set. Refused while still in use: owner with active tenants (409 `HAS_ACTIVE_TENANTS`), tenant with an active tenancy (409 `ACTIVE_TENANCY`), unpaid issued bills (409 `UNPAID_BILLS`). Then: user deactivated, name / phone / email / password / FCM token removed (phone replaced by a random placeholder so the number can register again), own tenant profile unlinked (owner's history kept), all sessions revoked.

## Production note

Email codes go through the same `OtpSender` as SMS codes. Locally they are written to the log; production needs an email sender before email change is enabled there.

## App

Profile → Security (live): change password; change phone / email with a code to the new one; signed-in devices with sign-out and "sign out all other devices"; delete account (password + type DELETE) then back to Login.
