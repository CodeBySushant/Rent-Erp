package com.renterp.domain.support.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.common.response.PagedResponse;
import com.renterp.domain.support.dto.AdminSupportTicketResponse;
import com.renterp.domain.support.dto.SupportTicketRequests;
import com.renterp.domain.support.dto.SupportTicketResponse;
import com.renterp.domain.support.service.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contact Support tickets: the signed-in user's own, and the admin queue. */
@RestController
public class SupportTicketController {

    private final SupportTicketService service;

    public SupportTicketController(SupportTicketService service) {
        this.service = service;
    }

    // ── POST /api/v1/support/tickets {topic, message, attachmentFileId?} ──
    @PostMapping("/api/v1/support/tickets")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> create(
            @Valid @RequestBody SupportTicketRequests.Create req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent to support", service.create(req)));
    }

    // ── GET /api/v1/support/tickets — the caller's own, newest first ──────
    @GetMapping("/api/v1/support/tickets")
    public ResponseEntity<ApiResponse<PagedResponse<SupportTicketResponse>>> mine(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Support tickets fetched",
                PagedResponse.from(service.mine(page, size))));
    }

    /** ADMIN only: every ticket with its sender; ?status=OPEN for the queue. */
    @GetMapping("/api/v1/admin/support/tickets")
    public ResponseEntity<ApiResponse<PagedResponse<AdminSupportTicketResponse>>> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success("Support tickets fetched",
                PagedResponse.from(service.adminList(status, page, size))));
    }

    /** ADMIN only: resolve with an optional note; the user is notified in the app. */
    @PostMapping("/api/v1/admin/support/tickets/{id}/resolve")
    public ResponseEntity<ApiResponse<AdminSupportTicketResponse>> resolve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) SupportTicketRequests.Resolve req) {
        return ResponseEntity.ok(ApiResponse.success("Support ticket resolved", service.resolve(id, req)));
    }
}
