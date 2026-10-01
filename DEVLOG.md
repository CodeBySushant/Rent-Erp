# Rent ERP — Development Log

> This file is updated after every task. It records what was built, why, the logic behind decisions, and any exceptions or open items.
> Git history tracks *what changed*. This log tracks *why it was done and how it works*.

---

## Phase 1 — Foundation

---

### [2026-07-30] Project scaffold + auth entities

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `backend/pom.xml` | Maven project — Spring Boot 4.1, Java 25 |
| `backend/src/main/resources/application.yml` | App config — DB, HikariCP, Flyway, virtual threads, db-scheduler |
| `backend/src/main/resources/log4j2.xml` | Log4j2 config replacing Spring Boot's default Logback |
| `db/migration/V1__init_auth.sql` | Creates `users`, `otp_attempts`, `user_sessions` |
| `db/migration/V2__db_scheduler.sql` | Creates `scheduled_tasks` table required by db-scheduler |
| `domain/auth/entity/User.java` | JPA entity for `users` table |
| `domain/auth/entity/OtpAttempt.java` | JPA entity for `otp_attempts` table |
| `domain/auth/entity/UserSession.java` | JPA entity for `user_sessions` table |
| `domain/auth/repository/UserRepository.java` | Spring Data repo for User |
| `domain/auth/repository/OtpAttemptRepository.java` | Spring Data repo for OtpAttempt |
| `domain/auth/repository/UserSessionRepository.java` | Spring Data repo for UserSession |

#### Dependencies added

| Dependency | Version | Reason |
|-----------|---------|--------|
| spring-boot-starter-web | 4.1.0 (managed) | REST API |
| spring-boot-starter-log4j2 | 4.1.0 (managed) | Replaces Logback |
| spring-boot-starter-data-jpa | 4.1.0 (managed) | ORM |
| spring-boot-starter-security | 4.1.0 (managed) | Auth filter chain |
| spring-boot-starter-validation | 4.1.0 (managed) | Bean Validation |
| spring-boot-starter-actuator | 4.1.0 (managed) | Health / metrics |
| postgresql | managed | JDBC driver |
| flyway-core + flyway-database-postgresql | managed | Schema migrations |
| db-scheduler-spring-boot-starter | 15.0.0 | Postgres-backed job queue |
| lombok | managed | Boilerplate reduction |

#### Key decisions

**Log4j2 over Logback**
Spring Boot ships Logback by default. We swap it because Log4j2 has async appenders, better pattern flexibility, and native support for virtual-thread names (`%t` shows "virtual-N"). `spring-boot-starter-logging` is excluded from `spring-boot-starter-web`; `spring-boot-starter-log4j2` is added separately.

**Log levels by package**
- `com.renterp` → DEBUG (all our code, full detail)
- `org.hibernate.SQL` → DEBUG (actual SQL sent to Postgres)
- `org.hibernate.orm.jdbc.bind` → TRACE (bind parameter values — see exact values substituted)
- `org.springframework.web` → DEBUG (request mapping, filter decisions)
- `org.springframework.security` → DEBUG (auth decisions)
- `org.springframework` → INFO (Spring internals, keep quieter)
- `com.github.kagkarlsson`, `org.flywaydb`, `com.zaxxer.hikari` → INFO
- Root → WARN

Three log files written: `logs/rent-erp.log` (main), `logs/rent-erp-sql.log` (SQL only, 7-day retention), console with ANSI colour.

**Flyway owns the schema, Hibernate validates only**
`spring.jpa.hibernate.ddl-auto=validate` — Hibernate checks that entity fields match what Flyway created. If they don't match, startup fails loudly. This prevents silent schema drift.

**DB rules enforced in V1 migration**
- All PKs: `UUID DEFAULT gen_random_uuid()` — no sequential IDs exposed to clients
- All timestamps: `TIMESTAMPTZ` (UTC), never `TIMESTAMP WITHOUT TIME ZONE`
- Soft deletes: `is_active` boolean — nothing is hard-deleted
- OTP code: stored as bcrypt hash in `code_hash` column, never plain text
- `user_sessions.revoked_at` NULL = session valid; set on logout (never deleted)

**Virtual threads config**
`spring.threads.virtual.enabled=true` — Tomcat's request threads become virtual threads (Project Loom). Each request gets its own virtual thread; JVM schedules them on a small carrier thread pool. Enables reactive-level concurrency with plain blocking code. HikariCP pool set to 20 (higher than default 10) to exploit the concurrency.

**db-scheduler table**
Created in V2 migration before db-scheduler auto-configures. Without this, db-scheduler startup throws `relation "scheduled_tasks" does not exist`. 5 worker threads configured; polling every 10 s.

#### Lombok boolean field naming convention
JPA/Lombok interacts badly with `isXxx` boolean field names — Lombok generates `isIsXxx()`. All boolean fields use plain name (`active`, `used`) with an explicit `@Column(name = "is_active")` so the DB column name stays conventional.

#### Open items before auth service implementation
- [ ] **Verify db-scheduler 15.0.0 is compatible with Spring Boot 4.1** — check Maven Central for latest release
- [ ] **Sparrow SMS API credentials** — needed before OTP service can be implemented
- [ ] **OTP rate limit values** — max sends per hour, max verify attempts, OTP TTL (minutes) — confirm with product spec
- [ ] **Session TTL** — how long before a token expires (30 days? 90 days?)
- [ ] Set `DB_PASSWORD` environment variable locally, or replace placeholder in `application.yml`

---

### [2026-07-30] .env setup — production-grade credential handling

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `backend/.env` | Real local values — gitignored, never committed |
| `backend/.env.example` | Committed template — shows every var that must be supplied |
| `backend/pom.xml` | Added `dotenv-java 3.0.0` |
| `RentErpApplication.java` | Loads `.env` before Spring starts |
| `application.yml` | All `${VAR}` with no defaults — missing var = startup failure |

#### How it works

`dotenv-java` reads `backend/.env` inside `main()`, before `SpringApplication.run()`. Each entry is written to `System.setProperty` **only if the key is not already in the OS environment**. This means:

- **Local dev:** `.env` is loaded → everything just works
- **Production (Docker, Railway, etc.):** real env vars are already set → `.env` is absent (`ignoreIfMissing()`), real vars win
- **Missing var:** Spring fails at startup with a clear `Could not resolve placeholder` error — no silent wrong behaviour

No Spring Boot plugin, no profile tricks. Pure 12-factor.

#### All env vars catalogued

