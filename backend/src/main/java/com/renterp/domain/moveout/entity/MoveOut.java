package com.renterp.domain.moveout.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A tenant's move-out: notice, then the owner's settlement (V19). */
@Entity
@Table(name = "move_outs")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MoveOut extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "property_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "requested_by", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_by_tenant", nullable = false, updatable = false)
    private boolean requestedByTenant;

    @Column(name = "notice_date_bs", nullable = false, length = 10, updatable = false)
    private String noticeDateBs;

    @Column(name = "planned_move_out_bs", nullable = false, length = 10)
    private String plannedMoveOutBs;

    @Column(name = "short_notice", nullable = false)
    private boolean shortNotice;

    @Column(length = 500)
    private String reason;

    @Column(name = "moved_out_bs", length = 10)
    private String movedOutBs;

    @Column(name = "outstanding_before", precision = 10, scale = 2)
    private BigDecimal outstandingBefore;

    @Column(name = "final_charges", precision = 10, scale = 2)
    private BigDecimal finalCharges;

    @Column(name = "final_charges_note", length = 500)
    private String finalChargesNote;

    @Column(precision = 10, scale = 2)
    private BigDecimal deductions;

    @Column(name = "deductions_note", length = 500)
    private String deductionsNote;

    @Column(name = "deposit_held", precision = 10, scale = 2)
    private BigDecimal depositHeld;

    @Column(name = "deposit_applied", precision = 10, scale = 2)
    private BigDecimal depositApplied;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "tenant_still_owes", precision = 10, scale = 2)
    private BigDecimal tenantStillOwes;

    @Column(name = "settled_by", columnDefinition = "uuid")
    private UUID settledBy;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public enum Status { NOTICE_GIVEN, SETTLED, CANCELLED }
}
