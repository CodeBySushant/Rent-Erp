package com.renterp.domain.notification.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.notification.dto.DeviceTokenRequest;
import com.renterp.domain.notification.dto.NotificationResponse;
import com.renterp.domain.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** The signed-in user's notifications. */
@RestController
@RequestMapping("/api/v1/me")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    // M13: same paged shape as every other list (PagedResponse), not a raw Page.
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<com.renterp.common.response.PagedResponse<NotificationResponse>>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched",
                com.renterp.common.response.PagedResponse.from(service.mine(page, size))));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unread() {
        return ResponseEntity.ok(ApiResponse.success("Unread count", Map.of("count", service.unreadCount())));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> read(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Marked read", service.markRead(id)));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> readAll() {
        return ResponseEntity.ok(ApiResponse.success("All marked read", Map.of("marked", service.markAllRead())));
    }

    @PostMapping("/devices")
    public ResponseEntity<ApiResponse<Void>> device(@Valid @RequestBody DeviceTokenRequest req) {
        service.registerDevice(req);
        return ResponseEntity.ok(ApiResponse.success("Device registered", null));
    }

    /** M12: stop push to this device. Body: {"token": "..."}. Logging out does this automatically. */
    @DeleteMapping("/devices")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> removeDevice(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("token");
        if (token == null || token.isBlank()) {
            throw com.renterp.common.exception.ApiException.badRequest("MISSING_PARAMETER", "token is required.");
        }
        return ResponseEntity.ok(ApiResponse.success("Device removed", Map.of("removed", service.unregisterDevice(token))));
    }
}
