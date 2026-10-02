# RENT ERP — Project Context File
**Paste this at the start of any new conversation to restore full context.**
Last updated: July 2026 | Phase: Beta specification complete, starting code implementation

---

## What This Project Is
A rental management SaaS for Nepal. Landlord-primary design — the app works fully without any tenant installing it. Tenant participation is an upgrade layer, never a dependency.

**Core goal:** Landlord enters meter readings once a month on one screen. System produces correct, itemised, auditable bills for every tenant. Everything else is automatic.

**Launch market:** Nepal only. BS calendar throughout. NPR only.

---

## Current Phase
**Beta spec 100% complete.** Phases 1–5 built and tested on `main`. Phase 6 (app integration, branch `feature/app-integration`, 2026-10-02) adds auth, payments (manual + proofs), move-out, room transfer, tenant requests, owner payment details, account self-service and in-app notifications — feature work complete, clean-up and production readiness remaining. Status per controller: `DEVLOG.md` → Controller Log.

### Beta scope (build this, nothing else):
- Full billing engine (all metering modes, NEA blended rate, segment billing)
- All meter types and lifecycle (replacement, split, coverage change, rollover)
- Linked tenant flow (has app) + Unlinked tenant flow (landlord does everything)
- Vacancy: clean exit, mid-month, overdue with extensions
- Room addition, partial vacate, room transfer
- Khalti + eSewa payments with full gateway failure handling
- Deposits, advance rent, existing tenant onboarding
- WhatsApp bill delivery (free share method, not API)
- In-app notification inbox + notice board
- Multi-property, property access roles (owner/manager/view only)
- English, Hindi and Nepali language support (runtime translation — no Nepali / Hindi columns in DB)
- PDF bill generation + reports
- Audit log

### Explicitly out of scope for beta:
Loan/credit facility, services marketplace (plumber/electrician), rental listings, financial proof/income statements, in-app wallet, advertising, voice assistant, lease agreement generation, in-app 2-way messaging, maintenance requests, credibility score, per-room deposits, generator meter, multi-currency.

---

## Tech Stack (locked)

- **Backend:** Java 25 (LTS), Spring Boot 4.1
- **Database:** PostgreSQL
- **Job queue:** db-scheduler (inside Postgres — no Kafka, no microservices at beta)
- **Virtual threads:** spring.threads.virtual.enabled=true (one line, Java 25 pinning fix included)
- **Architecture:** Monolith — NOT microservices. Extract later only when a measured scaling problem exists.
- **Mobile:** Flutter + Dart (repo `Renterp-frontend`), Android priority
- **Auth:** Phone OTP via Sparrow SMS (Nepal)
- **Payments:** Khalti (primary), eSewa (secondary)
- **File storage:** AWS S3 (meter photos, KYC images — KYC photos purged 30 days after verification)
- **PDF:** iText or Apache PDFBox (must render Devanagari)
- **Push:** Firebase Cloud Messaging
- **Timezone:** Store UTC, display Nepal time (UTC+05:45)
- **BS dates:** Stored as VARCHAR "2082-04-15" — never Postgres DATE type
- **Money:** NUMERIC(10,2) — never float. NUMERIC(10,4) for blended rates

---

## Project Structure
```
rent-erp/              ← single git repo (monorepo)
  backend/             ← Spring Boot 4.1 + Java 25
  (app: separate repo Renterp-frontend — Flutter + Dart, Android priority)
```

---

## Database Schema Rules (locked)

### No Nepali columns in the database
All Nepali language content is handled at runtime via translation/i18n.
There are NO `_nepali` columns in any table (e.g. no `name_nepali`, `label_nepali`, `address_nepali`).
The database stores English values only. The app layer handles display language.

### BS dates
Stored as VARCHAR(20) e.g. "2082-04-15" — never Postgres DATE type.
All BS arithmetic goes through a maintained Java library (verify covers 2082+).

### Money
NUMERIC(10,2) for all monetary values. NUMERIC(10,4) for blended rates.
Never use float or double for money.

