package com.renterp.domain.auth.service;

import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.otp.OtpSender;
import com.renterp.domain.auth.repository.OtpAttemptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The development-only fixed OTP: works in local, is refused everywhere else. */
class OtpServiceFixedCodeTest {

    private final OtpAttemptRepository repository = mock(OtpAttemptRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final OtpSender sender = mock(OtpSender.class);

    private OtpService service(String env, String fixed) {
        when(repository.save(any(OtpAttempt.class))).thenAnswer(i -> i.getArgument(0));
        return new OtpService(repository, encoder, sender, 300, 45, 5, 5, 15, env, fixed);
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