| Variable | Phase needed | Description |
|---------|-------------|-------------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | Now | PostgreSQL connection |
| `SERVER_PORT` | Now | HTTP port (8080 locally) |
| `DEV_SECURITY_USER` / `DEV_SECURITY_PASSWORD` | Now (removed in Phase 1 auth) | Spring HTTP basic placeholder |
| `JWT_SECRET` | Phase 1 auth | Min 32-char random string for token signing |
| `SPARROW_SMS_TOKEN` | Phase 1 auth | OTP SMS delivery |
| `AWS_*` | Phase 8 | S3 for meter photos / KYC images |
| `KHALTI_SECRET_KEY` / `ESEWA_SECRET_KEY` | Phase 6 | Payment gateways |
| `FCM_SERVER_KEY` | Phase 8 | Firebase push notifications |

---

### [2026-07-30] Environment setup + Spring Boot 4.1 startup fixes

**Status:** Complete — app boots cleanly, all tables created, health UP

#### Toolchain installed
Homebrew was compiling everything from source on macOS 12 (Intel) — cmake alone took 39 min. Abandoned brew for Java/Maven; installed prebuilt binaries directly:
- **Java:** Temurin OpenJDK 25.0.4 → `~/java/jdk-25.0.4+7`
- **Maven:** 3.9.9 → `~/maven/apache-maven-3.9.9`
- Both added to `PATH` in `~/.zshrc` via `JAVA_HOME` / `MAVEN_HOME`

#### Three Spring Boot 4.1 fixes (all in pom.xml)
1. **Lombok + Java 25** — pinned Lombok version failed (`TypeTag :: UNKNOWN`). Fix: removed version override, let Spring Boot BOM supply Lombok **1.18.46** (Java 25-aware). Added `maven-compiler-plugin` with Lombok as an `annotationProcessorPath`.
2. **Log4j2 conflict** — `log4j-slf4j2-impl cannot be present with log4j-to-slf4j`. Default `spring-boot-starter-logging` was leaking in through data-jpa, security, validation, actuator starters. Fix: excluded it from **every** starter, not just web.
3. **Flyway not running** (the big one) — Spring Boot 4.1 split auto-config into per-technology modules. `flyway-core` alone no longer triggers migrations. Fix: added **`org.springframework.boot:spring-boot-flyway`**. Migrations then ran → 5 tables created.

#### Also added
- `config/SecurityConfig.java` — permits all requests during dev (real OTP/JWT auth comes in AuthController phase). CSRF disabled, stateless sessions.

#### Verified
`flyway_schema_history`, `users`, `otp_attempts`, `user_sessions`, `scheduled_tasks` all created. Health endpoint UP, DB connected, db-scheduler running.

---

*Next: PropertyController*

---

## Phase 2 — Property structure

---

### [2026-07-30] PropertyController — properties CRUD

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `db/migration/V3__property.sql` | Creates `properties` table + billing-mode config columns |
| `domain/property/entity/Property.java` | JPA entity + 5 enums (electricity/water/split/NEA/mid-month modes) |
| `domain/property/dto/CreatePropertyRequest.java` | Request body for POST /properties |
| `domain/property/dto/UpdatePropertyRequest.java` | Request body for PUT /properties/{id} |
| `domain/property/dto/PropertyResponse.java` | Public API contract |
| `domain/property/repository/PropertyRepository.java` | Spring Data repo + `findByOwnerUserId` |
| `domain/property/service/PropertyService.java` | Business logic |
| `domain/property/controller/PropertyController.java` | HTTP layer — 5 endpoints |

Scoped to `properties` only. `property_ownership_transfers`, `property_mode_changes`, `property_blocked_tenants` (also mapped to this controller) are event-log tables deferred until their respective workflows are built — see [docs/CONTROLLER_TABLE_MAP.md](docs/CONTROLLER_TABLE_MAP.md).

#### Issue found during testing, fixed same pass

A non-existent `ownerUserId` on create relied on the DB foreign-key constraint to reject the insert, which fell through `GlobalExceptionHandler`'s catch-all as a generic 500. Fixed by adding an explicit `userRepository.existsById()` check in `PropertyService.createProperty()`, throwing `ResourceNotFoundException` → clean 404 instead. **Pattern adopted for all future controllers:** any FK reference in a create/update request body must be existence-checked in the service layer — never rely on the DB constraint to surface as the client-facing error.

#### Testing
12/12 scenarios passed against live PostgreSQL, including the owner-filter query and the FK-violation-turned-404 fix. Full protocol followed — see below.

- Detailed log: [docs/devlog/DEVLOG_PROPERTY.md](docs/devlog/DEVLOG_PROPERTY.md)
- Test results: [docs/api-tests/PropertyController/TEST_RESULTS.md](docs/api-tests/PropertyController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/PropertyController/RentERP-PropertyController.postman_collection.json](docs/api-tests/PropertyController/RentERP-PropertyController.postman_collection.json)

---

### [2026-07-30] PropertyAccessController — property_access CRUD + revoke

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `db/migration/V5__property_access.sql` | Creates `property_access` table |
| `domain/propertyaccess/entity/PropertyAccess.java` | JPA entity + `AccessRole` enum (OWNER/MANAGER/VIEW_ONLY) |
| `domain/propertyaccess/dto/*` | Create/Update/Response DTOs |
| `domain/propertyaccess/repository/PropertyAccessRepository.java` | Spring Data repo + property/user finders |
| `domain/propertyaccess/service/PropertyAccessService.java` | Business logic + `createOwnerGrant()` |
| `domain/propertyaccess/controller/PropertyAccessController.java` | HTTP layer — 5 endpoints |
| `common/exception/InvalidOperationException.java` | New — 400 for business-rule violations |

Grants MANAGER/VIEW_ONLY access per property (spec §4.2). `PropertyService.createProperty()` now auto-creates an OWNER row in `property_access` in the same transaction as the property itself, so `properties.owner_user_id` and `property_access` never disagree — a decision confirmed with the user before implementation. The OWNER role is fully fenced off from this controller: it can't be granted directly, a grant can't be changed to OWNER, and the OWNER row can't be edited or revoked (all via the new `InvalidOperationException` → 400) — ownership only ever changes through the still-deferred `property_ownership_transfers` workflow.

#### Testing
18/18 scenarios passed against live PostgreSQL, including the OWNER auto-grant verification, all 4 OWNER-fencing rules, the duplicate-grant 409, and the 404/400 error paths. Full protocol followed — see below.

- Detailed log: [docs/devlog/DEVLOG_PROPERTYACCESS.md](docs/devlog/DEVLOG_PROPERTYACCESS.md)
- Test results: [docs/api-tests/PropertyAccessController/TEST_RESULTS.md](docs/api-tests/PropertyAccessController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/PropertyAccessController/RentERP-PropertyAccessController.postman_collection.json](docs/api-tests/PropertyAccessController/RentERP-PropertyAccessController.postman_collection.json)

