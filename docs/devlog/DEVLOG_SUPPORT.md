# Support tickets — Dev Log

**Date:** 2026-10-28
**Branch:** `feature/app-integration`
**Migration:** `V29__support_tickets.sql` (V28 → V29)
**App side:** `Renterp-frontend` commit `feat(support): send Contact Support messages with an attachment`

## Why

Contact Support's form (Subject, Message, Attach a file, Send Message) said
"Support tickets need the support service (Phase 2)": nothing could store a
message, take an attachment, or get it to the people who answer.

## What changed

| Piece | Change |
|---|---|
| Schema | `support_tickets` (user, topic, message ≤ 4000, optional attachment, OPEN/RESOLVED, resolution note, resolved by/at, audit + version). One ticket per attachment (partial unique index) |
| Schema | `stored_files` purpose `SUPPORT_ATTACHMENT` (photo or PDF) |
| `POST /api/v1/support/tickets` | Signed in. `{topic, message, attachmentFileId?}` → 201 `SupportTicketResponse` |
| `GET /api/v1/support/tickets` | The caller's own tickets, newest first (`PagedResponse`, `page`, `size` ≤ 100) |
| `GET /api/v1/admin/support/tickets` | ADMIN. Every ticket with sender name, phone, email, role; `?status=OPEN\|RESOLVED` |
| `POST /api/v1/admin/support/tickets/{id}/resolve` | ADMIN. `{note?}` → resolved; the user gets an in-app notification `SUPPORT_TICKET_RESOLVED` ("Support replied", entity `SUPPORT_TICKET`) |
| `NotificationService.toUser` | New: notify one account directly (no property) |
| `MailGateway` | Reply-To overloads |
| `application.yml`, `.env.example` | `app.support.inbox` ← `SUPPORT_INBOX` |

New package `domain/support` (entity, repository, DTOs, service, controller).

`topic`: `ACCOUNT`, `PROPERTY`, `BILLING`, `TENANTS`, `OTHER` — the app's
subjects "Account & Login", "Property Management", "Billing & Payments",
"Tenants & Leases", "Something else".

`SupportTicketResponse = {id, reference, topic, message, attachmentFileId,
attachmentUrl, status, resolutionNote, createdAt, resolvedAt}`; `reference` is
`T-` + the first 8 characters of the id (what a user quotes on the phone).

## Rules

* **Attachment:** upload first (`POST /api/v1/files?purpose=SUPPORT_ATTACHMENT`,
  no `propertyId`; JPG/PNG/WebP/PDF, 5 MB), then send its id. It must be the
  caller's own upload with that purpose and not already on a ticket — else 400
  `ATTACHMENT_INVALID`. Only the uploader and admins can open it (the existing
  file rule for files without a property).
* **Message:** trimmed; at least 10 characters (400 `MESSAGE_TOO_SHORT`), at
  most 4000 (400 `VALIDATION_FAILED`).
* **Limit:** 5 tickets per account per hour (429 `SUPPORT_RATE_LIMITED`).
* **Inbox email:** when `MAIL_HOST` and `SUPPORT_INBOX` are both set, each new
  ticket is emailed to the inbox *after* it is committed, on a virtual thread,
  with Reply-To the user's email — support answers straight from the inbox.
  A mail failure is logged and never fails or loses the ticket. Without them
  the ticket is only stored; read the queue with the admin endpoint.
* **Resolve:** admin only, once (409 `TICKET_ALREADY_RESOLVED`); unknown id
  404 `SUPPORT_TICKET_NOT_FOUND`; bad `status` filter 400 `INVALID_STATUS`.

## Tests

`SupportTicketServiceTest` (9): stored OPEN with reference, trimmed message
and attachment URL, rate limit applied; short message refused; attachment of
someone else / wrong purpose / reused refused; rate limit stops the ticket;
inbox copy with Reply-To, subject and sender details; stored only without
inbox or email; admin resolve sets note and resolver and notifies the user;
resolving twice is 409; reference format.

## Try it

```powershell
# $h = @{ Authorization = "Bearer $token" }
$f = Invoke-RestMethod -Method Post "$base/api/v1/files?purpose=SUPPORT_ATTACHMENT" -Headers $h -Form @{ file = Get-Item .\screenshot.png }
$t = Invoke-RestMethod -Method Post "$base/api/v1/support/tickets" -Headers $h -ContentType application/json `
      -Body (@{ topic = "BILLING"; message = "My October bill shows the wrong amount."; attachmentFileId = $f.data.id } | ConvertTo-Json)
$t.data.reference       # T-xxxxxxxx
Invoke-RestMethod "$base/api/v1/support/tickets" -Headers $h
# admin token:
Invoke-RestMethod "$base/api/v1/admin/support/tickets?status=OPEN" -Headers $admin
Invoke-RestMethod -Method Post "$base/api/v1/admin/support/tickets/$($t.data.id)/resolve" -Headers $admin `
      -ContentType application/json -Body (@{ note = "Fixed - the bill was re-issued." } | ConvertTo-Json)
```

## Open items

* No admin screen yet: the queue is the admin API (or the inbox email).
* Replies are one note at resolve time; a back-and-forth thread would need a
  messages table.
