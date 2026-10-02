# Tenant requests — Detailed Dev Log

**Table:** `tenant_requests` (V20)
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/Requests/requests_test.ps1`

## Endpoints

| Method | Path | Who |
|---|---|---|
| POST | `/memberships/{id}/requests` `{type, title, description?, preferredDateBs?, photoFileId?}` | the tenant or a manager |
| GET | `/memberships/{id}/requests` | property viewers, the tenant |
| GET | `/properties/{id}/requests?status=` | property viewers |
| GET | `/me/requests` | signed-in tenant |
| POST | `/requests/{id}/approve` `{note?, fromRoomId?, toRoomId?, effectiveDateBs?}` | manager |
| POST | `/requests/{id}/reject` `{note}` | manager |
| POST | `/requests/{id}/complete` `{note?}` | manager |
| POST | `/requests/{id}/cancel` | the tenant or a manager |

Types: ROOM_CHANGE, VACATE, MAINTENANCE, OTHER. Statuses: PENDING → APPROVED → COMPLETED; PENDING → REJECTED; PENDING → CANCELLED.

## Rules

* One open (pending / approved) ROOM_CHANGE and one open VACATE per tenancy (409 `REQUEST_ALREADY_OPEN`, partial unique index); maintenance and other may repeat.
* VACATE needs a date (400 `DATE_REQUIRED`); dates must be real BS dates and not in the past.
* Photo: the caller's own upload with purpose REQUEST_PHOTO (400 `PHOTO_INVALID`).
* Reject needs a note (400 `NOTE_REQUIRED`). Every status change locks the row; a second decision → 409 `REQUEST_ALREADY_DECIDED`.
* **Approve a VACATE** → opens the move-out notice for the requested date (unless one is already open).
* **Approve a ROOM_CHANGE with `toRoomId`** → `RoomTransferService.transfer` runs in the same transaction (from the tenant's only room, or `fromRoomId`) and the request becomes COMPLETED; if the room is taken (409) nothing changes. Without `toRoomId` it is just APPROVED.

## App

Owner Requests: the property's requests with tenant and room; Approve (room change: pick a vacant room or approve without moving), Reject with a reason, Mark done. Tenant Requests tab: own requests, Withdraw while pending, New Request (type, title, details, date for room change / vacate, camera photo for maintenance).
