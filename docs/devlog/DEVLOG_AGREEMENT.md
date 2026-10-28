# Rental agreement document — Dev Log

**Date:** 2026-10-28
**Branch:** `feature/app-integration`
**Migration:** `V30__tenancy_agreement.sql` (V29 → V30)
**App side:** `Renterp-frontend` commit `feat(agreement): owners add the signed agreement, tenants download it`

## Why

My Agreement showed the tenancy terms with a disabled "Download PDF" and
"Not available yet": `stored_files` accepted purpose `AGREEMENT`, but nothing
linked such a file to a tenancy or let the tenant read it.

## What changed

| Piece | Change |
|---|---|
| Schema | `tenant_property_memberships.agreement_file_id` (FK `stored_files`), `agreement_uploaded_at`, `agreement_uploaded_by` (FK `users`) |
| `GET /api/v1/memberships/{id}/agreement` | Owner / manager / viewer of the property, or the tenant of that tenancy. `AgreementResponse` or `data: null` |
| `PUT /api/v1/memberships/{id}/agreement` | Owner or manager. `{fileId}` — the caller's own upload with purpose `AGREEMENT` (PDF or photo). Replaces any earlier agreement (the old file is deleted) and notifies the tenant |
| `DELETE /api/v1/memberships/{id}/agreement` | Owner or manager. Clears it and deletes the file |
| `GET /api/v1/me/stay` | Each stay gains `agreement` (`AgreementResponse` or null) |
| `FileService.canRead` | An `AGREEMENT` file is readable by the tenant whose tenancy it belongs to (besides the uploader, admins and the property's staff, as before) |
| `FileService.discard` | New: deletes a replaced file whoever uploaded it; the bytes go only after the transaction commits |
| Notifications | `AGREEMENT_ADDED` ("Agreement added", en/hi/ne), entity `AGREEMENT`, to the tenant |

`AgreementResponse = {membershipId, fileId, url, contentType, fileName,
sizeBytes, uploadedAt}`; `url` is `/api/v1/files/{id}/content` (bearer token).

New classes: `TenancyAgreementService`, `TenancyAgreementController`,
`AgreementResponse`.

## Flow (what the app does)

1. Owner: `POST /api/v1/files?purpose=AGREEMENT&propertyId=<property>`
   (PDF, JPG, PNG or WebP; 5 MB; the usual quotas).
2. `PUT /api/v1/memberships/{id}/agreement {fileId}`.
3. Tenant: `GET /me/stay` → `agreement.url` → `GET` it with the token.

## Errors

| Status | Code | When |
|---|---|---|
| 400 | `FILE_INVALID` | the upload is not purpose `AGREEMENT` |
| 400 | `FILE_OTHER_PROPERTY` | the upload is tied to another property |
| 403 | `FORBIDDEN` | someone else's upload, or no write access to the property |
| 404 | `FILE_NOT_FOUND`, `TENANCY_NOT_FOUND` | unknown ids |

## Tests

`TenancyAgreementServiceTest` (7): set attaches the file to the property,
records who/when and notifies the tenant; replacing deletes the old file;
someone else's upload or the wrong purpose refused; write access required for
set and remove; remove clears and deletes; get is null without one and checks
read access; unknown tenancy 404.

## Try it

```powershell
# owner token in $h
$f = Invoke-RestMethod -Method Post "$base/api/v1/files?purpose=AGREEMENT&propertyId=$propertyId" -Headers $h -Form @{ file = Get-Item .\agreement.pdf }
Invoke-RestMethod -Method Put "$base/api/v1/memberships/$membershipId/agreement" -Headers $h `
    -ContentType application/json -Body (@{ fileId = $f.data.id } | ConvertTo-Json)
# tenant token in $t
(Invoke-RestMethod "$base/api/v1/me/stay" -Headers $t).data.stays[0].agreement
```
