package com.renterp.domain.support.dto;

import com.renterp.domain.auth.entity.User;
import com.renterp.domain.support.entity.SupportTicket;

import java.time.Instant;
import java.util.UUID;

/** A ticket for the support team: the ticket plus who sent it and how to reach them. */
public record AdminSupportTicketResponse(UUID id, String reference, String topic, String message,
                                         UUID attachmentFileId, String attachmentUrl, String status,
                                         String resolutionNote, Instant createdAt, Instant resolvedAt,
                                         UUID userId, String userName, String userPhone, String userEmail,
                                         String userRole) {

    public static AdminSupportTicketResponse of(SupportTicket t, User u) {
        return new AdminSupportTicketResponse(t.getId(), t.reference(), t.getTopic().name(), t.getMessage(),
                t.getAttachmentFileId(), SupportTicketResponse.attachmentUrl(t), t.getStatus().name(),
                t.getResolutionNote(), t.getCreatedAt(), t.getResolvedAt(), t.getUserId(),
                u == null ? null : u.getName(), u == null ? null : u.getPhone(),
                u == null ? null : u.getEmail(), u == null ? null : u.getRole().name());
    }
}