### Soft deletes only
Nothing is hard-deleted. Use is_active flags, status fields, cancelled/archived states.
Audit log is append-only permanent.

### JSONB columns
Used for display-only snapshots (bill line items, segment details).
Never used for filtering — all filterable data has its own typed column + index.

---

## Key Architectural Decisions (all locked)

### Meter model
- Meters are independent of rooms
- **Coverage** (physical wiring, structural) and **Assignment** (who pays, dynamic) are separate relationships
- Meter types: main | tenant_supply | infrastructure
- Infrastructure scope: global (all tenants) or scoped (specific floors)
- Reading responsibility per meter: first_submission_wins | designated_tenant | landlord_only
- Infrastructure meters default to landlord_only (pump rooms are locked)
- Reading chain is append-only, immutable once confirmed
- 10 reading types: initial | billing_run | vacancy | tenant_join | departure_topup | replacement_close | replacement_open | coverage_anchor | gap_absorbed | correction

### Electricity billing modes (4)
1. **Sub-metered** — individual show meters, units × rate
2. **Main meter only** — total split equal or room-weighted
3. **Fixed per tenant** — flat amount, no readings
4. **Included in rent** — no electricity line item

### NEA compliance
- **Flat rate mode:** landlord sets per-unit price (existing market practice)
- **NEA blended rate mode:** apply slab table to main meter → total NEA bill ÷ total units = blended rate. All sub-meters billed at blended rate. Reconciles exactly to actual NEA bill.
- **Mid-month departure:** previous month's blended rate (default, immediate) OR deferred to billing day (exact). Property-level setting.
- NEA demand charge included in total before dividing
- Slabs stored in tariff_versions table with effective dates — never hardcoded
- billing_runs stores: nea_total_units, nea_energy_cost, nea_demand_charge, nea_vat_amount, nea_total_bill, nea_blended_rate (NUMERIC 10,4)

### Water modes (5)
included_in_rent | fixed_per_tenant | kukl_split (variable monthly amount) | boring_pump_only | kukl_and_boring

### Split rules
equal | room_weighted | custom. Property default, overridable per meter.
Vacant rooms NEVER counted in any denominator.

### Billing engine rules
- Segment-based: every mid-period event (join, exit, meter event) creates a boundary with its own denominator
- Reconciliation: Σ(tenant charges) must equal actual cost exactly — verified atomically before commit
- Immutable once confirmed — corrections are new records, never edits
- Wrong bill unpaid → cancel + regenerate. Wrong bill paid/partial → adjustment on next bill
- Concurrency: 4 layers (UI disable, idempotency key, row lock FOR UPDATE, unique DB index)
- Large properties: async via db-scheduler, 5 parallel workers, progress polling every 2 seconds

### Vacancy (3 types)
- **Clean exit:** leaves on billing day, no proration, departure reading on leave date
- **Mid-month:** prorated, departure reading on leave date
- **Overdue:** daily extension with new readings + charges + fine
- All types: tenant EXCLUDED from regular billing run, gets separate vacancy bill
- No grace period on vacancy bills (grace period is monthly bills only, starts from generation date)
- Departure reading IS the new chain anchor for remaining tenants on shared meters

### Payment gateway
- Intent created BEFORE gateway redirect
- Idempotent callbacks (keyed on gateway_order_id)
- Active verification on app return
- Nightly reconciliation job at 02:00 NPT via db-scheduler
- Exponential backoff: 5 retries at 30/60/120/240/480s

### Payment rules
- Cash payment cannot exceed remaining bill balance (hard block)
- Underpayment and multiple instalments always allowed
- Overpayment → credit (tenant requests or landlord generates)
- One-time charges/credits follow tenant to vacancy bill automatically
- Penalty: no cap by default, configurable cap, waivable at settlement

### KYC
- Admin team verifies manually (not landlord, not automated at beta)
- ID number + type stored permanently
- Photos purged 30 days after verification decision
- Tenant category, num_occupants, emergency contact collected at onboarding
- Future fields (occupation, income, etc.) in schema but not surfaced in beta UI

