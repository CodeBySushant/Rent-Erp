package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.common.web.ClientIp;
import com.renterp.domain.auth.dto.AuthResponse;
import com.renterp.domain.auth.dto.PasswordLoginRequest;
import com.renterp.domain.auth.dto.RegisterRequest;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AuthUser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Sign-up and login, as the app's auth screens use them:
 *
 *   sign-up : register                     (tokens returned)
 *   login   : login/password (email + password, tokens returned)
 *   session : refresh (rotates the refresh token), logout, me
 *
 * Branch temp/signup-no-otp has no phone OTP at all (no SMS provider yet):
 * the phone is stored but never verified (phone_verified_at stays null).
 *
 * The role an account gets comes from sign-up (LANDLORD or TENANT) and is
 * returned with the user, which is what the app uses to open the owner or
 * tenant side.
 */
@Service
public class AuthService {

    private static final Logger log = LogManager.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokenService;
    private final LoginAttemptService loginAttempts;
    private final RateLimiter rateLimiter;

    // Compared against when the email is unknown, so an unknown email takes as
    // long as a wrong password and the response time does not reveal accounts.
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthTokenService tokenService, LoginAttemptService loginAttempts, RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-0");
    }

    // ── Register ───────────────────────────────────────────────────────────────

    /**
     * Creates the account and logs it in, in one transaction: a failure
     * leaves no account. No phone verification (see the class comment).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent) {
        rateLimiter.hit("register-ip:" + ClientIp.current(), 10, Duration.ofHours(1),
                "REGISTER_RATE_LIMITED", "Too many sign-ups from this device. Try again later.");
        String problem = PasswordPolicy.problem(request.getPassword());
        if (problem != null) {
            throw ApiException.badRequest("PASSWORD_WEAK", problem);
        }
        if (request.getRole() != UserRole.LANDLORD && request.getRole() != UserRole.TENANT) {
            throw ApiException.badRequest("ROLE_NOT_ALLOWED", "Register as LANDLORD or TENANT.");
        }
        String email = normaliseEmail(request.getEmail());
        if (userRepository.existsByPhone(request.getPhone())) {
            throw ApiException.conflict("PHONE_TAKEN", "This number is already registered. Log in instead.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }

        User user = userRepository.saveAndFlush(User.builder()
                .phone(request.getPhone())
                .name(request.getName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .preferredLanguage(request.getPreferredLanguage() == null ? "en" : request.getPreferredLanguage())
                .build()); // phoneVerifiedAt stays null: the number was not verified
        log.info("Account registered - id: {}, role: {}", user.getId(), user.getRole());
        return tokenService.openSession(user, userAgent);
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    public AuthResponse loginWithPassword(PasswordLoginRequest request, String userAgent) {
        String email = normaliseEmail(request.getEmail());
        // H11: per address and per email, before any password is checked, so
        // guessing is slow even across many accounts.
        rateLimiter.hit("login-ip:" + ClientIp.current(), 30, Duration.ofMinutes(15),
                "LOGIN_RATE_LIMITED", "Too many login attempts. Try again in a few minutes.");
        rateLimiter.hit("login-email:" + email, 15, Duration.ofMinutes(15),
                "LOGIN_RATE_LIMITED", "Too many login attempts. Try again in a few minutes.");
        // L2: BCrypt cannot take more than 72 bytes; no stored password is longer.
        if (PasswordPolicy.tooLongForBcrypt(request.getPassword())) {
            throw invalidCredentials();
        }
        User user = userRepository.findByEmailIgnoreCase(email).filter(User::isActive).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.getPassword(), dummyHash);
            throw invalidCredentials();
        }
        Instant lockedUntil = user.getLoginLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            long minutes = Math.max(1, Duration.between(Instant.now(), lockedUntil).toMinutes() + 1);
            throw ApiException.tooManyRequests("LOGIN_LOCKED",
                    "Too many wrong passwords. Try again in " + minutes
                            + " minutes, or reset your password.");
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttempts.recordFailure(user.getId());
            throw invalidCredentials();
        }
        loginAttempts.recordSuccess(user.getId());
        return tokenService.openSession(user, userAgent);
    }

    // ── Session ────────────────────────────────────────────────────────────────

    public AuthResponse refresh(String refreshToken) {
        return tokenService.rotate(refreshToken);
    }

    public void logout(AuthUser caller) {
        tokenService.revoke(caller.sessionId(), caller.userId());
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthUser caller) {
        User user = userRepository.findById(caller.userId())
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("ACCOUNT_DISABLED",
                        "This account is no longer active."));
        return UserResponse.from(user);
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Incorrect email or password.");
    }
}
