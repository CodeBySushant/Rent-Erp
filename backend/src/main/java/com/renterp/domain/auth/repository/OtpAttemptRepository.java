package com.renterp.domain.auth.repository;

import com.renterp.domain.auth.entity.OtpAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpAttemptRepository extends JpaRepository<OtpAttempt, UUID> {

    // Fetch all unexpired, unused OTPs for a phone — used to validate submitted code
    List<OtpAttempt> findByPhoneAndUsedFalseAndExpiresAtAfter(String phone, Instant now);

    // Count recent unused OTPs — used for rate limiting (max N sends per hour)
    long countByPhoneAndUsedFalseAndCreatedAtAfter(String phone, Instant since);

    // ── V13: purpose-scoped rate limits and look-ups ──────────────────────────

    // Every send for this phone + purpose since a point in time (used or not) -
    // the hourly send cap counts all of them.
    long countByPhoneAndPurposeAndCreatedAtAfter(String phone, OtpAttempt.Purpose purpose, Instant since);

    // The most recent send, for the resend interval.
    Optional<OtpAttempt> findFirstByPhoneAndPurposeOrderByCreatedAtDesc(String phone, OtpAttempt.Purpose purpose);

    Optional<OtpAttempt> findByVerificationTokenHash(String verificationTokenHash);

    // Row-locked read for checking a code, so two concurrent verifies of the
    // same challenge cannot both pass or both miscount attempts.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OtpAttempt o where o.id = :id")
    Optional<OtpAttempt> findByIdForUpdate(@Param("id") UUID id);
}
