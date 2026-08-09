# ChargeController (charge_templates) — API Test Results

**Date:** 2026-07-31
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 23/23 passed

Raw responses are saved in [`responses/`](responses/). Import [`RentERP-ChargeController.postman_collection.json`](RentERP-ChargeController.postman_collection.json) into the Postman VS Code extension to re-run.

---

## How to re-run in Postman (VS Code extension)

1. Open the Postman panel → **Collections** → **Import**
2. Select `RentERP-ChargeController.postman_collection.json`
3. Make sure the backend is running (`mvn spring-boot:run`)
4. Create a landlord user via the UserController collection first; set collection variable `ownerUserId`
5. Run top to bottom — request 01 saves `propertyId`; 02/03/06 save `charge1Id`/`charge2Id`/`charge3Id`

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | Create property | POST | `/api/v1/properties` | 201 | 201 | ✅ |
| 2 | Create charge "Internet" (SHARED) | POST | `/api/v1/charge-templates` | 201 | 201 | ✅ |
| 3 | Create charge "Garbage" (FIXED_PER_TENANT) | POST | `/api/v1/charge-templates` | 201 | 201 | ✅ |
| 4 | Duplicate name in same property | POST | `/api/v1/charge-templates` | 409 | 409 | ✅ |
| 5 | Amount 0.00 without `zeroAmountAcknowledged` | POST | `/api/v1/charge-templates` | 400 | 400 | ✅ |
| 6 | Amount 0.00 **with** `zeroAmountAcknowledged: true` | POST | `/api/v1/charge-templates` | 201 | 201 | ✅ |
| 7 | List charge templates by `propertyId` | GET | `/api/v1/charge-templates?propertyId={id}` | 200, 3 results | 200, 3 results | ✅ |
| 8 | Get single charge template | GET | `/api/v1/charge-templates/{id}` | 200 | 200 | ✅ |
| 9 | Update name/amount/splitBasis | PUT | `/api/v1/charge-templates/{id}` | 200, all 3 updated, `updatedAt` fresh | 200, all 3 updated, `updatedAt` fresh | ✅ |
| 10 | Update amount to 0.00 without re-acknowledging | PUT | `/api/v1/charge-templates/{id}` | 400 | 400 | ✅ |
| 11 | Update amount to 0.00 **with** acknowledgement | PUT | `/api/v1/charge-templates/{id}` | 200 | 200 | ✅ |
| 12 | Rename to a name already used in the same property | PUT | `/api/v1/charge-templates/{id}` | 409 | 409 | ✅ |
| 13 | Deactivate with `deactivationMode=THIS_CYCLE_PRORATED` | DELETE | `/api/v1/charge-templates/{id}` | 200 | 200 | ✅ |
| 14 | Verify `deactivationMode` persisted | GET | `/api/v1/charge-templates/{id}` | 200, active=false, mode=THIS_CYCLE_PRORATED | 200, active=false, mode=THIS_CYCLE_PRORATED | ✅ |
| 15 | Deactivate with `deactivationMode=VOID` | DELETE | `/api/v1/charge-templates/{id}` | 200 | 200 | ✅ |
| 16 | Deactivate with **no** `deactivationMode` param | DELETE | `/api/v1/charge-templates/{id}` | 200, defaults to NEXT_CYCLE | 200, defaults to NEXT_CYCLE | ✅ |
| 17 | Re-deactivate an already-inactive charge | DELETE | `/api/v1/charge-templates/{id}` | 200 (idempotent), original `deactivationMode` **not** overwritten | 200 (idempotent), original `deactivationMode` preserved | ✅ |
| 18 | Get non-existent charge | GET | `/api/v1/charge-templates/{bad-id}` | 404 | 404 | ✅ |
| 19 | Create, non-existent `propertyId` | POST | `/api/v1/charge-templates` | 404 | 404 | ✅ |
| 20 | Create, missing `name` | POST | `/api/v1/charge-templates` | 400 | 400 | ✅ |
| 21 | Create, missing `splitBasis` | POST | `/api/v1/charge-templates` | 400 | 400 | ✅ |
| 22 | Create, negative `amount` | POST | `/api/v1/charge-templates` | 400 | 400 | ✅ |
| 23 | Delete non-existent charge | DELETE | `/api/v1/charge-templates/{bad-id}` | 404 | 404 | ✅ |

---

## Notes

- Tests 5/6 and 10/11 verify the spec §14.2 **B6** rule end-to-end: a zero amount is rejected unless explicitly acknowledged, both on create and on update.
- Tests 13–17 verify the spec §14.2 **B7** rule: deactivation always captures the landlord's chosen treatment (`THIS_CYCLE_PRORATED` / `NEXT_CYCLE` / `VOID`) for the not-yet-built Billing engine to read later. Test 16 confirms the endpoint's default (`NEXT_CYCLE`) when the query param is omitted — the safest option, since it never retroactively touches a cycle already in progress. Test 17 specifically confirms the idempotent no-op on an already-inactive charge does **not** silently overwrite a previously recorded `deactivationMode`.
- Test 4/12 verify the `(property_id, name)` uniqueness rule on both create and rename.
- Test data (the test property and its 3 charge templates) was deleted directly from Postgres after the run; the two properties from earlier passes were left untouched.
