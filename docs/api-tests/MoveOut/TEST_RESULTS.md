# Move-out — Test Results

Script: `moveout_test.ps1` (PowerShell, live).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\MoveOut\moveout_test.ps1
```

| Area | Scenarios |
|---|---|
| Notice | tenant gives notice (short notice flagged); second open notice 409; other owner / other tenant 403; past date 400; owner preview of owed and deposit; tenant withdraws and gives notice again |
| Guards | tenant / other owner cannot settle (403); a waiting payment proof blocks settlement (409) |
| Settle with deposit | deposit pays the Rs 10500 bill, then final charges 800 and damages 1000; refund 2700; bill PAID with a "Deposit applied at move-out" payment; deposit REFUNDED_PARTIAL; tenancy ended, room free; My Stay shows it ended; settling again 409; new notice on an ended tenancy 400 |
| Settle without deposit | owner gives notice; nothing refunded, Rs 8500 still owed and the bill kept unpaid; both rooms free; two settled move-outs listed |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| — | — | — | First run pending |
