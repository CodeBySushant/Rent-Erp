package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantKyc;
import com.renterp.domain.tenancy.entity.TenantKyc.IdType;
import com.renterp.domain.tenancy.entity.TenantKyc.KycStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class TenantKycResponse {

    private final UUID id;
    private final UUID tenantProfileId;
    private final IdType idType;
    private final String idNumber;
    private final String photoFrontUrl;
    private final String photoBackUrl;
    private final String photoSelfieUrl;
    private final KycStatus status;
    private final Instant submittedAt;
    private final Instant verifiedAt;
    private final UUID verifiedBy;
    private final String rejectionReason;
    private final String flagReason;
    private final short resubmitCount;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TenantKycResponse(TenantKyc k) {
        this.id = k.getId();
        this.tenantProfileId = k.getTenantProfileId();
        this.idType = k.getIdType();
        this.idNumber = k.getIdNumber();
        this.photoFrontUrl = k.getPhotoFrontUrl();
        this.photoBackUrl = k.getPhotoBackUrl();
        this.photoSelfieUrl = k.getPhotoSelfieUrl();
        this.status = k.getStatus();
        this.submittedAt = k.getSubmittedAt();
        this.verifiedAt = k.getVerifiedAt();
        this.verifiedBy = k.getVerifiedBy();
        this.rejectionReason = k.getRejectionReason();
        this.flagReason = k.getFlagReason();
        this.resubmitCount = k.getResubmitCount();
        this.createdAt = k.getCreatedAt();
        this.updatedAt = k.getUpdatedAt();
    }

    public static TenantKycResponse from(TenantKyc k) { return new TenantKycResponse(k); }
}
