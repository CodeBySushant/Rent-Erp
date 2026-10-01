package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.dto.VerificationTokenResponse;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.otp.OtpSender;
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
 * One-time codes: issue, check, and the single-use verification token that
 * proves a phone was verified before sign-up.
 *
 * Limits (application.yml, app.auth.otp.*): a code lives 5 minutes; a new code
 * for the same phone + purpose can be requested after 45 seconds; at most 5
 * codes per phone + purpose per hour; 5 wrong tries burn the code. Codes are
 * stored as BCrypt hashes and only ever leave the server through the
 * {@link OtpSender}.
 */
@Service
public class OtpService {

    private static final Logger log = LogManager.getLogger(OtpService.class);
    private static final int CODE_LENGTH = 6;

    private final OtpAttemptRepository repository;
    private final PasswordEncoder encoder;
    private final OtpSender sender;
    private final Duration ttl;
    private final Duration resendAfter;
    private final int maxSendsPerHour;
    private final int maxAttempts;
    private final Duration verificationTokenTtl;

    public OtpService(OtpAttemptRepository repository, PasswordEncoder encoder, OtpSender sender,
                      @Value("${app.auth.otp.ttl-seconds:300}") long ttlSeconds,
                      @Value("${app.auth.otp.resend-after-seconds:45}") long resendAfterSeconds,
                      @Value("${app.auth.otp.max-sends-per-hour:5}") int maxSendsPerHour,
                      @Value("${app.auth.otp.max-attempts:5}") int maxAttempts,
                      @Value("${app.auth.otp.verification-token-minutes:15}") long verificationTokenMinutes) {
        this.repository = repository;
        this.encoder = encoder;
        this.sender = sender;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.resendAfter = Duration.ofSeconds(resendAfterSeconds);
        this.maxSendsPerHour = maxSendsPerHour;
        this.maxAttempts = maxAttempts;
        this.verificationTokenTtl = Duration.ofMinutes(verificationTokenMinutes);
    }

    /**
     * Issues and sends a code. If sending fails the transaction rolls back, so
     * an unsent code never counts against the limits.
     */
    @Transactional
    public OtpChallengeResponse issue(String phone, OtpAttempt.Purpose purpose, UUID userId) {
        Instant now = Instant.now();

        repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose).ifPresent(last -> {
            Instant allowedAt = last.getCreatedAt().plus(resendAfter);
            if (allowedAt.isAfter(now)) {
                long wait = Math.max(1, Duration.between(now, allowedAt).toSeconds());
                throw ApiException.tooManyRequests("OTP_RESEND_TOO_SOON",
                        "Please wait " + wait + " seconds before requesting a new code.");
            }
        });
        if (repository.countByPhoneAndPurposeAndCreatedAtAfter(phone, purpose, now.minus(Duration.ofHours(1)))
                >= maxSendsPerHour) {
            throw ApiException.tooManyRequests("OTP_RATE_LIMITED",
                    "Too many codes requested for this number. Try again in an hour.");
        }

        String code = TokenHasher.randomDigits(CODE_LENGTH);
        OtpAttempt attempt = repository.save(OtpAttempt.builder()
                .phone(phone)
                .codeHash(encoder.encode(code))
                .expiresAt(now.plus(ttl))
                .purpose(purpose)
                .userId(userId)
                .build());
        sender.send(phone, code, purpose);
        log.info("OTP issued - purpose: {}, challenge: {}", purpose, attempt.getId());
        return new OtpChallengeResponse(attempt.getId(), ttl.toSeconds(), resendAfter.toSeconds());
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

    /**
     * Sign-up, after {@link #check} passed: attaches a single-use verification
     * token to the challenge and returns it. Called separately from check (not
     * from inside it) so check keeps its own transaction.
     */
    @Transactional
    public VerificationTokenResponse issueVerificationToken(UUID verificationId) {
        OtpAttempt o = repository.findById(verificationId).orElseThrow(OtpService::expired);
        if (o.getPurpose() != OtpAttempt.Purpose.SIGNUP || !o.isUsed() || o.getVerificationTokenHash() != null) {
            throw expired();
        }
        String token = TokenHasher.randomToken(32);
        o.setVerifiedAt(Instant.now());
        o.setVerificationTokenHash(TokenHasher.sha256Hex(token));
        repository.save(o);
        return new VerificationTokenResponse(token, verificationTokenTtl.toSeconds());
    }

    /**
     * Spends a sign-up verification token for {@code phone}. Must run inside
     * the registration transaction, so a failed registration does not burn it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeSignupToken(String token, String phone) {
        OtpAttempt o = repository.findByVerificationTokenHash(TokenHasher.sha256Hex(token))
                .orElseThrow(() -> ApiException.gone("VERIFICATION_INVALID",
                        "Your phone verification is not valid. Verify your number again."));
        if (!o.getPhone().equals(phone)) {
            throw ApiException.badRequest("VERIFICATION_PHONE_MISMATCH",
                    "This verification was for a different phone number.");
        }
        if (o.getTokenUsedAt() != null || o.getVerifiedAt() == null
                || o.getVerifiedAt().plus(verificationTokenTtl).isBefore(Instant.now())) {
            throw ApiException.gone("VERIFICATION_EXPIRED",
                    "Your verification expired. Verify your number again.");
        }
        o.setTokenUsedAt(Instant.now());
        repository.save(o);
    }

    private static ApiException expired() {
        return ApiException.gone("OTP_EXPIRED", "This code has expired. Request a new one.");
    }

    private static ApiException tooManyAttempts() {
        return ApiException.tooManyRequests("OTP_TOO_MANY_ATTEMPTS",
                "Too many wrong codes. Request a new one.");
    }
}
