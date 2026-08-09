package com.renterp.domain.meter.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "meter_room_coverage")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MeterRoomCoverage extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "meter_id", nullable = false, columnDefinition = "uuid")
    private UUID meterId;

    @Column(name = "room_id", nullable = false, columnDefinition = "uuid")
    private UUID roomId;

    // BS date VARCHAR (e.g. "2082-04-01") — never Postgres DATE. Enforced convention across schema.
    @Column(name = "effective_from_bs", nullable = false, length = 20)
    private String effectiveFromBs;

    // NULL = currently active.
    @Column(name = "effective_to_bs", length = 20)
    private String effectiveToBs;
}
