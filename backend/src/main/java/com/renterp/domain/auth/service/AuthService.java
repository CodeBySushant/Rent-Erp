package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.common.web.ClientIp;
import com.renterp.domain.auth.dto.AuthResponse;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.dto.PasswordLoginRequest;
import com.renterp.domain.auth.dto.RegisterRequest;
import com.renterp.domain.auth.dto.RequestOtpRequest;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.dto.VerificationTokenResponse;
import com.renterp.domain.auth.dto.VerifyOtpRequest;
import com.renterp.domain.auth.entity.OtpAttempt;
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
 *   sign-up : register                                          (tokens returned)
 *             The phone is NOT verified on this branch: there is no SMS
 *             provider yet, so sign-up skips otp/request + otp/verify and
 *             the account starts with phone_verified_at = null.
 *   login   : otp/request (LOGIN)  -> login                     (tokens returned)
 *             or login/password (email + password)
 *   session : refresh (rotates the refresh token), logout, me
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
    private final OtpService otpService;
    private final AuthTokenService tokenService;
    private final LoginAttemptService loginAttempts;
    private final RateLimiter rateLimiter;

    // Compared against when the email is unknown, so an unknown email takes as
    // long as a wrong password and the response time does not reveal accounts.
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, OtpService otpService,
                       AuthTokenService tokenService, LoginAttemptService loginAttempts, RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-0");
    }

    // ── OTP ────────────────────────────────────────────────────────────────────

    public OtpChallengeResponse requestOtp(RequestOtpRequest request) {
        // Per caller address, on top of the per-phone limits in OtpService (H11):
        // stops one client from sending codes to many numbers (SMS pumping).
        rateLimiter.hit("otp-ip:" + ClientIp.current(), 20, Duration.ofHours(1),
                "OTP_RATE_LIMITED", "Too many codes requested from this device. Try again later.");
        String phone = request.getPhone();
        if (request.getPurpose() == RequestOtpRequest.Purpose.SIGNUP) {
            if (userRepository.existsByPhone(phone)) {
                throw ApiException.conflict("PHONE_TAKEN", "This number is already registered. Log in instead.");
            }
            return otpService.issue(phone, OtpAttempt.Purpose.SIGNUP, null);
        }
        if (userRepository.findByPhoneAndActiveTrue(phone).isEmpty()) {
            throw ApiException.notFound("ACCOUNT_NOT_FOUND", "No account with this number. Sign up instead.");
        }
        return otpService.issue(phone, OtpAttempt.Purpose.LOGIN, null);
    }

    public VerificationTokenResponse verifySignupOtp(VerifyOtpRequest request) {
        limitCodeChecks();
        OtpAttempt checked = otpService.check(request.getVerificationId(), request.getCode(),
                OtpAttempt.Purpose.SIGNUP);
        return otpService.issueVerificationToken(checked.getId());
    }

    // ── Register ───────────────────────────────────────────────────────────────

    /**
     * Creates the account and logs it in, in one transaction: a failure
     * leaves no account. No phone OTP is required (see the class comment);
     * any verificationToken in the request is ignored.
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

    public AuthResponse loginWithOtp(VerifyOtpRequest request, String userAgent) {
        limitCodeChecks();
        OtpAttempt checked = otpService.check(request.getVerificationId(), request.getCode(),
                OtpAttempt.Purpose.LOGIN);
        User user = userRepository.findByPhoneAndActiveTrue(checked.getPhone())
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND",
                        "No account with this number. Sign up instead."));
        loginAttempts.recordSuccess(user.getId());
        return tokenService.openSession(user, userAgent);
    }

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
                            + " minutes, or log in with your phone number.");
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

    /** H11: wrong-code guessing across many challenges from one address. */
    private void limitCodeChecks() {
        rateLimiter.hit("otp-check-ip:" + ClientIp.current(), 30, Duration.ofMinutes(15),
                "OTP_RATE_LIMITED", "Too many code attempts. Try again in a few minutes.");
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Incorrect email or password.");
    }
}
