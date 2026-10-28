package com.renterp.domain.support.dto;

import com.renterp.domain.support.entity.SupportTicket;

import java.time.Instant;
import java.util.UUID;

/** A ticket as its author sees it. */
public record SupportTicketResponse(UUID id, String reference, String topic, String message,
                                    UUID attachmentFileId, String attachmentUrl, String status,
                                    String resolutionNote, Instant createdAt, Instant resolvedAt) {

    public static SupportTicketResponse from(SupportTicket t) {
        return new SupportTicketResponse(t.getId(), t.reference(), t.getTopic().name(), t.getMessage(),
                t.getAttachmentFileId(), attachmentUrl(t), t.getStatus().name(), t.getResolutionNote(),
                t.getCreatedAt(), t.getResolvedAt());
    }

    static String attachmentUrl(SupportTicket t) {
        return t.getAttachmentFileId() == null ? null : "/api/v1/files/" + t.getAttachmentFileId() + "/content";
    }
}
