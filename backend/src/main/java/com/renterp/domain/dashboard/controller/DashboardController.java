package com.renterp.domain.dashboard.controller;

import com.renterp.common.exception.ApiException;
import com.renterp.common.response.ApiResponse;
import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.domain.dashboard.dto.OwnerDashboardResponse;
import com.renterp.domain.dashboard.dto.PropertySummaryResponse;
import com.renterp.domain.dashboard.dto.TenantRowResponse;
import com.renterp.domain.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Screen-shaped reads for the owner app: the dashboard across all properties,
 * one property's summary, and the tenant list.
 */
@RestController
public class DashboardController {

    private static final Set<String> TENANT_STATUSES = Set.of("ACTIVE", "TERMINATED", "ALL");

    private final DashboardService service;
    private final ResourceAccess access;

    public DashboardController(DashboardService service, ResourceAccess access) {
        this.service = service;
        this.access = access;
    }

    // ── GET /api/v1/dashboard — every property the caller can see ───────────────
    @GetMapping("/api/v1/dashboard")
    public ResponseEntity<ApiResponse<OwnerDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard fetched", service.dashboard()));
    }

    // ── GET /api/v1/properties/{propertyId}/summary ──────────────────────────────
    @GetMapping("/api/v1/properties/{propertyId}/summary")
    public ResponseEntity<ApiResponse<PropertySummaryResponse>> summary(@PathVariable UUID propertyId) {
        access.property(propertyId, ResourceAccess.Level.READ);
        return ResponseEntity.ok(ApiResponse.success("Property summary fetched", service.summary(propertyId)));
    }

    // ── GET /api/v1/properties/{propertyId}/tenants?status=ACTIVE|TERMINATED|ALL ─
    @GetMapping("/api/v1/properties/{propertyId}/tenants")
    public ResponseEntity<ApiResponse<List<TenantRowResponse>>> tenants(
            @PathVariable UUID propertyId,
            @RequestParam(required = false) String status) {
        access.property(propertyId, ResourceAccess.Level.READ);
        if (status != null && !TENANT_STATUSES.contains(status.toUpperCase(java.util.Locale.ROOT))) {
            throw ApiException.badRequest("INVALID_PARAMETER", "status must be ACTIVE, TERMINATED or ALL.");
        }
        return ResponseEntity.ok(ApiResponse.success("Tenants fetched", service.tenants(propertyId, status)));
    }
}
