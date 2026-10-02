# Readings due and tenant readings — Detailed Dev Log

**Endpoints:** `GET /api/v1/properties/{propertyId}/readings/due`, `GET /api/v1/me/meters`, `POST /api/v1/me/meters/{meterId}/readings`
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/Readings/readings_test.ps1`

## What each returns

Per active meter: label, serial, type, purpose, responsibility, covered room names, last confirmed value and date, and this BS month's state — NONE / PENDING / CONFIRMED with the reading id, value, photo URL and whether a tenant submitted it. The tenant view adds `canSubmit` and, when false, the reason.

## Who may submit (tenant)

Active tenancy at the meter's property, the meter is TENANT_SUPPLY, and:

| Responsibility | Tenant may read when |
|---|---|
| LANDLORD_ONLY | never (403) |
| DESIGNATED_TENANT | they are `meters.designated_tenant_id` |
| FIRST_SUBMISSION_WINS | the meter currently covers one of their rooms |

The meter must already have a confirmed reading (the owner records the first, INITIAL, reading) → else 400 `NO_FIRST_READING`. One BILLING_RUN reading per meter per BS month: a second submission → 409 `READING_ALREADY_SUBMITTED` (first submission wins). The photo, if sent, must be the tenant's own upload with purpose METER_PHOTO (400 `PHOTO_INVALID`). The reading is created through `MeterReadingService.submitReading` (its value / rollover rules still apply), dated today (BS), PENDING, with `submitted_by` = the tenant; the owner confirms or discards it with the existing `/readings/{id}/confirm` and `DELETE /readings/{id}`.

## App

Owner Readings: every meter with Due / Waiting for confirmation / Done; record a new meter's first reading, enter the monthly reading (submit + confirm), or confirm / discard a tenant's reading with its photo. Tenant Reading Centre: their meters; submit the reading with a camera photo; owner-only / other-tenant / no-first-reading meters show why they cannot.
