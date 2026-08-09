package com.renterp.domain.billing.dto;

import com.renterp.domain.billing.entity.BillCorrection;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class BillCorrectionResponse {

    private final UUID id;
    private final UUID originalBillId;
    private final UUID membershipId;
    private final String correctionType;
    private final UUID regeneratedBillId;
    private final UUID adjustmentId;
    private final String reason;
    private final UUID correctedBy;
    private final Instant createdAt;

    private BillCorrectionResponse(BillCorrection c) {
        this.id = c.getId();
        this.originalBillId = c.getOriginalBillId();
        this.membershipId = c.getMembershipId();
        this.correctionType = c.getCorrectionType().name();
        this.regeneratedBillId = c.getRegeneratedBillId();
        this.adjustmentId = c.getAdjustmentId();
        this.reason = c.getReason();
        this.correctedBy = c.getCorrectedBy();
        this.createdAt = c.getCreatedAt();
    }

    public static BillCorrectionResponse from(BillCorrection c) { return new BillCorrectionResponse(c); }
}
