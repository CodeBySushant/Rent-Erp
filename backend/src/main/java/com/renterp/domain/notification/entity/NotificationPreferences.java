package com.renterp.domain.notification.entity;

import com.renterp.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * An account's Notification Setting choices (V31). No row = the defaults
 * ({@link #defaults(UUID)}), which match the columns' defaults.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferences extends BaseAuditEntity {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID userId;

    // ── Push ────────────────────────────────────────────────────────────────
    @Column(name = "push_rent_reminders", nullable = false)
    @Builder.Default
    private boolean pushRentReminders = true;

    @Column(name = "push_payment_received", nullable = false)
    @Builder.Default
    private boolean pushPaymentReceived = true;

    @Column(name = "push_payment_overdue", nullable = false)
    @Builder.Default
    private boolean pushPaymentOverdue = true;

    @Column(name = "push_new_tenant", nullable = false)
    @Builder.Default
    private boolean pushNewTenant = true;

    @Column(name = "push_maintenance", nullable = false)
    @Builder.Default
    private boolean pushMaintenance = false;

    @Column(name = "push_general_updates", nullable = false)
    @Builder.Default
    private boolean pushGeneralUpdates = false;

    // ── Email ───────────────────────────────────────────────────────────────
    @Column(name = "email_monthly_statements", nullable = false)
    @Builder.Default
    private boolean emailMonthlyStatements = true;

    @Column(name = "email_payment_receipts", nullable = false)
    @Builder.Default
    private boolean emailPaymentReceipts = true;

    @Column(name = "email_important_alerts", nullable = false)
    @Builder.Default
    private boolean emailImportantAlerts = true;

    @Column(name = "email_marketing", nullable = false)
    @Builder.Default
    private boolean emailMarketing = false;

    /** The choices of an account that never changed them (not saved). */
    public static NotificationPreferences defaults(UUID userId) {
        return NotificationPreferences.builder().userId(userId).build();
    }
}
