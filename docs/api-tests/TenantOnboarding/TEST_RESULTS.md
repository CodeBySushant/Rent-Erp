# Add Tenant (TenantOnboardingController) — Test Results

Script: `add_tenant_test.ps1` (PowerShell, live against PostgreSQL).

```powershell
powershell -ExecutionPolicy Bypass -File docs\api-tests\TenantOnboarding\add_tenant_test.ps1
```

| Area | Scenarios |
|---|---|
| One call | profile + membership + room assignment + deposit created together; tenant list shows room and rent; deposit readable; summary occupancy updates |
| Refusals | occupied room 409 `ROOM_OCCUPIED`; another property's room 400 `ROOM_NOT_IN_PROPERTY`; unknown room 400; same phone already living there 409 `TENANT_ALREADY_ACTIVE`; impossible BS date 400; negative rent, bad phone, missing room 400; another owner 403 |
| Nothing half-created | after every refusal the tenant count is unchanged and no profile exists for the refused phone |
| No deposit | tenant added without one; deposit lookup 404 |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| — | — | — | First run pending |
