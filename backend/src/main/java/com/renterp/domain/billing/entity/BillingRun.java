package com.renterp.domain.billing.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One billing run for a property over a single BS billing period. DRAFT → CONFIRMED
 * (immutable) | CANCELLED. See {@code BillingRunService} for the engine and the four
 * concurrency layers (B10), oldest-first confirmation (B13) and the empty-run case (B11).
 */
@Entity
@Table(name = "billing_runs")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BillingRun extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Column(name = "billing_month_bs", nullable = false, length = 7)
    private String billingMonthBs;      // "YYYY-MM"

    @Column(name = "period_start_bs", nullable = false, length = 20)
    private String periodStartBs;

    @Column(name = "period_end_bs", nullable = false, length = 20)
    private String periodEndBs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "tariff_mode", nullable = false, length = 20)
    private TariffMode tariffMode;

    // NEA blended-rate reconciliation — populated by pass 2. NULL for FLAT_RATE / pass-1 runs.
    @Column(name = "tariff_version_id", columnDefinition = "uuid")
    private UUID tariffVersionId;

    @Column(name = "nea_total_units", precision = 12, scale = 2)
    private BigDecimal neaTotalUnits;

    @Column(name = "nea_energy_cost", precision = 12, scale = 2)
    private BigDecimal neaEnergyCost;

    @Column(name = "nea_demand_charge", precision = 12, scale = 2)
    private BigDecimal neaDemandCharge;

    @Column(name = "nea_vat_amount", precision = 12, scale = 2)
    private BigDecimal neaVatAmount;

    @Column(name = "nea_total_bill", precision = 12, scale = 2)
    private BigDecimal neaTotalBill;

    @Column(name = "nea_blended_rate", precision = 10, scale = 4)
    private BigDecimal neaBlendedRate;

    @Column(name = "tenant_count", nullable = false)
    @Builder.Default
    private int tenantCount = 0;

    @Column(name = "total_billed", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalBilled = BigDecimal.ZERO;

    // B4: the grace period on every bill in this run counts from here, never the billing day.
    @Column(name = "generated_at_bs", nullable = false, length = 20)
    private String generatedAtBs;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "is_async", nullable = false)
    @Builder.Default
    private boolean async = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by", columnDefinition = "uuid")
    private UUID confirmedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", columnDefinition = "uuid")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    public enum Status { DRAFT, CONFIRMED, CANCELLED }

    public enum TariffMode { FLAT_RATE, BLENDED_RATE }
}
