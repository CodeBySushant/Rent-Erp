package com.renterp.domain.propertyaccess.dto;

import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePropertyAccessRequest {

    // Only the role can change — propertyId/userId are the identity of the grant itself.
    // Revoke and re-grant (via DELETE + POST) if the user or property needs to change.
    @NotNull(message = "Role is required")
    private AccessRole role;
}
