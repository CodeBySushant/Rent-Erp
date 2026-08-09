# ChargeController (charge_templates) — Detailed Dev Log

**Controller:** `ChargeTemplateController`
**Base URL:** `/api/v1/charge-templates`
**Table:** `charge_templates`
**Status:** Complete

---

## Files Created

| File | Purpose |
|------|---------|
| `db/migration/V7__charge_templates.sql` | Creates `charge_templates` table |
| `domain/charge/entity/ChargeTemplate.java` | JPA entity + `SplitBasis`/`DeactivationMode` enums |
| `domain/charge/dto/CreateChargeTemplateRequest.java` / `UpdateChargeTemplateRequest.java` / `ChargeTemplateResponse.java` | DTOs |
| `domain/charge/repository/ChargeTemplateRepository.java` | Spring Data repo + property/name finders |
| `domain/charge/service/ChargeTemplateService.java` | Business logic + the B6/B7 edge-case handling |
| `domain/charge/controller/ChargeTemplateController.java` | HTTP layer — 5 endpoints |

`tenant_charge_overrides` and `tariff_versions` — also mapped to `ChargeController` in `CONTROLLER_TABLE_MAP.md` — are **deliberately not built in this pass**:
- `tenant_charge_overrides` needs tenant-assignment context (spec §10.2 — charges are selected *while assigning a tenant*) that doesn't exist until `TenancyController` (Phase 4).
- `tariff_versions` is national NEA reference data (slabs, demand charge, versioned by effective date) consumed only by the Billing engine's blended-rate calculation (Phase 5) — inert until then.

Confirmed with the user before implementation.

---

## Schema — `charge_templates`

| Column | Type | Default | Notes |
|--------|------|---------|-------|
| `id` | UUID PK | `gen_random_uuid()` | |
| `property_id` | UUID FK → `properties.id` | — | |
| `name` | VARCHAR(255) | — | e.g. "Internet", "Garbage", or landlord-defined custom |
| `amount` | NUMERIC(10,2) | — | |
| `split_basis` | VARCHAR(50) | — | `FIXED_PER_TENANT` \| `SHARED` (spec §8.2) |
| `zero_amount_acknowledged` | BOOLEAN | `FALSE` | spec §14.2 B6 — must be explicitly true to save a zero amount |
| `deactivation_mode` | VARCHAR(50), nullable | `NULL` | spec §14.2 B7 — set only at deactivation; `THIS_CYCLE_PRORATED` \| `NEXT_CYCLE` \| `VOID` |
| `is_active` | BOOLEAN | `TRUE` | soft delete, never hard delete |
| `created_at` / `updated_at` | TIMESTAMPTZ | `NOW()` | JPA-audited |
| — | `UNIQUE (property_id, name)` | | **our own rule**, not in spec §20.2 — same reasoning as the floors/rooms constraints in V6 |

**Not modeled here (out of scope for `charge_templates`):** Rent, Electricity, Water (all computed, not reusable definitions), one-time charges/credits (ad-hoc, not templates), late penalty/TDS (rule-based, configured on `properties` already via the V4 policy fields).

---

## APIs

### POST /api/v1/charge-templates
Creates a charge under a property. `propertyId` must reference an existing property (checked in-service). `name` must be unique within that property — duplicate → 409. If `amount` is `0.00`, the request must include `"zeroAmountAcknowledged": true` or it's rejected with 400 (spec §14.2 B6).

### GET /api/v1/charge-templates/{id}
Fetch one charge template. 404 if missing. Returns inactive/deactivated templates too.

### GET /api/v1/charge-templates?propertyId=
Lists a property's charge templates, paginated, default sort `createdAt` descending (same convention as `PropertyController`/`PropertyAccessController` — unlike floors, there's no natural physical ordering here).

### PUT /api/v1/charge-templates/{id}
Updates name/amount/splitBasis. Renaming re-checks the `(propertyId, name)` uniqueness (excluding itself). Changing `amount` to `0.00` re-triggers the B6 acknowledgement check — the acknowledgement from a *previous* save doesn't carry forward silently; the request must re-assert it (or the stored flag is reused only if the amount isn't changing in this call — see design decision below). `propertyId` is not editable.

### DELETE /api/v1/charge-templates/{id}?deactivationMode=
Soft delete (deactivate) only — never removed from the DB. `deactivationMode` is an optional query param (`THIS_CYCLE_PRORATED` | `NEXT_CYCLE` | `VOID`), **defaulting to `NEXT_CYCLE`** if omitted — the safest option since it never retroactively touches a cycle already in progress (spec §14.2 B7). Idempotent: re-deactivating an already-inactive charge is a no-op and does **not** overwrite the `deactivationMode` recorded the first time (verified in testing).

---

## Exception handling

