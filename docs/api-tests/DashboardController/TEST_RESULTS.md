# DashboardController — Test Results

Script: `dashboard_test.ps1` (PowerShell, live against PostgreSQL). Backend
started from `backend\` with `APP_ENV=local`.

```powershell
powershell -ExecutionPolicy Bypass -File docs\api-tests\DashboardController\dashboard_test.ps1
```

| Area | Scenarios |
|---|---|
| Summary | 3 rooms / 1 occupied / 2 vacant, 2 active tenants, 1 pending join request, nothing owed, no billing run; other owner and a tenant 403 |
| Tenant list | sorted by name, room and floor names, rent from the current assignment, a tenant without a room, unlinked flag, nothing owed; `status=ALL`; bad status 400; other owner 403 |
| Dashboard | totals across the caller's properties, today's BS date, another owner's properties absent, login required |
| Live | assigning a room changes occupied / vacant on the next read |

Unit: `BsCalendarTest` — AD → BS on known New Year dates, month start, today.

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| — | — | — | First run pending |
