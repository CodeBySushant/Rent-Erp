package com.renterp.common.housekeeping;

import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.domain.auth.repository.OtpAttemptRepository;
import com.renterp.domain.auth.repository.UserSessionRepository;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import jakarta.persistence.EntityManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * M16: daily clean-up of technical rows that otherwise grow forever. Business
 * records (bills, payments, tenancies, notifications) are never touched.
 *
 * <ul>
 *   <li>one-time codes that expired more than {@code app.housekeeping.otp-retention-days} ago;</li>
 *   <li>sessions that ended (logged out / expired) more than
 *       {@code app.housekeeping.session-retention-days} ago;</li>
 *   <li>rate-limit windows older than a day;</li>
 *   <li>join requests past their expiry are marked EXPIRED (they used to expire
 *       only when someone happened to read them).</li>
 * </ul>
 *
 * Runs at {@code app.housekeeping.cron} (default 03:15 Nepal time). Every step is
 * idempotent, so running on two instances at once is harmless.
 */
@Component
public class HousekeepingJob {

    private static final Logger log = LogManager.getLogger(HousekeepingJob.class);

    private final OtpAttemptRepository otps;
    private final UserSessionRepository sessions;
    private final RateLimiter rateLimiter;
    private final EntityManager em;
    private final Duration otpRetention;
    private final Duration sessionRetention;

    public HousekeepingJob(OtpAttemptRepository otps, UserSessionRepository sessions, RateLimiter rateLimiter,
                           EntityManager em,
                           @Value("${app.housekeeping.otp-retention-days:7}") long otpDays,
                           @Value("${app.housekeeping.session-retention-days:90}") long sessionDays) {
        this.otps = otps;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.em = em;
        this.otpRetention = Duration.ofDays(otpDays);
        this.sessionRetention = Duration.ofDays(sessionDays);
    }

    @Scheduled(cron = "${app.housekeeping.cron:0 15 3 * * *}", zone = "Asia/Kathmandu")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        int codes = otps.deleteExpiredBefore(now.minus(otpRetention));
        int ended = sessions.deleteEndedBefore(now.minus(sessionRetention));
        int windows = rateLimiter.purgeBefore(now.minus(Duration.ofDays(1)));
        int expired = em.createQuery("update JoinRequest j set j.status = :expired, j.version = j.version + 1 "
                        + "where j.status = :pending and j.expiresAt < :now")
                .setParameter("expired", JoinRequestStatus.EXPIRED)
                .setParameter("pending", JoinRequestStatus.PENDING)
                .setParameter("now", now)
                .executeUpdate();
        log.info("Housekeeping done - codes removed: {}, sessions removed: {}, rate windows removed: {}, "
                + "join requests expired: {}", codes, ended, windows, expired);
    }
}