### Architecture decision
- Monolith + db-scheduler is the chosen architecture
- Microservices rejected for beta — adds 3-6× cost with no current benefit
- Virtual threads (Java 25) deliver reactive-level concurrency with zero reactive complexity
- Internal package structure mirrors future service boundary:
  com.renterp.billing | com.renterp.payments | com.renterp.notifications
- Extract a second service only when a measured bottleneck requires it

---

## Database — 59 Required Tables (grouped)

**Identity/Auth:** users, otp_attempts, user_sessions, tenant_kyc
**Property:** properties, property_access, property_ownership_transfers, property_mode_changes, property_blocked_tenants
**Structure:** floors, rooms
**Charges:** charge_templates, tenant_charge_overrides, tariff_versions
**Meters:** meters, meter_room_coverage, infrastructure_meter_scope, meter_tenant_assignments, meter_reading_log, meter_replacement_events, meter_coverage_events, meter_coverage_event_changes
**Tenancy:** join_requests, tenant_property_memberships, room_assignments, tenant_profiles, tenant_deposits, tenant_advance_rent, tenant_opening_balances, rent_increments
**Billing:** billing_runs, billing_run_segments, billing_run_progress, tenant_bills, tenant_bill_adjustments, bill_corrections
**Payments:** payment_intents, payments, payment_corrections, reconciliation_runs, reconciliation_issues, idempotency_keys
**Vacancy:** vacancy_requests, vacancy_negotiations, vacancy_meter_readings, vacancy_extensions, vacancy_settlements
**Room ops:** room_addition_events, partial_vacate_events, partial_vacate_rooms, partial_vacate_meter_readings
**Notifications:** notifications, notification_preferences, property_notices
**Platform:** audit_log, scheduled_tasks

**Future-only tables (schema designed, not built in beta):**
property_details, property_amenities, property_photos, room_details, room_amenities, room_photos, financial_statements, tenant_payment_scores, assistant_queries

---

## Build Phases (backend)

```
Phase 1 — Foundation
  Spring Boot 4.1 + Java 25 project setup
  PostgreSQL + HikariCP + Flyway migrations
  Virtual threads config
  db-scheduler setup
  users, otp_attempts, user_sessions entities + auth flow

Phase 2 — Property structure
  properties, floors, rooms, property_access

Phase 3 — Meters (most complex domain, do before billing)
  meters, meter_room_coverage, infrastructure_meter_scope
  meter_tenant_assignments, meter_reading_log
  meter_replacement_events, meter_coverage_events

Phase 4 — Tenancy
  join_requests, tenant_property_memberships, room_assignments
  tenant_profiles, tenant_deposits, tenant_advance_rent

Phase 5 — Billing engine (switch to Claude Opus here)
  charge_templates, tariff_versions, billing_runs
  billing_run_segments, tenant_bills, bill_corrections

Phase 6 — Payments                                   [manual payments + proofs done; gateway pending]
  payment_intents, payments, Khalti/eSewa integration
  db-scheduler failure handling + reconciliation

Phase 7 — Vacancy                                    [move-out notice + settlement done (move_outs)]
  All three vacancy types + settlement

Phase 8 — Notifications + PDF + WhatsApp delivery    [in-app notifications done; PDF, WhatsApp, push pending]
```

---

## UX Principles (mobile-first, late-boomer landlords)
- Home screen: 3 numbers only (outstanding, collected, billing day countdown)
- Maximum 3 actions visible at once
- Minimum 16sp font, 48dp tap targets
- Colour + word status (never colour alone): green=paid, red=unpaid, yellow=partial
- Plain language: "Amount to collect" not "Outstanding receivables"
- Confirmation dialog before every irreversible action
- Progress indication for async operations (bill generation)
- Draft save for reading entry (offline support)

---

## Open Verification Items (check before building the relevant module)
1. Java BS calendar library — confirm a maintained library covers 2082+
2. PDF library — confirm Devanagari rendering with embedded fonts before bill layout
3. Khalti webhook retry policy — determines reconciliation lookback window
4. Current NEA tariff + VAT — from official NEA notification only, never secondary sources
5. Nepal government ID verification API — exists or manual admin only?
6. NEA per-unit markup legality — Nepal property lawyer confirmation before public launch

