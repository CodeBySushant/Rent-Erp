# Notification preferences — Dev Log

**Date:** 2026-10-28
**Branch:** `feature/app-integration`
**Migration:** `V31__notification_preferences.sql` (V30 → V31)
**App side:** `Renterp-frontend` commit `feat(profile): save Notification Setting to the account`

## Why

Profile → Notification Setting had ten toggles that lived only in the
screen: they reset as soon as it closed, and nothing on the server knew them.

## What changed

| Piece | Change |
|---|---|
| Schema | `notification_preferences` — one row per account (PK `user_id`), six push and four email booleans, audit + version. No row = defaults |
| `GET /api/v1/me/notification-preferences` | The caller's choices (defaults if never changed) |
| `PUT /api/v1/me/notification-preferences` | Any subset of the fields; missing (null) fields keep their value. Returns all ten |
| `NotificationService.save` | Every notification still goes to the in-app list; it is **pushed** only if the matching push toggle is on |
| New | `NotificationPreferences` (entity), `NotificationPreferencesRepository`, `NotificationPreferencesDto`, `NotificationPreferenceService`, `NotificationPreferenceController` |

Fields: `pushRentReminders`, `pushPaymentReceived`, `pushPaymentOverdue`,
`pushNewTenant`, `pushMaintenance`, `pushGeneralUpdates`,
`emailMonthlyStatements`, `emailPaymentReceipts`, `emailImportantAlerts`,
`emailMarketing`. Defaults (the screen's design): all on except Maintenance,
General Updates and Marketing.

## Which toggle controls which push

| Toggle | Notification types |
|---|---|
| Rent Reminders | `BILL_ISSUED` |
| Payment Received | `PAYMENT_RECORDED`, `PAYMENT_PROOF_WAITING`, `PAYMENT_APPROVED`, `PAYMENT_REJECTED` |
| New Tenant | `JOIN_REQUESTED`, `JOIN_ACCEPTED`, `JOIN_REJECTED` |
| Maintenance Requests | `REQUEST_CREATED`, `REQUEST_DECIDED` |
| (always pushed) | `READING_SUBMITTED`, `MOVE_OUT_NOTICE`, `MOVE_OUT_SETTLED`, `AGREEMENT_ADDED`, `SUPPORT_TICKET_RESOLVED` |

Payment Overdue and General Updates have no notification type yet, and the
four email choices have no email behind them yet: they are stored so those
features honour them when they are built. Push itself is delivered by the
`PushSender` (logging only until Firebase is connected).

## Tests

`NotificationPreferenceServiceTest` (6): defaults without a row; first save
creates the row and keeps unsent fields; later save changes only sent
fields; push follows the matching toggle; account-critical types always
pushed; stored choices used (and defaults for an account without a row).

## Try it

```powershell
Invoke-RestMethod "$base/api/v1/me/notification-preferences" -Headers $h
Invoke-RestMethod -Method Put "$base/api/v1/me/notification-preferences" -Headers $h `
    -ContentType application/json -Body (@{ pushMaintenance = $true } | ConvertTo-Json)
```
