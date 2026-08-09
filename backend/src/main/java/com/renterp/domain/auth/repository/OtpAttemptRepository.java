package com.renterp.domain.auth.repository;

import com.renterp.domain.auth.entity.OtpAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OtpAttemptRepository extends JpaRepository<OtpAttempt, UUID> {

    // Fetch all unexpired, unused OTPs for a phone — used to validate submitted code
    List<OtpAttempt> findByPhoneAndUsedFalseAndExpiresAtAfter(String phone, Instant now);

    // Count recent unused OTPs — used for rate limiting (max N sends per hour)
    long countByPhoneAndUsedFalseAndCreatedAtAfter(String phone, Instant since);
}
