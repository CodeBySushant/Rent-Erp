package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.JoinRequest;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class JoinRequestResponse {

    private final UUID id;
    private final UUID tenantProfileId;
    private final UUID propertyId;
    private final JoinRequestStatus status;
    private final String message;
    private final Instant requestedAt;
    private final Instant expiresAt;
    private final Instant respondedAt;
    private final UUID respondedBy;
    private final String responseMessage;
    private final Instant createdAt;
    private final Instant updatedAt;

    private JoinRequestResponse(JoinRequest r) {
        this.id = r.getId();
        this.tenantProfileId = r.getTenantProfileId();
        this.propertyId = r.getPropertyId();
        this.status = r.getStatus();
        this.message = r.getMessage();
        this.requestedAt = r.getRequestedAt();
        this.expiresAt = r.getExpiresAt();
        this.respondedAt = r.getRespondedAt();
        this.respondedBy = r.getRespondedBy();
        this.responseMessage = r.getResponseMessage();
        this.createdAt = r.getCreatedAt();
        this.updatedAt = r.getUpdatedAt();
    }

    public static JoinRequestResponse from(JoinRequest r) { return new JoinRequestResponse(r); }
}
