# TenancyController — API Test Results

**Date:** 2026-07-31
**Base URL:** `http://localhost:8080`
**Environment:** Local (Java 25, Spring Boot 4.1, PostgreSQL 16)
**Result:** ✅ 45/45 passed

Raw responses in [`responses/`](responses/). Postman collection: [`RentERP-TenancyController.postman_collection.json`](RentERP-TenancyController.postman_collection.json).

Covers four controllers under this pass: `TenantProfileController` (profiles + KYC), `JoinRequestController`, `MembershipController` (+ nested room assignments), `BlockedTenantController` (property-scoped).

---

## Results

| # | Test | Method | Endpoint | Expected | Actual | Pass |
|---|------|--------|----------|----------|--------|------|
| 1 | Create LINKED tenant profile | POST | `/tenant-profiles` | 201 | 201 | ✅ |
| 2 | Duplicate LINKED profile (same `userId`) → 409 | POST | `/tenant-profiles` | 409 | 409 | ✅ |
| 3 | Create UNLINKED tenant profile (T7) | POST | `/tenant-profiles` | 201 | 201 | ✅ |
| 4 | Bad phone → 400 | POST | `/tenant-profiles` | 400 | 400 | ✅ |
| 5 | Unknown `userId` → 404 | POST | `/tenant-profiles` | 404 | 404 | ✅ |
| 6 | GET linked profile (`linked=true`) | GET | `/tenant-profiles/{id}` | 200 | 200 | ✅ |
| 7 | Update profile fields | PUT | `/tenant-profiles/{id}` | 200 | 200 | ✅ |
| 8 | Submit KYC | POST | `/tenant-profiles/{id}/kyc` | 201 | 201 | ✅ |
| 9 | Resubmit while PENDING → 400 | POST | `/tenant-profiles/{id}/kyc` | 400 | 400 | ✅ |
| 10 | Reject KYC | POST | `/tenant-profiles/{id}/kyc/reject` | 200 | 200 | ✅ |
| 11 | Resubmit after REJECTED (T5, `resubmitCount=1`) | POST | `/tenant-profiles/{id}/kyc` | 201 | 201 | ✅ |
| 12 | Approve KYC | POST | `/tenant-profiles/{id}/kyc/approve` | 200 | 200 | ✅ |
| 13 | Resubmit after APPROVED → 400 (terminal) | POST | `/tenant-profiles/{id}/kyc` | 400 | 400 | ✅ |
| 14 | Approve when not PENDING → 400 | POST | `/tenant-profiles/{id}/kyc/approve` | 400 | 400 | ✅ |
| 15 | Flag KYC (§14.3 **T6**) | POST | `/tenant-profiles/{id}/kyc/flag` | 200 | 200 | ✅ |
| 16 | Block tenant on property | POST | `/properties/{pid}/blocked-tenants` | 201 | 201 | ✅ |
| 17 | Double block same tenant → 409 | POST | `/properties/{pid}/blocked-tenants` | 409 | 409 | ✅ |
| 18 | List blocked tenants | GET | `/properties/{pid}/blocked-tenants` | 200 | 200 | ✅ |
| 19 | Unblock | DELETE | `/properties/{pid}/blocked-tenants/{tid}` | 200 | 200 | ✅ |
| 20 | Unblock non-blocked → 400 | DELETE | `/properties/{pid}/blocked-tenants/{tid}` | 400 | 400 | ✅ |
| 21 | Blocked tenant creates join request → 400 (**T3**) | POST | `/join-requests` | 400 | 400 | ✅ |
| 22 | UNLINKED tenant creates join request → 400 | POST | `/join-requests` | 400 | 400 | ✅ |
| 23 | Linked tenant creates join request | POST | `/join-requests` | 201 | 201 | ✅ |
| 24 | Duplicate PENDING request → 409 (**T4**) | POST | `/join-requests` | 409 | 409 | ✅ |
| 25 | Reject join request (**T2**) | POST | `/join-requests/{id}/reject` | 200 | 200 | ✅ |
| 26 | Re-request after REJECTED (**T2**) | POST | `/join-requests` | 201 | 201 | ✅ |
| 27 | Accept → creates membership atomically (`linkedFromJoinRequestId` set) | POST | `/join-requests/{id}/accept` | 201 | 201 | ✅ |
| 28 | Re-accept an ACCEPTED request → 400 | POST | `/join-requests/{id}/accept` | 400 | 400 | ✅ |
| 29 | Direct-create membership for unlinked tenant (**T7**) | POST | `/memberships` | 201 | 201 | ✅ |
| 30 | Duplicate ACTIVE membership → 409 | POST | `/memberships` | 409 | 409 | ✅ |
| 31 | Update `paymentModelOverride` (**T10**) | PUT | `/memberships/{id}` | 200 | 200 | ✅ |
| 32 | Delete profile with ACTIVE membership → 400 | DELETE | `/tenant-profiles/{id}` | 400 | 400 | ✅ |
| 33 | Assign room to membership | POST | `/memberships/{id}/room-assignments` | 201 | 201 | ✅ |
| 34 | Room single-occupancy invariant (§10.2) → 409 | POST | `/memberships/{id}/room-assignments` | 409 | 409 | ✅ |
| 35 | Cross-property room → 400 | POST | `/memberships/{id}/room-assignments` | 400 | 400 | ✅ |
| 36 | Assign a different room | POST | `/memberships/{id}/room-assignments` | 201 | 201 | ✅ |
| 37 | List assignments | GET | `/memberships/{id}/room-assignments` | 200 | 200 | ✅ |
| 38 | End assignment (PATCH, retained for history) | PATCH | `/memberships/{id}/room-assignments/{aid}/end` | 200 | 200 | ✅ |
| 39 | Reassign freed room after end | POST | `/memberships/{id}/room-assignments` | 201 | 201 | ✅ |
| 40 | End already-ended assignment → 400 | PATCH | `/memberships/{id}/room-assignments/{aid}/end` | 400 | 400 | ✅ |
| 41 | Terminate membership (auto-ends active assignments) | POST | `/memberships/{id}/terminate` | 200 | 200 | ✅ |
| 42 | Re-terminate → 400 | POST | `/memberships/{id}/terminate` | 400 | 400 | ✅ |
| 43 | Delete profile after all memberships TERMINATED | DELETE | `/tenant-profiles/{id}` | 200 | 200 | ✅ |
| 44 | List memberships by property | GET | `/memberships?propertyId=...` | 200 | 200 | ✅ |
| 45 | Blocked-tenants on unknown property → 404 | GET | `/properties/{bad-id}/blocked-tenants` | 404 | 404 | ✅ |

