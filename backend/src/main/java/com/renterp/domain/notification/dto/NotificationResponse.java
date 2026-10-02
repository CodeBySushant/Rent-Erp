package com.renterp.domain.notification.dto;

import com.renterp.domain.notification.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String type, String title, String body, UUID propertyId,
                                   String entityType, UUID entityId, boolean read, Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType().name(), n.getTitle(), n.getBody(), n.getPropertyId(),
                n.getEntityType(), n.getEntityId(), n.getReadAt() != null, n.getCreatedAt());
    }
}
