# Notifications — Test Results

Script: `notifications_test.ps1` (PowerShell, live).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\Notifications\notifications_test.ps1
```

| Area | Scenarios |
|---|---|
| Join | owner told of a join request (tenant not told of their own); tenant told when accepted |
| Bill | nothing while a draft; tenant told when sent, with amount (Rs 10,500), month and bill link |
| Payments | owner told a proof is waiting; tenant told of approval, owner-recorded payment, rejection with the reason |
| Requests | owner told of a new request; tenant told of the decision |
| Move-out | owner told of the tenant's notice; tenant told of the settlement (still owes Rs 5,500) |
| Inbox | newest first; mark one read (count drops); another user cannot (404); mark all read; other owner has none; login required |
| Devices | register a push token; bad platform 400 |

Reading-submitted notifications are created by the same mechanism (tenant submission → property staff) and are not exercised here.

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| — | — | — | First run pending |
