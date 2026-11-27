package com.renterp.domain.auth.service;

import com.renterp.common.mail.MailGateway;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.otp.OtpSender;
import com.renterp.domain.auth.repository.OtpAttemptRepository;
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

/** The development-only fixed OTP: works in local, is refused everywhere else. */
class OtpServiceFixedCodeTest {

    private final OtpAttemptRepository repository = mock(OtpAttemptRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final OtpSender sender = mock(OtpSender.class);
    private final MailGateway mail = mock(MailGateway.class);

    private OtpService service(String env, String fixed) {
        when(repository.save(any(OtpAttempt.class))).thenAnswer(i -> i.getArgument(0));
        return new OtpService(repository, encoder, sender, mail, 300, 45, 5, 5, 15, env, fixed);
    }

    private String sentCode(OtpService s) {
        s.issue("9800000000", OtpAttempt.Purpose.LOGIN, null);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("9800000000"), code.capture(), any());
        return code.getValue();
    }

    @Test
    void localWithFixedCodeAlwaysSendsThatCode() {
        assertEquals("123456", sentCode(service("local", "123456")));
    }

    @Test
    void noFixedCodeMeansRandomSixDigits() {
        String code = sentCode(service("local", ""));
        assertEquals(6, code.length());
        assertNotEquals("not-a-fixed-value", code);
    }

    @Test
    void emailedCodeIsRandomEvenWithFixedCode() {
        when(mail.isConfigured()).thenReturn(true);
        OtpService s = service("local", "123456");
        s.issue("sita@example.com", OtpAttempt.Purpose.RESET_PASSWORD, null);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("sita@example.com"), anyString(), body.capture());
        assertFalse(body.getValue().contains("123456"));
        assertTrue(body.getValue().matches("(?s).*\\d{6}.*"));
        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void fixedCodeIsRefusedOutsideLocal() {
        assertThrows(IllegalStateException.class, () -> service("production", "123456"));
        assertThrows(IllegalStateException.class, () -> service("staging", "123456"));
    }

    @Test
    void fixedCodeMustBeSixDigits() {
        assertThrows(IllegalStateException.class, () -> service("local", "12345"));
        assertThrows(IllegalStateException.class, () -> service("local", "abcdef"));
    }
}
