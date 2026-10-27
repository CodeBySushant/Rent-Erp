# Pre-deployment hardening — Dev Log

**Date:** 2026-10-28
**Branch:** `feature/app-integration`
**Migration:** `V25__hardening.sql` (V24 → V25)
**Status:** Built and verified on 2026-10-28: `mvn clean install` passes (71 tests), V25 applies on PostgreSQL 17, the app starts. Re-run the live PowerShell scripts before merging (see 2.3).

This change set fixes the issues found in the backend audit of 2026-10-28. Every issue has an id: **C** = critical, **H** = high, **M** = medium, **L** = low. The ids also appear in code comments (`// C7: …`), so `git grep "C7"` finds every line that implements a fix.

---

## 1. Summary

| Id | Area | Problem | Fix |
|---|---|---|---|
| C2 | Meter readings | Several pending readings per meter; confirming them out of order gave wrong or negative units | One pending reading per meter (409 `READING_PENDING`), meter row lock, chain re-checked at confirm (`READING_CHAIN_CHANGED`) |
| C6 | Billing | Month label, period and generation date unchecked, so overlapping runs could bill the same days twice | Period validated; overlapping live runs refused (`PERIOD_OVERLAP`, `INVALID_PERIOD`) |
| C7 | Bills | Tenants saw DRAFT (unsent) bills | Hidden from tenants in bill detail, bill list and My Stay |
| C8 | Tenants | An owner-added tenant got a second profile on sign-up, so their bills were invisible to them | `TenantProfileLinker` links and merges profiles by verified phone at sign-up, login, join, ID check and Add Tenant |
| C10 | Logging | SQL bind values (password and token hashes, ID numbers) and DEBUG logging always on | Levels come from env vars with safe defaults; phone numbers no longer logged |
| H1 | Move-out / requests | A tenant could withdraw a notice the owner gave | A tenant withdraws only notices and requests they created |
| H2 | Tenancy | `/memberships/{id}/terminate` skipped move-out settlement | Admin-only; refused while money is open; dates validated |
| H3 | Users | `DELETE /users/{id}` on yourself skipped account-deletion checks | Admin-only; ends sessions and push tokens; deactivated users stop at once |
| H4 | Account delete | A deleted owner's properties stayed open and joinable | Properties, their access grants and waiting join requests are closed |
| H5 | Files | The owner could not open a tenant's receipt, meter photo or request photo uploaded without a property | Files are tied to the bill's, meter's or tenancy's property on use; files from another property are refused |
| H6 | KYC | Owners and managers decided identity checks; photos stored as free URLs; `verifiedBy` never set | Admin (developer) decides; photos stored as file ids; admin review list; `verifiedBy` recorded |
| H11 | Abuse limits | Join codes could be brute-forced; no per-IP limits on OTP or login | Rate limits stored in PostgreSQL on OTP, code checks, login, register, change codes, join look-ups and joins |
| H12 | Join requests | Expired after 5 minutes | Configurable, default 7 days; swept daily |
| H13 | Re-use | A revoked access grant could never be given again; a removed room's name or floor's number could never be reused | Revoked grant reactivated; uniqueness only among active rooms and floors |
| H14 | Charges | Deactivation mode stored but never used | `deactivated_at_bs` recorded; engine bills NEXT_CYCLE in full and THIS_CYCLE_PRORATED up to the stop day |
| H15 | Meters | Coverage could point at another property's room; one room on two sub-meters; several MAIN meters | `CoverageRules`; one active MAIN meter per property and purpose |
| H16 | Dates | BS dates only regex-checked; end before start accepted | `@BsDate` on every request date; end ≥ start checks |
| H17 | Config | Health details public; no CORS setup; unused dev user required | Health details admin-only; `APP_CORS_ALLOWED_ORIGINS`; dev user removed |
| M1 | Electricity | A mid-month incoming tenant paid the previous tenant's usage | Bills units after the move-in (`TENANT_JOIN`) reading, otherwise prorates by days |
| M2 | Electricity | MAIN_METER_ONLY ignored main-meter readings; infrastructure meters and meter split rules ignored; designated tenant could not be set | All three implemented |
| M4 | Penalties | Never charged | Late fees on overdue bills (FIXED / PERCENTAGE / PER_DAY, frequency, grace days, cap) |
| M5 | Readings due | Calendar month instead of billing window; tenant photo optional; several monthly readings allowed | Billing window, photo required, one BILLING_RUN reading per meter per month |
| M6 | Meter events | Replacement and coverage anchor readings unchecked; replacement serial duplicates | Checked against the last confirmed reading; serial unique |
| M7 | Async billing | 2-second sleep before the job; yml settings ignored; null-pointer on confirm | Enqueued after commit; settings from yml; guards |
| M8 | Closed properties | Deleting had no guards; closed properties still listed and editable | Delete guards; closed properties hidden from lists and frozen for new data |
| M10 | Billing settings | Could change while a draft run was open | 409 `DRAFT_RUN_OPEN` |
| M11 | Ended tenancies | Opening balances and adjustments could be added to them, then never billed | Refused (`TENANCY_ENDED`) |
| M12 | Notifications | English only; no push hook; device tokens never removed | Written in the recipient's language; `PushSender` called after commit; tokens removed on logout, delete or session end |
| M13 | Notifications | Raw Spring `Page` | `PagedResponse` like every other list |
| M14 | Errors | 500s on a bad status filter, a bad sort field, lock conflicts and parameter validation | 400 / 409 with codes |
| M15 | Phones | Profile phone fields unchecked | Nepal mobile rule on every phone field (app sign-up is Nepal-only too) |
| M16 | Housekeeping | Expired codes, sessions and rate windows kept forever; unlimited sessions | Daily `HousekeepingJob`; at most 10 sessions per user |
| M17 | Tariffs | Could overlap; editable while used by a draft run | Overlap refused (an open-ended one is auto-closed); in-use edits refused |
| M18 | Errors | Generic codes; messages leaked entity names and ids | Specific codes such as `ROOM_NOT_FOUND`, `TENANCY_NOT_FOUND`, `BILLING_RUN_ALREADY_EXISTS` |
| M20 | Tenant profiles | The creator kept access to a profile forever | Only until the profile is tied to a property |
| M21 | Uploads | Unlimited per user | 100 per day and 500 MB per user (configurable) |
| L2 | Passwords | More than 72 bytes could make BCrypt throw a 500 | Rejected before the encoder is called |
| L3 | Payments | An idempotency key reused for another bill returned the wrong payment | 409 `IDEMPOTENCY_KEY_REUSED` |
| L4 | Concurrency | Concurrent edits overwrote each other silently | `@Version` on every audited table; 409 `CONCURRENT_UPDATE` |

