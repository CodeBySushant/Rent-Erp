package com.renterp.domain.meter.dto;

import com.renterp.domain.meter.entity.InfrastructureMeterScope.ScopeBy;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateInfrastructureMeterScopeRequest {

    @NotNull(message = "scopeBy is required")
    private ScopeBy scopeBy;

    // Required when scopeBy = FLOOR, null otherwise. Cross-field check in the service.
    private UUID floorId;

    // Required when scopeBy = TENANT — currently rejected by MeterController until Phase 4.
    private UUID tenantId;
}
