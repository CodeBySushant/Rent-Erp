# PaymentController — Test Results

Script: `payments_test.ps1` (PowerShell, live).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\PaymentController\payments_test.ps1
```

| Area | Scenarios |
|---|---|
| Owner records | cash applied at once (PARTIAL); same Idempotency-Key replays without reducing again; over balance 400; bad date 400; other owner / tenant 403 |
| Tenant proof | no file 400; someone else's file 400; owner cannot send tenant proof 403; proof PENDING with bill unchanged; second pending 409; summary counts it; owner's pending list shows it |
| Approve | other owner / tenant 403; approve reduces the bill; approving again 409 and the bill is not reduced twice |
| Reject / withdraw | reject with reason (bill unchanged); reason required; only the sender withdraws; withdrawn → CANCELLED |
| Paid in full | PAID, nothing left; paying again 409; tenant list, summary and My Stay all show nothing owed |
| History and guards | bill history (5) for the tenant, 403 for another owner; tenant's own payments; billing run with payments cannot be cancelled (409); draft bill not payable (400) |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| 2026-10-02 | 4 | 35 | Tests ran against the previous backend still holding port 8080 (payment endpoints 404). Not a code failure. |
| 2026-10-02 | 39 | 0 | After stopping the old process and starting the new build. |
| 2026-10-02 | 39 | 0 | Re-run after notifications (V23). |