---

## 2. Before running this build

### 2.1 Database
Flyway applies **V25** on start. It is additive except:

* Floors and rooms: the unique constraints `uq_floors_property_floor_number` and `uq_rooms_floor_name` are replaced by partial unique indexes over **active** rows only.
* `tenant_bill_adjustments.chk_adjustment_source` now also allows `PENALTY`.

No data is changed. Existing rows get `version = 0`.

### 2.2 Environment (`backend/.env`)
* **Remove** `DEV_SECURITY_USER` and `DEV_SECURITY_PASSWORD`. They are no longer read, and keeping them does no harm.
* New variables are optional; the defaults are safe for production. They are listed in `backend/.env.example`:

| Variable | Default | Purpose |
|---|---|---|
| `APP_LOG_LEVEL` | INFO | `com.renterp` log level (DEBUG locally) |
| `SQL_LOG_LEVEL` | WARN | Hibernate SQL (DEBUG prints queries) |
| `SQL_BIND_LOG_LEVEL` | OFF | Bind values. TRACE locally only, because it prints hashes and personal data |
| `WEB_LOG_LEVEL` | INFO | Spring web and security |
| `LOG_DIR` | logs | Log folder |
| `RATE_LIMIT_ENABLED` | true | `false` only on a developer machine running the live scripts |
| `FORWARD_HEADERS_STRATEGY` | native | Real client IP behind a private-network reverse proxy |
| `APP_CORS_ALLOWED_ORIGINS` | (empty) | Comma-separated browser origins |
| `JOIN_REQUEST_TTL_HOURS` | 168 | Join request lifetime |
| `MAX_SESSIONS_PER_USER` | 10 | Signed-in devices per account |
| `FILES_MAX_UPLOADS_PER_DAY` | 100 | Upload quota |
| `FILES_MAX_TOTAL_MB_PER_USER` | 500 | Storage quota |

To see the old verbose logs locally, add `APP_LOG_LEVEL=DEBUG` and `SQL_LOG_LEVEL=DEBUG`. With `APP_ENV=local`, OTP codes are still written to `logs/rent-erp.log` (at WARN level).

### 2.3 Live PowerShell scripts
* Run them with `RATE_LIMIT_ENABLED=false`: many scripts request more OTP codes from one address than the per-IP limit allows. The exception is the last check of `JoinByCode/join_test.ps1` (21 look-ups → 429), which needs limits **on**. If you re-run within 10 minutes with limits on, clear the counters first: `psql … -c "DELETE FROM rate_limit_counters;"`.
* Already updated in this change:
  * `MyStay/mystay_test.ps1`: the tenancy now ends through move-out notice and settle (H2).
  * `BillingFlow/billing_flow_test.ps1`: duplicate-month code `DUPLICATE` → `BILLING_RUN_ALREADY_EXISTS` (M18).

