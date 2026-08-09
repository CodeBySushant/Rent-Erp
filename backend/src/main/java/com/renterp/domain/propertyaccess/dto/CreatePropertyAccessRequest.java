package com.renterp.domain.propertyaccess.dto;

import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreatePropertyAccessRequest {

    @NotNull(message = "Property id is required")
    private UUID propertyId;

    @NotNull(message = "User id is required")
    private UUID userId;

    // OWNER is rejected here — it's created automatically alongside the property
    // (PropertyService.createProperty()), never granted through this endpoint.
    @NotNull(message = "Role is required")
    private AccessRole role;

    // Who is granting this access. No auth context wired yet (same open item as
    // CreatePropertyRequest.ownerUserId), so passed explicitly until a principal resolver exists.
    private UUID grantedBy;
}
