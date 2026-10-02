# Add Tenant (TenantOnboardingController) — Detailed Dev Log

**Endpoint:** `POST /api/v1/properties/{propertyId}/tenants`
**Tables written:** `tenant_profiles`, `tenant_property_memberships`, `room_assignments`, `tenant_deposits`
**Migration:** V16 — one open assignment per room (partial unique index)
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/TenantOnboarding/add_tenant_test.ps1`

---

## Request

`{fullName, phone, whatsappNumber?, preferredLanguage?, numOccupants?, emergencyContactName?, emergencyContactPhone?, roomId, moveInDateBs, monthlyRent, depositAmount?, depositReceivedAtBs?, paymentModelOverride?}` → 201 `{membershipId, tenantProfileId, roomAssignmentId, depositId|null, propertyId, roomId}`. Needs owner/manager rights on the property.

## Order of work (`TenantOnboardingService.addTenant`, one transaction)

1. **Check everything before writing:** property exists and is active; move-in (and deposit) date is a real BS date; the room exists, is active, belongs to this property; the room has no current tenant; no tenant with this phone already lives here.
2. **Create through the existing services**, so their own rules still apply: `TenantProfileService.createProfile` (records the creator) → `MembershipService.createInternal` (one active membership per tenant and property) → `MembershipService.assignRoom` (cross-property check, single occupancy) → `TenantDepositService.create` when a deposit is given.
3. Any failure rolls back every step: no profile without a membership, no membership without a room, no deposit without a tenancy.

## Concurrency

The room row is read with `SELECT … FOR UPDATE` (`RoomRepository.findByIdForUpdate`), so two Add Tenant requests for the same room run one after the other and the second sees the room taken (409 `ROOM_OCCUPIED`). V16 adds `uq_room_assignments_room_open` so even a different code path cannot leave two open assignments on one room (it would get 409 `DATA_CONFLICT`).

## Error codes

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | missing/invalid field (phone, rent, dates format) |
| 400 | `INVALID_DATE` | not a real BS date |
| 400 | `ROOM_NOT_FOUND`, `ROOM_NOT_IN_PROPERTY`, `ROOM_INACTIVE` | room problems |
| 400 | `PROPERTY_INACTIVE` | property deactivated |
| 403 | `FORBIDDEN` | not an owner/manager of the property |
| 409 | `ROOM_OCCUPIED` | room has a current tenant |
| 409 | `TENANT_ALREADY_ACTIVE` | a tenant with this phone already lives here |

## App

Add Tenant (`AddTenantScreen`) calls this once, offers only vacant rooms, shows the server's message on refusal, and refreshes the tenant list, property detail and dashboard on success.
