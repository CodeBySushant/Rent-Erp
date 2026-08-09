package com.renterp.domain.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record of a bill correction (B8). Append-only.
 *
 * <ul>
 *   <li>{@code CANCEL_REGENERATE} — the original (unpaid) bill was cancelled and a
 *       replacement generated; {@link #regeneratedBillId} links the new bill.</li>
 *   <li>{@code NEXT_BILL_ADJUSTMENT} — the original (paid/partial) bill stands and the
 *       delta carries onto the next bill via a {@code tenant_bill_adjustments} row;
 *       {@link #adjustmentId} links it.</li>
 * </ul>
 */
@Entity
@Table(name = "bill_corrections")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "original_bill_id", nullable = false, columnDefinition = "uuid")
    private UUID originalBillId;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_type", nullable = false, length = 30)
    private CorrectionType correctionType;

    @Column(name = "regenerated_bill_id", columnDefinition = "uuid")
    private UUID regeneratedBillId;

    @Column(name = "adjustment_id", columnDefinition = "uuid")
    private UUID adjustmentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "corrected_by", columnDefinition = "uuid")
    private UUID correctedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum CorrectionType { CANCEL_REGENERATE, NEXT_BILL_ADJUSTMENT }
}