---

## Edge Case Register (spec §14 — authoritative, condensed)
Every case below was explicitly resolved during specification (full detail: `Rent_ERP_Specification.docx` §14). This condensed version is kept in sync and is sufficient for implementation — only fall back to the docx if a case needs more nuance than one line gives. **Read the matching subsection before writing that domain's service layer** (SOP — see DEVLOG.md).

### 14.1 Meter & Reading
| # | Case → Resolution |
|---|---|
| M1 | Meter rolls over (current < previous) → confirm/deny prompt; confirmed = (max−previous)+current, flagged; denied = blocked as entry error |
| M2 | Current < previous, no rollover → blocked at submission, both roles |
| M3 | Reading = 0 → blocked unless opening reading (new/replaced meter) or confirmed rollover |
| M4 | Meter replaced → formal replacement event; old chain closes (final reading), new chain opens; coverage/assignment/split carry forward; billing period splits into sub-periods and sums |
| M5 | Failed meter, final reading unknown → estimate from rolling 3-month avg or manual entry, flagged as estimated |
| M6 | Replacement meter doesn't start at 0 → fine, only deltas within a chain matter |
| M7 | 2+ replacements in one period → more sub-periods, same loop generalises |
| M8 | Replacement lands on billing day → replacement processed first, billing uses new meter's opening reading |
| M9 | 2nd meter added for already-covered rooms → coverage-change event, anchor readings on both, original meter's coverage shrinks; historical bills use coverage-as-of-billing-date |
| M10 | Two meters merged → symmetric to split; removed meter's chain closes, coverage returns to survivor, anchor reading taken |
| M11 | Meter permanently removed, no replacement → deactivation; show affected tenants/rooms before confirming; chain closed, meter inactive (never deleted) |
| M12 | Duplicate meter serial in a property → blocked at creation |
| M13 | Photo upload fails (tenant) → reading+photo is one atomic submission, blocked without photo; landlord may submit without photo |
| M14 | Vacant gap between tenants → fresh reading on new assignment; gap consumption absorbed by landlord (default) or common pool (setting); incoming tenant never billed for it |
| M15 | New tenant assigned before prior vacancy reading confirmed → blocked, prevents chain conflict |
| M16 | Landlord forgot a reading, now overdue → backdated entry allowed, flagged, both dates stored, landlord confirmation required; cannot predate last confirmed reading |
| M17 | Sum of sub-meters > main meter → compare to configurable threshold; notify always fires; block depends on overage action setting; common units floored at 0 |
| M18 | Nobody submits a shared reading → responsibility per meter: first-submission-wins / designated tenant / landlord-only (infra defaults landlord-only) |

### 14.2 Billing
| # | Case → Resolution |
|---|---|
| B1 | Reading missing on billing day → run stays PENDING; landlord enters manually / approves without that tenant's electricity / waits |
| B2 | Bill approved without a reading → next run's predecessor = last confirmed reading; next bill covers both periods with an explanatory date-range note |
| B3 | Private reading missing, shared submitted → electricity not zeroed; bill shows what's computable, marks private portion pending |
| B4 | Bill generated late (chased reading) → grace period runs from generation date, never billing day |
| B5 | Rate changed mid-period → latest rate + effective date stored; takes effect next cycle by default (no retroactive repricing) |
| B6 | Rate/charge set to 0 → strong warning requiring explicit acknowledgement |
| B7 | Charge removed mid-tenancy → deactivated never deleted; landlord chooses: prorated this cycle / from next cycle / void entirely |
| B8 | Wrong reading found post-confirm → unpaid: cancel+regenerate (original retained, linked); paid/partial: adjustment on next bill |
| B9 | Rounding leaves remainder → allocated per config; hard assertion sum(charges)==actual cost before commit |
| B10 | Double-tap Generate / 2 devices → 4 layers: UI disable, idempotency key, row lock+atomic status, unique DB index |
| B11 | No tenants on billing day → empty run recorded (no timeline gap); landlord may still enter readings or skip (gap flagged, next bill marked estimated) |
| B12 | Billing day changed mid-tenancy → landlord prompted: apply to current or next cycle, prorated accordingly |
| B13 | 2+ unconfirmed draft runs exist → confirm in chronological order, oldest first |
| B14 | BS months are 29-32 days → all proration from a maintained calendar library, never hardcode 30 |
| B15 | 50-tenant property times out → async: 1 job/tenant, 5 parallel workers, progress-table polling, PDFs on-demand at beta |
| B16 | Landlord runs multiple properties → fully independent configs; property switcher; every notification names its property |

