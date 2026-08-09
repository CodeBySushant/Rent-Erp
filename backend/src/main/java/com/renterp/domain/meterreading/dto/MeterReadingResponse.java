package com.renterp.domain.meterreading.dto;

import com.renterp.domain.meterreading.entity.MeterReading;
import com.renterp.domain.meterreading.entity.MeterReading.EstimationBasis;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingStatus;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class MeterReadingResponse {

    private final UUID id;
    private final UUID meterId;
    private final UUID propertyId;
    private final ReadingType readingType;
    private final BigDecimal readingValue;
    private final String readingDateBs;
    private final String submissionDateBs;
    private final boolean backdated;
    private final ReadingStatus status;
    private final BigDecimal consumption;
    private final boolean rollover;
    private final boolean estimated;
    private final EstimationBasis estimationBasis;
    private final boolean gapAbsorbed;
    private final String photoUrl;
    private final String notes;
    private final UUID correctsReadingId;
    private final UUID replacementEventId;
    private final UUID coverageEventId;
    private final UUID submittedBy;
    private final UUID confirmedBy;
    private final Instant confirmedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MeterReadingResponse(MeterReading r) {
        this.id = r.getId();
        this.meterId = r.getMeterId();
        this.propertyId = r.getPropertyId();
        this.readingType = r.getReadingType();
        this.readingValue = r.getReadingValue();
        this.readingDateBs = r.getReadingDateBs();
        this.submissionDateBs = r.getSubmissionDateBs();
        this.backdated = r.isBackdated();
        this.status = r.getStatus();
        this.consumption = r.getConsumption();
        this.rollover = r.isRollover();
        this.estimated = r.isEstimated();
        this.estimationBasis = r.getEstimationBasis();
        this.gapAbsorbed = r.isGapAbsorbed();
        this.photoUrl = r.getPhotoUrl();
        this.notes = r.getNotes();
        this.correctsReadingId = r.getCorrectsReadingId();
        this.replacementEventId = r.getReplacementEventId();
        this.coverageEventId = r.getCoverageEventId();
        this.submittedBy = r.getSubmittedBy();
        this.confirmedBy = r.getConfirmedBy();
        this.confirmedAt = r.getConfirmedAt();
        this.createdAt = r.getCreatedAt();
        this.updatedAt = r.getUpdatedAt();
    }

    public static MeterReadingResponse from(MeterReading r) {
        return new MeterReadingResponse(r);
    }
}
