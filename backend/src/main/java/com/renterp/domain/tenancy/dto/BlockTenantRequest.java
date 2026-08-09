package com.renterp.domain.tenancy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BlockTenantRequest {

    @NotNull(message = "tenantProfileId is required")
    private UUID tenantProfileId;

    private String reason;
}
