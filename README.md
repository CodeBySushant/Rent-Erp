# Rent ERP — Backend

Rental management SaaS for Nepal. Landlord-primary design: the landlord enters meter readings once a month on one screen, and the system produces correct, itemised, auditable bills for every tenant.

BS (Bikram Sambat) calendar throughout. NPR only.

---

## Table of contents

1. [Tech stack](#tech-stack)
2. [Prerequisites](#prerequisites)
3. [First-time setup](#first-time-setup)
4. [Running the backend](#running-the-backend)
5. [Verifying it works](#verifying-it-works)
6. [Testing the API with Postman](#testing-the-api-with-postman)
7. [Resetting the database](#resetting-the-database)
8. [Project structure](#project-structure)
9. [API surface](#api-surface)
10. [Troubleshooting](#troubleshooting)

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 25 (LTS) |
| Framework | Spring Boot 4.1.0 |
| Build | Maven 3.9+ (no wrapper — Maven must be installed) |
| Database | PostgreSQL 13+ (developed against 17) |
| Migrations | Flyway (V1 → V12) |
| ORM | Hibernate / Spring Data JPA, `ddl-auto: validate` |
| Job queue | db-scheduler 15.0.0 (Postgres-backed, no Kafka) |
| Connection pool | HikariCP (max 20) |
| Logging | Log4j2 (Logback excluded) |
| Concurrency | Virtual threads enabled (Project Loom) |
| Config | `.env` via dotenv-java; real env vars take precedence |

**Architecture:** monolith, not microservices. Flyway owns the schema; Hibernate only validates that entities match it. If they drift, startup fails loudly.

---

## Prerequisites

Install these before anything else.

| Requirement | Version | Verify with |
|---|---|---|
| JDK | **25** (hard requirement) | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| PostgreSQL | 13+ | `psql --version` |

### Important: Maven must run on JDK 25

`mvn -v` prints the JDK **Maven itself** is using, which comes from `JAVA_HOME` — not from your PATH. If you have multiple JDKs installed, `java -version` and `mvn -v` can disagree.

`pom.xml` sets `<java.version>25</java.version>`. If Maven is on anything older you get `invalid source release: 25`.

Fix it (Windows PowerShell):

```powershell
# Current session only
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"

# Permanent (new terminals only)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot", "User")
```

Adjust the path to your actual JDK 25 folder. Confirm with `mvn -v` — it should report `Java version: 25.x`.

### PostgreSQL on PATH

If `psql` isn't recognised, its `bin` folder isn't on PATH:

```powershell
# Current session only
$env:Path += ";C:\Program Files\PostgreSQL\17\bin"

# Permanent (new terminals only)
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\PostgreSQL\17\bin", "User")
```

Session-scoped changes do **not** carry into other already-open terminals.

---

## First-time setup

### 1. Create the database

```powershell
psql -U postgres -c "CREATE DATABASE renterp;"
```

Do not create any tables. Flyway builds the entire schema on first boot.

The migrations use `gen_random_uuid()`, which is built into PostgreSQL 13+. No `pgcrypto` extension needed.

To avoid retyping the password each command in a session:

```powershell
$env:PGPASSWORD = "your_postgres_password"
```

### 2. Create `backend/.env`

Copy `.env.example` to `.env` and fill it in. The file **must** be at `backend/.env` — dotenv resolves `./` relative to where you launch the app, so you must also run from inside `backend/`.

```dotenv
APP_ENV=local
SERVER_PORT=8080

DB_HOST=localhost
DB_PORT=5432
DB_NAME=renterp
DB_USERNAME=postgres
DB_PASSWORD=your_postgres_password

DEV_SECURITY_USER=dev
DEV_SECURITY_PASSWORD=dev123456

JWT_SECRET=local_dev_secret_at_least_thirty_two_chars_long
```

Notes:

- **No defaults exist.** `application.yml` uses `${VAR}` with no fallbacks, so a missing variable is a startup crash, not a warning.
- **Skip inline comments.** dotenv-java does not reliably strip a trailing `# comment` from a value.
- **No quotes** around values. Special characters like `@` and `.` are fine; avoid `#`.
- Everything below `JWT_SECRET` in `.env.example` (Sparrow SMS, AWS, Khalti, eSewa, FCM) is **not read by any current code**. Omit it.
- Watch out for Notepad saving as `.env.txt`. Verify with `dir -Force .env*`.

### 3. Build

```powershell
cd backend
mvn clean install -DskipTests
```

First run downloads several hundred MB of dependencies.

---

## Running the backend

```powershell
cd backend
mvn spring-boot:run
```

Must be run from `backend/`, or `.env` won't be found.

A healthy start looks like:

```
Successfully applied 12 migrations to schema "public", now at version v12
112 mappings in 'requestMappingHandlerMapping'
Tomcat started on port 8080 (http)
Started RentErpApplication in 6.4 seconds
```

Leave this terminal running. Use a second terminal for everything else.

Alternatively, run the packaged jar:

```powershell
cd backend
java -jar target/rent-erp-backend-0.1.0-SNAPSHOT.jar
```

Logs are written to `backend/logs/rent-erp.log` and `backend/logs/rent-erp-sql.log` (SQL only, 7-day retention).

### Harmless startup messages

- `ΓÇö` instead of an em-dash — PowerShell rendering UTF-8 in cp437. Cosmetic.
- `HHH90000025: PostgreSQLDialect does not need to be specified explicitly` — `application.yml` sets a dialect Spring would auto-detect anyway.
- Lombok `sun.misc.Unsafe` deprecation warnings during compile.

---

## Verifying it works

### In a browser

| URL | Shows |
|---|---|
| `http://localhost:8080/actuator/health` | Overall health; `db` component proves the Postgres connection |
| `http://localhost:8080/actuator/flyway` | All 12 migrations with checksums |
| `http://localhost:8080/actuator/metrics` | JVM memory, threads, request timings |
| `http://localhost:8080/api/v1/users` | Real endpoint — empty paginated list on a fresh DB |

Health should report `"status":"UP"` with `db` showing `result: 1`. That's the single best "is the backend fine?" check — if it's green, any API failure is application logic, not infrastructure.

There is **no root page** (`/` returns 404 JSON) and **no Swagger UI** (springdoc isn't a dependency). The Postman collections are the API documentation.

### From the command line

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/v1/users -Method Post -ContentType 'application/json' -Body '{"phone":"9800000001","name":"Test Owner","role":"LANDLORD"}'
```

Expect a 201 with a UUID, `createdAt`, `kycStatus: PENDING`.

> **PowerShell warning:** bare `curl` is an alias for `Invoke-WebRequest` and takes different flags. Use `curl.exe` for real curl — but note PowerShell mangles JSON in `-d` arguments. `Invoke-RestMethod` is the reliable option. Never use `--%` with a variable; it stops PowerShell parsing entirely, so `$body` is passed as a literal string.

---

## Testing the API with Postman

Ten collections live in `docs/api-tests/<Controller>/`, ~280 requests total.

### Import

Postman → **Import** → **Folders** → select `docs/api-tests` → Import. All ten load at once. (Or **Files**, multi-selecting the ten `.postman_collection.json` files.)

### Run order

Each collection seeds its own fixtures, but concepts build up. Run top to bottom.

| # | Collection | Requests | Auto-chains IDs |
|---|---|---|---|
| 1 | UserController | 10 | Yes |
| 2 | PropertyController | 12 | Yes |
| 3 | PropertyAccessController | 18 | Yes |
| 4 | StructureController | 30 | Yes |
| 5 | ChargeController | 25 | Yes |
| 6 | MeterController | 39 | Yes |
| 7 | MeterReadingController | 46 | Yes |
| 8 | TenancyController | 42 | Yes |
| 9 | TenantFinanceController | 33 | Yes |
| 10 | BillingController | 25 | **No** |

Collections 1–9 have test scripts on every request that capture IDs (`propertyId`, `floorId`, `roomId`, …) into collection variables. Select a collection → **Run** → 1 iteration → **Run**. Everything self-wires.

Each folder also contains:

- `TEST_RESULTS.md` — expected status code and outcome per scenario
- `responses/` — previously recorded response bodies, for diffing failures

### Two gotchas

**BillingController uses a different `baseUrl`.** It is `http://localhost:8080/api/v1`, while the other nine are `http://localhost:8080` with `/api/v1` inside each request path. Keep these as per-collection variables — one shared environment variable will 404 half your requests.

**BillingController has no test scripts,** so `propertyId`, `membershipId`, `tariffId`, `runId`, and `billId` are not populated automatically. Use the bundled script instead (Git Bash or WSL):

```bash
cd docs/api-tests/BillingController
bash billing_test.sh
```

It builds its own fixtures using run-unique phone numbers (safely re-runnable) and prints PASS/FAIL across 35 scenarios. It echoes the IDs it creates, which you can paste into the Postman collection variables to explore individual endpoints afterwards.

There is also `billing_pass2_test.sh` for the metered/corrections/async scenarios.

### Unit tests

```powershell
cd backend
mvn test
```

44 tests on `feature/app-integration`: `BsCalendarTest` (BS month lengths, parsing, day arithmetic, today in Nepal time), `BillingPass2MathTest`, `JwtServiceTest`, `TokenHasherTest`, `PasswordPolicyTest`, `AccessGuardTest`, `FileTypeSnifferTest`, `LocalFileStorageTest`, `JoinCodeGeneratorTest` and the application context test (needs PostgreSQL).

### Live API scripts (branch `feature/app-integration`)

PowerShell scripts that register their own users and data on a running backend (`APP_ENV=local`, so OTP codes are read from `backend/logs/rent-erp.log`). Each waits up to 90 s for `/actuator/health`. Stop any older backend on port 8080 first, or it answers instead of the new build:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
powershell -ExecutionPolicy Bypass -File docs\api-tests\<Area>\<script>.ps1
```

| Area | Script | Last run |
|---|---|---|
| Auth | `AuthController/auth_test.ps1` | 46/46 |
| Authorization | `Authorization/authz_test.ps1` | 59/59 |
| Files | `FileController/files_test.ps1` | 28/28 |
| Dashboard | `DashboardController/dashboard_test.ps1` | 27/27 |
| Add Tenant | `TenantOnboarding/add_tenant_test.ps1` | 22/22 |
| Join by code | `JoinByCode/join_test.ps1` | 22/22 |
| My Stay | `MyStay/mystay_test.ps1` | 20/20 |
| Billing flow | `BillingFlow/billing_flow_test.ps1` | 21/21 |
| Payments | `PaymentController/payments_test.ps1` | 39/39 |
| Readings due | `Readings/readings_test.ps1` | 21/21 |
| Move-out | `MoveOut/moveout_test.ps1` | 29/29 |
| Room transfer | `RoomTransfer/transfer_test.ps1` | 16/16 |
| Requests | `Requests/requests_test.ps1` | 26/26 |
| Payment details | `PaymentDetails/payment_details_test.ps1` | 14/14 |
| Account | `Account/account_test.ps1` | 38/38 |
| Notifications | `Notifications/notifications_test.ps1` | 23/23 |

---

## Resetting the database

Postman runs leave data behind, and `phone` is `UNIQUE`. If a collection cascades into 409s, reset:

```powershell
# 1. Stop the app first — Ctrl+C in the spring-boot:run terminal.
#    Postgres refuses DROP DATABASE while Hikari holds connections.

psql -U postgres -c "DROP DATABASE renterp;"
psql -U postgres -c "CREATE DATABASE renterp;"

# 2. Restart
cd backend
mvn spring-boot:run
```

Flyway rebuilds all 12 migrations in under a second. Whole cycle takes under a minute.

If connections persist after stopping the app:

```powershell
psql -U postgres -c "DROP DATABASE renterp WITH (FORCE);"
```

---

## Project structure

```
Rent-Erp/
├── backend/
│   ├── pom.xml
│   ├── .env                      ← you create this (gitignored)
│   ├── .env.example
│   ├── logs/                     ← generated at runtime
│   └── src/main/
│       ├── java/com/renterp/
│       │   ├── RentErpApplication.java
│       │   ├── config/           SecurityConfig, JpaAuditingConfig
│       │   ├── common/           BsCalendar, ApiResponse, exceptions
│       │   └── domain/           one package per bounded context
│       │       ├── auth/         users, sessions, OTP attempts
│       │       ├── property/     properties + policy settings
│       │       ├── propertyaccess/  owner / manager / view-only grants
│       │       ├── structure/    floors, rooms
│       │       ├── charge/       charge templates
│       │       ├── meter/        meters, coverage, infra scope
│       │       ├── meterreading/ readings, corrections, replacements
│       │       ├── tenancy/      profiles, KYC, join requests, memberships
│       │       ├── tenantfinance/ deposits, advance rent, rent increments
│       │       ├── billing/      tariffs, billing runs, bills, corrections
│       │       ├── file/         uploads (type from bytes, size cap, access rules)
│       │       ├── dashboard/    dashboard, property summary, tenant rows
│       │       ├── payment/      payments, proofs, approval
│       │       ├── moveout/      move-out notice and settlement
│       │       ├── request/      tenant requests
│       │       └── notification/ in-app notifications, device tokens
│       └── resources/
│           ├── application.yml
│           ├── log4j2.xml
│           └── db/migration/     V1 → V23
├── scripts/
│   └── update_branch_log.ps1     regenerates docs/BRANCH_COMMITS.md
└── docs/
    ├── api-tests/<Area>/         Postman collections or live PowerShell scripts + TEST_RESULTS.md
    ├── devlog/                   per-domain development notes
    ├── BRANCH_COMMITS.md         commits on the current branch (generated)
    ├── PR_APP_INTEGRATION.md     pull-request draft for feature/app-integration
    └── CONTROLLER_TABLE_MAP.md
```

Each domain package follows the same layout: `entity/` → `repository/` → `service/` → `controller/`, with `dto/` for request and response shapes.

---

## API surface

All routes are under `/api/v1`. **On `feature/app-integration`, every route needs `Authorization: Bearer <accessToken>`** except `/api/v1/auth/*` (sign-up, login, refresh) and `/actuator/health`. Get a token from `/api/v1/auth/register`, `/auth/login` or `/auth/login/password`; see [docs/devlog/DEVLOG_AUTH.md](docs/devlog/DEVLOG_AUTH.md). For local runs of the older Postman collections, set `AUTH_ENFORCED=false` in `backend/.env`. With `APP_ENV=local`, OTP codes are written to `backend/logs/rent-erp.log`.

| Base path | Controller |
|---|---|
| `/auth` | AuthController (sign-up, login, refresh, logout, me) |
| `/users` | UserController |
| `/properties` | PropertyController |
| `/property-access` | PropertyAccessController |
| `/floors`, `/rooms` | FloorController, RoomController |
| `/charge-templates` | ChargeTemplateController |
| `/meters` | MeterController (+ coverage, infra-scope sub-resources) |
| `/meters/{id}/readings`, `/readings` | MeterReadingController |
| `/coverage-events` | CoverageEventController |
| `/tenant-profiles` | TenantProfileController (+ KYC sub-resources) |
| `/join-requests` | JoinRequestController |
| `/memberships` | MembershipController (+ room assignments) |
| `/properties/{id}/blocked-tenants` | BlockedTenantController |
| `/memberships/{id}/deposit`, `/advance-rent`, `/opening-balance` | TenantFinanceController |
| `/tariffs` | TariffController |
| `/properties/{id}/billing-runs`, `/billing-runs` | BillingRunController |
| `/tenant-bills`, `/memberships/{id}/bills` | TenantBillController |
| `/auth` | AuthController (branch `feature/app-integration`) |
| `/files` | FileController (branch `feature/app-integration`) |
| `/dashboard`, `/properties/{id}/summary`, `/properties/{id}/tenants` | DashboardController (branch `feature/app-integration`) |
| `POST /properties/{id}/tenants` | TenantOnboardingController — Add Tenant (branch `feature/app-integration`) |
| `/join/{code}` | JoinByCodeController (branch `feature/app-integration`) |
| `/me/stay` | MyStayController (branch `feature/app-integration`) |
| `/tenant-bills/{id}/payments`, `/payment-proofs`, `/payments/{id}/…`, `/me/payments` | PaymentController (branch `feature/app-integration`) |
| `/properties/{id}/readings/due`, `/me/meters` | ReadingDueController (branch `feature/app-integration`) |
| `/memberships/{id}/move-out`, `/move-outs/{id}/…` | MoveOutController (branch `feature/app-integration`) |
| `/memberships/{id}/room-transfer` | RoomTransferController (branch `feature/app-integration`) |
| `/memberships/{id}/requests`, `/requests/{id}/…`, `/me/requests` | TenantRequestController (branch `feature/app-integration`) |
| `/properties/{id}/payment-details` | PaymentDetailsController (branch `feature/app-integration`) |
| `/me/password`, `/me/phone`, `/me/email`, `/me/sessions`, `DELETE /me` | AccountController (branch `feature/app-integration`) |
| `/me/notifications…`, `/me/devices` | NotificationController (branch `feature/app-integration`) |

### Response envelope

Every response is wrapped:

```json
{
  "success": true,
  "data": { },
  "timestamp": "2026-08-10T15:36:54.452Z"
}
```

### Error status codes

| Status | Thrown by |
|---|---|
| 400 | `InvalidOperationException` (business rule) or bean validation failure |
| 404 | `ResourceNotFoundException` |
| 409 | `DuplicateResourceException` |
| 500 | Anything unmapped — check the log for `Unhandled exception:` |

A 500 with `"An unexpected error occurred"` usually means a malformed request body reached the controller, since `HttpMessageNotReadableException` isn't specifically handled.

### Domain conventions

- **All primary keys are UUIDs** (`gen_random_uuid()`), never sequential
- **All timestamps are `TIMESTAMPTZ`** stored in UTC; Nepal time (UTC+05:45) is a display concern
- **BS dates are `VARCHAR`** in `"2082-04-15"` form — never a Postgres `DATE`
- **Money is `NUMERIC(10,2)`**, never float; blended rates are `NUMERIC(10,4)`
- **Soft deletes only** — records carry `is_active`, nothing is hard-deleted
- **BS months run 29–32 days.** Never assume 30. `BsCalendar` covers 1970–2090 and fails loudly outside that range.
- Phone numbers must match `^(97|98)\d{8}$` (Nepal mobile)

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Could not resolve placeholder 'DB_HOST'` | `.env` not found. Run from `backend/`, and check the file isn't named `.env.txt`. |
| `invalid source release: 25` | Maven is on an older JDK. Set `JAVA_HOME` to JDK 25 and confirm with `mvn -v`. |
| `Schema-validation: missing table [x]` | Flyway didn't run. Confirm `renterp` exists and credentials are right. |
| `relation "scheduled_tasks" does not exist` | V2 migration didn't apply — check `/actuator/flyway`. |
| `password authentication failed for user "postgres"` | Wrong password. Set `$env:PGPASSWORD` to avoid retyping. |
| `database "renterp" is being accessed by other users` | The app still holds connections. Ctrl+C it first, or use `WITH (FORCE)`. |
| `psql: term not recognized` | PATH not set in *this* terminal — session changes don't propagate to open windows. |
| `Port 8080 already in use` | An earlier instance is still running: `netstat -ano \| findstr :8080`, then `taskkill /PID <pid> /F`. |
| 500 on a POST from PowerShell | PowerShell mangled the JSON body. Use `Invoke-RestMethod`, or just use Postman. |
| 409 during a Postman run | Leftover data from a previous run — `phone` is UNIQUE. Reset the database. |
| `curl: URL rejected: Port number was not a decimal number` | PowerShell split the command. Not a curl problem — switch to `Invoke-RestMethod`. |

### Reading errors

The catch-all handler logs a full stack trace even though the client only sees a generic message. To find it:

```powershell
Select-String -Path backend\logs\rent-erp.log -Pattern "Unhandled exception" -Context 0,15
```

SQL statements and bind parameters go to `backend/logs/rent-erp-sql.log` — useful when a constraint violation isn't obvious from the API response.

---

## Current state

**`main`:** Phase 5 complete: billing engine, meters and readings, tenancy, tenant finance, and property structure are all implemented and tested.

**Branch `feature/app-integration` (2026-10-02):** the backend now serves the Rentlo Flutter app end to end — authentication and sessions, authorization on every endpoint, files, dashboard, Add Tenant, join by code, My Stay, payments, readings due, move-out, room transfer, requests, owner payment details, account self-service and in-app notifications (migrations V13–V23). Every area has a passing live script. Left before merging: app clean-up and production readiness (SMS / email senders, file storage, push) — see [docs/PR_APP_INTEGRATION.md](docs/PR_APP_INTEGRATION.md). Progress and reasons: [docs/devlog/DEVLOG_APP_INTEGRATION.md](docs/devlog/DEVLOG_APP_INTEGRATION.md); commits: [docs/BRANCH_COMMITS.md](docs/BRANCH_COMMITS.md).

**Not yet built:** the auth flow (OTP via Sparrow SMS, JWT filter, session management). The entities and tables exist from V1, but there is no `AuthController` — hence `permitAll()` security and explicit `ownerUserId` parameters in request bodies where an authenticated principal would normally supply it.