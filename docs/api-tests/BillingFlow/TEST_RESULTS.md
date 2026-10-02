# Billing flow (as the app uses it) — Test Results

Script: `billing_flow_test.ps1` (PowerShell, live). Exercises the existing billing-run endpoints the way the app's Create Bill / Bills screens call them.

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\BillingFlow\billing_flow_test.ps1
```

| Area | Scenarios |
|---|---|
| Inputs | missing electricity amount for a FIXED_PER_TENANT property → 400 |
| Draft | created; same Idempotency-Key replays the same run; new key for the same month → 409; other owner 403 |
| Engine | one draft bill with rent 12000 and electricity 500; a draft owes nothing in the tenant list |
| Send | confirm → CONFIRMED, bill ISSUED / UNPAID; tenant list owed and summary billed / pending match the bill; sending twice refused; bill readable, other owner 403 |
| Discard | an April draft cancelled, then drafted again |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| — | — | — | First run pending |
