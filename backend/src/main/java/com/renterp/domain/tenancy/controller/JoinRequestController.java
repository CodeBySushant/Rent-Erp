package com.renterp.domain.tenancy.controller;

import com.renterp.domain.auth.security.ResourceAccess;
import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.tenancy.dto.CreateJoinRequestRequest;
import com.renterp.domain.tenancy.dto.JoinRequestDecisionRequest;
import com.renterp.domain.tenancy.dto.JoinRequestResponse;
import com.renterp.domain.tenancy.dto.MembershipResponse;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import com.renterp.domain.tenancy.service.JoinRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/join-requests")
public class JoinRequestController {

    private final JoinRequestService service;

    private final ResourceAccess access;

    public JoinRequestController(ResourceAccess access,
            JoinRequestService service) {
        this.access = access; this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<JoinRequestResponse>> create(@Valid @RequestBody CreateJoinRequestRequest req) {
        access.createJoinRequest(req.getTenantProfileId(), req.getPropertyId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Join request created", service.create(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> get(@PathVariable UUID id) {
        access.joinRequestRead(id);
        return ResponseEntity.ok(ApiResponse.success("Join request fetched", service.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<JoinRequestResponse>>> list(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) UUID tenantProfileId,
            @RequestParam(required = false) JoinRequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        access.tenancyList(propertyId, tenantProfileId);
        Page<JoinRequestResponse> page = service.list(propertyId, tenantProfileId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Join requests fetched", PagedResponse.from(page)));
    }

    // Landlord accept — atomically creates a membership. Response returns the
    // resulting membership rather than the request itself, since that's what the
    // caller most likely needs next.
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<MembershipResponse>> accept(@PathVariable UUID id,
                                                                   @Valid @RequestBody JoinRequestDecisionRequest req) {
        access.joinRequestDecide(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Join request accepted; membership created", service.accept(id, req)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> reject(@PathVariable UUID id,
                                                                    @RequestBody(required = false) JoinRequestDecisionRequest req) {
        access.joinRequestDecide(id);
        return ResponseEntity.ok(ApiResponse.success("Join request rejected",
                service.reject(id, req != null ? req : new JoinRequestDecisionRequest())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> cancel(@PathVariable UUID id) {
        access.joinRequestCancel(id);
        return ResponseEntity.ok(ApiResponse.success("Join request cancelled by tenant", service.cancel(id)));
    }
}
