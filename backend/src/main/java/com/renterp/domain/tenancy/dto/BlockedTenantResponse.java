package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.PropertyBlockedTenant;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class BlockedTenantResponse {

    private final UUID id;
    private final UUID propertyId;
    private final UUID tenantProfileId;
    private final String reason;
    private final Instant blockedAt;
    private final UUID blockedBy;
    private final boolean active;

    private BlockedTenantResponse(PropertyBlockedTenant b) {
        this.id = b.getId();
        this.propertyId = b.getPropertyId();
        this.tenantProfileId = b.getTenantProfileId();
        this.reason = b.getReason();
        this.blockedAt = b.getBlockedAt();
        this.blockedBy = b.getBlockedBy();
        this.active = b.isActive();
    }

    public static BlockedTenantResponse from(PropertyBlockedTenant b) { return new BlockedTenantResponse(b); }
}