---

## Phase 2 — Property structure (continued)

### [2026-07-31] StructureController (Floor + Room) — floors + rooms CRUD

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `db/migration/V6__structure.sql` | Creates `floors` and `rooms` tables |
| `domain/structure/entity/Floor.java`, `Room.java` | JPA entities |
| `domain/structure/dto/*` | Create/Update/Response DTOs for both |
| `domain/structure/repository/FloorRepository.java`, `RoomRepository.java` | Spring Data repos |
| `domain/structure/service/FloorService.java`, `RoomService.java` | Business logic |
| `domain/structure/controller/FloorController.java`, `RoomController.java` | HTTP layer — 5 endpoints each |

Setup wizard step 2 (spec §6.1): "Floors -> rooms per floor -> room names". Rooms deliberately hold no meter or tenant reference (spec §20.1) — no occupancy/status column either, since vacancy is always derived from `room_assignments` (Tenancy phase, not yet built), never stored on the room. Two new, not-spec-mandated uniqueness rules confirmed with the user before implementation: `(property_id, floor_number)` and `(floor_id, name)` — the latter scoped per floor, not per property. Floor deletion is blocked (400, `InvalidOperationException`) while it still has active rooms; room deletion has no equivalent gate yet since `meter_room_coverage`/`room_assignments` don't exist until later phases (tracked TODO).

Per the DEVLOG SOP: no §14 Edge Case Register subsection applies directly to Structure — it's pure setup-time CRUD with no billing/tenancy consequences yet; the cases that reference rooms (T11, T12, M9) belong to later controllers that attach meters/tenants to the rooms created here.

#### Testing
30/30 scenarios passed against live PostgreSQL, including both uniqueness constraints (with room-name scoping verified per-floor), the floor-deletion gate in both states, idempotent delete, and the usual 404/409/400 paths.

- Detailed log: [docs/devlog/DEVLOG_STRUCTURE.md](docs/devlog/DEVLOG_STRUCTURE.md)
- Test results: [docs/api-tests/StructureController/TEST_RESULTS.md](docs/api-tests/StructureController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/StructureController/RentERP-StructureController.postman_collection.json](docs/api-tests/StructureController/RentERP-StructureController.postman_collection.json)

---

### [2026-07-31] ChargeController (charge_templates) — reusable charge definitions

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `db/migration/V7__charge_templates.sql` | Creates `charge_templates` table |
| `domain/charge/entity/ChargeTemplate.java` | JPA entity + `SplitBasis`/`DeactivationMode` enums |
| `domain/charge/dto/*` | Create/Update/Response DTOs |
| `domain/charge/repository/ChargeTemplateRepository.java` | Spring Data repo |
| `domain/charge/service/ChargeTemplateService.java` | Business logic + B6/B7 edge-case handling |
| `domain/charge/controller/ChargeTemplateController.java` | HTTP layer — 5 endpoints |

Reusable fixed/shared charge definitions per property (Internet, Garbage, Maintenance, Security, custom — spec §8.1/§8.2). Scope confirmed with the user before implementation: only `charge_templates` built this pass. `tenant_charge_overrides` deferred to `TenancyController` (Phase 4 — needs tenant-assignment context per spec §10.2) and `tariff_versions` deferred to the Billing engine (Phase 5 — national NEA reference data, inert until blended-rate calculation exists), both tracked in `CONTROLLER_TABLE_MAP.md`.

Per the DEVLOG SOP: read §14.2 Billing Edge Cases first, since Charges directly feed Billing's per-tenant totals. Two cases handled end-to-end — **B6** (a zero charge amount requires explicit `zeroAmountAcknowledged: true`, re-checked on every save that touches the amount) and **B7** (deactivation is never deletion; the landlord's chosen treatment — `THIS_CYCLE_PRORATED`/`NEXT_CYCLE`/`VOID` — is captured via a `deactivationMode` query param on delete, defaulting to the safe `NEXT_CYCLE`, for the not-yet-built Billing engine to apply later). `(property_id, name)` uniqueness added, same pattern and same user confirmation as the V6 floors/rooms constraints.

#### Testing
23/23 scenarios passed against live PostgreSQL, including both B6/B7 edge cases, the idempotent-delete-preserves-mode behavior, the uniqueness constraint on create and rename, and the usual 404/409/400 paths.

- Detailed log: [docs/devlog/DEVLOG_CHARGE.md](docs/devlog/DEVLOG_CHARGE.md)
- Test results: [docs/api-tests/ChargeController/TEST_RESULTS.md](docs/api-tests/ChargeController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/ChargeController/RentERP-ChargeController.postman_collection.json](docs/api-tests/ChargeController/RentERP-ChargeController.postman_collection.json)

---

## Phase 3 — Meters

---

### [2026-07-31] MeterController — meters + coverage + infrastructure scope

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `db/migration/V8__meters.sql` | Creates `meters`, `meter_room_coverage`, `infrastructure_meter_scope` |
| `domain/meter/entity/Meter.java` | JPA entity + 6 enums (MeterPurpose, MeterType, InfrastructureScopeType, SplitRule, ReadingResponsibility, MeterStatus) |
| `domain/meter/entity/MeterRoomCoverage.java`, `InfrastructureMeterScope.java` | JPA entities for the two dated sub-tables |
| `domain/meter/dto/*` | Create/Update/Response DTOs for meter + coverage + scope |
| `domain/meter/repository/*` | Spring Data repos with property/type/purpose filters, active-coverage helpers, and the §6.5 existence check |
| `domain/meter/service/MeterService.java` | Business logic — cross-field validation + §14.1 M11/M12/M18 handling + coverage/scope sub-resources |
| `domain/meter/controller/MeterController.java` | HTTP layer — 5 top-level endpoints + 6 sub-resource endpoints |

Scoped to `meters`, `meter_room_coverage`, and the FLOOR-scoped subset of `infrastructure_meter_scope`. `meter_tenant_assignments` (whole table), TENANT-scoped scope rows, and `meters.designated_tenant_id` population are deferred to `TenancyController` (Phase 4) — none of them can be built cleanly without a live `tenants` table. Reading-chain tables (`meter_reading_log`, `meter_replacement_events`, `meter_coverage_events`, `meter_coverage_event_changes`) belong to `MeterReadingController`, not this controller, per `docs/CONTROLLER_TABLE_MAP.md`.

#### §14.1 Edge Case Register — cases handled

