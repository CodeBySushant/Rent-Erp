package com.renterp.domain.tenancy.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.tenancy.dto.AddTenantRequest;
import com.renterp.domain.tenancy.dto.AddTenantResponse;
import com.renterp.domain.tenancy.service.TenantOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Add Tenant in one call. The tenant list (GET on the same path) is in DashboardController. */
@RestController
public class TenantOnboardingController {

    private final TenantOnboardingService service;
    private final ResourceAccess access;

    public TenantOnboardingController(TenantOnboardingService service, ResourceAccess access) {
        this.service = service;
        this.access = access;
    }

    // ── POST /api/v1/properties/{propertyId}/tenants ─────────────────────────────
    @PostMapping("/api/v1/properties/{propertyId}/tenants")
    public ResponseEntity<ApiResponse<AddTenantResponse>> addTenant(@PathVariable UUID propertyId,
                                                                    @Valid @RequestBody AddTenantRequest req) {
        access.property(propertyId, ResourceAccess.Level.WRITE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant added", service.addTenant(propertyId, req)));
    }
}
