package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.mail.MailGateway;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.repository.OtpAttemptRepository;
import com.renterp.domain.auth.security.TokenHasher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Password-reset codes, sent by email only. Branch temp/signup-no-otp has no
 * phone OTP: there is no SMS sender, and sign-up, login and account changes
 * use no codes. The one code left is Forgot Password's, sent to the account's
 * email.
 *
 * Limits (application.yml, app.auth.otp.*): a code lives 5 minutes; a new code
 * for the same address + purpose can be requested after 45 seconds; at most 5
 * codes per address + purpose per hour; 5 wrong tries burn the code. Codes are
 * stored as BCrypt hashes and leave the server only by email
 * ({@link MailGateway}, when MAIL_HOST is set). With APP_ENV=local and no
 * MAIL_HOST, the code is written to the backend log instead, for development.
 *
 * The otp_attempts table keeps its name and columns, so this branch and the
 * OTP branches share one database.
 */
@Service
public class OtpService {

    private static final Logger log = LogManager.getLogger(OtpService.class);
    private static final int CODE_LENGTH = 6;

    private final OtpAttemptRepository repository;
    private final PasswordEncoder encoder;
    private final MailGateway mail;
    private final boolean local;
    private final Duration ttl;
    private final Duration resendAfter;
    private final int maxSendsPerHour;
    private final int maxAttempts;
    /** Non-null only in local development; see the constructor. */
    private final String devFixedCode;

    public OtpService(OtpAttemptRepository repository, PasswordEncoder encoder, MailGateway mail,
                      @Value("${app.auth.otp.ttl-seconds:300}") long ttlSeconds,
                      @Value("${app.auth.otp.resend-after-seconds:45}") long resendAfterSeconds,
                      @Value("${app.auth.otp.max-sends-per-hour:5}") int maxSendsPerHour,
                      @Value("${app.auth.otp.max-attempts:5}") int maxAttempts,
                      @Value("${app.env:production}") String appEnv,
                      @Value("${app.auth.otp.dev-fixed-code:}") String devFixedCode) {
        this.repository = repository;
        this.encoder = encoder;
        this.mail = mail;
        this.local = "local".equals(appEnv);
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.resendAfter = Duration.ofSeconds(resendAfterSeconds);
        this.maxSendsPerHour = maxSendsPerHour;
        this.maxAttempts = maxAttempts;

        // Development shortcut: every code is this fixed value. It only works
        // with APP_ENV=local, and the app refuses to start if it is set
        // anywhere else, so it cannot reach staging/production.
        String fixed = devFixedCode == null ? "" : devFixedCode.trim();
        if (fixed.isEmpty()) {
            this.devFixedCode = null;
        } else {
            if (!local) {
                throw new IllegalStateException(
                        "OTP_DEV_FIXED_CODE is set but APP_ENV is '" + appEnv
                                + "'. A fixed code is only allowed with APP_ENV=local - remove OTP_DEV_FIXED_CODE.");
            }
            if (!fixed.matches("\\d{" + CODE_LENGTH + "}")) {
                throw new IllegalStateException("OTP_DEV_FIXED_CODE must be exactly " + CODE_LENGTH + " digits.");
            }
            this.devFixedCode = fixed;
            log.warn("DEV ONLY: every code is fixed to {} (OTP_DEV_FIXED_CODE). Never use outside local development.", fixed);
        }
    }

    /**
     * Issues and emails a code to {@code email}. If sending fails the
     * transaction rolls back, so an unsent code never counts against the limits.
     */
    @Transactional
    public OtpChallengeResponse issue(String email, OtpAttempt.Purpose purpose, UUID userId) {
        if (!isEmail(email)) {
            throw new IllegalArgumentException("Codes are sent by email only");
        }
        Instant now = Instant.now();

        repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(email, purpose).ifPresent(last -> {
            Instant allowedAt = last.getCreatedAt().plus(resendAfter);
            if (allowedAt.isAfter(now)) {
                long wait = Math.max(1, Duration.between(now, allowedAt).toSeconds());
                throw ApiException.tooManyRequests("OTP_RESEND_TOO_SOON",
                        "Please wait " + wait + " seconds before requesting a new code.");
            }
        });
        if (repository.countByPhoneAndPurposeAndCreatedAtAfter(email, purpose, now.minus(Duration.ofHours(1)))
                >= maxSendsPerHour) {
            throw ApiException.tooManyRequests("OTP_RATE_LIMITED",
                    "Too many codes requested for this address. Try again in an hour.");
        }

        String code = devFixedCode != null ? devFixedCode : TokenHasher.randomDigits(CODE_LENGTH);
        // otp_attempts.phone holds the destination address (an email here).
        OtpAttempt attempt = repository.save(OtpAttempt.builder()
                .phone(email)
                .codeHash(encoder.encode(code))
                .expiresAt(now.plus(ttl))
                .purpose(purpose)
                .userId(userId)
                .build());
        deliver(email, code, purpose);
        log.info("Code issued - purpose: {}, challenge: {}", purpose, attempt.getId());
        return new OtpChallengeResponse(attempt.getId(), ttl.toSeconds(), resendAfter.toSeconds());
    }

