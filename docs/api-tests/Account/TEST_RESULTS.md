# Account self-service — Test Results

Script: `account_test.ps1` (PowerShell, live; OTP codes are read from the backend log, so `APP_ENV=local`).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\Account\account_test.ps1
```

| Area | Scenarios |
|---|---|
| Password | wrong current 400; weak 400; unchanged 400; change signs out other devices (401 there), this device keeps working; old password 401, new 200 |
| Devices | list with exactly one current; another user cannot end it (404); end one device (401 there); end all others |
| Phone | same 400; taken 409; invalid 400; code to the new number; another user cannot use it (410); wrong code 400; change; code reused 410 |
| Email | taken 409; invalid 400; code to the new email; change; log in with the new email, not the old |
| Delete | missing DELETE 400; wrong password 400; tenant with a tenancy 409; owner with tenants 409; free user deletes, is logged out and cannot log in; others untouched |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| — | — | — | First run pending |