- **M11** (permanent removal) — `DELETE /meters/{id}` soft-marks `status=INACTIVE` and blocks while any active coverage row exists (`InvalidOperationException` → 400). The landlord must end coverage first, which is the moment the UI can show "who loses coverage before you confirm". Idempotent re-DELETE on an already-INACTIVE meter is a no-op.
- **M12** (duplicate serial in a property) — enforced via a partial unique index `uq_meters_property_serial` and re-checked at the service layer on both create and update. Serial is optional; NULL rows are exempt from the constraint by scoping the index with `WHERE serial_number IS NOT NULL`.
- **M18** (reading responsibility per meter) — `reading_responsibility` enum column with the three spec values. Infra meters default to `LANDLORD_ONLY` when the caller omits the field (matches the "pump rooms are locked" reality); non-infra meters must specify explicitly, forcing the property config to surface the decision rather than silently defaulting.

Deferred to `MeterReadingController` (Phase 3, next controller): M1–M10, M13, M14, M15, M16, M17 — all reading-chain / replacement / coverage-event mechanics, none of whose tables exist yet.

#### Also this pass — §6.5 gate closed in PropertyService

The TODO left in `PropertyService.updateProperty()` from the 2026-07-30 spec audit is now closed: switching electricity billing mode to `SUB_METERED` or `MAIN_METER_ONLY` requires at least one active meter to already exist for the property. Uses `MeterRepository.existsByPropertyIdAndActive(id, true)`, throws `InvalidOperationException` → 400. The reverse switch (metered → non-metered) is deliberately not gated. The *opening-reading* half of §6.5 still waits on `MeterReadingController` — tracked as an open item in `DEVLOG_METER.md`.

#### Testing
35/35 scenarios passed against live PostgreSQL, including the three §14.1 edge cases end-to-end, both directions of the §6.5 gate, the four cross-field validation cases, coverage type-restrictions and duplicate-active blocks, the four infra-scope refusal shapes, TENANT-deferral, idempotent re-DELETE, and the usual 404/409/400 paths.

- Detailed log: [docs/devlog/DEVLOG_METER.md](docs/devlog/DEVLOG_METER.md)
- Test results: [docs/api-tests/MeterController/TEST_RESULTS.md](docs/api-tests/MeterController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/MeterController/RentERP-MeterController.postman_collection.json](docs/api-tests/MeterController/RentERP-MeterController.postman_collection.json)

---

### [2026-07-31] MeterReadingController + CoverageEventController — reading chain, replacement, coverage events

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `db/migration/V9__meter_readings.sql` | Adds `meters.max_reading_value`; creates `meter_reading_log`, `meter_replacement_events`, `meter_coverage_events`, `meter_coverage_event_changes` |
| `domain/meterreading/entity/*` | 4 entities — reading log + replacement event + coverage event header + per-meter change |
| `domain/meterreading/dto/*` | SubmitReading, Correction, CreateReplacement, CreateCoverageEvent, response DTOs, estimation-hint |
| `domain/meterreading/repository/*` | Repos with `findLatestConfirmed` and `findRecentConsumption` chain helpers |
| `domain/meterreading/service/MeterReadingService.java` | Reading log — submit/confirm/discard/correct + §14.1 M1/M2/M3/M13/M16 rules + estimation helper |
| `domain/meterreading/service/MeterReplacementService.java` | Atomic §14.1 M4 flow — new-meter creation + coverage carry-forward + close/open readings + event row |
| `domain/meterreading/service/CoverageEventService.java` | §14.1 M9/M10 flow — atomic multi-meter coverage change + anchor readings |
| `domain/meterreading/controller/MeterReadingController.java` | Reading log + estimation-hint + replacement endpoints |
| `domain/meterreading/controller/CoverageEventController.java` | Top-level `/coverage-events` (SPLIT/MERGE touches ≥2 meters) |

Scope confirmed with the user before starting (Cut A): full 4-table build, two-state `PENDING → CONFIRMED` lifecycle, `meters.max_reading_value` added as a piggyback column, replacement is one atomic endpoint, coverage events at top-level. Also modifies `Meter.java` + Meter DTOs + `MeterService.java` to expose `maxReadingValue`.

#### §14.1 Edge Case Register — cases handled

- **M1** rollover — `rolloverConfirmed=true` on the submit body; consumption uses `(max − previous) + current`, `is_rollover=true` flagged. Verified live: 9999-max, prev 150, current 50 → consumption 9899.
- **M2** current < previous, no rollover confirm → 400.
- **M3** zero reading → 400 unless the row is chain-anchor (`INITIAL`/`REPLACEMENT_OPEN`) or a confirmed rollover.
- **M4** replacement — one atomic endpoint carries every characteristic forward (purpose/type/scope/split/responsibility/max), ends old coverage on event date, opens new coverage the same day, inserts `REPLACEMENT_CLOSE` on old chain, marks old meter INACTIVE + `replaced_by_meter_id`, inserts `REPLACEMENT_OPEN` on new chain (chain anchor), persists event.
- **M5** failed meter estimation — close reading accepts `estimated=true` with a mandatory `estimationBasis`; `GET /meters/{id}/estimation-hint` returns the rolling 3-month average for landlord-side manual entry.
- **M6** replacement doesn't start at 0 — no special handling needed; `REPLACEMENT_OPEN` is a chain anchor, absolute value irrelevant.
- **M7/M8** — same endpoint, called twice; sub-periods sum in the billing engine later. On the billing day, replacement is the atomic op so runs against the new chain.
- **M9** coverage change — event endpoint requires an anchor reading on every meter whose coverage actually changed (400 otherwise). Coverage rows are ended and opened atomically inside the same transaction, matching the raw MeterController's duplicate-active-pair 409.
- **M10** merge — same shape as SPLIT with `roomsAdded`/`roomsRemoved` swapped between the surviving and removed meter; event type `MERGE`.
- **M13** atomic photo + reading — `photo_url` column NULL-able; landlord submissions accept photo optionally (tenant-required-photo path is Phase 4).
- **M16** backdated reading — `submissionDateBs > readingDateBs` flags `is_backdated=true`; `readingDateBs < prevConfirmed.readingDateBs` is blocked with 400 (regardless of rollover flag).

Deferred: **M14** (needs `room_assignments`), **M15** (belongs to TenancyController), **M17** (belongs to Billing engine). Reading types `VACANCY`/`TENANT_JOIN`/`DEPARTURE_TOPUP`/`GAP_ABSORBED` refused at the controller gate until their owning domains exist.

