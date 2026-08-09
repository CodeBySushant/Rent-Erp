package com.renterp.domain.structure.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "floors")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Floor extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "property_id", nullable = false, columnDefinition = "uuid")
    private UUID propertyId;

    @Column(nullable = false, length = 255)
    private String name;

    // Ordering key, not a display label — "Ground Floor" might be floorNumber 0,
    // a basement -1. Uniqueness per property enforced at the DB and service layer.
    @Column(name = "floor_number", nullable = false)
    private short floorNumber;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // createdAt / updatedAt are inherited from BaseAuditEntity and set by JPA auditing
}
