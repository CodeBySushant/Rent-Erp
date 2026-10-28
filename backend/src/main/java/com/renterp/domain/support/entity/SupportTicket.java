package com.renterp.domain.support.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/** A message to Rentlo support from Contact Support (V29). */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Topic topic;

    @Column(nullable = false, length = 4000, updatable = false)
    private String message;

    @Column(name = "attachment_file_id", columnDefinition = "uuid", updatable = false)
    private UUID attachmentFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "resolved_by", columnDefinition = "uuid")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** The Contact Support subjects (the Help Center's topics). */
    public enum Topic { ACCOUNT, PROPERTY, BILLING, TENANTS, OTHER }

    public enum Status { OPEN, RESOLVED }

    /** What the user quotes: "T-" and the first 8 characters of the id. */
    public String reference() {
        return reference(id);
    }

    public static String reference(UUID id) {
        return id == null ? null : "T-" + id.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}
