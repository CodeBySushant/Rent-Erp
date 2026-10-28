package com.renterp.common.mail;

import com.renterp.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Email is off without MAIL_HOST, and MAIL_FROM is required once it is on. */
class MailGatewayTest {

    @Test
    void noHostMeansNotConfigured() {
        MailGateway m = new MailGateway(" ", 587, "", "", "", true, false);
        assertFalse(m.isConfigured());
        assertFalse(m.sendQuietly("a@b.co", "s", "t"));
        ApiException e = assertThrows(ApiException.class, () -> m.send("a@b.co", "s", "t"));
        assertEquals("EMAIL_NOT_CONFIGURED", e.getCode());
    }

    @Test
    void hostWithoutFromIsRefusedAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new MailGateway("smtp.example.com", 587, "u", "p", " ", true, false));
    }

    @Test
    void hostAndFromConfigure() {
        assertTrue(new MailGateway("smtp.example.com", 587, "u", "p", "Rentlo <no-reply@example.com>", true, false)
                .isConfigured());
    }
}
