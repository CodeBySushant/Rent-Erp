# Owner payment details — Test Results

Script: `payment_details_test.ps1` (PowerShell, live).

```powershell
powershell -ExecutionPolicy Bypass -File D:\PROJECTS\Rent-Erp\docs\api-tests\PaymentDetails\payment_details_test.ps1
```

| Area | Scenarios |
|---|---|
| Owner | nothing set → null; empty set 400; QR of another property / not a payment QR 400; saves QR, wallet, bank and note; QR link returned |
| Readers | active tenant reads the details and opens the QR; non-tenant, other owner 403 |
| Writers | other owner, tenant 403 |
| Update | replaces the whole set |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| — | — | — | First run pending |
