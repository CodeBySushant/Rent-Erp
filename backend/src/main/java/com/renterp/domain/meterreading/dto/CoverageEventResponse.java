package com.renterp.domain.meterreading.dto;

import com.renterp.domain.meterreading.entity.MeterCoverageEvent;
import com.renterp.domain.meterreading.entity.MeterCoverageEvent.CoverageEventType;
import com.renterp.domain.meterreading.entity.MeterCoverageEventChange;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CoverageEventResponse {

    private final UUID id;
    private final UUID propertyId;
    private final CoverageEventType eventType;
    private final String eventDateBs;
    private final String notes;
    private final Instant createdAt;
    private final List<Change> changes;

    @Getter
    @Builder
    public static class Change {
        private final UUID id;
        private final UUID meterId;
        private final UUID anchorReadingId;
        private final List<UUID> roomsAdded;
        private final List<UUID> roomsRemoved;
        private final Instant createdAt;

        public static Change from(MeterCoverageEventChange c) {
            return Change.builder()
                    .id(c.getId())
                    .meterId(c.getMeterId())
                    .anchorReadingId(c.getAnchorReadingId())
                    .roomsAdded(c.getRoomsAdded())
                    .roomsRemoved(c.getRoomsRemoved())
                    .createdAt(c.getCreatedAt())
                    .build();
        }
    }

    public static CoverageEventResponse from(MeterCoverageEvent e, List<MeterCoverageEventChange> changes) {
        return CoverageEventResponse.builder()
                .id(e.getId())
                .propertyId(e.getPropertyId())
                .eventType(e.getEventType())
                .eventDateBs(e.getEventDateBs())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .changes(changes.stream().map(Change::from).toList())
                .build();
    }
}
