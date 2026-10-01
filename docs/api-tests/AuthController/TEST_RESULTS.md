# AuthController — Test Results

Script: `auth_test.ps1` (PowerShell, live against PostgreSQL). Backend started
from `backend\` with `APP_ENV=local` and `AUTH_ENFORCED` unset or `true`.

```powershell
powershell -ExecutionPolicy Bypass -File docs\api-tests\AuthController\auth_test.ps1
```

| Area | Scenarios |
|---|---|
| Sign-up | code request, resend too soon (429), bad phone (400), wrong code (400), right code, reused code (410), weak password (400), register (201), no password in response, register with taken phone (409), sign-up code for taken phone (409) |
| Session | me with token, Hindi preference stored, me without token (401), tampered token (401), protected route without token (401) |
| Password login | wrong (401), unknown email (401), right with different email case |
| Refresh | rotate, old refresh token rejected (401) |
| Logout | logout, access token rejected after logout (401 SESSION_REVOKED), refresh rejected after logout, other session unaffected |
| OTP login | request, unknown phone (404), login |
| Authorization | create property (owner = caller, body owner ignored), read own, other owner read / update / delete (403), list scoped and `ownerUserId` ignored, other account read / update (403), list users (403), update own account to Hindi, self-grant on another's property (403) |
| Errors | malformed JSON (400 MALFORMED_REQUEST), bad UUID (400 INVALID_PARAMETER) |
| Lockout | five wrong passwords then right password (429 LOGIN_LOCKED) |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| 2026-10-02 | 46 | 0 | Windows 11, PostgreSQL 17.10, JDK 25.0.4; `mvn clean install` 34/34 |
