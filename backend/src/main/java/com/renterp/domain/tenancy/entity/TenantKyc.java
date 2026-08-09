package com.renterp.domain.tenancy.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_kyc")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TenantKyc extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_profile_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 30)
    private IdType idType;

    @Column(name = "id_number", nullable = false, length = 100)
    private String idNumber;

    @Column(name = "photo_front_url", length = 500)
    private String photoFrontUrl;

    @Column(name = "photo_back_url", length = 500)
    private String photoBackUrl;

    @Column(name = "photo_selfie_url", length = 500)
    private String photoSelfieUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private Instant submittedAt = Instant.now();

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by", columnDefinition = "uuid")
    private UUID verifiedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "flag_reason", columnDefinition = "TEXT")
    private String flagReason;

    // T5 — resubmit up to 5 times, enforced at service.
    @Column(name = "resubmit_count", nullable = false)
    @Builder.Default
    private short resubmitCount = 0;

    public enum IdType { CITIZENSHIP, PASSPORT, DRIVING_LICENSE, NATIONAL_ID }
    public enum KycStatus { PENDING, APPROVED, REJECTED, FLAGGED }
}
