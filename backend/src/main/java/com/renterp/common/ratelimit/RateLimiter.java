package com.renterp.common.ratelimit;

import com.renterp.common.exception.ApiException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window request limits stored in PostgreSQL (H11).
 *
 * Each call adds one hit to {@code (bucket, current window)} with a single
 * upsert and refuses (429) once the window holds more than {@code limit} hits.
 * Because the counts live in the database, every backend instance shares them
 * and a restart does not reset them (the old in-memory map did both wrong).
 *
 * Runs in its own transaction and keeps the hit even when it refuses, so a
 * refused caller cannot try again for free. Old windows are deleted by
 * {@code HousekeepingJob}.
 *
 * {@code RATE_LIMIT_ENABLED=false} switches every limit off - only for a
 * developer machine running the live test scripts, which make many requests
 * from one address.
 */
@Component
public class RateLimiter {

    private static final Logger log = LogManager.getLogger(RateLimiter.class);

    private static final String UPSERT = """
            INSERT INTO rate_limit_counters (bucket_key, window_start, hits)
            VALUES (?, ?, 1)
            ON CONFLICT (bucket_key, window_start)
            DO UPDATE SET hits = rate_limit_counters.hits + 1
            RETURNING hits
            """;

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public RateLimiter(JdbcTemplate jdbc, @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        if (!enabled) {
            log.warn("RATE_LIMIT_ENABLED=false - request limits are off. Development only.");
        }
    }

    /**
     * Counts one request against {@code bucket}; throws 429 {@code code} when the
     * current window already had {@code limit} requests.
     *
     * @param bucket  what is limited, e.g. "otp-ip:1.2.3.4" (kept under 200 chars)
     * @param limit   requests allowed per window
     * @param window  window length (whole seconds)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void hit(String bucket, int limit, Duration window, String code, String message) {
        if (!enabled) {
            return;
        }
        String key = bucket.length() > 200 ? bucket.substring(0, 200) : bucket;
        long seconds = Math.max(1, window.toSeconds());
        long now = Instant.now().getEpochSecond();
        Instant windowStart = Instant.ofEpochSecond((now / seconds) * seconds);
        Integer hits = jdbc.queryForObject(UPSERT, Integer.class, key, Timestamp.from(windowStart));
        if (hits != null && hits > limit) {
            log.warn("Rate limit hit - bucket: {}, hits: {}/{}", key, hits, limit);
            throw ApiException.tooManyRequests(code, message);
        }
    }

    /** Deletes windows that ended before {@code before}; returns how many rows went. */
    @Transactional
    public int purgeBefore(Instant before) {
        return jdbc.update("DELETE FROM rate_limit_counters WHERE window_start < ?", Timestamp.from(before));
    }
}
