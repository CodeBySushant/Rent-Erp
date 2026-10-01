package com.renterp.domain.auth.security;

import com.renterp.domain.auth.entity.User.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short-lived access tokens: HS256 JWTs carrying the user id ({@code sub}),
 * the session id ({@code sid}) and the role.
 *
 * Written against the JDK only (javax.crypto) - the payload is a fixed shape
 * this class both writes and reads, so no JWT library is needed. The signature
 * is checked with a constant-time comparison before anything in the payload
 * is trusted. Session validity (logout, revocation) is checked separately on
 * every request by {@link JwtAuthenticationFilter}.
 */
@Component
public class JwtService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = B64.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private static final Pattern SUB = Pattern.compile("\"sub\":\"([0-9a-fA-F-]{36})\"");
    private static final Pattern SID = Pattern.compile("\"sid\":\"([0-9a-fA-F-]{36})\"");
    private static final Pattern ROLE = Pattern.compile("\"role\":\"([A-Z_]+)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] secret;
    private final Duration accessTtl;
    private final Clock clock;

    // Two constructors (the package-private one is for tests), so Spring must be
    // told which one to use.
    @Autowired
    public JwtService(@Value("${app.auth.jwt-secret}") String secret,
                      @Value("${app.auth.access-token-minutes:15}") long accessTokenMinutes) {
        this(secret, Duration.ofMinutes(accessTokenMinutes), Clock.systemUTC());
    }

    JwtService(String secret, Duration accessTtl, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtl = accessTtl;
        this.clock = clock;
    }

    public Duration accessTokenTtl() {
        return accessTtl;
    }

    public String issue(AuthUser user) {
        long now = Instant.now(clock).getEpochSecond();
        String payload = "{\"sub\":\"" + user.userId() + "\",\"sid\":\"" + user.sessionId()
                + "\",\"role\":\"" + user.role().name() + "\",\"iat\":" + now
                + ",\"exp\":" + (now + accessTtl.toSeconds()) + "}";
        String body = HEADER + "." + B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + B64.encodeToString(sign(body));
    }

    /** Result of checking a token: either a user or the reason it was rejected. */
    public record Verification(AuthUser user, String errorCode) {
        public boolean ok() {
            return user != null;
        }
    }

    public Verification verify(String token) {
        if (token == null) {
            return new Verification(null, "TOKEN_INVALID");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !HEADER.equals(parts[0])) {
            return new Verification(null, "TOKEN_INVALID");
        }
        byte[] given;
        try {
            given = B64D.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return new Verification(null, "TOKEN_INVALID");
        }
        if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), given)) {
            return new Verification(null, "TOKEN_INVALID");
        }
        String payload;
        try {
            payload = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return new Verification(null, "TOKEN_INVALID");
        }
        Matcher sub = SUB.matcher(payload);
        Matcher sid = SID.matcher(payload);
        Matcher role = ROLE.matcher(payload);
        Matcher exp = EXP.matcher(payload);
        if (!sub.find() || !sid.find() || !role.find() || !exp.find()) {
            return new Verification(null, "TOKEN_INVALID");
        }
        if (Long.parseLong(exp.group(1)) <= Instant.now(clock).getEpochSecond()) {
            return new Verification(null, "TOKEN_EXPIRED");
        }
        try {
            return new Verification(new AuthUser(
                    UUID.fromString(sub.group(1)),
                    UUID.fromString(sid.group(1)),
                    UserRole.valueOf(role.group(1))), null);
        } catch (IllegalArgumentException e) {
            return new Verification(null, "TOKEN_INVALID");
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