#### Testing
35/35 scenarios passed against live PostgreSQL, covering every §14.1 case handled this pass, both event flows end-to-end (replacement leaves old meter INACTIVE with `replaced_by_meter_id`; coverage event ends the affected meters' coverage rows and stamps anchor readings), correction chain integrity (auto-CONFIRMED, no correction-of-correction, PENDING can't be corrected), pagination + type filter, and estimation-hint on empty/populated/unknown meters.

- Detailed log: [docs/devlog/DEVLOG_METERREADING.md](docs/devlog/DEVLOG_METERREADING.md)
- Test results: [docs/api-tests/MeterReadingController/TEST_RESULTS.md](docs/api-tests/MeterReadingController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/MeterReadingController/RentERP-MeterReadingController.postman_collection.json](docs/api-tests/MeterReadingController/RentERP-MeterReadingController.postman_collection.json)

---

## Phase 4 — Tenancy

---

### [2026-07-31] TenancyController — profiles, KYC, join requests, memberships, room assignments, tenant blocks

**Status:** Complete

#### What was built

| File | Purpose |
|------|---------|
| `db/migration/V10__tenancy.sql` | 6 tables (`tenant_profiles`, `tenant_kyc`, `join_requests`, `tenant_property_memberships`, `room_assignments`, `property_blocked_tenants`) + partial unique indexes + `fk_meters_designated_tenant` FK added on `meters.designated_tenant_id` (deferred from V8) |
| `domain/tenancy/entity/*` | 6 entities covering linked/unlinked identity, KYC state machine, join request TTL, membership status/payment-model override, dated room assignments, property-scoped blocks |
| `domain/tenancy/dto/*` | 18 request + response DTOs |
| `domain/tenancy/repository/*` | 6 Spring Data repos with active-status and uniqueness helpers |
| `domain/tenancy/service/TenantProfileService.java` | Profile CRUD + full KYC lifecycle (submit/approve/reject/flag) with resubmit cap 5 (T5) and terminal-APPROVED rule |
| `domain/tenancy/service/JoinRequestService.java` | Create/accept/reject/cancel with lazy 5-min expiry (T1) + T3 block check + T4 duplicate-PENDING guard; `accept` atomically creates a membership |
| `domain/tenancy/service/MembershipService.java` | Direct-create + internal-from-accept + update + terminate + nested room-assignment CRUD; single-occupancy invariant across memberships (§10.2) |
| `domain/tenancy/service/BlockedTenantService.java` | T3 block/list/unblock + `isBlocked()` helper consumed by join request creation |
| `domain/tenancy/controller/*` | 4 controllers — `TenantProfileController` (profiles + KYC), `JoinRequestController`, `MembershipController` (+ nested room-assignments), `BlockedTenantController` (property-scoped) |

