package com.renterp.domain.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Async progress for a large billing run (B15). One row per async run: the sync engine
 * never writes this. The worker (db-scheduler, 1 task per tenant) increments
 * {@link #processedTenants}/{@link #failedTenants} as each tenant's bill lands; the UI
 * polls the GET progress endpoint every ~2s. RUNNING → COMPLETED | FAILED.
 */
@Entity
@Table(name = "billing_run_progress")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingRunProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "billing_run_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID billingRunId;

    @Column(name = "total_tenants", nullable = false)
    @Builder.Default
    private int totalTenants = 0;

    @Column(name = "processed_tenants", nullable = false)
    @Builder.Default
    private int processedTenants = 0;

    @Column(name = "failed_tenants", nullable = false)
    @Builder.Default
    private int failedTenants = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.RUNNING;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreatedDate
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Status { RUNNING, COMPLETED, FAILED }
}
