package com.renterp.domain.notification.service;

import com.renterp.domain.notification.entity.Notification.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M12: notification text follows the recipient's language; values are untouched. */
class NotificationTextTest {

    @Test
    void englishIsUnchanged() {
        assertEquals("Your bill is ready", NotificationText.title(Type.BILL_ISSUED, "en"));
        assertEquals("2082-06 · Rs 12,000 · due 2082-06-10",
                NotificationText.body("2082-06 · Rs 12,000 · due 2082-06-10", "en"));
    }

    @Test
    void nepaliTranslatesTitleAndFixedWordsOnly() {
        assertEquals("तपाईंको बिल तयार छ", NotificationText.title(Type.BILL_ISSUED, "ne"));
        String body = NotificationText.body("2082-06 · Rs 12,000 · due 2082-06-10", "ne");
        assertEquals("2082-06 · Rs 12,000 · म्याद 2082-06-10", body);
    }

    @Test
    void hindiTranslatesRequestLabelsAndStatus() {
        String body = NotificationText.body("Maintenance · Leaking tap · approved", "hi");
        assertEquals("मरम्मत · Leaking tap · स्वीकृत", body);
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        assertTrue(NotificationText.title(Type.PAYMENT_APPROVED, "fr").startsWith("Payment"));
    }
}
