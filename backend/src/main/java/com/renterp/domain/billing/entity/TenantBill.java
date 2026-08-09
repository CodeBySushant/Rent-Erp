package com.renterp.domain.billing.entity;

import com.renterp.common.entity.BaseAuditEntity;
import com.renterp.domain.billing.dto.BillLineItem;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An immutable per-tenant bill produced by a billing run. Never edited: a wrong unpaid bill
 * is cancelled and regenerated ({@link #supersededByBillId}); a wrong paid/partial bill is
 * corrected via a {@code tenant_bill_adjustments} row on the next bill (B8). Every computed
 * component is both a typed column and a line in {@link #lineItems}.
 */
@Entity
@Table(name = "tenant_bills")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBill extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "billing_run_id", nullable = false, columnDefinition = "uuid")
    private UUID billingRunId;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Column(name = "billing_month_bs", nullable = false, length = 7)
    private String billingMonthBs;

    @Column(name = "period_start_bs", nullable = false, length = 20)
    private String periodStartBs;

    @Column(name = "period_end_bs", nullable = false, length = 20)
    private String periodEndBs;

    @Column(name = "days_occupied", nullable = false)
    private int daysOccupied;

    @Column(name = "days_in_period", nullable = false)
    private int daysInPeriod;

    @Column(name = "is_prorated", nullable = false)
    @Builder.Default
    private boolean prorated = false;

    @Column(name = "rent_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal rentAmount = BigDecimal.ZERO;

    @Column(name = "electricity_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal electricityAmount = BigDecimal.ZERO;

    @Column(name = "water_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal waterAmount = BigDecimal.ZERO;

    @Column(name = "charges_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal chargesAmount = BigDecimal.ZERO;

    @Column(name = "penalty_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Column(name = "adjustments_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal adjustmentsAmount = BigDecimal.ZERO;

    @Column(name = "advance_applied_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal advanceAppliedAmount = BigDecimal.ZERO;

    @Column(name = "previous_balance", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal previousBalance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tds_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(name = "rounding_adjustment", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal roundingAdjustment = BigDecimal.ZERO;

    @Column(name = "total_due", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalDue = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "balance_due", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<BillLineItem> lineItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "generated_at_bs", nullable = false, length = 20)
    private String generatedAtBs;

    @Column(name = "grace_period_days", nullable = false)
    private short gracePeriodDays;

    @Column(name = "due_date_bs", nullable = false, length = 20)
    private String dueDateBs;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "supersedes_bill_id", columnDefinition = "uuid")
    private UUID supersedesBillId;

    @Column(name = "superseded_by_bill_id", columnDefinition = "uuid")
    private UUID supersededByBillId;

    public enum Status { DRAFT, ISSUED, CANCELLED }

    public enum PaymentStatus { UNPAID, PARTIAL, PAID }
}
