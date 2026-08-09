package com.renterp.domain.propertyaccess.dto;

import com.renterp.domain.propertyaccess.entity.PropertyAccess;
import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class PropertyAccessResponse {

    private final UUID id;
    private final UUID propertyId;
    private final UUID userId;
    private final AccessRole role;
    private final UUID grantedBy;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PropertyAccessResponse(PropertyAccess access) {
        this.id = access.getId();
        this.propertyId = access.getPropertyId();
        this.userId = access.getUserId();
        this.role = access.getRole();
        this.grantedBy = access.getGrantedBy();
        this.active = access.isActive();
        this.createdAt = access.getCreatedAt();
        this.updatedAt = access.getUpdatedAt();
    }

    public static PropertyAccessResponse from(PropertyAccess access) {
        return new PropertyAccessResponse(access);
    }
}
