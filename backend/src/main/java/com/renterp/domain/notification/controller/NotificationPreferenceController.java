package com.renterp.domain.notification.controller;

import com.renterp.common.response.ApiResponse;
import com.renterp.domain.notification.dto.NotificationPreferencesDto;
import com.renterp.domain.notification.service.NotificationPreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Profile → Notification Setting (V31): the signed-in user's own choices. */
@RestController
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me/notification-preferences")
    public ResponseEntity<ApiResponse<NotificationPreferencesDto>> mine() {
        return ResponseEntity.ok(ApiResponse.success("Notification preferences fetched", service.mine()));
    }

    /** Any subset of the fields; missing ones keep their value. Returns all of them. */
    @PutMapping("/api/v1/me/notification-preferences")
    public ResponseEntity<ApiResponse<NotificationPreferencesDto>> update(
            @RequestBody NotificationPreferencesDto req) {
        return ResponseEntity.ok(ApiResponse.success("Notification preferences saved", service.update(req)));
    }
}
