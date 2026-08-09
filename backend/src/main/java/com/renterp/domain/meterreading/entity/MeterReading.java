package com.renterp.domain.meterreading.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter_reading_log")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MeterReading extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "meter_id", nullable = false, columnDefinition = "uuid")
    private UUID meterId;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_type", nullable = false, length = 30)
    private ReadingType readingType;

    @Column(name = "reading_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal readingValue;

    @Column(name = "reading_date_bs", nullable = false, length = 20)
    private String readingDateBs;

    @Column(name = "submission_date_bs", nullable = false, length = 20)
    private String submissionDateBs;

    @Column(name = "is_backdated", nullable = false)
    @Builder.Default
    private boolean backdated = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReadingStatus status = ReadingStatus.PENDING;

    // Delta from the previous CONFIRMED reading in the chain, stamped at confirm-time.
    // NULL until confirmed. Chain-anchor rows (INITIAL, REPLACEMENT_OPEN) stay NULL forever.
    @Column(precision = 12, scale = 2)
    private BigDecimal consumption;

    @Column(name = "is_rollover", nullable = false)
    @Builder.Default
    private boolean rollover = false;

    @Column(name = "is_estimated", nullable = false)
    @Builder.Default
    private boolean estimated = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "estimation_basis", length = 30)
    private EstimationBasis estimationBasis;

    @Column(name = "is_gap_absorbed", nullable = false)
    @Builder.Default
    private boolean gapAbsorbed = false;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // CORRECTION rows point back at the wrong-but-confirmed row they replace.
    @Column(name = "corrects_reading_id", columnDefinition = "uuid")
    private UUID correctsReadingId;

    @Column(name = "replacement_event_id", columnDefinition = "uuid")
    private UUID replacementEventId;

    @Column(name = "coverage_event_id", columnDefinition = "uuid")
    private UUID coverageEventId;

    @Column(name = "submitted_by", columnDefinition = "uuid")
    private UUID submittedBy;

    @Column(name = "confirmed_by", columnDefinition = "uuid")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public enum ReadingType {
        INITIAL, BILLING_RUN, VACANCY, TENANT_JOIN, DEPARTURE_TOPUP,
        REPLACEMENT_CLOSE, REPLACEMENT_OPEN, COVERAGE_ANCHOR, GAP_ABSORBED, CORRECTION
    }

    public enum ReadingStatus {
        PENDING, CONFIRMED
    }

    public enum EstimationBasis {
        ROLLING_3_MONTH_AVG, MANUAL
    }
}
