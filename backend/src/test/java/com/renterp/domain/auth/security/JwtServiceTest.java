package com.renterp.domain.auth.security;

import com.renterp.domain.auth.entity.User.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test_secret_that_is_at_least_thirty_two_chars";
    private static final Instant T0 = Instant.parse("2026-10-02T00:00:00Z");

    private static JwtService at(Instant now) {
        return new JwtService(SECRET, Duration.ofMinutes(15), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenVerifiesAndCarriesTheUser() {
        AuthUser user = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), UserRole.LANDLORD);
        String token = at(T0).issue(user);

        JwtService.Verification v = at(T0.plusSeconds(60)).verify(token);

        assertTrue(v.ok());
        assertEquals(user, v.user());
    }

    @Test
    void expiredTokenIsReportedAsExpired() {
        AuthUser user = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), UserRole.TENANT);
        String token = at(T0).issue(user);

        JwtService.Verification v = at(T0.plus(Duration.ofMinutes(15))).verify(token);

        assertFalse(v.ok());
        assertEquals("TOKEN_EXPIRED", v.errorCode());
    }

    @Test
    void tamperedPayloadIsRejected() {
        AuthUser owner = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), UserRole.TENANT);
        String token = at(T0).issue(owner);
        String[] parts = token.split("\\.");
        // Swap in a payload claiming ADMIN, keep the old signature.
        String forged = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + owner.userId() + "\",\"sid\":\"" + owner.sessionId()
                        + "\",\"role\":\"ADMIN\",\"iat\":0,\"exp\":9999999999}").getBytes()) + "." + parts[2];

        JwtService.Verification v = at(T0).verify(forged);

        assertFalse(v.ok());
        assertEquals("TOKEN_INVALID", v.errorCode());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        AuthUser user = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), UserRole.LANDLORD);
        String token = new JwtService("another_secret_that_is_also_32_chars_long", Duration.ofMinutes(15),
                Clock.fixed(T0, ZoneOffset.UTC)).issue(user);

        assertFalse(at(T0).verify(token).ok());
    }

    @Test
    void garbageIsRejected() {
        assertEquals("TOKEN_INVALID", at(T0).verify("not.a.token").errorCode());
        assertEquals("TOKEN_INVALID", at(T0).verify("").errorCode());
        assertEquals("TOKEN_INVALID", at(T0).verify(null).errorCode());
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("too-short", Duration.ofMinutes(15), Clock.systemUTC()));
    }
}
