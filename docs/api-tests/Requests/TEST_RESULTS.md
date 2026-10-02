# Tenant requests — Test Results

Script: `requests_test.ps1` (PowerShell, live).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\Requests\requests_test.ps1
```

| Area | Scenarios |
|---|---|
| Raise | maintenance with photo; a second maintenance allowed; room change; second open room change 409; vacate without date 400; past date 400; other tenant / owner 403; owner sees 3 pending; other tenant sees none; other owner cannot list |
| Decide | reject needs a note; reject; deciding twice 409; tenant cannot approve; approve and complete maintenance |
| Room change | approve into an occupied room 409 and nothing changes; approve into a vacant room moves the tenant and completes |
| Vacate | approval opens the move-out notice on the requested date |
| Withdraw | another tenant 403; the tenant withdraws; tenant lists all 5 of theirs |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| 2026-10-02 | 26 | 0 | Move-out re-run 29/29. |
