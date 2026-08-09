package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.BillingRunSegment;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
public class BillingRunSegmentResponse {

    private final UUID id;
    private final UUID billingRunId;
    private final UUID membershipId;
    private final String segmentStartBs;
    private final String segmentEndBs;
    private final int days;
    private final int denominatorCount;
    private final String reason;
    private final Map<String, Object> details;
    private final Instant createdAt;

    private BillingRunSegmentResponse(BillingRunSegment s) {
        this.id = s.getId();
        this.billingRunId = s.getBillingRunId();
        this.membershipId = s.getMembershipId();
        this.segmentStartBs = s.getSegmentStartBs();
        this.segmentEndBs = s.getSegmentEndBs();
        this.days = s.getDays();
        this.denominatorCount = s.getDenominatorCount();
        this.reason = s.getReason().name();
        this.details = s.getDetails();
        this.createdAt = s.getCreatedAt();
    }

    public static BillingRunSegmentResponse from(BillingRunSegment s) { return new BillingRunSegmentResponse(s); }
}
