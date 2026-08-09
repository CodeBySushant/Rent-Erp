package com.renterp.domain.tenancy.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.tenancy.dto.*;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memberships")
public class MembershipController {

    private final MembershipService service;

    public MembershipController(MembershipService service) { this.service = service; }

    // Direct-create path used by unlinked/existing-tenant onboarding (spec §10.3 / T7).
    // Linked tenants land here via POST /join-requests/{id}/accept instead.
    @PostMapping
    public ResponseEntity<ApiResponse<MembershipResponse>> create(@Valid @RequestBody CreateMembershipRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Membership created", service.createDirect(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MembershipResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Membership fetched", service.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<MembershipResponse>>> list(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) UUID tenantProfileId,
            @RequestParam(required = false) MembershipStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MembershipResponse> page = service.list(propertyId, tenantProfileId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Memberships fetched", PagedResponse.from(page)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MembershipResponse>> update(@PathVariable UUID id,
                                                                    @Valid @RequestBody UpdateMembershipRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Membership updated", service.update(id, req)));
    }

    // Formal termination — the Vacancy flow (Phase 7) will supersede this endpoint,
    // but the raw form is needed for admin/testing until then. Ends all active room
    // assignments on the same endedAtBs inside the same transaction.
    @PostMapping("/{id}/terminate")
    public ResponseEntity<ApiResponse<MembershipResponse>> terminate(@PathVariable UUID id,
                                                                      @Valid @RequestBody TerminateMembershipRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Membership terminated", service.terminate(id, req)));
    }

    // ── Room assignments — nested under membership ────────────────────────

    @PostMapping("/{id}/room-assignments")
    public ResponseEntity<ApiResponse<RoomAssignmentResponse>> assignRoom(@PathVariable UUID id,
                                                                            @Valid @RequestBody CreateRoomAssignmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room assigned", service.assignRoom(id, req)));
    }

    @GetMapping("/{id}/room-assignments")
    public ResponseEntity<ApiResponse<List<RoomAssignmentResponse>>> listAssignments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Room assignments fetched", service.listAssignments(id)));
    }

    // PATCH not DELETE — the row is retained forever for historical bill reconstruction
    // (same pattern as meter_room_coverage's PATCH-to-end shape).
    @PatchMapping("/{id}/room-assignments/{assignmentId}/end")
    public ResponseEntity<ApiResponse<RoomAssignmentResponse>> endAssignment(@PathVariable UUID id,
                                                                              @PathVariable UUID assignmentId,
                                                                              @Valid @RequestBody EndRoomAssignmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Room assignment ended",
                service.endAssignment(id, assignmentId, req)));
    }
}
