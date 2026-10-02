# My Stay (GET /me/stay) — Test Results

Script: `mystay_test.ps1` (PowerShell, live against PostgreSQL).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\MyStay\mystay_test.ps1
```

| Area | Scenarios |
|---|---|
| Before joining | empty stay for a tenant without a profile; login required; after asking to join, the pending request shows with the property name |
| Active tenancy | property name/address/city/join code, landlord name and phone, room and floor names, rent, deposit, notice period, billing day, no bill / nothing owed, today's BS date |
| Isolation | an owner who is not a tenant sees nothing |
| Moved out | status TERMINATED, rooms released, end date, landlord phone no longer shared |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| 2026-10-02 | 20 | 0 | |
