package com.renterp.domain.meterreading.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meter_coverage_event_changes")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeterCoverageEventChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "coverage_event_id", nullable = false, columnDefinition = "uuid")
    private UUID coverageEventId;

    @Column(name = "meter_id", nullable = false, columnDefinition = "uuid")
    private UUID meterId;

    @Column(name = "anchor_reading_id", columnDefinition = "uuid")
    private UUID anchorReadingId;

    // Hibernate 6 native JSONB mapping — no external dep needed. Rooms are display/
    // audit only on this row; the authoritative structural coverage still lives in
    // meter_room_coverage.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rooms_added", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> roomsAdded = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rooms_removed", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<UUID> roomsRemoved = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
