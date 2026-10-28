package com.renterp.domain.auth.service;

import com.renterp.common.mail.MailGateway;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.otp.OtpSender;
import com.renterp.domain.auth.repository.OtpAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Where a code goes: email addresses by email when it is configured, the rest to the OtpSender. */
class OtpServiceDeliveryTest {

    private final OtpAttemptRepository repository = mock(OtpAttemptRepository.class);
    private final OtpSender sender = mock(OtpSender.class);
    private final MailGateway mail = mock(MailGateway.class);
    private OtpService service;

    @BeforeEach
    void setUp() {
        when(repository.save(any(OtpAttempt.class))).thenAnswer(i -> i.getArgument(0));
        service = new OtpService(repository, mock(PasswordEncoder.class), sender, mail,
                300, 45, 5, 5, 15, "local", "");
    }

    @Test
    void emailCodeGoesByEmailWhenConfigured() {
        when(mail.isConfigured()).thenReturn(true);
        service.issue("sita@example.com", OtpAttempt.Purpose.RESET_PASSWORD, null);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("sita@example.com"), eq("Your Rentlo password reset code"), body.capture());
        assertTrue(body.getValue().contains("set a new password"));
        assertTrue(body.getValue().contains("5 minutes"));
        verify(sender, never()).send(anyString(), anyString(), any());
    }

    @Test
    void emailCodeFallsBackToSenderWithoutMail() {
        when(mail.isConfigured()).thenReturn(false);
        service.issue("sita@example.com", OtpAttempt.Purpose.CHANGE_EMAIL, null);
        verify(sender).send(eq("sita@example.com"), anyString(), eq(OtpAttempt.Purpose.CHANGE_EMAIL));
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void phoneCodeNeverGoesByEmail() {
        when(mail.isConfigured()).thenReturn(true);
        service.issue("9800000000", OtpAttempt.Purpose.RESET_PASSWORD, null);
        verify(sender).send(eq("9800000000"), anyString(), eq(OtpAttempt.Purpose.RESET_PASSWORD));
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void canDeliverFollowsMailAndSender() {
        when(sender.available()).thenReturn(false);
        when(mail.isConfigured()).thenReturn(true);
        assertTrue(service.canDeliver("a@b.co"));
        assertFalse(service.canDeliver("9800000000"));
        when(mail.isConfigured()).thenReturn(false);
        assertFalse(service.canDeliver("a@b.co"));
        when(sender.available()).thenReturn(true);
        assertTrue(service.canDeliver("a@b.co"));
        assertTrue(service.canDeliver("9800000000"));
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
