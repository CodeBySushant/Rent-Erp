package com.renterp.domain.payment.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Money against one tenant bill (V18). Only APPROVED payments change the bill. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "property_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "membership_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "bill_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID billId;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "paid_at_bs", nullable = false, length = 10, updatable = false)
    private String paidAtBs;

    @Column(length = 100, updatable = false)
    private String reference;

    @Column(length = 500, updatable = false)
    private String note;

    @Column(name = "proof_file_id", columnDefinition = "uuid", updatable = false)
    private UUID proofFileId;

    @Column(name = "submitted_by", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "decided_by", columnDefinition = "uuid")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    public enum Method { CASH, BANK, WALLET, OTHER }

    public enum Source { OWNER_RECORDED, TENANT_PROOF }

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }
}
