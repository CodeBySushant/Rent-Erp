package com.renterp.domain.request.dto;

import com.renterp.domain.request.entity.TenantRequest;

import java.time.Instant;
import java.util.UUID;

public record RequestResponse(UUID id, UUID membershipId, UUID propertyId, String type, String status, String title,
                              String description, String preferredDateBs, UUID photoFileId, String photoUrl,
                              boolean byTenant, String tenantName, String rooms, String ownerNote,
                              Instant decidedAt, Instant completedAt, Instant createdAt) {

    public static RequestResponse of(TenantRequest r, String tenantName, String rooms) {
        return new RequestResponse(r.getId(), r.getMembershipId(), r.getPropertyId(), r.getType().name(),
                r.getStatus().name(), r.getTitle(), r.getDescription(), r.getPreferredDateBs(), r.getPhotoFileId(),
                r.getPhotoFileId() == null ? null : "/api/v1/files/" + r.getPhotoFileId() + "/content",
                r.isByTenant(), tenantName, rooms, r.getOwnerNote(), r.getDecidedAt(), r.getCompletedAt(),
                r.getCreatedAt());
    }
}