---

## Notes

- **§14.3 T1 (5-minute expiry):** enforced at DB shape (`expires_at` column set to `requested_at + 5 min` on create) and applied lazily on every GET/state-transition via `expireIfStale()`. A background sweep via db-scheduler is noted as a small follow-up. Scenarios don't cover the natural time-elapse path (would need to sleep 5+ minutes in the test); the lazy-expiry code path is covered by unit-level correctness — every read/state check calls `expireIfStale` before any decision.
- **§14.3 T2 (reject/re-request):** scenarios 25/26 — a REJECTED row is terminal; the tenant may create a new PENDING for the same `(tenant, property)`.
- **§14.3 T3 (block):** scenarios 21 + 16/19 — a blocked tenant attempting to create a join request gets a neutral message ("This landlord is not accepting new join requests from you at this time") wrapped as 400. Block is property-scoped, unblock-able, only one active block per `(property, tenant)`.
- **§14.3 T4 (simultaneous requests):** scenario 24 — the partial unique index `uq_join_requests_pending_per_tenant_property` guarantees at most one PENDING row per `(tenant, property)`; the service pre-check catches it as a clean 409.
- **§14.3 T5 (KYC resubmit cap):** scenarios 8–14 exercise the full lifecycle. Resubmit is only allowed from REJECTED/FLAGGED; APPROVED is terminal; cap set to 5. Each resubmission wipes prior decision fields (`verifiedAt`, `verifiedBy`, `rejectionReason`) but preserves and increments `resubmitCount`.
- **§14.3 T6 (physical doc mismatch):** scenario 15 — landlord flags KYC regardless of current status. Sets `status=FLAGGED` + `flag_reason`. Admin queue routing lands with the notifications system (Phase 8).
- **§14.3 T7 (existing tenant, pre-app history):** scenario 29 — direct `POST /memberships` for an unlinked tenant. Opening-position financial fields (deposit/advance/opening balance) land in the `TenantFinanceController` pass — this pass builds only the identity + assignment layer they attach to.
- **§14.3 T10 (mixed payment models):** scenario 31 — `payment_model_override` on the membership, NULL falls back to `Property.paymentModelDefault`.
- **Room single-occupancy (§10.2):** scenarios 34 + 39 — enforced at the service layer (`RoomAssignmentRepository.findFirstByRoomIdAndEffectiveToBsIsNull`) rather than a partial unique index, because the constraint spans memberships and Postgres partial-uniques can't express that cleanly. Ending an assignment frees the room for a new tenant.
- **Cascade termination:** scenario 41 confirms `POST /memberships/{id}/terminate` also ends every active `room_assignments` row on the same `endedAtBs` inside the same transaction. Prevents dangling "room occupied by terminated membership" states.
- **Profile deletion gate:** scenarios 32/43 — a soft-delete of a tenant profile is refused while any ACTIVE membership exists (protects billing target continuity). After all memberships terminate, the profile may be deleted.
- Test data was created under isolated users (`9800001001–9800001004`) and left in place for regression runs.
