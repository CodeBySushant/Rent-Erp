package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.domain.auth.dto.AuthResponse;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.dto.PasswordResetRequests;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.repository.UserSessionRepository;
import com.renterp.domain.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Forgotten password: who gets a code, and what a valid code changes. */
class PasswordResetServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID challenge = UUID.randomUUID();

    private UserRepository users;
    private OtpService otp;
    private PasswordEncoder encoder;
    private UserSessionRepository sessions;
    private DeviceTokenRepository deviceTokens;
    private AuthTokenService tokens;
    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        otp = mock(OtpService.class);
        encoder = mock(PasswordEncoder.class);
        sessions = mock(UserSessionRepository.class);
        deviceTokens = mock(DeviceTokenRepository.class);
        tokens = mock(AuthTokenService.class);
        service = new PasswordResetService(users, otp, encoder, sessions, deviceTokens, tokens,
                mock(RateLimiter.class));
        user = User.builder().id(userId).phone("9800000001").email("sita@example.com")
                .role(UserRole.TENANT).passwordHash("old").failedLoginAttempts(3)
                .loginLockedUntil(Instant.now().plusSeconds(600)).build();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(encoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(otp.canDeliver(anyString())).thenReturn(true);
    }

    private static PasswordResetRequests.Forgot forgot(String email, String phone) {
        PasswordResetRequests.Forgot f = new PasswordResetRequests.Forgot();
        f.setEmail(email);
        f.setPhone(phone);
        return f;
    }

    private PasswordResetRequests.Reset reset(String password) {
        PasswordResetRequests.Reset r = new PasswordResetRequests.Reset();
        r.setVerificationId(challenge);
        r.setCode("123456");
        r.setNewPassword(password);
        return r;
    }

    private void codeChecksOut(String destination) {
        when(otp.check(challenge, "123456", OtpAttempt.Purpose.RESET_PASSWORD)).thenReturn(OtpAttempt.builder()
                .id(challenge).phone(destination).userId(userId)
                .purpose(OtpAttempt.Purpose.RESET_PASSWORD).used(true).build());
    }

    @Test
    void knownEmailGetsARealCode() {
        when(users.findByEmailIgnoreCase("sita@example.com")).thenReturn(Optional.of(user));
        OtpChallengeResponse real = new OtpChallengeResponse(UUID.randomUUID(), 300, 45);
        when(otp.issue("sita@example.com", OtpAttempt.Purpose.RESET_PASSWORD, userId)).thenReturn(real);
        assertSame(real, service.requestCode(forgot(" Sita@Example.com ", null)));
    }

    @Test
    void knownPhoneGetsARealCode() {
        when(users.findByPhoneAndActiveTrue("9800000001")).thenReturn(Optional.of(user));
        service.requestCode(forgot(null, "9800000001"));
        verify(otp).issue("9800000001", OtpAttempt.Purpose.RESET_PASSWORD, userId);
    }

    @Test
    void unknownOrInactiveAccountGetsADecoyAndNoCode() {
        OtpChallengeResponse decoy = new OtpChallengeResponse(UUID.randomUUID(), 300, 45);
        when(otp.decoyChallenge()).thenReturn(decoy);
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
        assertSame(decoy, service.requestCode(forgot("nobody@example.com", null)));

        user.setActive(false);
        when(users.findByEmailIgnoreCase("sita@example.com")).thenReturn(Optional.of(user));
        assertSame(decoy, service.requestCode(forgot("sita@example.com", null)));
        verify(otp, never()).issue(anyString(), any(), any());
    }

    @Test
    void exactlyOneAddressIsRequired() {
        ApiException none = assertThrows(ApiException.class, () -> service.requestCode(forgot(null, " ")));
        assertEquals("IDENTIFIER_REQUIRED", none.getCode());
        ApiException both = assertThrows(ApiException.class,
                () -> service.requestCode(forgot("sita@example.com", "9800000001")));
        assertEquals("IDENTIFIER_REQUIRED", both.getCode());
    }

    @Test
    void undeliverableIsRefusedBeforeAnyLookup() {
        when(otp.canDeliver("sita@example.com")).thenReturn(false);
        ApiException e = assertThrows(ApiException.class, () -> service.requestCode(forgot("sita@example.com", null)));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertEquals("EMAIL_NOT_CONFIGURED", e.getCode());
        verify(users, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void validCodeSetsPasswordEndsSessionsAndLogsIn() {
        codeChecksOut("sita@example.com");
        AuthResponse session = new AuthResponse(null, "access", "refresh", "Bearer", 900, 2_592_000);
        when(tokens.openSession(user, "ua")).thenReturn(session);

        assertSame(session, service.reset(reset("newpass123"), "ua"));
        assertEquals("hash:newpass123", user.getPasswordHash());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLoginLockedUntil());
        assertNotNull(user.getEmailVerifiedAt());
        verify(sessions).revokeAllByUserId(eq(userId), any(Instant.class));
        verify(deviceTokens).deleteByUserId(userId);
    }

    @Test
    void weakPasswordIsRefusedWithoutSpendingTheCode() {
        ApiException e = assertThrows(ApiException.class, () -> service.reset(reset("short"), "ua"));
        assertEquals("WEAK_PASSWORD", e.getCode());
        verify(otp, never()).check(any(), anyString(), any());
        assertEquals("old", user.getPasswordHash());
    }

    @Test
    void codeForAnAddressNoLongerTheirsIsRefused() {
        codeChecksOut("old@example.com");
        ApiException e = assertThrows(ApiException.class, () -> service.reset(reset("newpass123"), "ua"));
        assertEquals("OTP_EXPIRED", e.getCode());
        assertEquals("old", user.getPasswordHash());
        verify(tokens, never()).openSession(any(), any());
    }
}
