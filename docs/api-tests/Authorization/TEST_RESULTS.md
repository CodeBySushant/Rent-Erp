# Authorization (all domains) — Test Results

Script: `authz_test.ps1` (PowerShell, live against PostgreSQL). Backend started
from `backend\` with `APP_ENV=local` and `AUTH_ENFORCED` unset or `true`.

```powershell
powershell -ExecutionPolicy Bypass -File docs\api-tests\Authorization\authz_test.ps1
```

Three accounts per run: owners A and B (LANDLORD) and tenant C (TENANT).

| Area | Scenarios |
|---|---|
| Owner A, own data | property, floor, room, charge template, unlinked tenant profile, membership, deposit: create and read |
| Owner B vs A | read / rename floor, delete room, add floor to A's property, list A's floors / rooms / memberships / billing runs / blocked tenants, read A's charge template, tenant profile (read, edit), membership, deposit, bills — all 403; attach A's tenant to B's property 403; B's profile list excludes A's tenant |
| Lists | floors / meters without `propertyId` → 400 `PROPERTY_ID_REQUIRED` for a non-admin |
| Tariffs | list without a token → 401 |
| Tenant C | cannot read A's membership; cannot link a profile to another account; creates own linked profile; cannot ask to join for someone else's profile; asks to join A's property |
| Join | B cannot accept C's request (403); A reads the request and C's profile; A accepts → membership |
| Tenant C, own data | reads own membership, lists own memberships and bills; cannot list another tenant's memberships or bills; cannot record a deposit, terminate the membership or approve own KYC; cannot read A's floor; B cannot read C's membership |

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| 2026-10-02 | 58 | 1 | The failure was the script: accepting a join request answers 201 Created (it creates a membership), the script expected 200. Expectation corrected. |
