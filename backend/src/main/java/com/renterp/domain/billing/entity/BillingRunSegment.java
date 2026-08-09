package com.renterp.domain.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A continuous sub-period a membership was billed under within a run. Pass 1 writes a
 * single FULL_PERIOD segment per bill (audit parity); pass 2's segment engine splits it
 * on mid-period join/exit/meter events (T9, M9), each with its own denominator. Append-only.
 */
@Entity
@Table(name = "billing_run_segments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingRunSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "billing_run_id", nullable = false, columnDefinition = "uuid")
    private UUID billingRunId;

    @Column(name = "membership_id", nullable = false, columnDefinition = "uuid")
    private UUID membershipId;

    @Column(name = "segment_start_bs", nullable = false, length = 20)
    private String segmentStartBs;

    @Column(name = "segment_end_bs", nullable = false, length = 20)
    private String segmentEndBs;

    @Column(nullable = false)
    private int days;

    @Column(name = "denominator_count", nullable = false)
    private int denominatorCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Reason reason = Reason.FULL_PERIOD;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Reason { FULL_PERIOD, MID_MONTH_JOIN, MID_MONTH_EXIT, METER_EVENT, RENT_CHANGE }
}
