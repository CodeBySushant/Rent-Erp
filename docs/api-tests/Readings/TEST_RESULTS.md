# Readings due and tenant readings — Test Results

Script: `readings_test.ps1` (PowerShell, live).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\Readings\readings_test.ps1
```

| Area | Scenarios |
|---|---|
| Owner due list | three meters; M-101 due with last value 1000 and room 101 |
| Tenant meters | tenant in 101 sees its three meters: one readable, one owner-only, one waiting for its first reading; tenant in 102 sees none |
| Tenant submit | other room 403; owner-only 403; no first reading 400; someone else's photo 400; submit with photo → PENDING; second this month 409; shows pending; a user without a tenant profile 403 |
| Owner review | sees tenant's value and photo; confirms; meter shows CONFIRMED |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| 2026-10-02 | 21 | 0 | |