### 14.3 Tenancy & Occupancy
| # | Case → Resolution |
|---|---|
| T1 | Join request unanswered → expires after 5 min, tenant may resend |
| T2 | Landlord rejects → tenant may re-request; optional message; no reason required |
| T3 | Landlord blocks → no further requests; neutral message shown; unblock-able; property-scoped only |
| T4 | Multiple simultaneous join requests → no conflict, requests join the property not a room; rooms assigned after acceptance |
| T5 | Unacceptable ID submitted → admin rejects with reason; resubmit up to N attempts then support |
| T6 | Physical doc doesn't match → landlord flags, routes to admin; landlord cannot approve/reject verification |
| T7 | Existing tenant, pre-app history → deposit/advance/months-covered/balance/move-in-date entered manually; balance shows as "Previous balance" on first bill |
| T8 | Mid-month join, pay-in-advance → full advance collected, unused days credited on first bill, charges prorated by actual BS month length |
| T9 | Shared-charge denominator changes on join → segment-based: pre-join segment old denominator, post-join segment new; reconciles exactly |
| T10 | Mixed payment models same building → payment model = property default, overridable per tenant; both coexist, same billing day |
| T11 | Tenant adds a room → room addition event; rent/charges prorated from addition date; new reading only if different meter; room-weighted shares recalc |
| T12 | Tenant gives up 1 room, stays → partial vacate; deposit untouched; system auto-detects affected meters incl. infra scope exits, shows landlord a reading checklist |
| T13 | Tenant changes rooms → formal transfer (continuous, deposit carries forward) or vacate-and-rejoin (fresh start); end-of-cycle by default |
| T14 | Rent increased → increment recorded (prev/new value, effective date, reason, actor); notifying tenant is landlord's choice |
| T15 | Ownership transfer/sale → tenants/deposits/pending bills/meter chains always transfer; history transfer optional; emergency transfer via admin+docs |

### 14.4 Vacancy
| # | Case → Resolution |
|---|---|
| V1 | Notice too short (e.g. 3 of 30 days) → both parties warned; landlord may approve/reject/propose different date — not blocked outright |
| V2 | Leave-date disagreement → negotiation loop (propose/accept/decline/counter), every round recorded |
| V3 | Clean-exit tenant only submits standard window reading → departure reading on actual leave date is mandatory (this is why vacating tenants are excluded from the regular run) |
| V4 | Regular bill generated+unpaid when vacancy confirmed → cancelled+retained; vacancy bill authoritative for days occupied; due date from vacancy bill's generation |
| V5 | Regular bill already paid → cannot cancel; credit for unused days → deposit settlement; electricity not credited (departure reading captured actual use) |
| V6 | Penalty accrued on cancelled bill → landlord chooses: transfer to vacancy bill or waive |
| V7 | Tenant overdue, stays past leave date → status OVERDUE; new readings mandatory every extension day; rent/consumption/charges/fine recalculated daily |
| V8 | Tenant won't settle/leave → landlord may force vacate anytime; balance applied to deposit, residue = bad debt; records preserved permanently |
| V9 | Two tenants leave same shared meter same day → one reading serves both vacancy bills; remaining tenants' next bill anchors from it |
| V10 | Deposit > owed → refund obligation on landlord, recorded, marked paid when returned |
| V11 | Owed > deposit → shortfall flagged for collection before exit approval; landlord may still approve with balance outstanding |
| V12 | Tenant disputes reading/bill → configurable dispute window (default 7 days); landlord accepts (correction) or rejects with note; outcome logged both sides |

