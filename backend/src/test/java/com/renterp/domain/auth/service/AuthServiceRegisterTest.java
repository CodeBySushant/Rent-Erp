package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.domain.auth.dto.AuthResponse;
import com.renterp.domain.auth.dto.RegisterRequest;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Sign-up with no phone OTP at all (branch temp/signup-no-otp). */
class AuthServiceRegisterTest {

    private UserRepository users;
    private AuthTokenService tokens;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(AuthTokenService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        service = new AuthService(users, encoder, tokens, mock(LoginAttemptService.class),
                mock(RateLimiter.class));
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static RegisterRequest request() {
        RegisterRequest r = new RegisterRequest();
        r.setName("Sita Sharma");
        r.setPhone("9812345678");
        r.setEmail("Sita@Example.com");
        r.setPassword("abc12345");
        r.setRole(UserRole.TENANT);
        return r;
    }

    @Test
    void registersWithoutAnyPhoneVerification() {
        AuthResponse session = mock(AuthResponse.class);
        when(tokens.openSession(any(User.class), eq("ua"))).thenReturn(session);

        assertSame(session, service.register(request(), "ua"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertEquals("9812345678", saved.getValue().getPhone());
        assertEquals("sita@example.com", saved.getValue().getEmail());
        assertEquals("hash:abc12345", saved.getValue().getPasswordHash());
        assertNull(saved.getValue().getPhoneVerifiedAt(), "the number was not verified");
    }

    @Test
    void aTakenPhoneIsStillRejected() {
        when(users.existsByPhone("9812345678")).thenReturn(true);
        ApiException e = assertThrows(ApiException.class, () -> service.register(request(), "ua"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("PHONE_TAKEN", e.getCode());
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void aTakenEmailIsStillRejected() {
        when(users.existsByEmailIgnoreCase("sita@example.com")).thenReturn(true);
        ApiException e = assertThrows(ApiException.class, () -> service.register(request(), "ua"));
        assertEquals("EMAIL_TAKEN", e.getCode());
        verify(users, never()).saveAndFlush(any(User.class));
    }
}
