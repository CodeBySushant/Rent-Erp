package com.renterp.domain.meter.dto;

import com.renterp.domain.meter.entity.InfrastructureMeterScope;
import com.renterp.domain.meter.entity.InfrastructureMeterScope.ScopeBy;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class InfrastructureMeterScopeResponse {

    private final UUID id;
    private final UUID meterId;
    private final ScopeBy scopeBy;
    private final UUID floorId;
    private final UUID tenantId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private InfrastructureMeterScopeResponse(InfrastructureMeterScope s) {
        this.id = s.getId();
        this.meterId = s.getMeterId();
        this.scopeBy = s.getScopeBy();
        this.floorId = s.getFloorId();
        this.tenantId = s.getTenantId();
        this.createdAt = s.getCreatedAt();
        this.updatedAt = s.getUpdatedAt();
    }

    public static InfrastructureMeterScopeResponse from(InfrastructureMeterScope s) {
        return new InfrastructureMeterScopeResponse(s);
    }
}