### 14.5 Payment & Platform
| # | Case → Resolution |
|---|---|
| P1 | Cash amount > remaining balance → hard block, may never exceed remaining balance |
| P2 | Instalment payments → fully supported, each partial recorded, balance carries forward |
| P3 | Genuine overpayment → credit (tenant-requested or landlord-generated), deducted from next/vacancy bill |
| P4 | Wrong amount recorded → edited with original value/reason/actor/timestamp retained |
| P5 | Payment against wrong tenant → reversed (not deleted), re-recorded correctly; both entries visible |
| P6 | Gateway callback never arrives → intent created pre-redirect; active verification on app return + nightly reconciliation; manual override always available |
| P7 | Duplicate gateway callback → idempotent, keyed on gateway_order_id; 2nd call exits without touching ledger |
| P8 | Gateway reverses settled payment → reversal webhook queues job; payment marked reversed, bill reopens, both notified |
| P9 | One-time charge added, tenant leaves before next bill → charge follows tenant, auto-attaches to vacancy bill |
| P10 | Credit exceeds bill total → bill shows zero due, remainder carries forward; negative amount due never displayed |
| P11 | Credit remains at tenant exit → applied to vacancy bill; excess added to deposit refund |
| P12 | Push notification never arrives → in-app inbox is source of truth; every notification written to DB before delivery attempted |
| P13 | WhatsApp delivery unconfirmable → accepted limitation; system records share triggered, not delivered; reshare always available |
| P14 | OTP SMS doesn't arrive → 3 SMS attempts → voice-call OTP → cool-down; delivery receipts stored per attempt |
| P15 | Landlord loses phone → recovery via phone/email/support-issued reset with documented admin review |
| P16 | Billing logic changes in new app version → core updates forced, app unusable until updated (no two clients calculate differently) |
| P17 | No connectivity while reading meters → readings saved as draft, synced later; bill generation requires the server |

### Other locked decisions (outside §14)
- **Deletion doctrine (§17.2):** nothing hard-deleted, ever — records archived/deactivated/soft-deleted. A landlord cannot delete a tenant's billing/payment history (would let a party destroy their own dispute evidence). Tenant can export their own complete data anytime.
- **Notification delivery doctrine (§16.1):** every notification written to DB *before* any delivery attempt — in-app inbox is ground truth, push/SMS/WhatsApp are convenience layers only. A failed push is never a lost notification.
- **In-app wallet / credit facility (§21.4, §21.6):** both gated behind a legal/regulatory review (NRB licensing, capital requirements) before any product design work — legal question first, product question second. Not a beta concern, but don't let either get designed-around informally.

---

## Documents Available
- `Rent_ERP_Specification.docx` — 88-page full product + technical spec
- `Rent_ERP_QuickReference.docx` — 11-page 1-2 line summary of everything
- `Rent_ERP_DataSchema.xlsx` — Full schema with all column details + future fields
- `Rent_ERP_SchemaSnapshot.xlsx` — Compact table/column snapshot (2 sheets: required + future)
- `Rent_ERP_Architecture_Decision.docx` — Monolith vs microservices decision record
- `complete_erd.svg` / `complete_erd.png` — Full ERD (open SVG in browser to zoom)
- `rent_erp_schema.dbml` — Paste into dbdiagram.io for interactive online ERD
- 9 domain-grouped ER diagram PNGs

---

## How to Use This File
This file is the single reference for restoring context — including the condensed Edge Case Register above — so reading the 88-page spec doc from scratch each time is no longer necessary. Read this file only; fall back to `Rent_ERP_Specification.docx` §14 only when a specific case needs more nuance than its one-line resolution gives.
Keep this file in sync: whenever the full spec doc is amended, or a spec-vs-code audit turns up a new locked decision worth remembering, update the matching section here in the same pass.
