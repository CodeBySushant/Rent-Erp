# FileController — Test Results

Script: `files_test.ps1` (PowerShell, live against PostgreSQL). Backend started
from `backend\` with `APP_ENV=local`.

```powershell
powershell -ExecutionPolicy Bypass -File docs\api-tests\FileController\files_test.ps1
```

| Area | Scenarios |
|---|---|
| Owner QR | upload (201, content url, folder part of the name dropped, type from bytes), download same bytes, metadata |
| Access | other owner 403, not-yet-tenant 403, no token 401; after joining, the active tenant reads the QR |
| Tenant proof | tenant uploads a PDF proof to the property; the property owner reads it, another owner 403; tenant cannot upload a QR (403); another owner cannot attach files to the property (403) |
| Validation | text renamed .png 400, PDF as meter photo 400, unknown purpose 400, missing part 400, over 5 MB 413, no token 401 |
| Private files | a file with no property is visible to its uploader only |
| Delete | another owner 403, uploader 200, then 404 |

Unit: `FileTypeSnifferTest` (accepted formats, refused formats, name cleaning), `LocalFileStorageTest` (round trip, keys cannot leave the folder).

## Runs

| Date | Passed | Failed | Notes |
|---|---|---|---|
| 2026-10-02 | 25 | 3 | The 3 failures were the script: `$qr` and `$QR` are the same variable in PowerShell, so the upload response was overwritten by its id before the checks on url, name and type. Renamed; backend behaviour was correct (download, access and validation checks all passed). |
