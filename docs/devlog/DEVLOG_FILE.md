# FileController — Detailed Dev Log

**Controller:** `FileController`
**Base URL:** `/api/v1/files`
**Table:** `stored_files` (V15)
**Status:** Implemented on `feature/app-integration`; live test `docs/api-tests/FileController/files_test.ps1`

---

## Files

| File | Purpose |
|------|---------|
| `db/migration/V15__stored_files.sql` | Upload metadata: owner, optional property, purpose, sniffed type, size, SHA-256, storage key, display name, soft delete |
| `domain/file/entity/StoredFile.java` | Entity + `FilePurpose` |
| `domain/file/storage/FileStorage.java` | Storage port: `store`, `open`, `delete` |
| `domain/file/storage/LocalFileStorage.java` | Development storage under `app.files.local-dir` (`FILES_DIR`, default `./uploads`) |
| `domain/file/service/FileTypeSniffer.java` | Type from magic bytes (JPEG, PNG, WebP, PDF) |
| `domain/file/service/FileService.java` | Upload / download / delete rules |
| `domain/file/controller/FileController.java` | 4 endpoints |

## Endpoints

| Method | Path | Body / params | Response |
|---|---|---|---|
| POST | `/files` | multipart `file`; `purpose`; optional `propertyId` | 201 `{id, purpose, contentType, sizeBytes, propertyId, originalName, url, createdAt}` |
| GET | `/files/{id}` | — | metadata |
| GET | `/files/{id}/content` | — | the bytes (`Cache-Control: no-store, private`, `X-Content-Type-Options: nosniff`) |
| DELETE | `/files/{id}` | — | soft delete, bytes removed |

`purpose`: `KYC_ID_FRONT`, `KYC_ID_BACK`, `KYC_SELFIE`, `METER_PHOTO`, `PAYMENT_PROOF`, `PAYMENT_QR`, `PROFILE_PHOTO`, `AGREEMENT`, `REQUEST_PHOTO`, `OTHER`. Selfie, meter photo, QR, profile and request photos must be images.

Other endpoints store the returned `url` where they take a photo / document reference (KYC `photo*Url`, reading `photoUrl`, and the payment / request flows that follow).

## Rules

* **Who uploads:** any signed-in user; the uploader owns the file. A file tied to a property needs access to that property, or a membership / join request there (tenants sending proofs or KYC). A `PAYMENT_QR` needs owner/manager rights on the property.
* **Who reads:** the uploader; an admin; anyone with access to the file's property; and, for a `PAYMENT_QR` only, the property's active tenants (they scan it to pay). Files without a property are private to the uploader. Nothing is public; every read needs the bearer token.
* **Type:** from the first bytes, never the name or `Content-Type`. Anything else → 400 `FILE_TYPE_NOT_ALLOWED`.
* **Size:** 5 MB (`app.files.max-bytes`, `spring.servlet.multipart`). Larger → 413 `FILE_TOO_LARGE`.
* **Names and paths:** the storage key is `yyyy/MM/<uuid>`, generated server-side. The client's file name is kept for display only, without folders or control characters. `LocalFileStorage` refuses any key that resolves outside its folder.
* **Consistency:** bytes are written first (temp file + atomic move); if the metadata row then fails, the bytes are deleted.
* **Delete:** uploader or admin; the row stays (`deleted_at`) for audit.

## Switching storage

Implement `FileStorage` for S3 or Supabase Storage and make it the active bean. Keys, metadata, endpoints and the app are unchanged; existing local files need copying under the same keys.

## Error codes

| Status | Codes |
|---|---|
| 400 | `FILE_EMPTY`, `FILE_UNREADABLE`, `FILE_TYPE_NOT_ALLOWED`, `INVALID_PARAMETER` (purpose), `MISSING_PARAMETER` (no `file` part), `MALFORMED_REQUEST` |
| 401 | `UNAUTHENTICATED` |
| 403 | `FORBIDDEN` |
| 404 | `FILE_NOT_FOUND` |
| 413 | `FILE_TOO_LARGE` |
| 503 | `STORAGE_UNAVAILABLE` |

## Tests

* Unit: `FileTypeSnifferTest`, `LocalFileStorageTest`.
* Live: `files_test.ps1` — see `docs/api-tests/FileController/TEST_RESULTS.md`.
