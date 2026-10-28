package com.renterp.domain.support.dto;

import com.renterp.domain.support.entity.SupportTicket.Topic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Request bodies for /api/v1/support/tickets and the admin endpoints. */
public final class SupportTicketRequests {

    private SupportTicketRequests() {
    }

    /** POST /support/tickets. */
    @Getter
    @Setter
    public static class Create {
        @NotNull(message = "topic is required (ACCOUNT, PROPERTY, BILLING, TENANTS or OTHER)")
        private Topic topic;

        @NotBlank(message = "Write your message")
        @Size(max = 4000, message = "Keep the message under 4000 characters")
        private String message;

        /** Optional: a SUPPORT_ATTACHMENT upload of the caller (POST /files). */
        private UUID attachmentFileId;
    }

    /** POST /admin/support/tickets/{id}/resolve. */
    @Getter
    @Setter
    public static class Resolve {
        /** Optional reply the user sees with the ticket. */
        @Size(max = 1000, message = "Keep the note under 1000 characters")
        private String note;
    }
}
