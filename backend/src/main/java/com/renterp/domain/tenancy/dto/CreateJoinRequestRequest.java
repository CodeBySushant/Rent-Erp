package com.renterp.domain.tenancy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateJoinRequestRequest {

    @NotNull(message = "tenantProfileId is required")
    private UUID tenantProfileId;

    @NotNull(message = "propertyId is required")
    private UUID propertyId;

    private String message;
}
