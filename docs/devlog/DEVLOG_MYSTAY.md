# My Stay (GET /me/stay) — Detailed Dev Log

**Endpoint:** `GET /api/v1/me/stay` — always about the signed-in user; no id parameter
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/MyStay/mystay_test.ps1`

## Response

`{todayBs, stays: [...], pendingRequests: [...]}`

Each stay: membership id and status, start/end dates, property (name, address, city, join code), landlord name, landlord phone (**only while the tenancy is active**), current rooms with floor names and rent, monthly rent total, deposit (amount, status, date), notice period, billing day, grace period, current bill (month, status, payment status, total, paid, balance, due date), amount owed (issued, unsuperseded bills) and an overdue flag. Active tenancies come first.

Each pending request: join request id, property name and join code, requested at.

A user without a tenant profile gets empty lists (200).

## Implementation

`MyStayService` resolves the caller's linked profile, then loads memberships, properties, owners, current assignments, rooms, floors, deposits and live bills in a fixed number of queries (bills via `DashboardQueries.liveBills`, the same "owed / overdue" rules as the owner dashboard).

## App

`TenantStayGate` (home, My Rooms) loads `/me/stay`: active stay → the screens show it; pending request → "Request sent, waiting for the owner"; nothing → join prompt. Tenant screens read the stay through `TenantStayScope.of(context)` instead of the sample. Join flow: code → `GET /join/{code}` → Property found (real property and landlord) → Send Join Request → `POST /join/{code}` → home shows the pending note until the owner accepts.
