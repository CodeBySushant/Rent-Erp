package com.renterp.domain.propertyaccess.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "property_access")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyAccess extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AccessRole role;

    // Null for the OWNER row auto-created alongside the property — nobody "granted" it.
    @Column(name = "granted_by", columnDefinition = "uuid")
    private UUID grantedBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // createdAt / updatedAt are inherited from BaseAuditEntity and set by JPA auditing

    public enum AccessRole {
        OWNER, MANAGER, VIEW_ONLY
    }
}
