package com.renterp.domain.meterreading.dto;

import com.renterp.domain.meterreading.entity.MeterReplacementEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class ReplacementEventResponse {

    private final UUID id;
    private final UUID oldMeterId;
    private final UUID newMeterId;
    private final UUID closeReadingId;
    private final UUID openReadingId;
    private final String replacementDateBs;
    private final String reason;
    private final Instant createdAt;

    private ReplacementEventResponse(MeterReplacementEvent e) {
        this.id = e.getId();
        this.oldMeterId = e.getOldMeterId();
        this.newMeterId = e.getNewMeterId();
        this.closeReadingId = e.getCloseReadingId();
        this.openReadingId = e.getOpenReadingId();
        this.replacementDateBs = e.getReplacementDateBs();
        this.reason = e.getReason();
        this.createdAt = e.getCreatedAt();
    }

    public static ReplacementEventResponse from(MeterReplacementEvent e) {
        return new ReplacementEventResponse(e);
    }
}
