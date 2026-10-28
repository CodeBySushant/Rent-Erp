package com.renterp.domain.notification.dto;

import com.renterp.domain.notification.entity.NotificationPreferences;

/**
 * GET / PUT /api/v1/me/notification-preferences. On PUT every field is
 * optional: a missing (null) field keeps its current value.
 */
public record NotificationPreferencesDto(
        Boolean pushRentReminders,
        Boolean pushPaymentReceived,
        Boolean pushPaymentOverdue,
        Boolean pushNewTenant,
        Boolean pushMaintenance,
        Boolean pushGeneralUpdates,
        Boolean emailMonthlyStatements,
        Boolean emailPaymentReceipts,
        Boolean emailImportantAlerts,
        Boolean emailMarketing) {

    public static NotificationPreferencesDto from(NotificationPreferences p) {
        return new NotificationPreferencesDto(p.isPushRentReminders(), p.isPushPaymentReceived(),
                p.isPushPaymentOverdue(), p.isPushNewTenant(), p.isPushMaintenance(), p.isPushGeneralUpdates(),
                p.isEmailMonthlyStatements(), p.isEmailPaymentReceipts(), p.isEmailImportantAlerts(),
                p.isEmailMarketing());
    }

    /** Copies every non-null field onto [p]. */
    public void applyTo(NotificationPreferences p) {
        if (pushRentReminders != null) p.setPushRentReminders(pushRentReminders);
        if (pushPaymentReceived != null) p.setPushPaymentReceived(pushPaymentReceived);
        if (pushPaymentOverdue != null) p.setPushPaymentOverdue(pushPaymentOverdue);
        if (pushNewTenant != null) p.setPushNewTenant(pushNewTenant);
        if (pushMaintenance != null) p.setPushMaintenance(pushMaintenance);
        if (pushGeneralUpdates != null) p.setPushGeneralUpdates(pushGeneralUpdates);
        if (emailMonthlyStatements != null) p.setEmailMonthlyStatements(emailMonthlyStatements);
        if (emailPaymentReceipts != null) p.setEmailPaymentReceipts(emailPaymentReceipts);
        if (emailImportantAlerts != null) p.setEmailImportantAlerts(emailImportantAlerts);
        if (emailMarketing != null) p.setEmailMarketing(emailMarketing);
    }
}
