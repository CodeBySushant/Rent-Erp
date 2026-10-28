package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.common.web.ClientIp;
import com.renterp.domain.auth.dto.AuthResponse;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.dto.PasswordResetRequests;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.repository.UserSessionRepository;
import com.renterp.domain.notification.repository.DeviceTokenRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Forgotten password, for someone who cannot log in:
 *
 * <pre>
 *   POST /auth/password/forgot {email | phone}  -> a 6-digit code to that address
 *   POST /auth/password/reset  {verificationId, code, newPassword}
 *                                              -> new password, every device signed
 *                                                 out, this device signed in
 * </pre>
 *
 * <ul>
 *   <li>The code goes to the address the person typed, and only when it is the
 *       email or phone of an active account. For an unknown address the reply
 *       looks the same (a decoy challenge), so the endpoint does not reveal
 *       which emails have accounts.</li>
 *   <li>Same code rules as every other OTP (5 minutes, 5 tries, resend limits);
 *       purpose RESET_PASSWORD, so a login or sign-up code never resets a
 *       password and the other way round.</li>
 *   <li>The address must still belong to the account when the code is used.</li>
 *   <li>The new password follows {@link PasswordPolicy}, is checked before the
 *       code is spent, and clears the password-login lockout.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LogManager.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final OtpService otp;
    private final PasswordEncoder encoder;
    private final UserSessionRepository sessions;
    private final DeviceTokenRepository deviceTokens;
    private final AuthTokenService tokens;
    private final RateLimiter rateLimiter;

    public PasswordResetService(UserRepository users, OtpService otp, PasswordEncoder encoder,
                                UserSessionRepository sessions, DeviceTokenRepository deviceTokens,
                                AuthTokenService tokens, RateLimiter rateLimiter) {
        this.users = users;
        this.otp = otp;
        this.encoder = encoder;
        this.sessions = sessions;
        this.deviceTokens = deviceTokens;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Sends a reset code to the account with this email or phone. 400
     * IDENTIFIER_REQUIRED unless exactly one is given; 503 EMAIL_NOT_CONFIGURED
     * / SMS_NOT_CONFIGURED when codes cannot be sent to that kind of address
     * (said before any lookup, so it reveals nothing); 429 from the limits.
     */
    public OtpChallengeResponse requestCode(PasswordResetRequests.Forgot req) {
        rateLimiter.hit("reset-ip:" + ClientIp.current(), 10, Duration.ofHours(1),
                "OTP_RATE_LIMITED", "Too many reset codes requested from this device. Try again later.");
        String email = blankToNull(req.getEmail());
        String phone = blankToNull(req.getPhone());
        if ((email == null) == (phone == null)) {
            throw ApiException.badRequest("IDENTIFIER_REQUIRED", "Enter your email address or your phone number.");
        }
        String destination = email != null ? email.toLowerCase(Locale.ROOT) : phone;
        if (!otp.canDeliver(destination)) {
            throw email != null
                    ? ApiException.unavailable("EMAIL_NOT_CONFIGURED",
                            "Reset codes cannot be emailed yet. Try your phone number, or contact support.")
                    : ApiException.unavailable("SMS_NOT_CONFIGURED",
                            "Reset codes cannot be texted yet. Try your email address, or contact support.");
        }
        Optional<User> account = email != null
                ? users.findByEmailIgnoreCase(destination).filter(User::isActive)
                : users.findByPhoneAndActiveTrue(destination);
        if (account.isEmpty()) {
            log.info("Password reset asked for an unknown {} - decoy returned", email != null ? "email" : "phone");
            return otp.decoyChallenge();
        }
        return otp.issue(destination, OtpAttempt.Purpose.RESET_PASSWORD, account.get().getId());
    }

    /**
     * Checks the code, sets the new password, ends every session of the
     * account and opens one for this device. 400 WEAK_PASSWORD (code not
     * spent), 400 OTP_INCORRECT, 410 OTP_EXPIRED, 429 OTP_TOO_MANY_ATTEMPTS.
     */
    @Transactional
    public AuthResponse reset(PasswordResetRequests.Reset req, String userAgent) {
        rateLimiter.hit("otp-check-ip:" + ClientIp.current(), 30, Duration.ofMinutes(15),
                "OTP_RATE_LIMITED", "Too many code attempts. Try again in a few minutes.");
        // Policy first: a weak password must not use up the code.
        String problem = PasswordPolicy.problem(req.getNewPassword());
        if (problem != null) {
            throw ApiException.badRequest("WEAK_PASSWORD", problem);
        }
        OtpAttempt code = otp.check(req.getVerificationId(), req.getCode(), OtpAttempt.Purpose.RESET_PASSWORD);
        User user = Optional.ofNullable(code.getUserId())
                .flatMap(users::findById)
                .filter(User::isActive)
                .filter(u -> stillTheirs(u, code.getPhone()))
                .orElseThrow(() -> ApiException.gone("OTP_EXPIRED", "This code has expired. Request a new one."));

        Instant now = Instant.now();
        user.setPasswordHash(encoder.encode(req.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        // The code reached this address, so it is proven to be theirs.
        if (isEmail(code.getPhone())) {
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(now);
            }
        } else if (user.getPhoneVerifiedAt() == null) {
            user.setPhoneVerifiedAt(now);
        }
        users.saveAndFlush(user);
        int ended = sessions.revokeAllByUserId(user.getId(), now);
        deviceTokens.deleteByUserId(user.getId());
        log.info("Password reset - user: {}, via: {}, sessions ended: {}",
                user.getId(), isEmail(code.getPhone()) ? "email" : "phone", ended);
        return tokens.openSession(user, userAgent);
    }

    /** The code's address is still this account's email or phone. */
    private static boolean stillTheirs(User u, String destination) {
        if (isEmail(destination)) {
            return u.getEmail() != null && u.getEmail().equalsIgnoreCase(destination);
        }
        return destination.equals(u.getPhone());
    }

    private static boolean isEmail(String destination) {
        return destination != null && destination.indexOf('@') >= 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