| Exception | HTTP Status | When thrown |
|-----------|-------------|-------------|
| `ResourceNotFoundException` | 404 | Charge template id doesn't exist, or `propertyId` on create doesn't reference a real property |
| `DuplicateResourceException` | 409 | `(propertyId, name)` collision, on create or on rename |
| `InvalidOperationException` | 400 | Amount is `0.00` without `zeroAmountAcknowledged: true`, on create or update |
| `MethodArgumentNotValidException` | 400 | `@Valid` fails (blank name, missing splitBasis, negative amount) |
| `Exception` (catch-all) | 500 | Anything unexpected |

---

## Design decisions

**Why is `zeroAmountAcknowledged` a stored column, not just a one-time request flag?**
So it's visible later *why* a zero-amount charge exists — e.g. "Maintenance: Rs 0" on a property where maintenance is genuinely bundled elsewhere, versus a data-entry mistake nobody caught. A transient flag would answer "did they confirm it," but not "what did they confirm and when," which matters for a dispute six months later (spec's own stated design goal, §1.3).

**Why does updating the amount to zero re-require acknowledgement, rather than trusting a previously-stored `true`?**
`updateChargeTemplate()` resolves acknowledgement as: use the request's `zeroAmountAcknowledged` if provided, otherwise fall back to the entity's current stored value. This means changing the amount *to* zero without passing the flag in that same request only succeeds if the template's stored flag was already `true` — i.e. it was already a zero-amount charge. Moving a **nonzero** charge to zero for the first time always requires the explicit flag in that request; it can't inherit an acknowledgement that was never given. This is deliberately stricter than "acknowledge once, forever" — matching B6's framing that zero is "far more often an error than an intention," which applies at the moment of the change, not just at creation.

**Why does `DELETE` take a query param instead of a request body?**
Deactivation is a single categorical choice (three enum options), not a data payload — a query param keeps `DELETE` idiomatic (no body) while still letting the landlord's decision be captured. Compare to `PropertyAccessController`'s `PUT` for role changes, which *is* a data update and correctly uses a body.

**Why `(property_id, name)` uniqueness instead of leaving it open like `PropertyController`'s property names?**
Confirmed with the user before implementation, same reasoning as `floors`/`rooms` in V6: two "Internet" charges on the same property is never intentional, unlike two properties legitimately sharing a name across different landlords.

---

## Testing

All 5 endpoints tested live against PostgreSQL — **23/23 scenarios passed**, including both B6 (zero-amount acknowledgement) and B7 (deactivation-mode capture, including its default and its idempotent-preserve behavior) edge cases end-to-end, the uniqueness constraint on both create and rename, and the usual 404/409/400 paths.

- Full results: [../api-tests/ChargeController/TEST_RESULTS.md](../api-tests/ChargeController/TEST_RESULTS.md)
- Postman collection: [../api-tests/ChargeController/RentERP-ChargeController.postman_collection.json](../api-tests/ChargeController/RentERP-ChargeController.postman_collection.json)
- Raw responses: [../api-tests/ChargeController/responses/](../api-tests/ChargeController/responses/)

No issues found during testing — the FK-existence-check, soft-delete/deactivate, uniqueness, and `saveAndFlush()`-for-fresh-`updatedAt` patterns were already established; the two new B6/B7 edge-case rules behaved as designed on the first pass.

---

## Edge cases handled

Per the DEVLOG.md SOP, read §14.2 Billing Edge Cases before writing the service layer (Charges directly feed the Billing engine's per-tenant totals, so this is the one non-Structure controller so far where a §14 subsection applies directly, even though Billing itself isn't built yet).

- **B6 (rate/charge set to zero)** — handled: `requireZeroAmountAcknowledgement()` blocks any zero-amount save (create or update) unless `zeroAmountAcknowledged: true` is explicitly passed in that request.
- **B7 (charge removed mid-tenancy)** — handled at the data-model level: deactivation is never deletion, and always records which of the three treatments the landlord chose. The *application* of that choice (prorating the current cycle, letting it run through next cycle, or voiding retroactively) is necessarily deferred to the Billing engine (Phase 5) — there's no billing run yet to apply it to. Tracked below.
- All other §14.2 cases (B1–B5, B8–B16) concern the billing *run* itself — reading windows, rounding, concurrency, async generation — and don't apply to a charge-definition CRUD controller. Not handled here; will be revisited when `BillingController` is built.

---

## Open items
- [ ] The Billing engine (Phase 5) must actually read `deactivation_mode` when generating a run and apply the recorded treatment — this controller only captures the landlord's choice, it doesn't (and can't yet) act on it.
- [ ] `tenant_charge_overrides` — per-tenant exclusion/custom-amount records — build alongside `TenancyController`'s assignment flow (Phase 4), not as standalone CRUD here.
- [ ] `tariff_versions` — NEA slab/demand-charge reference data — build alongside the Billing engine's blended-rate calculation (Phase 5).
- [ ] No RBAC enforcement yet — same open item as every controller so far, deferred until JWT auth exists.
- [ ] Add `?active=true` filter on the list endpoint when frontend needs it (same recurring open item).