### 2.4 Pre-auth Postman collections (`AUTH_ENFORCED=false`)
* `TenancyController` KYC submit now takes `idFrontFileId`, `idBackFileId` and `selfieFileId`. The old `photo*Url` fields are gone, and the files must be uploads owned by the tenant's account (H6). Those requests will fail until rewritten. Approve, reject and flag still work, because admin checks pass when enforcement is off.
* Any assertion on `"code": "NOT_FOUND"`, `"DUPLICATE"` or `"INVALID_OPERATION"` for a not-found or duplicate case now sees a specific code (M18).

---

## 3. API changes for app and client developers

### 3.1 Behaviour changes (could break a client)
| Endpoint | Change | Id |
|---|---|---|
| `POST /memberships/{id}/terminate` | Admin only; refused while a move-out notice, waiting payment, held deposit or unpaid bill exists (`SETTLE_WITH_MOVE_OUT`) | H2 |
| `DELETE /users/{id}` | Admin only. Users delete themselves with `DELETE /me` | H3 |
| `POST /tenant-profiles/{id}/kyc` | Tenant (self) or admin only; body uses file ids | H6 |
| `GET /tenant-profiles/{id}/kyc` | Tenant (self) or admin only | H6 |
| `POST /tenant-profiles/{id}/kyc/approve\|reject\|flag` | Admin only | H6 |
| `POST /me/meters/{id}/readings` | `photoFileId` required (`PHOTO_REQUIRED`) | M5 |
| `POST /meters/{id}/readings` | 409 `READING_PENDING` while another reading waits; 409 `READING_ALREADY_SUBMITTED` for a second BILLING_RUN reading in a BS month | C2, M5 |
| `POST /readings/{id}/confirm` | 400 `READING_CHAIN_CHANGED` when a later reading was confirmed first | C2 |
| `POST /properties/{id}/billing-runs` | `INVALID_PERIOD`, `PERIOD_OVERLAP`, `PROPERTY_INACTIVE` | C6, M8 |
| `PUT /properties/{id}` | 409 `DRAFT_RUN_OPEN` for billing settings while a draft run exists; 409 `PROPERTY_INACTIVE` | M10, M8 |
| `DELETE /properties/{id}` | 409 `PROPERTY_HAS_TENANTS` / `DRAFT_RUN_OPEN` | M8 |
| `GET /properties` (non-admin) | Closed properties no longer listed | M8 |
| `GET /me/notifications` | Body is `PagedResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `last`); `title` and `body` are in the user's language | M13, M12 |
| `GET /memberships/{id}/bills`, `GET /tenant-bills/{id}`, `GET /me/stay` | Tenants never see DRAFT bills (404 for a draft bill id) | C7 |
| `POST /move-outs/{id}/cancel`, `POST /requests/{id}/cancel` | A tenant only withdraws what they created | H1 |
| All 404 / 409 from the older services | Specific `code` and plain message | M18 |
| Optimistic-lock conflicts | 409 `CONCURRENT_UPDATE` | L4 |

### 3.2 New endpoints and fields
| Endpoint / field | Purpose | Id |
|---|---|---|
| `GET /api/v1/admin/tenant-kyc?status=PENDING&page=&size=` | Admin review list for tenant identity documents (full ID number, photo file ids) | H6 |
| `DELETE /api/v1/me/devices` body `{token}` | Stop push to a device | M12 |
| `PUT /meters/{id}` `designatedTenantId`, `clearDesignatedTenant` | Who reads a DESIGNATED_TENANT meter | M2 |
| `DELETE /charge-templates/{id}?deactivationMode=&effectiveDateBs=` | Stop date (BS, default today) | H14 |
| Property `penaltyAmount` (create, update, response) | The penalty value | M4 |
| `TENANT_JOIN` reading type (`POST /meters/{id}/readings`) | Owner's reading on the move-in day | M1 |
| Tenant KYC response `idFrontFileId`, `idBackFileId`, `selfieFileId` | Photo files | H6 |
| Bill line item code `PENALTY` | Late fee lines | M4 |
| `MAIN_METER_ONLY` billing without `electricityTotalAmount` | Total taken from main-meter readings (needs `electricityRatePerUnit` on FLAT_RATE) | M2 |

### 3.3 New error codes
`READING_PENDING`, `READING_CHAIN_CHANGED`, `READING_BEFORE_PREVIOUS`, `READING_BELOW_PREVIOUS`, `READING_ALREADY_SUBMITTED`, `PHOTO_REQUIRED`, `INVALID_PERIOD`, `PERIOD_OVERLAP`, `PROPERTY_INACTIVE`, `PROPERTY_HAS_TENANTS`, `DRAFT_RUN_OPEN`, `SETTLE_WITH_MOVE_OUT`, `TENANCY_ENDED`, `ROOM_INACTIVE`, `FLOOR_INACTIVE`, `ROOM_NOT_IN_PROPERTY`, `ROOM_ALREADY_METERED`, `MAIN_METER_EXISTS`, `TENANT_NOT_AT_PROPERTY`, `TARIFF_OVERLAP`, `TARIFF_IN_USE`, `FILE_OTHER_PROPERTY`, `UPLOAD_LIMIT`, `STORAGE_QUOTA_FULL`, `KYC_NEEDS_ACCOUNT`, `IDEMPOTENCY_KEY_REUSED`, `OTP_RATE_LIMITED` (per IP), `LOGIN_RATE_LIMITED`, `REGISTER_RATE_LIMITED`, `CONCURRENT_UPDATE`, `INVALID_SORT`, `INVALID_STATUS`, `ELECTRICITY_TOTAL_REQUIRED`, `RUN_NOT_DRAFT`, `<RESOURCE>_NOT_FOUND`, `<RESOURCE>_ALREADY_EXISTS`.

---

## 4. Details per issue

Each entry lists the problem, the fix, the files and how to test it. Paths are relative to `backend/src/main/java/com/renterp/`.

### C2 — Reading chain integrity
**Problem.** Every pending reading was checked against the latest confirmed reading at the time it was submitted. With two pending readings on a meter, confirming the later one first and then the earlier one stamped the earlier one with negative units.
**Fix.**
* `MeterReadingService.submitReading` locks the meter row (`MeterRepository.findByIdForUpdate`) and refuses while another reading of the meter is PENDING.
* `confirmReading` locks the meter and runs `revalidateAtConfirm`: anchor types need an empty chain; others need a predecessor, a date not before it, and a value not below it unless rollover was confirmed.

**Files.** `domain/meterreading/service/MeterReadingService.java`, `domain/meterreading/repository/MeterReadingRepository.java`, `domain/meter/repository/MeterRepository.java`.
**Test.** Submit A (pending), then submit B → 409 `READING_PENDING`. Discard A, submit and confirm B, then confirm an older pending reading → 400 `READING_CHAIN_CHANGED`.

### C6 — Billing period validation
**Problem.** `billingMonthBs`, the period and `generatedAtBs` were taken from the client unchecked. Runs labelled with different months could cover the same days.
**Fix.** `BillingRunService.validatePeriod`:
* the month is real (01–12) and the period starts in that month;
* the period is at most 32 days;
* the generation date is not before the period start and not in the future;
* no live run of the property overlaps (`BillingRunRepository.findOverlapping`).

Idempotent replays still return the original run before these checks run.

**Files.** `domain/billing/service/BillingRunService.java`, `domain/billing/repository/BillingRunRepository.java`.
**Test.** Create May (05-01..05-31). Creating "2082-06" with period 05-15..06-10 → 400 `PERIOD_OVERLAP`. A future `generatedAtBs` → `INVALID_PERIOD`.

### C7 — Draft bills hidden from tenants
**Fix.**
* `ResourceAccess.tenantBill(READ)` answers 404 for a DRAFT bill to anyone without property access.
* `TenantBillService.listByMembership(…, includeDrafts)` excludes drafts unless the caller can see them (`ResourceAccess.canSeeDraftsOfMembership`).
* `MyStayService` skips DRAFT bills.

**Files.** `domain/auth/security/ResourceAccess.java`, `domain/billing/controller/TenantBillController.java`, `domain/billing/service/TenantBillService.java`, `domain/billing/repository/TenantBillRepository.java`, `domain/tenancy/service/MyStayService.java`.
**Test.** Generate a run without confirming it. The tenant's `/me/stay` shows no current bill, and the bill id returns 404 for the tenant and 200 for the owner.

### C8 — One profile per person
**Problem.** Add Tenant creates an unlinked profile from the phone number. When that person signed up and joined by code (or verified ID), a second profile was created, then a second tenancy, and the original bills were invisible to them.
**Fix.** New `domain/tenancy/service/TenantProfileLinker.linkForUser(userId, phone)`:
* It keeps the user's own profile, or links the best unlinked active profile with that phone (one with an active tenancy, then the newest).
* It moves the other unlinked profiles' tenancies, join requests, blocks, designated meters and identity record onto it, then deactivates them. A clashing active tenancy at the same property stays put and is logged.
* It is called:
  * after register, OTP login and password login (`AuthController`, own transaction, never fails the login);
  * before a profile would be created in `JoinByCodeService.join` and `IdentityService` (tenant ID check);
  * in `TenantOnboardingService`: when the phone belongs to an app account, the tenancy goes on that person's own profile.

**Files.** `TenantProfileLinker.java` (new), `AuthController.java`, `JoinByCodeService.java`, `IdentityService.java`, `TenantOnboardingService.java`, `TenantProfileRepository.java`, `PropertyBlockedTenantRepository.java`, `MeterRepository.java`.
**Test.** The owner adds a tenant with phone P. A user signs up with P and logs in → `/me/stay` shows the tenancy and its bills. Joining by code then reuses the same profile.

### C10 — Logging
**Fix.**
* `log4j2.xml` levels come from `APP_LOG_LEVEL`, `SQL_LOG_LEVEL`, `SQL_BIND_LOG_LEVEL` and `WEB_LOG_LEVEL` (system property first, then environment variable). The defaults are INFO / WARN / OFF / INFO.
* `RentErpApplication` no longer has a static logger, so `.env` is loaded before Log4j2 starts.
* `UserService` and `UserController` no longer log phone numbers.

**Files.** `resources/log4j2.xml`, `RentErpApplication.java`, `UserService.java`, `UserController.java`.

### H1 — Who may withdraw
**Fix.** In `ResourceAccess.moveOut(…, allowTenant)` and `tenantRequest(…, allowTenant)`, the tenant path applies only when the notice or request was created by the tenant (`requestedByTenant` / `byTenant`). Otherwise property WRITE is required.

### H2 — Raw termination
**Fix.**
* `MembershipController` terminate → `access.requireAdmin()` and `MembershipService.terminateDirect`, which refuses while a move-out notice, pending payment, held deposit or unpaid bill exists.
* `terminate` (also used by move-out) now requires a valid end date that is not before the tenancy start or any current room's start.

### H3 — Admin-only user deactivation
**Fix.**
* `UserController.deleteUser` → admin only.
* `UserService.deleteUser` ends all sessions and removes push tokens.
* `UserSessionRepository.isActive` also requires `user.active`, so a deactivated account's access token stops working at once.

### H4 — Owner account deletion
**Fix.** `AccountService.closeOwnedProperties`:
* the owner's properties become inactive;
* every grant on them, and the user's own grants elsewhere, are deactivated;
* waiting join requests are cancelled;
* push tokens are removed.

It runs only after the existing "no active tenants" check.

### H5 — Files reachable by the owner
**Fix.** `FileService.attachToProperty(file, propertyId)` sets the property on an upload that has none and refuses one from another property. It is used by:
* `PaymentService.submitProof` (bill's property);
* `ReadingDueService.submitOwn` (meter's property);
* `TenantRequestService.create` (tenancy's property).

`stored_files.property_id` is now updatable for that one-time attach.

### H6 — Identity (KYC) handled by the developer
**Decision.** Both owner and tenant identity documents are stored, and verification is done by the developer (an ADMIN account) for now. The full review flow comes later.
**Fix.**
* Approve, reject and flag are admin only (`ResourceAccess.tenantProfileDecide`), and `verifiedBy` is recorded.
* Submitting or reading tenant KYC is limited to the tenant themself or an admin (`ResourceAccess.tenantKyc`).
* `tenant_kyc` gains `id_front_file_id`, `id_back_file_id` and `selfie_file_id`. `SubmitKycRequest` takes file ids, and the files must be the tenant's own uploads with the right purpose. The `photo_*_url` columns are still filled with `/api/v1/files/{id}/content`.
* New admin list: `GET /api/v1/admin/tenant-kyc` (`IdentityController`, `IdentityService.adminTenantKyc`, `AdminTenantKycResponse`). Owner identities are still at `GET /api/v1/admin/owner-identities`.

**Admin account.** There is still no admin bootstrap (audit item H7, not in this change). Promote a developer account with SQL: `UPDATE users SET role = 'ADMIN' WHERE email = '…';`

### H11 — Rate limits
**Fix.** New `common/ratelimit/RateLimiter` with a fixed window, one upsert into `rate_limit_counters` per hit, in its own transaction, so counts are kept even when the request is refused. Shared by all instances. The caller's IP comes from `common/web/ClientIp` (behind a proxy via `server.forward-headers-strategy`).

| Bucket | Limit |
|---|---|
| `otp-ip` (OTP request) | 20 / hour |
| `otp-check-ip` (sign-up verify, OTP login) | 30 / 15 min |
| `register-ip` | 10 / hour |
| `login-ip` | 30 / 15 min |
| `login-email` | 15 / 15 min |
| `change-code-user` (phone / email change codes) | 10 / hour |
| `join-user` | 20 / 10 min |
| `join-ip` | 40 / 10 min |

The join limits apply to both look-up and join. The old in-memory map in `JoinByCodeService` is gone. The per-phone OTP limits in `OtpService` and the password lockout are unchanged.

### H12 — Join request lifetime
**Fix.** `app.tenancy.join-request-ttl-hours` (default 168). Expired requests are marked EXPIRED by the housekeeping job, as well as lazily when read.

### H13 — Re-grant and re-use
**Fix.**
* `PropertyAccessService.createAccess` reactivates a revoked grant with the new role. `grantedBy` is now the signed-in user, not the request body.
* V25 makes floor-number and room-name uniqueness apply to active rows only, and `FloorService` / `RoomService` check only active rows.

### H14 — Charge deactivation modes
**Fix.**
* `charge_templates.deactivated_at_bs` is set on deactivation (`effectiveDateBs`, default today).
* `ChargeTemplateRepository.findBillableInPeriod` returns active charges plus THIS_CYCLE_PRORATED / NEXT_CYCLE charges stopped on or after the period start.
* `BillingRunService.chargeLastDay` limits THIS_CYCLE_PRORATED to the stop day. Shared charges use day-clipped windows; fixed charges use the days the tenant was present while the charge was on.
* VOID and pre-V25 rows are not billed.

### H15 — Meter coverage integrity
**Fix.**
* New `domain/meter/service/CoverageRules.requireCoverable`: the room is active, in the meter's property, and not openly covered by another active TENANT_SUPPLY meter of the same purpose.
* Used by `MeterService.addRoomCoverage` and `CoverageEventService`. Coverage events now remove all rooms first and then add, so a room can move between meters in one event.
* `MeterService.createMeter` allows one active MAIN meter per property and purpose. Replacement is unaffected.

### H16 — BS date validation
**Fix.**
* New `common/validation/@BsDate` (validator: `BsCalendar.isValid`) on every date field of every request DTO.
* End ≥ start checks: room assignment end, meter coverage end, coverage-event removals, termination, and a room assignment not before its tenancy start.

### H17 — Actuator, CORS, dev user
**Fix.**
* `management.endpoint.health.show-details: when-authorized` with role ADMIN; `/actuator/info` is no longer public.
* `SecurityConfig` adds a `CorsConfigurationSource` from `APP_CORS_ALLOWED_ORIGINS` (empty means none) and a no-op `UserDetailsService`, so Spring Boot creates no default user.
* `spring.security.user` was removed from `application.yml` and `DEV_SECURITY_*` from `.env.example`.
* `server.shutdown: graceful`.

### M1 — Incoming tenant on a sub-meter
**Fix.**
* `TENANT_JOIN` readings can be submitted by the owner.
* `ElectricityEngine.unitsSinceMoveIn`: for a tenant who started after the period start, units count only after the latest confirmed reading on or before the move-in day (inside the period). Without such a reading, the period's units are prorated by the days present and the bill line says "estimated".
* Several tenancies on one meter split by covered rooms × days present.

### M2 — Main meter, infrastructure meters, designated tenant
**Fix.**
* **MAIN_METER_ONLY:** when `electricityTotalAmount` is not sent, `ElectricityEngine.mainMeterTotal` takes the main meter's units for the period and prices them: the NEA bill (BLENDED_RATE, stored on the run) or units × `electricityRatePerUnit` (FLAT_RATE).
* **SUB_METERED infrastructure meters:**
  * Units are shared, with the segment engine (days present), among all tenancies (GLOBAL) or those with a room on a scoped floor (SCOPED).
  * The weight is the meter's `splitRuleOverride`, or the property's rule.
  * Their units count with the sub-meters in the M17 overage check and the common-units figure.
* **Designated tenant:** `PUT /meters/{id}` accepts `designatedTenantId` (must have an active tenancy at the property) and `clearDesignatedTenant`.

### M4 — Penalties
**Fix.** `properties.penalty_amount` (V25) together with the existing type, frequency, grace days and cap. At generation, `BillingRunService.penaltiesFor` charges each of the tenancy's issued, unpaid bills past its due date + grace days:
* FIXED: `penaltyAmount`;
* PERCENTAGE: `penaltyAmount` % of the unpaid balance;
* PER_DAY: `penaltyAmount` × days late since the last fee.

Frequency ONE_TIME charges a bill once; PER_MONTH once per run; PER_DAY multiplies FIXED / PERCENTAGE by days. The cap limits the total per overdue bill. Fees appear as `PENALTY` line items and `penaltyAmount` on the bill, and are recorded as APPLIED `PENALTY` adjustments (`origin_bill_id` = the overdue bill). Cancelling the run marks them VOID; they are recomputed next time.

### M5 — Readings due
**Fix.**
* `BsCalendar.billingPeriodStart(today, billingDay)` uses the same rule as the app's Create Bill screen. It is used by `ReadingDueService` and the dashboard "readings pending" count.
* The tenant's photo is required (service check after the access checks).
* One BILLING_RUN reading per meter per BS month, for owner and tenant (`READING_ALREADY_SUBMITTED`).

### M6 — Event readings
**Fix.** `MeterReadingService.insertConfirmedInternal` refuses non-anchor event readings (replacement close, coverage anchor) dated before, or lower than, the last confirmed reading. `MeterReplacementService` checks the new meter's serial for uniqueness within the property.

### M7 — Async billing
**Fix.**
* `BillingAsyncService.enqueue` schedules after the transaction commits (no fixed delay, no orphan job on rollback).
* `BillingSchedulerConfig` reads `db-scheduler.*` from `application.yml`.
* `buildAsync` builds nothing for a non-DRAFT run.
* `confirm` refuses when the progress row or total is missing.

### M8 — Closed properties
**Fix.**
* `PropertyService.deleteProperty` refuses while there are active tenants or draft runs. On closing, it deactivates non-owner grants and cancels waiting join requests.
* Non-admin property lists show active properties only.
* New `common/guard/PropertyGuard` blocks new floors, rooms, meters, charges, tenancies, join requests, grants and billing runs on a closed property.
* A room on a removed floor cannot be created; a removed room cannot be assigned.

### M10 — Billing settings during a draft
**Fix.** `PropertyService.updateProperty` refuses billing-relevant fields while a DRAFT run exists (`changesBillingSettings`). Name, address and city are always editable.

### M11 — Ended tenancies
**Fix.** `TenantOpeningBalanceService.create` and `BillAdjustmentService.create` refuse a non-ACTIVE tenancy.

### M12 — Notifications
**Fix.**
* `NotificationText` gives titles and fixed words (due, refund, still owed, request types and statuses) in en / hi / ne, chosen by the recipient's `preferredLanguage`. Names, amounts and dates are kept as they are.
* `PushSender` (interface) plus `LoggingPushSender` (placeholder) is called after commit. Adding Firebase is one `@Primary` class.
* `device_tokens.session_id`: tokens are removed on logout, session end, "sign out other devices", the session cap and account deletion. There is also `DELETE /me/devices`.
* `users.fcm_token` is no longer written (the field in `PUT /users/{id}` is ignored).

### M13 — Notification list shape
**Fix.** `NotificationController.list` returns `PagedResponse`. The app reads `content`, which is unchanged.

### M14 — 500s turned into 4xx
**Fix.** `GlobalExceptionHandler` handles:
* `ConcurrencyFailureException` and `OptimisticLockException` → 409;
* `PropertyReferenceException` (also when wrapped) → 400 `INVALID_SORT`. Matched by class name, not imported: Spring Data 4 moved the class from `org.springframework.data.mapping` to `org.springframework.data.core`, and the import broke the first build;
* `HandlerMethodValidationException` and `ConstraintViolationException` → 400.

`DashboardService.tenants` answers 400 for an unknown status.

### M15 — Nepal phone numbers only
**Fix.** `common/validation/NepalPhone` is applied to the tenant-profile WhatsApp and emergency-contact fields (the others already had it). The app's sign-up offers Nepal only.

### M16 — Housekeeping and session cap
**Fix.** `common/housekeeping/HousekeepingJob`, daily at 03:15 Nepal time (`@EnableScheduling`), removes or updates:
* expired OTPs older than 7 days;
* ended sessions older than 90 days;
* rate windows older than 1 day;
* join requests past their lifetime (marked EXPIRED).

`AuthTokenService` keeps at most `MAX_SESSIONS_PER_USER` sessions and ends the least recently used.

### M17 — Tariffs
**Fix.** `TariffVersionService.requireNoOverlap`: on create, an earlier open-ended tariff is closed the day before the new one starts; any other overlap → 409 `TARIFF_OVERLAP`. Editing a tariff used by a DRAFT or CONFIRMED run → 400 `TARIFF_IN_USE`.

### M18 — Error codes and messages
**Fix.**
* `ResourceNotFoundException` and `DuplicateResourceException` build `<RESOURCE>_NOT_FOUND` / `<RESOURCE>_ALREADY_EXISTS` and plain messages through `ErrorNames`. The internal field and value go to the log only.
* `InvalidOperationException` accepts an optional code. Its message text is unchanged, because the app matches some sentences.

### M20 — Profile creator access
**Fix.** `ResourceAccess.tenantProfile` and `TenantProfileRepository.findVisible` give the creator access only while the profile has no tenancy or join request. After that, access comes from the property.

### M21 — Upload quotas
**Fix.** `FileService.requireWithinQuota`: 100 uploads per 24 hours and 500 MB kept per user (admins exempt). Local-disk storage remains. An S3 implementation of `FileStorage` is still to do.

### L2 — Long passwords
**Fix.** `PasswordPolicy.tooLongForBcrypt`. Password login, change password and delete account reject values over 72 bytes before calling BCrypt. Change password now checks the policy first.

### L3 — Idempotency key scope
**Fix.** `PaymentService.replay(user, key, billId)` returns the earlier payment only for the same bill. For another bill it answers 409 `IDEMPOTENCY_KEY_REUSED`.

### L4 — Optimistic locking
**Fix.** `BaseAuditEntity.version` (`@Version`), with V25 adding `version BIGINT NOT NULL DEFAULT 0` to every table whose entity extends it. Bulk JPQL updates in this change bump `version` by hand. Conflicts → 409 `CONCURRENT_UPDATE`.

---

## 5. Tests added or changed

| Test | Covers |
|---|---|
| `common/util/BsCalendarBillingPeriodTest` (new) | M5 billing window, month end |
| `common/exception/ErrorNamesTest` (new) | M18 codes and messages |
| `domain/notification/service/NotificationTextTest` (new) | M12 languages |
| `domain/auth/service/PasswordPolicyTest` (+1) | L2 |
| `IdentityServiceTest`, `RoomServiceDeleteTest` | Constructor changes only |

---

## 6. Companion app change (`Renterp-frontend`)
* `lib/features/auth/signup_draft.dart`: sign-up offers Nepal only (M15).
* `lib/features/readings/readings_live.dart`: the tenant must take a meter photo (M5).
* `lib/features/setup/setup_commit.dart`: sends `penaltyAmount` (M4); it is no longer listed as "not saved".

See `docs/FIXES-2026-10-28.md` in the app repo.

---

## 7. Audit items not in this change
C1 (correction double counting), C3 (replaced meter's units lost mid-period), C4 (rent ignores mid-period room and rent changes), C5 (partial last month on move-out), C9 (reverse a recorded payment), C11 (SMS sender), C12 (file deletion of referenced files), H7 (admin bootstrap and tariff seed), H8 (correction vs pending proof), H9 (negative totals), H10 (other spoofable audit fields: `confirmedBy`, `cancelledBy`, `correctedBy`, adjustment `createdBy`), H18 (move-out settlement gaps), M3 (custom split weights validation), M9 (fixed account role), M19 (hard deletes of draft readings and infra scopes), L1 (role in token until expiry), L5–L8 (performance, docs, Dockerfile / CI, proxy settings beyond forward headers).

---

## 8. Build fixes after the first run

The code was written without a compiler. The first local build found three errors and one warning flood, all fixed before committing (they are part of the commits below, not separate commits):

| Problem | Fix |
|---|---|
| `org.springframework.data.mapping.PropertyReferenceException` does not exist in Spring Data 4 (moved to `org.springframework.data.core`) | `GlobalExceptionHandler` matches the exception by class name, so it works with either package |
| `ElectricityEngine.computeSubMetered` declared a local `members` that clashed with the parameter | Local renamed to `owing` |
| V25 created `idx_device_tokens_user`, which V23 already creates; Flyway failed | Line removed from V25 |
| `log4j2.xml` property `LOG_DIR` referred to its own name and printed "Infinite loop in property interpolation" warnings | Internal property renamed to `LOG_PATH`; the env variable is still `LOG_DIR` |

---

## 9. Commit map

The change set is committed as one commit per area, in this order. Each commit message lists the issue ids it covers; section 4 has the detail per issue. Files touched by several issues sit in the commit of their area, so only the last commit of the series is guaranteed to build; bisect at area level, not inside the series.

| # | Commit | Issues |
|---|---|---|
| 1 | `feat(db): V25 hardening migration and optimistic locking` | L4, H11, H6, H13, H14, M4, M12 |
| 2 | `feat(common): shared guards, validation, rate limiter and error codes` | M14, M18, H11, H16, M15, M8, M5, M16 |
| 3 | `fix(config): env-driven log levels, CORS, actuator and startup` | C10, H17, M7, M16 |
| 4 | `fix(auth): admin-only user delete, rate limits, session cap, account closure` | H1, H3, H4, H11, L2, M16, M20, C7 |
| 5 | `fix(tenancy): one profile per person, guarded termination, developer KYC` | C8, H2, H6, H12, M8, M11, M15, M20, H16 |
| 6 | `fix(property): closed-property guards, re-grantable access, reusable names` | M8, M10, H13, H4, M14 |
| 7 | `fix(meters): reading chain integrity, coverage rules, incoming tenant units` | C2, M1, M2, M5, M6, H15, H5, H16 |
| 8 | `fix(billing): period validation, charge stop dates, penalties, tariff overlap` | C6, C7, H14, M1, M2, M4, M7, M11, M17 |
| 9 | `fix(files): property-scoped uploads, quotas and idempotency scope` | H5, M21, L3 |
| 10 | `feat(notifications): localized text, push hook and device-token cleanup` | M12, M13 |
| 11 | `docs: hardening devlog, README and updated live test scripts` | all |

Find a fix in history with `git log --grep "C8"` or `git log --oneline -- <file>`.