Scope confirmed with the user (recommendation-set): pull `property_blocked_tenants` from PropertyController's map into this pass (T3 needs it, it's a tenant↔property relationship), KYC resubmit cap 5, two-call unlinked flow, no deferred meter follow-ups folded in. Also lands the previously-deferred `meters.designated_tenant_id → tenant_profiles(id)` FK in V10 (V8 couldn't create it before `tenant_profiles` existed).

#### §14.3 Edge Case Register — cases handled

- **T1** unanswered request → 5-min TTL enforced by `expires_at` + `expireIfStale()` on every read/state-transition. Batch sweep via db-scheduler noted as follow-up.
- **T2** landlord rejects, tenant re-requests → REJECTED terminal; new PENDING allowed for same `(tenant, property)`; optional `responseMessage`.
- **T3** landlord blocks → `property_blocked_tenants` with property-scoped partial unique index; join-request creation refuses blocked tenants with a spec-mandated neutral message wrapped as 400; unblock-able.
- **T4** simultaneous join requests → partial unique index `WHERE status='PENDING'` + service pre-check → 409. Requests are `(tenant, property)`-scoped, rooms attach after acceptance.
- **T5** KYC resubmit → cap 5, resubmit only from REJECTED/FLAGGED, APPROVED terminal, `resubmitCount` increments and prior verification fields wiped on each resubmit.
- **T6** landlord flags physical doc mismatch → `POST /kyc/flag` from any state (physical mismatch is discovered independently of lifecycle position); routes to admin queue when Phase 8 notifications land.
- **T7** existing tenant, pre-app history → direct `POST /tenant-profiles` (unlinked, `userId=null`) then direct `POST /memberships` — no join-request flow; opening-position financial fields land in TenantFinanceController next pass.
- **T10** mixed payment models → `payment_model_override` nullable on membership; NULL falls back to `Property.paymentModelDefault`.

Deferred: **T8/T9** (Billing engine), **T11/T12/T13** (RoomOpsController), **T14** (TenantFinanceController), **T15** (property_ownership_transfers under PropertyController).

#### Testing
45/45 scenarios passed against live PostgreSQL, covering every §14.3 case handled this pass end-to-end plus the room single-occupancy invariant across memberships, cross-property room refusal, cascade termination (terminate a membership → all active room_assignments end in the same transaction), and the profile-delete-with-ACTIVE-membership gate.

- Detailed log: [docs/devlog/DEVLOG_TENANCY.md](docs/devlog/DEVLOG_TENANCY.md)
- Test results: [docs/api-tests/TenancyController/TEST_RESULTS.md](docs/api-tests/TenancyController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/TenancyController/RentERP-TenancyController.postman_collection.json](docs/api-tests/TenancyController/RentERP-TenancyController.postman_collection.json)

---

### [2026-07-31] TenantFinanceController — deposits, advance rent, opening balances, rent increments (+ rent-storage piggyback)

**Status:** Complete — Phase 4 now closed.

#### What was built

| File | Purpose |
|------|---------|
| `db/migration/V11__tenant_finance.sql` | 4 tables + `room_assignments.monthly_rent` piggyback ALTER + CHECK constraints |
| `domain/tenantfinance/entity/*` | `TenantDeposit`, `TenantAdvanceRent`, `TenantOpeningBalance`, `RentIncrement` |
| `domain/tenantfinance/dto/*` | 9 request + response DTOs |
| `domain/tenantfinance/repository/*` | 4 Spring Data repos |
| `domain/tenantfinance/service/*` | 4 services — `TenantDepositService`, `TenantAdvanceRentService`, `TenantOpeningBalanceService`, `RentIncrementService` (atomic T14 apply) |
| `domain/tenantfinance/controller/TenantFinanceController.java` | One combined controller — 13 endpoints under `/memberships/{mid}/...` + top-level GET-by-id lookups for advance-rent and rent-increments |

Also modifies `RoomAssignment` (adds `monthlyRent` field), `RoomAssignmentResponse`, `CreateRoomAssignmentRequest` (adds required `monthlyRent` field), and `MembershipService.assignRoom()` to thread the value through.

#### Scope decisions confirmed with user

- **Option A** for rent storage: rent lives per `room_assignments`. T11/T12 fall out for free — sum of active assignments = total rent. `rent_increments.room_assignment_id` points at the specific room's rent that changed.
- **Piggyback** the `monthly_rent` ALTER in V11 (rather than a separate V12). Column nullable in DB so tenancy-pass fixture rows survive; API refuses new creates without it.
- **No backfill** of NULL `monthly_rent` rows — production is empty, tenancy-pass test data is expendable.
- Deferred surfaces: deposit refund/forfeit/apply (Vacancy), advance-rent consumption (Billing), opening-balance application (Billing).

#### §14.3 cases handled

- **T7** — existing tenant, pre-app history — completed here (tenancy pass built the identity + membership; this pass adds deposits, advance rent, opening balance).
- **T14** — rent increment. Atomic apply: capture `previousAmount` from the assignment (never client-supplied), append the row, update `room_assignments.monthly_rent`, all in one transaction.

Deferred: **T8/T9** (Billing), **T15** (property_ownership_transfers), all deposit status transitions (Vacancy).

#### Standing SOP adopted this pass

**Every DEVLOG going forward MUST contain an explicit "Our-own rules" enumeration** — a table of every rule the pass added beyond what the spec strictly mandates, with rationale. See `DEVLOG_TENANTFINANCE.md` for the shape (14 rules enumerated). This is a user directive as of 2026-07-31 and applies to all future controller passes.

#### Testing
33/33 scenarios passed against live PostgreSQL, covering the piggyback `monthly_rent` requirement, deposit lifecycle (create/dup/get/update/negative/unknown), advance rent multi-row + validation, opening balance one-shot immutability, T14 atomic rent-increment apply with cross-membership + before-start + ended-assignment + negative refusals, TERMINATED-membership gate on all three write surfaces.

- Detailed log: [docs/devlog/DEVLOG_TENANTFINANCE.md](docs/devlog/DEVLOG_TENANTFINANCE.md)
- Test results: [docs/api-tests/TenantFinanceController/TEST_RESULTS.md](docs/api-tests/TenantFinanceController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/TenantFinanceController/RentERP-TenantFinanceController.postman_collection.json](docs/api-tests/TenantFinanceController/RentERP-TenantFinanceController.postman_collection.json)

---

## Phase 5 — Billing engine

---

### [2026-07-31] BillingController (Pass 1 of 2) — tariffs, billing runs, tenant bills, adjustments + BS calendar

**Status:** Pass 1 complete. Non-metered engine end-to-end; metered/NEA/segments/async/corrections deferred to pass 2.

#### What was built

| File | Purpose |
|------|---------|
| `common/util/BsCalendar.java` | Embedded BS month-length table (1970–2090) — resolves Open Verification Item #1 |
| `db/migration/V12__billing.sql` | 7 billing tables + CHECK constraints + partial unique indexes (idempotency, one-live-run-per-period, one-live-bill-per-run) |
| `domain/billing/entity/*` | `TariffVersion`, `BillingRun`, `BillingRunSegment`, `TenantBill`, `TenantBillAdjustment` (+ `BillLineItem` JSONB DTO) |
| `domain/billing/service/BillingMath.java` | Money split + rounding + **B9** remainder reconciliation |
| `domain/billing/service/BillingRunService.java` | The engine — generate / confirm / cancel / reads |
| `domain/billing/service/*` | `TariffVersionService`, `TenantBillService`, `BillAdjustmentService` |
| `domain/billing/controller/*` | `TariffController`, `BillingRunController`, `TenantBillController` |
| `src/test/java/com/renterp/common/util/BsCalendarTest.java` | 9 unit tests for the BS table |

Also modifies `PropertyRepository` (`findByIdForUpdate` PESSIMISTIC_WRITE, B10 layer 2), `RoomAssignmentRepository`, `TenantPropertyMembershipRepository`, `ChargeTemplateRepository` (engine read finders).

#### BS calendar decision (Open Verification Item #1 — RESOLVED)

No maintained Java BS library exists on Maven Central (candidates publish only to GitHub Packages / private staging repos, which would force auth tokens into the build). Per user's call ("wire a BS lib now"), embedded the canonical `medic/bikram-sambat` month-length dataset (BS 1970–2090, well past 2082+) directly in `BsCalendar`, verified by 9 unit tests. Pure-BS arithmetic (day counts + diffs); fails loudly outside the range rather than assuming 30 (B14). Maintenance checkpoint documented at BS 2090.

#### Two-pass cut (confirmed with user)

Pass 1 = scaffolding + non-metered engine. Pass 2 = SUB_METERED, NEA blended, mid-period segment engine (T9/M9), async workers (B15), bill corrections (B8 paid path), KUKL/boring water, penalty accrual, CUSTOM split. Deferred modes are **rejected with 400**, never silently mis-billed.

#### §14.2 cases handled

**B4** (grace from generation), **B5** (snapshot amounts + tariff freeze once referenced by a CONFIRMED run), **B9** (remainder reconciliation to the paisa, asserted in the split and again at confirm), **B10** (4 concurrency layers), **B11** (empty run), **B13** (oldest-first confirm), **B14** (BS-actual proration). Cross-refs: **T7** (opening balance on first bill), **T8** (ledger-based advance consumption), **P9** (one-time carry-forward adjustment), **B8** partial (cancel+regenerate path).

Deferred: B1/B2/B3 (needs meter readings), B6 (already in ChargeController), B7 prorate-this-cycle apply, B8 paid-path adjustment, B12, B15, B16, M17 — all pass 2 / later.

#### Testing
35/35 live PostgreSQL API scenarios + 9/9 BsCalendar unit tests passed. Covers tariff CRUD + guards, MAIN_METER_ONLY + FIXED_PER_TENANT engines, B9 exact reconciliation (1000 → 334/333/333), mid-month proration, idempotency + duplicate-period + oldest-first + cancel/regenerate lifecycle, empty run, T7/T8/P9 money application (total_due=9200 hand-checked), deferred-mode guardrails.

- Detailed log: [docs/devlog/DEVLOG_BILLING.md](docs/devlog/DEVLOG_BILLING.md)
- Test results: [docs/api-tests/BillingController/TEST_RESULTS.md](docs/api-tests/BillingController/TEST_RESULTS.md)
- Postman collection: [docs/api-tests/BillingController/BillingController.postman_collection.json](docs/api-tests/BillingController/BillingController.postman_collection.json)

### [2026-08-01] BillingController (Pass 2 of 2) — metered engine, NEA blended, segments, async, corrections

**Status:** Pass 2 complete. The full billing engine now runs every metering/water mode. No new migration (V12 pre-provisioned all pass-2 columns/tables).

**Landed:** `SUB_METERED` electricity (`ElectricityEngine`: units from confirmed readings in a half-open window, payer via coverage-as-of-date → room → membership); NEA `BLENDED_RATE` (main-meter units → slab pricing → blended rate → `nea_*` snapshot, common-unit allocation reconciling exactly to the NEA bill); mid-period **segment engine** (`SegmentEngine`, day-granular denominators — T9/M9) applied to MAIN_METER_ONLY electricity, SHARED charges, KUKL/boring water; KUKL/boring water modes; CUSTOM split (per-membership request weights); **M17** overage (notify/BLOCK, common units floored); **B8** corrections (`POST /tenant-bills/{id}/correct` — unpaid cancel+regenerate, paid/partial next-bill adjustment, `bill_corrections` audit); **B15** async db-scheduler workers + `billing_run_progress` polling (`GET /billing-runs/{id}/progress`).

**Correctness fix from pass 1:** the B9 rounding remainder was double-added to `total_due` on top of the already-final component share; removed (component amounts are the final reconciled shares). Escaped pass-1 tests because no multi-tenant shared-split `total_due` was asserted.

**Deferred (documented):** penalty accrual (Phase 6 — needs paid/overdue state); mid-month departure rate `DEFERRED` variant; B7 proration-adjustment emission (Charge domain); §6.5 INITIAL-reading gate; RBAC/auth.

**Testing:** 31/31 live PostgreSQL pass-2 scenarios + 5/5 pass-2 math unit tests; pass-1 re-run 35/35 (3 assertions updated for intended behavior changes — segment count, SUB_METERED guard scope, tariff-effective isolation). Script: [docs/api-tests/BillingController/billing_pass2_test.sh](docs/api-tests/BillingController/billing_pass2_test.sh).

---

## Phase 6 — App integration (branch `feature/app-integration`)

Work to make this backend serve the Rentlo Flutter app. It lives on its own
branch and reaches `main` as one pull request. Detail, decisions and the gap
list: [docs/devlog/DEVLOG_APP_INTEGRATION.md](docs/devlog/DEVLOG_APP_INTEGRATION.md).
Commits: [docs/BRANCH_COMMITS.md](docs/BRANCH_COMMITS.md). PR draft:
[docs/PR_APP_INTEGRATION.md](docs/PR_APP_INTEGRATION.md).

---

### [2026-10-02] Branch created, integration log started

Branch cut from an up-to-date `main`. Added the integration devlog (with the
audit of `main`: no auth, Hindi rejected, missing 401/410/429 and 400 for
malformed input, screen-shaped reads, unbuilt domains, docs drift), the PR
draft and a script that lists the branch's commits. No code or schema change.

---
### [2026-10-02] Authentication, authorization foundation, error codes, Hindi

**Built:** `AuthController` — `/auth/otp/request`, `/otp/verify`, `/register`, `/login` (OTP), `/login/password`, `/refresh`, `/logout`, `/me`. HS256 access tokens (15 min, JDK crypto, no new dependency) naming a `user_sessions` row; refresh tokens (30 days) stored hashed and rotated on every refresh; the filter checks the session on every request, so logout is immediate. OTP: hashed, 5 min, resend 45 s, 5/hour, 5 wrong tries burn it; codes go to the log only when `APP_ENV=local`, elsewhere `/otp/request` returns 503 until an SMS provider exists. BCrypt passwords, case-insensitive email, 15-minute lockout after 5 wrong passwords. `SecurityConfig` now requires a token for everything except auth and health. `AccessGuard` (property rights from `property_access`) applied to users, properties and property access: `GET /properties` is scoped to the caller and `POST /properties` makes the caller the owner. Every error now carries a `code`; malformed JSON / bad UUIDs / missing params are 400 (were 500), constraint violations 409. **V13** adds the auth columns and allows Hindi (`hi`) for users and tenant profiles.

**`AUTH_ENFORCED`** (default true) can be set false in a developer `.env` to run the earlier Postman collections without tokens.

**Testing:** unit tests `JwtServiceTest`, `TokenHasherTest`, `PasswordPolicyTest`, `AccessGuardTest`; live script `docs/api-tests/AuthController/auth_test.ps1`. Detail: [docs/devlog/DEVLOG_AUTH.md](docs/devlog/DEVLOG_AUTH.md).

---
### [2026-10-02] Authentication and authorization

`AuthController` (OTP + password login, register, refresh, logout, me) with JWT access tokens and rotating refresh tokens in `user_sessions` (V13); every controller now authorizes from the token via `AccessGuard` / `ResourceAccess` (owner / manager / view-only per property, tenants limited to their own membership); `tenant_profiles.created_by` (V14); standard error `code` on every error; Hindi accepted. Detail: [DEVLOG_AUTH.md](docs/devlog/DEVLOG_AUTH.md). Tests: `mvn test` 34/34, [AuthController](docs/api-tests/AuthController/TEST_RESULTS.md) 46/46, [Authorization](docs/api-tests/Authorization/TEST_RESULTS.md).

---

### [2026-10-02] File uploads

`FileController` (`/files`): multipart upload, metadata, protected download, delete; `stored_files` (V15); local storage behind a `FileStorage` port. Detail: [DEVLOG_FILE.md](docs/devlog/DEVLOG_FILE.md). Tests: [FileController](docs/api-tests/FileController/TEST_RESULTS.md).

---

### [2026-10-02] Dashboard, property summary, tenant list

`DashboardController`: `/dashboard`, `/properties/{id}/summary`, `/properties/{id}/tenants` — screen-shaped reads computed from current data; `BsCalendar.today()`. Detail: [DEVLOG_DASHBOARD.md](docs/devlog/DEVLOG_DASHBOARD.md). Tests: [DashboardController](docs/api-tests/DashboardController/TEST_RESULTS.md).

---

## Controller Log

| Controller | Status | Detail | Tests |
|-----------|--------|--------|-------|
| UserController | ✅ Complete, tested & audit-fix applied; scoped to self/admin on `feature/app-integration` | [DEVLOG_USER.md](docs/devlog/DEVLOG_USER.md) | [10/10 passed](docs/api-tests/UserController/TEST_RESULTS.md) |
| AuthController (+ security, AccessGuard) | 🚧 `feature/app-integration` — implemented, live run pending | [DEVLOG_AUTH.md](docs/devlog/DEVLOG_AUTH.md) | [auth_test.ps1](docs/api-tests/AuthController/TEST_RESULTS.md) |
| PropertyController | ✅ Complete, tested & FK-check fix applied; §6.5 gate closed 2026-07-31 | [DEVLOG_PROPERTY.md](docs/devlog/DEVLOG_PROPERTY.md) | [12/12 passed](docs/api-tests/PropertyController/TEST_RESULTS.md) |
| PropertyAccessController | ✅ Complete, tested, auto OWNER-grant wired into PropertyController | [DEVLOG_PROPERTYACCESS.md](docs/devlog/DEVLOG_PROPERTYACCESS.md) | [18/18 passed](docs/api-tests/PropertyAccessController/TEST_RESULTS.md) |
| StructureController (Floor + Room) | ✅ Complete, tested | [DEVLOG_STRUCTURE.md](docs/devlog/DEVLOG_STRUCTURE.md) | [30/30 passed](docs/api-tests/StructureController/TEST_RESULTS.md) |
| ChargeController (charge_templates) | ✅ Complete, tested; tenant_charge_overrides + tariff_versions deferred | [DEVLOG_CHARGE.md](docs/devlog/DEVLOG_CHARGE.md) | [23/23 passed](docs/api-tests/ChargeController/TEST_RESULTS.md) |
| MeterController (meters + coverage + infra-scope) | ✅ Complete, tested; meter_tenant_assignments + TENANT-scoped infra-scope + designated_tenant_id population deferred to TenancyController | [DEVLOG_METER.md](docs/devlog/DEVLOG_METER.md) | [35/35 passed](docs/api-tests/MeterController/TEST_RESULTS.md) |
| MeterReadingController + CoverageEventController | ✅ Complete, tested; §14.1 M1/M2/M3/M4/M5/M6/M9/M10/M13/M16 handled; M14/M15/M17 deferred | [DEVLOG_METERREADING.md](docs/devlog/DEVLOG_METERREADING.md) | [35/35 passed](docs/api-tests/MeterReadingController/TEST_RESULTS.md) |
| TenancyController (profiles + KYC + join + memberships + room-assignments + blocks) | ✅ Complete, tested; §14.3 T1/T2/T3/T4/T5/T6/T7/T10 handled; T8/T9/T11/T12/T13/T14/T15 deferred | [DEVLOG_TENANCY.md](docs/devlog/DEVLOG_TENANCY.md) | [45/45 passed](docs/api-tests/TenancyController/TEST_RESULTS.md) |
| TenantFinanceController (deposits + advance rent + opening balances + rent increments) | ✅ Complete, tested; §14.3 T7 completion + T14 (atomic rent-increment apply). Piggybacks `room_assignments.monthly_rent`. Deposit status transitions deferred to Vacancy, consumption to Billing. | [DEVLOG_TENANTFINANCE.md](docs/devlog/DEVLOG_TENANTFINANCE.md) | [33/33 passed](docs/api-tests/TenantFinanceController/TEST_RESULTS.md) |
| AuthController (+ authorization on every controller) | ✅ Complete, tested (branch `feature/app-integration`) | [DEVLOG_AUTH.md](docs/devlog/DEVLOG_AUTH.md) | [46/46](docs/api-tests/AuthController/TEST_RESULTS.md), [authz](docs/api-tests/Authorization/TEST_RESULTS.md) |
| FileController | ✅ Implemented (branch `feature/app-integration`) | [DEVLOG_FILE.md](docs/devlog/DEVLOG_FILE.md) | [files](docs/api-tests/FileController/TEST_RESULTS.md) |
| DashboardController | ✅ Implemented (branch `feature/app-integration`) | [DEVLOG_DASHBOARD.md](docs/devlog/DEVLOG_DASHBOARD.md) | [dashboard](docs/api-tests/DashboardController/TEST_RESULTS.md) |
| BillingController (tariffs + billing runs + tenant bills + adjustments + corrections + async) + BsCalendar | ✅ **Pass 1 + 2 complete**, tested; §14.2 B3/B4/B5/B8/B9/B10/B11/B13/B14/B15 + M9/M14/M17 + T7/T8/T9/P9 + CUSTOM. Full metered engine (SUB_METERED, NEA blended, segment engine, KUKL/boring, overage, corrections, async workers). Resolves Open Verification Item #1 (BS calendar). Penalty deferred to Payment phase. | [DEVLOG_BILLING.md](docs/devlog/DEVLOG_BILLING.md) | [35/35 + 31/31 + 14/14 unit passed](docs/api-tests/BillingController/TEST_RESULTS.md) |

**Global convention adopted (2026-07-30):** JPA Auditing via `BaseAuditEntity` (`@CreatedDate`/`@LastModifiedDate`). Mutable entities extend it; append-only entities use `@CreatedDate` + `AuditingEntityListener`. Services use `saveAndFlush()` on update/delete so responses carry a fresh `updatedAt`. Applies to all future controllers.

**Global convention adopted (2026-07-30, PropertyController pass):** every controller's endpoints must be tested live against real Postgres before being called done — save raw responses to `docs/api-tests/<Controller>/responses/`, a re-runnable Postman collection, and a `TEST_RESULTS.md` with a pass/fail table. Write a detailed `docs/devlog/DEVLOG_<CONTROLLER>.md` (files created, schema, per-endpoint rules/logic, exception table, design decisions, issues found) and a short summary entry in this file's phase section + the Controller Log table above. Any FK reference in a request body must be existence-checked in the service layer, never left to the DB constraint. This is the standard protocol for all remaining controllers.

**Global convention adopted (2026-07-30, spec audit pass):** before writing the service layer for any controller, read the Edge Case Register in `RENT_ERP_CONTEXT.md` (§14, condensed) for the subsection matching that controller's domain — 14.1 Meter & Reading before `MeterController`, 14.2 Billing before `BillingController`, 14.3 Tenancy & Occupancy before `TenancyController`, 14.4 Vacancy before `VacancyController`, 14.5 Payment & Platform before `PaymentController`/`NotificationController`. Design the service methods to explicitly handle the cases that apply (validation blocks, chain rules, segment boundaries, idempotency, etc.) — don't discover them from a bug report later. In that controller's `DEVLOG_<CONTROLLER>.md`, add a short "Edge cases handled" list naming which numbered cases (e.g. M2, B9, V7) were addressed and how, and which are deferred with a reason. `RENT_ERP_CONTEXT.md` is deliberately kept in sync with the full spec doc's edge-case register — read it, not the 88-page doc, unless a case needs more detail than the condensed version gives.
