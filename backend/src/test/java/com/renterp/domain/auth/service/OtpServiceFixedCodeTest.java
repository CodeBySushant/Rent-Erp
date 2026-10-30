package com.renterp.domain.auth.service;

import com.renterp.common.mail.MailGateway;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.repository.OtpAttemptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The development-only fixed code: works in local, is refused everywhere else. */
class OtpServiceFixedCodeTest {

    private final OtpAttemptRepository repository = mock(OtpAttemptRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MailGateway mail = mock(MailGateway.class);

    private OtpService service(String env, String fixed) {
        when(repository.save(any(OtpAttempt.class))).thenAnswer(i -> i.getArgument(0));
        when(encoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        return new OtpService(repository, encoder, mail, 300, 45, 5, 5, env, fixed);
    }

    /** The code that was hashed for the new challenge. */
    private String issuedCode(OtpService s) {
        s.issue("sita@example.com", OtpAttempt.Purpose.RESET_PASSWORD, null);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(encoder).encode(code.capture());
        return code.getValue();
    }

    @Test
    void localWithFixedCodeAlwaysUsesThatCode() {
        assertEquals("123456", issuedCode(service("local", "123456")));
    }

    @Test
    void noFixedCodeMeansRandomSixDigits() {
        String code = issuedCode(service("local", ""));
        assertTrue(code.matches("\\d{6}"));
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
