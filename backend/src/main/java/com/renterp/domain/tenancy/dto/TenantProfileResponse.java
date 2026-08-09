package com.renterp.domain.tenancy.dto;

import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.entity.TenantProfile.TenantCategory;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class TenantProfileResponse {

    private final UUID id;
    private final UUID userId;
    private final boolean linked;
    private final String fullName;
    private final String phone;
    private final String whatsappNumber;
    private final String preferredLanguage;
    private final TenantCategory tenantCategory;
    private final short numOccupants;
    private final String emergencyContactName;
    private final String emergencyContactPhone;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TenantProfileResponse(TenantProfile t) {
        this.id = t.getId();
        this.userId = t.getUserId();
        this.linked = t.getUserId() != null;
        this.fullName = t.getFullName();
        this.phone = t.getPhone();
        this.whatsappNumber = t.getWhatsappNumber();
        this.preferredLanguage = t.getPreferredLanguage();
        this.tenantCategory = t.getTenantCategory();
        this.numOccupants = t.getNumOccupants();
        this.emergencyContactName = t.getEmergencyContactName();
        this.emergencyContactPhone = t.getEmergencyContactPhone();
        this.active = t.isActive();
        this.createdAt = t.getCreatedAt();
        this.updatedAt = t.getUpdatedAt();
    }

    public static TenantProfileResponse from(TenantProfile t) { return new TenantProfileResponse(t); }
}
