# Room transfer — Test Results

Script: `transfer_test.ps1` (PowerShell, live).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\RoomTransfer\transfer_test.ps1
```

| Area | Scenarios |
|---|---|
| Transfer | 101 → 102 keeps rent; tenant list shows the new room; history keeps 101 closed on the date; occupancy unchanged |
| Refusals | occupied room 409 (tenant stays put); not in the from-room 400; same room 400; date not after current move-in 400; other property's room 400; other owner 403 |
| New rent | back to 101 at Rs 12000; the freed room takes a new tenant |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| — | — | — | First run pending |
