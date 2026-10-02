# Join by property code — Test Results

Script: `join_test.ps1` (PowerShell, live against PostgreSQL).

```powershell
powershell -ExecutionPolicy Bypass -File docs\api-tests\JoinByCode\join_test.ps1
```

| Area | Scenarios |
|---|---|
| Code | new property gets `SR-KTH-nnnn` from "Shrestha Residency, Kathmandu"; returned on property read |
| Look-up | name, city, landlord name; landlord phone not exposed; not member / not pending; lower-case accepted; unknown 404; login required |
| Join | request created (profile made from the account); repeat 409 `REQUEST_PENDING`; look-up shows pending; owner cannot join own property 400 |
| Owner | sees the request; another owner cannot accept (403); owner accepts → member; look-up shows member; joining again 409 `ALREADY_MEMBER` |
| Abuse | 21 look-ups in a row → 429 `JOIN_LOOKUP_LIMIT` |

Unit: `JoinCodeGeneratorTest`.

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| 2026-10-02 | 22 | 0 | |
| 2026-10-02 | 22 | 0 | Re-run after notifications (V23). |
