# Notifications — Detailed Dev Log

**Tables:** `notifications`, `device_tokens` (V23)
**Endpoints (signed-in user, own data only):** `GET /api/v1/me/notifications?page&size` (newest first), `GET /api/v1/me/notifications/unread-count`, `POST /api/v1/me/notifications/{id}/read`, `POST /api/v1/me/notifications/read-all`, `POST /api/v1/me/devices {token, platform}`
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/Notifications/notifications_test.ps1`

## What creates a notification

| Event (service) | Recipient | Type | Opens |
|---|---|---|---|
| Billing run sent (`BillingRunService.confirm`) | each tenant | BILL_ISSUED (month · amount · due date) | bill |
| Owner records a payment (`PaymentService.record`) | tenant | PAYMENT_RECORDED | bill |
| Tenant sends a proof (`submitProof`) | property staff | PAYMENT_PROOF_WAITING | bill |
| Owner approves / rejects a proof | tenant | PAYMENT_APPROVED / PAYMENT_REJECTED (with reason) | bill |
| Tenant submits a meter reading (`ReadingDueService.submitOwn`) | property staff | READING_SUBMITTED | meter |
| Request raised (`TenantRequestService.create`) | staff (by tenant) or tenant (by owner) | REQUEST_CREATED | request |
| Request approved / rejected / completed | tenant | REQUEST_DECIDED | request |
| Join request (`JoinRequestService.create`, incl. join by code) | property staff | JOIN_REQUESTED | join request |
| Join request accepted / rejected | tenant | JOIN_ACCEPTED / JOIN_REJECTED | membership / join request |
| Move-out notice (`MoveOutService.giveNotice`) | staff (by tenant) or tenant (by owner) | MOVE_OUT_NOTICE | move-out |
| Move-out settled | tenant | MOVE_OUT_SETTLED (refund, still owed) | move-out |

"Property staff" = active `property_access` users of the property (owner and managers). A tenant is reached through their linked account; unlinked tenants get nothing. The person who made the change is never notified of it.

## Rules

* Written with `Propagation.MANDATORY` inside the change's own transaction: if the change rolls back, so does the notification.
* Amounts use Nepali grouping with "Rs" (`NotificationService.rs`), never the rupee sign.
* `title` is English; the app shows a translated title by `type`. `body` holds the details (month, amount, names, reasons).
* `device_tokens` stores push tokens (ANDROID / IOS / WEB) for Firebase later; nothing is pushed yet.

## App

Bell on the owner and tenant home screens shows an unread badge (refreshed every minute). Notifications screen (live): newest first, unread marked, tap → marks read and opens the bill / request / join requests / readings / move-out; "Mark all as read". Titles in English, Hindi, Nepali.
