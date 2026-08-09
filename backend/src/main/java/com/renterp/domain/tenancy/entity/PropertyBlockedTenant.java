package com.renterp.domain.tenancy.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "property_blocked_tenants")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyBlockedTenant extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Column(name = "tenant_profile_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantProfileId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "blocked_by", columnDefinition = "uuid")
    private UUID blockedBy;

    @Column(name = "blocked_at", nullable = false)
    @Builder.Default
    private Instant blockedAt = Instant.now();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
