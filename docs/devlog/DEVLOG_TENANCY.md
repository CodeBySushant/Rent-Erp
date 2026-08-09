# DEVLOG — TenancyController

**Phase:** 4 — Tenancy (identity + relationship layer)
**Date:** 2026-07-31
**Status:** ✅ Complete — 45/45 live PostgreSQL scenarios passed
**Scope tables (this pass):** `tenant_profiles`, `tenant_kyc`, `join_requests`, `tenant_property_memberships`, `room_assignments`, `property_blocked_tenants`
**Scope tables deferred:** none from this controller's map; opening-position financial fields (`tenant_deposits`, `tenant_advance_rent`, `tenant_opening_balances`, `rent_increments`) land in `TenantFinanceController` — next pass.

---

## Scope confirmed with user

- Pull `property_blocked_tenants` from `PropertyController` into this pass (T3 needs it, it's a tenant↔property relationship, not a property config). `CONTROLLER_TABLE_MAP.md` updated accordingly.
- KYC resubmit cap: 5.
- Two-call unlinked flow (`POST /tenant-profiles` then `POST /memberships`), not a combined atomic endpoint.
- No deferred meter follow-ups (A–E from the scope cut) folded in — each becomes its own small commit.
- Lazy join-request expiry on every read/state-transition; batch sweep via db-scheduler is a small follow-up.

---

## Files created

| File | Purpose |
|------|---------|
| `db/migration/V10__tenancy.sql` | 6 tables + partial unique indexes; adds `fk_meters_designated_tenant` FK on `meters.designated_tenant_id` |
| `domain/tenancy/entity/TenantProfile.java` | Identity row — linked (`user_id` FK) or unlinked (`user_id` NULL) |
| `domain/tenancy/entity/TenantKyc.java` | KYC state machine — id_type/id_number + photo URLs + status + resubmit_count |
| `domain/tenancy/entity/JoinRequest.java` | Linked-tenant → property request with 5-min expiry |
| `domain/tenancy/entity/TenantPropertyMembership.java` | ACTIVE/TERMINATED relationship with per-tenant paymentModelOverride |
| `domain/tenancy/entity/RoomAssignment.java` | Dated tenant↔room mapping (VARCHAR BS dates) |
| `domain/tenancy/entity/PropertyBlockedTenant.java` | T3 property-scoped block set |
| `domain/tenancy/dto/*` | 18 request/response DTOs |
| `domain/tenancy/repository/*` | 6 Spring Data repos with active-status lookups, uniqueness helpers |
| `domain/tenancy/service/TenantProfileService.java` | Profile CRUD + KYC lifecycle (submit/approve/reject/flag) with resubmit cap and terminal-status rules |
| `domain/tenancy/service/BlockedTenantService.java` | Block/list/unblock + `isBlocked()` helper consumed by JoinRequestService |
| `domain/tenancy/service/JoinRequestService.java` | Create/accept/reject/cancel with lazy expiry + T3 block check + T4 duplicate PENDING guard |
| `domain/tenancy/service/MembershipService.java` | Direct-create + `createInternal` (used by JoinRequestService.accept) + update + terminate + room-assignment CRUD |
| `domain/tenancy/controller/TenantProfileController.java` | Profile + KYC endpoints |
| `domain/tenancy/controller/JoinRequestController.java` | Join-request endpoints (accept returns the created membership) |
| `domain/tenancy/controller/MembershipController.java` | Memberships + nested room-assignments |
| `domain/tenancy/controller/BlockedTenantController.java` | Property-scoped block/list/unblock |

---

## Schema highlights (V10)

- **`tenant_profiles.user_id`** nullable; `UNIQUE` constraint applies only when non-NULL (Postgres `UNIQUE` treats NULL as distinct, so multiple unlinked rows are fine). Linked profiles have exactly one profile per user; unlinked profiles are unconstrained on phone (spec §10.3 — a person may be a separate tenant identity per landlord).
- **`tenant_kyc`** has a `UNIQUE (tenant_profile_id)` — one live KYC row per tenant, resubmission edits it in place and increments `resubmit_count`. Historical KYC audit lives in `audit_log` (Phase 8); retaining old KYC rows here would conflict with the photo-purge doctrine.
- **`join_requests`** has a partial `UNIQUE` index `WHERE status = 'PENDING'` — at most one PENDING request per `(tenant, property)` at any moment. Terminal statuses (ACCEPTED/REJECTED/EXPIRED/CANCELLED) accumulate freely.
- **`tenant_property_memberships`** has the same partial `UNIQUE` shape `WHERE status = 'ACTIVE'` — one ACTIVE membership per `(tenant, property)`. Historical TERMINATED rows accumulate.
- **`room_assignments`** has `UNIQUE (membership_id, room_id, effective_from_bs)` — no exact duplicate rows; but the **cross-membership single-occupancy** invariant (spec §10.2 "a room is occupied by ≤1 tenancy at a time") is enforced at the service layer, not the DB. Reason: partial unique indexes in Postgres can't express "no other row across any parent" cleanly.
- **`property_blocked_tenants`** partial `UNIQUE WHERE is_active = TRUE` — one active block per `(property, tenant)`; unblock flips `is_active=false` (soft delete, kept for audit).
- **`meters.designated_tenant_id`** FK to `tenant_profiles(id)` now added via `ALTER … ADD CONSTRAINT` — the deferred piece from MeterController's V8. Existing meter rows still have NULL `designated_tenant_id`; the population endpoint is a follow-up commit.

---

## Per-endpoint logic

### Tenant profiles (`/tenant-profiles`)

| Endpoint | Rules |
|---|---|
| `POST /` | Linked: user must exist, no existing profile for that user. Unlinked (`userId` omitted): no cross-landlord constraint on phone. Phone regex `^(97\|98)\d{8}$` enforced. |
| `GET /{id}` | 404 on unknown. Response carries a computed `linked` flag = `userId != null` for convenience. |
| `GET /` | Paginated. |
| `PUT /{id}` | Identity fields (`userId`, `phone`) are NOT updatable. Contact info + emergency contact + language + category updatable. |
| `DELETE /{id}` | Soft — blocked while any ACTIVE membership exists (protects billing target continuity). Idempotent no-op if already inactive. |

### KYC (`/tenant-profiles/{id}/kyc`)

| Endpoint | Rules |
|---|---|
| `POST /` | First submission: create PENDING. Resubmit: only from REJECTED/FLAGGED (APPROVED is terminal → 400; PENDING → 400 with "already under review"). Cap 5 (T5) → 400 beyond. Wipes prior verification fields. |
| `GET /` | Latest single row. 404 if never submitted. |
| `POST /approve` | Only from PENDING → 400 otherwise. Stamps `verifiedAt`. |
| `POST /reject` | Only from PENDING. `reason` required. |
| `POST /flag` (T6) | Landlord flags physical doc mismatch. Accepts from any non-FLAGGED state (bypasses lifecycle position — a physical mismatch is discovered independently). |

### Join requests (`/join-requests`)

| Endpoint | Rules |
|---|---|
| `POST /` | Tenant must be linked (`userId != null`). Property must exist. Blocked check via `BlockedTenantService.isBlocked` (T3) → 400 with neutral message. Stale PENDING expired first, then partial-unique index catches T4 dup. `expires_at = now + 5 min` (T1). |
| `GET /{id}` and `GET /` | Lazy-expire PENDING rows past `expires_at` before returning. |
| `POST /accept` | 400 unless currently PENDING (post lazy-expiry). Requires `startedAtBs`. Atomically creates the membership with `linkedFromJoinRequestId` set. Response returns the created membership. |
| `POST /reject` | Terminal REJECTED. Optional `responseMessage`. T2 — tenant may re-request afterwards. |
| `POST /cancel` | Tenant self-cancel. Terminal CANCELLED. |

### Memberships (`/memberships`)

| Endpoint | Rules |
|---|---|
| `POST /` | Direct-create path — for unlinked/existing-tenant onboarding (T7). Landlord provides tenant, property, `startedAtBs`. Duplicate ACTIVE membership → 409. |
| `GET /{id}`, `GET /` | Filters: `propertyId`, `tenantProfileId`, `status`. |
| `PUT /{id}` | `paymentModelOverride` only (T10). Blocked on TERMINATED. |
| `POST /terminate` | Ends all active room_assignments on the same `endedAtBs` inside the same transaction. Blocked on already-TERMINATED. |

### Room assignments (nested under membership)

| Endpoint | Rules |
|---|---|
| `POST /{id}/room-assignments` | Membership must be ACTIVE. Room must belong to the membership's property (cross-property → 400). Single-occupancy check across memberships (§10.2) → 409. Duplicate active `(membership, room)` pair → 409. |
| `GET /{id}/room-assignments` | Full dated history, ordered by `effective_from_bs` ASC. |
| `PATCH /{id}/room-assignments/{aid}/end` | Sets `effective_to_bs`. PATCH not DELETE — row retained for historical bill reconstruction. Cross-parent-id mismatch → 404 (same shape as MeterController). |

### Blocked tenants (property-scoped)

| Endpoint | Rules |
|---|---|
| `POST /properties/{pid}/blocked-tenants` | Duplicate active block → 409. |
| `GET /` | Lists active blocks for a property. |
| `DELETE /{tenantId}` | Flips active block to `is_active=false` (kept for audit). 400 if not currently blocked. |

---

## §14.3 edge cases handled

| Case | Handling |
|------|---------|
| **T1** — unanswered join request expires in 5 min | `expires_at = requested_at + 5 min` on create; `expireIfStale()` runs on every read/state-transition; batch sweep via db-scheduler is a follow-up. |
| **T2** — landlord rejects, tenant re-requests | REJECTED is terminal for that row; new PENDING may be created for the same `(tenant, property)`. Optional `responseMessage`. |
| **T3** — landlord blocks | `property_blocked_tenants` set; join-request creation refuses with neutral message. Unblock-able, property-scoped only. |
| **T4** — simultaneous join requests | Partial unique index + service pre-check → 409. Requests are `(tenant, property)`-scoped, not `(tenant, room)` — rooms attach after acceptance. |
| **T5** — KYC resubmit up to N | Resubmit only from REJECTED/FLAGGED; APPROVED terminal; cap 5. `resubmitCount` increments per resubmit. |
| **T6** — landlord flags physical doc mismatch | `POST /kyc/flag` sets status FLAGGED + `flag_reason`. Admin queue routing → Phase 8 notifications. |
| **T7** — existing tenant, pre-app history | Direct `POST /tenant-profiles` (unlinked) + direct `POST /memberships`. Deposit/advance/opening balance land in TenantFinanceController — this pass builds the identity + membership hooks they attach to. |
| **T10** — mixed payment models | `payment_model_override` nullable on membership; NULL falls back to `Property.paymentModelDefault`. |

## Explicitly deferred in §14.3

- **T8** mid-month join proration — belongs to Billing engine.
- **T9** shared-charge denominator changes on join — belongs to Billing engine (segment boundary).
- **T11** tenant adds a room, **T12** partial vacate, **T13** transfer — belong to `RoomOpsController` (Phase 7). This pass builds only the raw `room_assignments` CRUD they're built on top of.
- **T14** rent increment — belongs to `TenantFinanceController` (next pass).
- **T15** ownership transfer — belongs to a future `property_ownership_transfers` surface under PropertyController.

---

## Design decisions

1. **Unlinked path stays two-call.** `POST /tenant-profiles` (userId omitted) then `POST /memberships` is orthogonal. A combined atomic endpoint would collapse two clear concerns and would need to grow further when deposits/opening-balance land in TenantFinanceController.
2. **KYC is a single-row-per-tenant state machine, not a history.** Retaining old KYC rows here would conflict with the 30-day photo purge (spec §10.4). The audit_log (Phase 8) is where the trail lives.
3. **KYC flag bypasses lifecycle position.** T6 fires from any prior state (PENDING/APPROVED/REJECTED) — a landlord discovers a physical mismatch independently of the current KYC state, so we can't require it start from PENDING.
4. **Join requests: lazy expiry on read.** No background job needed for correctness; every read touches `expireIfStale`. A batch sweep is a UX-nicety for the "requests list" screen — small db-scheduler task, deferred.
5. **`accept` returns the membership, not the request.** The caller almost always needs to work with the new membership next (add room assignments, set deposit). Returning the request would force a second GET.
6. **Membership `terminate` cascades to room assignments** in the same transaction. Otherwise the invariant "active room_assignments belong to ACTIVE memberships" would break the moment vacancy runs.
7. **Room single-occupancy check is service-layer, not a DB partial unique.** The invariant spans memberships; partial unique on `(room_id) WHERE effective_to_bs IS NULL` would work but is more brittle to reason about than a query in the service.
8. **`property_blocked_tenants` was moved from PropertyController to TenancyController.** Confirmed with user in-session. The block is a tenant↔property relationship — belongs with the other tenant relationship tables.
9. **`meters.designated_tenant_id` FK added in V10, not V8.** V8 couldn't create the FK because `tenant_profiles` didn't exist. Added via `ALTER … ADD CONSTRAINT` now — no existing rows populated (population endpoint is a follow-up).

---

## Open items

- [ ] **Meter follow-ups (A–E from scope cut):** designated_tenant PATCH endpoint on meters; MeterController accepts TENANT-scoped `infrastructure_meter_scope`; `meter_tenant_assignments` full CRUD; §6.5 second-half gate (SUB_METERED requires an INITIAL reading); GAP_ABSORBED reading path. Each becomes its own small commit — I'd recommend batching A+B+D as one commit (small edits to existing controllers), C as its own commit (new sub-controller), and E as its own commit.
- [ ] **TenantFinanceController** (next controller in build order) — `tenant_deposits`, `tenant_advance_rent`, `tenant_opening_balances`, `rent_increments`. Also delivers T14 rent-increment handling.
- [ ] **`join_requests` expiry sweep** — db-scheduler task to flip stale PENDING → EXPIRED once/minute. Nice-to-have; lazy-expiry already keeps state correct on any observation.
- [ ] **KYC photo purge** — 30-day job clearing `photo_*_url` after `verifiedAt`. db-scheduler task. Not required until we're actually storing photos in S3 (Phase 8).
- [ ] **Admin RBAC for KYC approve/reject** — currently anyone can hit `POST /kyc/approve`. Wait for JWT + admin role in AuthController.
- [ ] **Notification hook** for T3/T6 events — belongs to the notifications system (Phase 8).
- [ ] **Delete-blocked-when-tenant-has-audit-history** — currently a soft-deleted profile is fully hidden by is_active=false; the deletion doctrine (§17.2) will need a review pass once the audit_log arrives.

---

## Test coverage

45/45 scenarios pass — see [`TEST_RESULTS.md`](../api-tests/TenancyController/TEST_RESULTS.md). Every §14.3 case handled this pass is exercised end-to-end: T2 reject-then-re-request, T3 block/unblock and its effect on join requests, T4 duplicate PENDING refused, T5 full KYC lifecycle with resubmit-count increment and terminal APPROVED, T6 flag from any state, T7 unlinked direct-membership, T10 payment-model override. Also covers the room single-occupancy invariant across memberships (a room can only be assigned to one tenancy at a time), cross-property room assignments blocked, cascade termination (terminate a membership → all active room assignments end), and the profile-delete-with-active-membership gate.
