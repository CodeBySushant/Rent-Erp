# Forgotten password + email delivery — Dev Log

**Date:** 2026-10-28
**Branch:** `feature/app-integration`
**Migration:** `V28__password_reset.sql` (V27 → V28)
**App side:** `Renterp-frontend` commit `feat(auth): reset a forgotten password from Log In`

## Why

Log In → **Forgot Password?** had nothing behind it: there was no reset
endpoint, so the app switched people to phone-code login instead. Someone
whose phone could not receive a code (or who had changed number) had no way
back into their account.

Second gap found on the way: nothing could send an email. Codes for an email
address (email change, and now password reset) went to the same `OtpSender`
as SMS — the backend log in local, a 503 everywhere else.

## What changed

| Piece | Change |
|---|---|
| Schema | `otp_attempts.chk_otp_purpose` allows `RESET_PASSWORD` |
| `OtpAttempt.Purpose` | `RESET_PASSWORD` |
| `POST /api/v1/auth/password/forgot` | Public. Body `{email}` **or** `{phone}` (exactly one). Sends a 6-digit code to that address when it is the email or phone of an active account → `{verificationId, expiresInSeconds, resendAfterSeconds}` |
| `POST /api/v1/auth/password/reset` | Public. Body `{verificationId, code, newPassword}`. Sets the password, signs out **every** device, opens a session for this one → `AuthResponse` (same as a login) |
| `PasswordResetService` | New — both steps |
| `common/mail/MailGateway` | New — plain-text SMTP email. Off unless `MAIL_HOST` is set |
| `OtpService` | Codes for an email address go by email when `MAIL_HOST` is set (subject/body per purpose); otherwise to the `OtpSender` as before. New `canDeliver(destination)` and `decoyChallenge()` |
| `OtpSender` | `available()` (default true); `UnconfiguredOtpSender` returns false and says `EMAIL_NOT_CONFIGURED` for an email address |
| `SecurityConfig` | The two endpoints are public |
| `pom.xml` | `spring-boot-starter-mail` (JavaMail + Spring's `JavaMailSenderImpl`) |
| `application.yml`, `.env.example` | `app.mail.*` ← `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_STARTTLS`, `MAIL_SSL` |

## Rules

* **No account enumeration by email.** For an address with no active
  account, `/forgot` answers exactly like a real one, with a decoy
  `verificationId` that no code matches (any code → 410 `OTP_EXPIRED`).
  When codes cannot be sent to that kind of address at all, the 503 comes
  *before* the account lookup, so it says nothing about the account either.
  Known limit: a second request for a real address within 45 s gets
  `OTP_RESEND_TOO_SOON`, a decoy does not. Phone numbers were already
  discoverable through `/otp/request` (LOGIN → `ACCOUNT_NOT_FOUND`).
* **Same code rules as every OTP:** 5 minutes, 5 wrong tries burn it, one per
  45 s and five per hour per address, plus 10 `/forgot` per hour per client
  address and the shared 30-per-15-minutes code-check limit. A login or
  sign-up code never resets a password (purposes never cross).
* **The password policy is checked before the code** — 400 `WEAK_PASSWORD`
  leaves the code usable, so a typo in the new password does not cost a code.
* **The address must still be the account's** when the code is used (an
  email/phone changed in between → 410 `OTP_EXPIRED`).
* **On success:** new BCrypt hash, password-login lockout cleared, the
  address is marked verified (it just received the code), every session and
  push token of the account ended, a new session opened for this device.

## Email (MailGateway)

* `MAIL_HOST` empty (default): no mail sender exists at all. Email codes go to
  the `OtpSender` — the log in local, 503 `EMAIL_NOT_CONFIGURED` elsewhere.
* `MAIL_HOST` set: `MAIL_FROM` is required (startup fails without it). Port
  587 + STARTTLS by default; `MAIL_SSL=true` for port 465. 10-second
  connect/read/write timeouts. A refused send → 503 `EMAIL_NOT_SENT` and,
  because issuing a code is one transaction, the code is not counted.
* Built by hand instead of Spring Boot's mail auto-configuration (which only
  activates on `spring.mail.host`, never set here), so there is no mail
  health indicator that could turn `/actuator/health` DOWN.

Gmail works for a start: `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`,
`MAIL_USERNAME=<address>`, `MAIL_PASSWORD=<App Password>` (Google account →
Security → 2-Step Verification → App passwords), `MAIL_FROM=Rentlo <address>`.
Use a transactional provider (SES, Postmark, Brevo…) before real traffic.

## Error codes

| Status | Code | When |
|---|---|---|
| 400 | `IDENTIFIER_REQUIRED` | neither or both of email / phone |
| 400 | `VALIDATION_FAILED` | bad email or phone format, code not 6 digits |
| 400 | `WEAK_PASSWORD` | new password breaks the policy (code not spent) |
| 400 | `OTP_INCORRECT` | wrong code, tries left |
| 410 | `OTP_EXPIRED` | expired / used / decoy / address no longer the account's |
| 429 | `OTP_RESEND_TOO_SOON`, `OTP_RATE_LIMITED`, `OTP_TOO_MANY_ATTEMPTS` | limits |
| 503 | `EMAIL_NOT_CONFIGURED`, `SMS_NOT_CONFIGURED`, `EMAIL_NOT_SENT` | cannot send |

## Tests

* `PasswordResetServiceTest` (8): real code for a known email / phone; decoy
  and no code for unknown or deactivated accounts; exactly one address; 503
  before any lookup; success sets the hash, clears the lockout, verifies the
  address, ends sessions and push tokens, logs in; weak password refused
  without spending the code; code for an address no longer theirs refused.
* `OtpServiceDeliveryTest` (5): email by email when configured, fallback to
  the sender, phones never by email, `canDeliver`, decoy shape.
* `MailGatewayTest` (3): off without host, host without from refused,
  configured.
* `OtpServiceFixedCodeTest`: constructor gains the `MailGateway`.

## Try it (local)

```powershell
$base = "http://localhost:8080"
$c = Invoke-RestMethod -Method Post "$base/api/v1/auth/password/forgot" -ContentType application/json `
      -Body (@{ email = "owner@example.com" } | ConvertTo-Json)
# Without MAIL_HOST the code is in backend\logs\rent-erp.log: "DEV OTP for owner@example.com (RESET_PASSWORD)"
$r = Invoke-RestMethod -Method Post "$base/api/v1/auth/password/reset" -ContentType application/json `
      -Body (@{ verificationId = $c.data.verificationId; code = "123456"; newPassword = "newpass123" } | ConvertTo-Json)
$r.data.accessToken   # logged in; every other device is signed out
```
