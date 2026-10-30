package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.mail.MailGateway;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.repository.OtpAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Codes go by email only (no SMS on branch temp/signup-no-otp); the log stands in locally. */
class OtpServiceDeliveryTest {

    private final OtpAttemptRepository repository = mock(OtpAttemptRepository.class);
    private final MailGateway mail = mock(MailGateway.class);

    private OtpService service(String env) {
        when(repository.save(any(OtpAttempt.class))).thenAnswer(i -> i.getArgument(0));
        return new OtpService(repository, mock(PasswordEncoder.class), mail, 300, 45, 5, 5, env, "");
    }

    private OtpService service;

    @BeforeEach
    void setUp() {
        service = service("local");
    }

    @Test
    void codeGoesByEmailWhenConfigured() {
        when(mail.isConfigured()).thenReturn(true);
        service.issue("sita@example.com", OtpAttempt.Purpose.RESET_PASSWORD, null);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("sita@example.com"), eq("Your Rentlo password reset code"), body.capture());
        assertTrue(body.getValue().contains("set a new password"));
        assertTrue(body.getValue().contains("5 minutes"));
    }

    @Test
    void withoutMailLocalLogsInsteadOfSending() {
        when(mail.isConfigured()).thenReturn(false);
        service.issue("sita@example.com", OtpAttempt.Purpose.RESET_PASSWORD, null);
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void withoutMailProductionRefuses() {
        when(mail.isConfigured()).thenReturn(false);
        ApiException e = assertThrows(ApiException.class, () -> service("production")
                .issue("sita@example.com", OtpAttempt.Purpose.RESET_PASSWORD, null));
        assertEquals("EMAIL_NOT_CONFIGURED", e.getCode());
    }

    @Test
    void phoneNumbersGetNoCode() {
        when(mail.isConfigured()).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.issue("9800000000", OtpAttempt.Purpose.RESET_PASSWORD, null));
        assertFalse(service.canDeliver("9800000000"));
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void canDeliverFollowsMailOrLocal() {
        when(mail.isConfigured()).thenReturn(true);
        assertTrue(service("production").canDeliver("a@b.co"));
        when(mail.isConfigured()).thenReturn(false);
        assertFalse(service("production").canDeliver("a@b.co"));
        assertTrue(service("local").canDeliver("a@b.co"));
    }

    @Test
    void decoyLooksLikeARealChallenge() {
        OtpChallengeResponse a = service.decoyChallenge();
        OtpChallengeResponse b = service.decoyChallenge();
        assertEquals(300, a.expiresInSeconds());
        assertEquals(45, a.resendAfterSeconds());
        assertNotEquals(a.verificationId(), b.verificationId());
    }
}
