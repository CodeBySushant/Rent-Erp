package com.renterp.domain.structure.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Room extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "floor_id", nullable = false, columnDefinition = "uuid")
    private UUID floorId;

    @Column(nullable = false, length = 100)
    private String name;

    // Deliberately no meter/tenant reference and no occupancy status column (spec §20.1) —
    // both are derived elsewhere (meter_room_coverage, room_assignments), never stored here.
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // createdAt / updatedAt are inherited from BaseAuditEntity and set by JPA auditing
}
