package com.renterp.domain.request.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/** A tenant's request (V20). */
@Entity
@Table(name = "tenant_requests")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRequest extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "membership_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "property_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(length = 2000, updatable = false)
    private String description;

    @Column(name = "preferred_date_bs", length = 10, updatable = false)
    private String preferredDateBs;

    @Column(name = "photo_file_id", columnDefinition = "uuid", updatable = false)
    private UUID photoFileId;

    @Column(name = "created_by", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "by_tenant", nullable = false, updatable = false)
    private boolean byTenant;

    @Column(name = "owner_note", length = 1000)
    private String ownerNote;

    @Column(name = "decided_by", columnDefinition = "uuid")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public enum Type { ROOM_CHANGE, VACATE, MAINTENANCE, OTHER }

    public enum Status { PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED }
}
