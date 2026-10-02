# Controller ↔ Table Map

Full controller surface across all 59 required tables, grouped to mirror the backend Phase build order in `RENT_ERP_CONTEXT.md`; `scheduled_tasks` stays internal to db-scheduler with no controller. Rows 10–14 were completed on `feature/app-integration`; the controllers added there for the app are listed in the second table.

_Last updated 2026-10-02._

| # | Controller | Tables touched | Phase | Status |
|---|---|---|---|---|
| 1 | UserController | users, otp_attempts, user_sessions | 1 | Done |
| 2 | PropertyController | properties, property_ownership_transfers, property_mode_changes | 2 | Done (property_blocked_tenants moved to TenancyController 2026-07-31) |
| 3 | PropertyAccessController | property_access | 2 | Done |
| 4 | StructureController (Floor + Room) | floors, rooms | 2 | Done |
| 5 | ChargeController | charge_templates (done), tenant_charge_overrides (deferred to Phase 4), tariff_versions (deferred to Phase 5) | 2/5 | Done |
| 6 | MeterController | meters (done), meter_room_coverage (done), infrastructure_meter_scope (FLOOR scope done, TENANT scope deferred to Phase 4), meter_tenant_assignments (deferred to Phase 4) | 3 | Done |
| 7 | MeterReadingController (+ CoverageEventController) | meter_reading_log, meter_replacement_events, meter_coverage_events, meter_coverage_event_changes | 3 | Done |
| 8 | TenancyController | join_requests, tenant_property_memberships, room_assignments, tenant_profiles, tenant_kyc, property_blocked_tenants (moved from PropertyController) | 4 | Done |
| 9 | TenantFinanceController | tenant_deposits, tenant_advance_rent, tenant_opening_balances, rent_increments; adds `room_assignments.monthly_rent` piggyback | 4 | Done |
| 10 | BillingController | billing_runs, billing_run_segments, billing_run_progress, tenant_bills, tenant_bill_adjustments, bill_corrections, tariff_versions | 5 | Done |
| 11 | PaymentController | payments (V18: owner-recorded + tenant proofs, idempotency key on the row). payment_intents, payment_corrections, reconciliation_runs, reconciliation_issues, idempotency_keys deferred until a Khalti / eSewa gateway | 6 | Done (manual payments) |
| 12 | VacancyController → built as **MoveOutController** | move_outs (V19: notice + settlement in one table); deposit via tenant_deposits, payments. vacancy_* tables not used in beta | 7 | Done (notice, withdraw, settle) |
| 13 | RoomOpsController → built as **RoomTransferController** | room_assignments (close old, open new in one transaction). room_addition_events, partial_vacate_* not used in beta | 7 | Done (room transfer; add room via memberships) |
| 14 | NotificationController | notifications, device_tokens (V23). notification_preferences, property_notices deferred | 8 | Done (in-app inbox) |
| 15 | AuditLogController (read-only) | audit_log | cross-cutting | Pending |
| — | *(no controller)* | scheduled_tasks | internal to db-scheduler | N/A |

Future-only tables (schema-only, not built in beta): property_details, property_amenities, property_photos, room_details, room_amenities, room_photos, financial_statements, tenant_payment_scores, assistant_queries.

## Added on `feature/app-integration` (for the Rentlo app)

| Controller | Tables | Migration | Status |
|---|---|---|---|
| AuthController | users (email, password, verification), otp_attempts (purpose, token), user_sessions | V13 | Done |
| AccountController (`/me/...`) | users, otp_attempts (destination widened, CHANGE_EMAIL), user_sessions | V22 | Done |
| FileController | stored_files *(new, beyond the 59)* | V15 | Done |
| DashboardController | reads properties, rooms, memberships, bills, payments, meters | — | Done |
| TenantOnboardingController (Add Tenant) | tenant_profiles, tenant_property_memberships, room_assignments, tenant_deposits | V16 (one open assignment per room) | Done |
| JoinByCodeController | properties.join_code, join_requests | V17 | Done |
| MyStayController | reads tenancy, rooms, bills, deposits | — | Done |
| ReadingDueController | meters, meter_room_coverage, meter_reading_log | — | Done |
| TenantRequestController | tenant_requests *(new)* | V20 | Done |
| PaymentDetailsController | property_payment_details *(new)* | V21 | Done |

Tables added beyond the original 59: `stored_files`, `move_outs`, `tenant_requests`, `property_payment_details`, `device_tokens` (`payments` and `notifications` were in the 59).

