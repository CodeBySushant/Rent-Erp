# Room transfer — Detailed Dev Log

**Endpoint:** `POST /api/v1/memberships/{membershipId}/room-transfer` `{fromRoomId, toRoomId, effectiveDateBs, monthlyRent?}` (owner / manager)
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/RoomTransfer/transfer_test.ps1`

## Rules

* One transaction: the tenant's open assignment of the from-room ends on the date and a new assignment of the to-room starts that day (through `MembershipService.assignRoom`, which re-checks property and vacancy). Rent defaults to the old room's rent.
* Both rooms are locked (`SELECT … FOR UPDATE`, in id order so two transfers cannot deadlock); V16's one-open-assignment-per-room index backs it up.
* Refusals (nothing changes): tenancy ended 400 `TENANCY_ENDED`; same room 400 `SAME_ROOM`; not in the from-room 400 `NOT_IN_ROOM`; date not after the current move-in 400 `INVALID_DATE`; room of another property 400 `ROOM_NOT_IN_PROPERTY`; removed room 400 `ROOM_INACTIVE`; taken room 409 `ROOM_OCCUPIED`; another owner 403.
* History: the old assignment is closed, not deleted; billing for days before the date uses the old room and rent.

## App

Owner tenant page → Add Rooms → the tenant's rooms: move one to a vacant room (date, rent) or add another vacant room.
