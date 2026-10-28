# Profile photos — Dev Log

**Date:** 2026-10-28
**Branch:** `feature/app-integration`
**Migration:** `V26__profile_photo.sql` (V25 → V26)
**App side:** `Renterp-frontend` commit `feat(profile): upload and show real profile photos`

## Why

The app showed "Profile photo upload is coming soon": the backend had nowhere
to keep a photo. `stored_files` already allowed purpose `PROFILE_PHOTO`, but
no account pointed at a file, no response carried a photo URL, and nothing
let an owner see a tenant's photo.

## What changed

| Piece | Change |
|---|---|
| Schema | `users.photo_file_id UUID REFERENCES stored_files(id)` (nullable) |
| Entity | `User.photoFileId` |
| `UserResponse` | `photoUrl` — `/api/v1/files/{id}/content` or null. Returned by `/auth/me`, login, register, phone/email change |
| `PUT /api/v1/me/photo` | Body `{ "fileId": uuid }`. The file must exist, belong to the caller (403 otherwise) and have purpose `PROFILE_PHOTO` (400 `FILE_INVALID`). Sets it as the photo, deletes the previous photo file. Returns the `UserResponse` |
| `DELETE /api/v1/me/photo` | Clears the photo and deletes its file. Returns the `UserResponse` |
| `TenantRowResponse` (`GET /properties/{id}/tenants`) | New last field `photoUrl` (the tenant's linked account's photo, or null) |
| `TenantProfileResponse` (`/tenant-profiles/...`) | New field `photoUrl`; the app's join-request cards read it |
| `FileService.canRead` | A `PROFILE_PHOTO` file can be read by its owner, an admin, or anyone with at least view access to a property where the owner has a tenancy (any status) or a join request — and only while it is that account's **current** photo |
| `FileService.delete` | Deleting the current photo file also clears `users.photo_file_id` |

New class: `domain/auth/service/ProfilePhotoService.java` (set, remove, URLs
for lists).

## Upload flow (what the app does)

1. `POST /api/v1/files` multipart, `purpose=PROFILE_PHOTO`, no `propertyId`.
   The existing checks apply: JPG/PNG/WebP only (PDF refused for this
   purpose), 5 MB cap, per-user daily and total quotas.
2. `PUT /api/v1/me/photo {fileId}` with the returned id.
3. Show `photoUrl` with the `Authorization` header (files are never public).

A failed step 2 leaves the upload unused; it counts towards the quota until
deleted, like any other unused upload.

## Privacy

Photos are private files: no public URL, `Cache-Control: no-store, private`.
An owner sees a tenant's photo only for tenants of (or applicants to) their
properties. A replaced or removed photo is deleted and unreadable to others.

## Tests

`src/test/java/com/renterp/domain/auth/service/ProfilePhotoServiceTest.java`:
own photo set and URL returned; replacing deletes the old file; someone
else's upload 403; wrong purpose `FILE_INVALID`; missing file 404; remove
clears and deletes; list URLs only for accounts with a photo.

## Try it

```powershell
# after login, $h = @{ Authorization = "Bearer $token" }
$up = Invoke-RestMethod -Method Post -Uri "$base/api/v1/files?purpose=PROFILE_PHOTO" -Headers $h -Form @{ file = Get-Item .\me.jpg }
Invoke-RestMethod -Method Put -Uri "$base/api/v1/me/photo" -Headers $h -ContentType 'application/json' -Body (@{ fileId = $up.data.id } | ConvertTo-Json)
(Invoke-RestMethod -Uri "$base/api/v1/auth/me" -Headers $h).data.photoUrl
```
(`-Form` needs PowerShell 7.)

---

## Edit Profile save (V27)

**Why.** The app's Edit Profile "Save Changes" button only showed "Profile
changes are saved once the user API is wired (Phase 2)". The name could
already be saved (`PUT /api/v1/users/{id}`, self or admin), but the app never
called it, and the screen's Location field had no column at all.

**Change.**

| Piece | Change |
|---|---|
| Schema | `V27__user_location.sql`: `users.location VARCHAR(120)` (nullable) |
| `User` | `location` |
| `UpdateUserRequest` | `location` (max 120); blank clears it |
| `UserService.updateUser` | `name` is trimmed; a blank name is refused with 400 `NAME_REQUIRED` (it used to be stored as empty); `location` trimmed, blank → null |
| `UserResponse` | `location` |

Phone and email are not edited here: they stay on the verified change flow
(`/me/phone`, `/me/email`, one-time code).
