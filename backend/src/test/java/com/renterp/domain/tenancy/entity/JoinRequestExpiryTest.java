package com.renterp.domain.tenancy.entity;

import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Join requests lapse 24 hours after they are sent (owner, 13 Nov 2026). */
class JoinRequestExpiryTest {

    private static final Duration DAY = Duration.ofHours(24);
    private static final Instant SENT = Instant.parse("2026-11-13T10:00:00Z");

    private static JoinRequest request(Instant expiresAt, JoinRequestStatus status) {
        JoinRequest j = new JoinRequest();
        j.setRequestedAt(SENT);
        j.setExpiresAt(expiresAt);
        j.setStatus(status);
        return j;
    }

    @Test
    void anOldSevenDayRequestNowLapsesAfter24Hours() {
        JoinRequest j = request(SENT.plus(Duration.ofDays(7)), JoinRequestStatus.PENDING);
        assertEquals(SENT.plus(DAY), j.effectiveExpiry(DAY));
        assertFalse(j.isStale(DAY, SENT.plus(Duration.ofHours(23))));
        assertTrue(j.isStale(DAY, SENT.plus(Duration.ofHours(25))));
    }

    @Test
    void aStoredExpiryEarlierThanTheTtlStillWins() {
        JoinRequest j = request(SENT.plus(Duration.ofHours(2)), JoinRequestStatus.PENDING);
        assertEquals(SENT.plus(Duration.ofHours(2)), j.effectiveExpiry(DAY));
        assertTrue(j.isStale(DAY, SENT.plus(Duration.ofHours(3))));
    }

    @Test
    void onlyPendingRequestsGoStale() {
        Instant late = SENT.plus(Duration.ofDays(3));
        for (JoinRequestStatus s : new JoinRequestStatus[]{
                JoinRequestStatus.ACCEPTED, JoinRequestStatus.REJECTED,
                JoinRequestStatus.CANCELLED, JoinRequestStatus.EXPIRED}) {
            assertFalse(request(SENT.plus(DAY), s).isStale(DAY, late), s.name());
        }
    }
}