    /**
     * True when a code to {@code destination} can be sent right now: it is an
     * email address and email is configured (or this is local development).
     */
    public boolean canDeliver(String destination) {
        return isEmail(destination) && (mail.isConfigured() || local);
    }

    /**
     * A challenge shaped exactly like a real one but tied to no code, for a
     * request about an account that does not exist: the reply does not reveal
     * whether the account exists, and any code typed for it is "expired".
     */
    public OtpChallengeResponse decoyChallenge() {
        return new OtpChallengeResponse(UUID.randomUUID(), ttl.toSeconds(), resendAfter.toSeconds());
    }

    private void deliver(String email, String code, OtpAttempt.Purpose purpose) {
        if (mail.isConfigured()) {
            mail.send(email, emailSubject(purpose), emailBody(code, purpose));
            return;
        }
        if (local) {
            log.warn("DEV email code for {} ({}): {}", email, purpose, code);
            return;
        }
        throw ApiException.unavailable("EMAIL_NOT_CONFIGURED", "Codes cannot be emailed yet. Contact support.");
    }

    private String emailSubject(OtpAttempt.Purpose purpose) {
        return purpose == OtpAttempt.Purpose.RESET_PASSWORD
                ? "Your Rentlo password reset code" : "Your Rentlo code";
    }

    private String emailBody(String code, OtpAttempt.Purpose purpose) {
        String why = purpose == OtpAttempt.Purpose.RESET_PASSWORD
                ? "Use this code in the Rentlo app to set a new password:" : "Your Rentlo code:";
        return why + "\n\n    " + code + "\n\n"
                + "It expires in " + Math.max(1, ttl.toMinutes()) + " minutes. Never share it with anyone.\n"
                + "If you did not ask for this, ignore this email - nothing changes.\n\n"
                + "- Rentlo";
    }

    private static boolean isEmail(String destination) {
        return destination != null && destination.indexOf('@') >= 0;
    }

    /**
     * Checks a code and marks it used. Runs in its own transaction and does not
     * roll back on rejection, so a wrong-code count is saved even though the
     * caller's request fails.
     *
     * 410 OTP_EXPIRED - unknown, expired, already used, or for another purpose;
     * 429 OTP_TOO_MANY_ATTEMPTS - burned after too many wrong codes;
     * 400 OTP_INCORRECT - wrong code, tries left.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public OtpAttempt check(UUID verificationId, String code, OtpAttempt.Purpose expected) {
        Instant now = Instant.now();
        OtpAttempt o = repository.findByIdForUpdate(verificationId)
                .filter(a -> a.getPurpose() == expected)
                .orElseThrow(OtpService::expired);
        if (o.isUsed() || !o.getExpiresAt().isAfter(now)) {
            throw expired();
        }
        if (o.getAttemptCount() >= maxAttempts) {
            o.setUsed(true);
            repository.save(o);
            throw tooManyAttempts();
        }
        if (!encoder.matches(code, o.getCodeHash())) {
            o.setAttemptCount(o.getAttemptCount() + 1);
            if (o.getAttemptCount() >= maxAttempts) {
                o.setUsed(true);
                repository.save(o);
                throw tooManyAttempts();
            }
            repository.save(o);
            throw ApiException.badRequest("OTP_INCORRECT", "That code is incorrect.");
        }
        o.setUsed(true);
        return repository.save(o);
    }

    private static ApiException expired() {
        return ApiException.gone("OTP_EXPIRED", "This code has expired. Request a new one.");
    }

    private static ApiException tooManyAttempts() {
        return ApiException.tooManyRequests("OTP_TOO_MANY_ATTEMPTS",
                "Too many wrong codes. Request a new one.");
    }
}
