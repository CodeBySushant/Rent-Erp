package com.renterp.domain.tenancy.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "tenant_property_memberships")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPropertyMembership extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_profile_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantProfileId;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    // NULL = fall back to Property.paymentModelDefault (T10).
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_model_override", length = 30)
    private PaymentModelOverride paymentModelOverride;

    @Column(name = "linked_from_join_request_id", columnDefinition = "uuid")
    private UUID linkedFromJoinRequestId;

    @Column(name = "started_at_bs", nullable = false, length = 20)
    private String startedAtBs;

    @Column(name = "ended_at_bs", length = 20)
    private String endedAtBs;

    @Column(name = "termination_reason", columnDefinition = "TEXT")
    private String terminationReason;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public enum MembershipStatus { ACTIVE, TERMINATED }

    // Kept separate from Property.PaymentModel to avoid a compile-time cross-domain
    // dep for a shared enum — same rationale as Meter.SplitRule vs Property.SplitRule.
    public enum PaymentModelOverride { PAY_IN_ADVANCE, PAY_AFTER_STAY }
}
