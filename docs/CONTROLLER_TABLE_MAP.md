# Controller ↔ Table Map

Full controller surface across all 59 required tables, grouped to mirror the backend Phase build order in `RENT_ERP_CONTEXT.md`. 14 REST controllers total; `scheduled_tasks` stays internal to db-scheduler with no controller.

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
| 10 | BillingController | billing_runs, billing_run_segments, billing_run_progress, tenant_bills, tenant_bill_adjustments, bill_corrections | 5 | Pending |
| 11 | PaymentController | payment_intents, payments, payment_corrections, reconciliation_runs, reconciliation_issues, idempotency_keys | 6 | Pending |
| 12 | VacancyController | vacancy_requests, vacancy_negotiations, vacancy_meter_readings, vacancy_extensions, vacancy_settlements | 7 | Pending |
| 13 | RoomOpsController | room_addition_events, partial_vacate_events, partial_vacate_rooms, partial_vacate_meter_readings | 7 | Pending |
| 14 | NotificationController | notifications, notification_preferences, property_notices | 8 | Pending |
| 15 | AuditLogController (read-only) | audit_log | cross-cutting | Pending |
| — | *(no controller)* | scheduled_tasks | internal to db-scheduler | N/A |

Future-only tables (schema-only, not built in beta): property_details, property_amenities, property_photos, room_details, room_amenities, room_photos, financial_statements, tenant_payment_scores, assistant_queries.
