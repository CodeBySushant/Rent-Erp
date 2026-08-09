package com.renterp.domain.meterreading.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter_replacement_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeterReplacementEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "old_meter_id", nullable = false, columnDefinition = "uuid")
    private UUID oldMeterId;

    @Column(name = "new_meter_id", nullable = false, columnDefinition = "uuid")
    private UUID newMeterId;

    @Column(name = "close_reading_id", nullable = false, columnDefinition = "uuid")
    private UUID closeReadingId;

    @Column(name = "open_reading_id", nullable = false, columnDefinition = "uuid")
    private UUID openReadingId;

    @Column(name = "replacement_date_bs", nullable = false, length = 20)
    private String replacementDateBs;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
