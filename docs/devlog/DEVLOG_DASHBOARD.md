# DashboardController — Detailed Dev Log

**Controller:** `DashboardController` (read-only)
**Endpoints:** `/api/v1/dashboard`, `/api/v1/properties/{propertyId}/summary`, `/api/v1/properties/{propertyId}/tenants`
**Tables read:** properties, floors, rooms, room_assignments, tenant_property_memberships, tenant_profiles, join_requests, billing_runs, tenant_bills, meters, meter_reading_log
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/DashboardController/dashboard_test.ps1`

---

## Why

The app's dashboard, property list and tenant list used to compose their numbers from many calls (rooms + memberships per property; profile + assignments + bill per tenant) and still showed "—" where no endpoint existed. These endpoints compute everything server-side from current data in a fixed number of queries per property.

## Files

| File | Purpose |
|------|---------|
| `domain/dashboard/service/DashboardQueries.java` | JPQL aggregates (counts, sums, latest run, live bills) |
| `domain/dashboard/service/DashboardService.java` | Summary, owner dashboard, tenant rows |
| `domain/dashboard/controller/DashboardController.java` | 3 endpoints |
| `domain/dashboard/dto/*` | `PropertySummaryResponse`, `OwnerDashboardResponse`, `TenantRowResponse` |
| `common/util/BsCalendar.java` | + `fromAd`, `today()` (Nepal time), `monthStart` |
| `tenancy/repository/RoomAssignmentRepository.java` | + current assignments for many memberships |

## Definitions

| Number | Meaning |
|---|---|
| totalRooms | active rooms on active floors |
| occupiedRooms | distinct rooms with an open assignment (`effective_to_bs` null) on an ACTIVE membership |
| vacantRooms | total − occupied (never negative) |
| activeTenants | ACTIVE memberships |
| pendingJoinRequests | PENDING join requests |
| outstanding / amountOwed | sum of `balance_due` on ISSUED bills not superseded by a correction (drafts owe nothing yet) |
| overdueTenants / overdue | an ISSUED, unsuperseded bill with `balance_due > 0` and `due_date_bs` before today (BS, Nepal time) |
| currentBilling | newest non-cancelled run: billed / collected / pending over its live bills, bills paid |
| readingsPending | active meters without a CONFIRMED reading dated in the current BS month |

## Access

Summary and tenants: property VIEW_ONLY or higher (`ResourceAccess.property`). Dashboard: the caller's visible properties (`AccessGuard.visiblePropertyIds`), every active property for an admin.

## Today's date

`BsCalendar.today()` converts today's date in Asia/Kathmandu to BS from one anchor (BS 2000-01-01 = AD 1943-04-14, the same anchor as the app) using the existing month-length table. Unit-tested on New Year's Day 2000, 2081 and 2082.
