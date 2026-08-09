package com.renterp.domain.billing.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A carry-forward charge or credit that attaches to a membership's next bill: a one-time
 * charge/credit that follows the tenant even to the vacancy bill (P9), a paid-bill
 * correction (B8 paid path, pass 2), or a genuine overpayment credit (P3, Payment phase).
 * The engine consumes PENDING rows when generating a bill, stamping {@link #appliedBillId}
 * and flipping to APPLIED. {@link #amount} is always positive; direction is
 * {@link #adjustmentType}.
 */
@Entity
@Table(name = "tenant_bill_adjustments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBillAdjustment extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private AdjustmentType adjustmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Source source;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "origin_bill_id", columnDefinition = "uuid")
    private UUID originBillId;

    @Column(name = "applied_bill_id", columnDefinition = "uuid")
    private UUID appliedBillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    public enum AdjustmentType { CHARGE, CREDIT }

    public enum Source { ONE_TIME, BILL_CORRECTION, OVERPAYMENT, MANUAL }

    public enum Status { PENDING, APPLIED, VOID }
}
