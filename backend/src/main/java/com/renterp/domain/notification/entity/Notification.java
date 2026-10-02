package com.renterp.domain.notification.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/** One notification for one user (V23). */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private Type type;

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(length = 500, updatable = false)
    private String body;

    @Column(name = "property_id", columnDefinition = "uuid", updatable = false)
    private UUID propertyId;

    @Column(name = "entity_type", length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", columnDefinition = "uuid", updatable = false)
    private UUID entityId;

    @Column(name = "read_at")
    private Instant readAt;

    /** What happened. The app shows a translated title per type. */
    public enum Type {
        BILL_ISSUED("Your bill is ready"),
        PAYMENT_RECORDED("Payment recorded"),
        PAYMENT_PROOF_WAITING("Payment waiting for approval"),
        PAYMENT_APPROVED("Payment approved"),
        PAYMENT_REJECTED("Payment rejected"),
        READING_SUBMITTED("Meter reading submitted"),
        REQUEST_CREATED("New request"),
        REQUEST_DECIDED("Request updated"),
        JOIN_REQUESTED("New join request"),
        JOIN_ACCEPTED("Join request accepted"),
        JOIN_REJECTED("Join request rejected"),
        MOVE_OUT_NOTICE("Move-out notice"),
        MOVE_OUT_SETTLED("Move-out settled");

        public final String title;

        Type(String title) {
            this.title = title;
        }
    }
}
