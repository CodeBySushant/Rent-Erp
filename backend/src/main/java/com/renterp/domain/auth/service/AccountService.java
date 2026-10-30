package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.domain.auth.dto.AccountRequests;
import com.renterp.domain.auth.dto.SessionResponse;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.UserSession;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.repository.UserSessionRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.auth.security.TokenHasher;
import com.renterp.domain.notification.repository.DeviceTokenRepository;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import jakarta.persistence.EntityManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Account self-service for the signed-in user: password, profile photo,
 * signed-in devices, and deleting the account. (Changing the phone or email
 * with a one-time code is removed on branch temp/signup-no-otp.)
 *
 * <ul>
 *   <li>Password change needs the current password when one is set, follows
 *       {@link PasswordPolicy}, and signs out every other device.</li>
 *   <li>Delete is refused while the account is still in use — an owner with
 *       active tenants, a tenant with an active tenancy or unpaid bills. It
 *       then deactivates and anonymises the user, unlinks their tenant
 *       profile (history stays with the owner) and ends all sessions.</li>
 * </ul>
 */
@Service
public class AccountService {

    private static final Logger log = LogManager.getLogger(AccountService.class);

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final TenantProfileRepository profiles;
    private final AccessGuard guard;
    private final EntityManager em;
    private final DeviceTokenRepository deviceTokens;
    private final RateLimiter rateLimiter;

    public AccountService(UserRepository users, UserSessionRepository sessions, PasswordEncoder encoder,
                          TenantProfileRepository profiles, AccessGuard guard,
                          EntityManager em, DeviceTokenRepository deviceTokens, RateLimiter rateLimiter) {
        this.deviceTokens = deviceTokens;
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.profiles = profiles;
        this.guard = guard;
        this.em = em;
    }

    // ── Password ───────────────────────────────────────────────────────────

    /** Returns how many other devices were signed out. */
    @Transactional
    public int changePassword(AccountRequests.ChangePassword req) {
        AuthUser me = guard.requireUser();
        User user = current(me);
        // Policy first (it rejects > 72 bytes), so the encoder never sees a
        // value it cannot handle (L2).
        String problem = PasswordPolicy.problem(req.getNewPassword());
        if (problem != null) {
            throw ApiException.badRequest("WEAK_PASSWORD", problem);
        }
        if (user.getPasswordHash() != null) {
            if (!passwordMatches(req.getCurrentPassword(), user.getPasswordHash())) {
                throw ApiException.badRequest("PASSWORD_INCORRECT", "Your current password is not correct.");
            }
            if (encoder.matches(req.getNewPassword(), user.getPasswordHash())) {
                throw ApiException.badRequest("PASSWORD_UNCHANGED", "Choose a password different from the current one.");
            }
        }
        user.setPasswordHash(encoder.encode(req.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        users.save(user);
        int ended = revokeOthers(me);
        log.info("Password changed — user: {}, other sessions ended: {}", user.getId(), ended);
        return ended;
    }

    // ── Devices ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions() {
        AuthUser me = guard.requireUser();
        return sessions.findActiveByUserId(me.userId(), Instant.now()).stream()
                .sorted(Comparator.comparing(UserSession::getLastUsedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> new SessionResponse(s.getId(), device(s), s.getCreatedAt(), s.getLastUsedAt(),
                        s.getExpiresAt(), s.getId().equals(me.sessionId())))
                .toList();
    }

    @Transactional
    public void endSession(UUID sessionId) {
        AuthUser me = guard.requireUser();
        UserSession s = sessions.findById(sessionId)
                .filter(x -> x.getUser().getId().equals(me.userId()) && x.getRevokedAt() == null)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "That device is not signed in."));
        s.setRevokedAt(Instant.now());
        sessions.save(s);
        deviceTokens.deleteBySessionId(s.getId());
    }

    @Transactional
    public int endOtherSessions() {
        return revokeOthers(guard.requireUser());
    }

    // ── Delete ───────────────────────────────────────────────────────────

    @Transactional
    public void deleteAccount(AccountRequests.DeleteAccount req) {
        AuthUser me = guard.requireUser();
        User user = current(me);
        if (!"DELETE".equals(req.getConfirm())) {
            throw ApiException.badRequest("CONFIRM_REQUIRED", "Type DELETE to confirm.");
        }
        if (user.getPasswordHash() != null && !passwordMatches(req.getPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_INCORRECT", "Your password is not correct.");
        }
        long activeTenants = em.createQuery("""
                select count(m) from TenantPropertyMembership m
                where m.status = com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus.ACTIVE
                  and m.propertyId in (select p.id from Property p where p.ownerUserId = :uid)
                """, Long.class).setParameter("uid", user.getId()).getSingleResult();
        if (activeTenants > 0) {
            throw ApiException.conflict("HAS_ACTIVE_TENANTS",
                    "Your properties still have active tenants. Move them out first.");
        }
        TenantProfile profile = profiles.findByUserId(user.getId()).orElse(null);
        if (profile != null) {
            long activeStays = em.createQuery("""
                    select count(m) from TenantPropertyMembership m
                    where m.tenantProfileId = :pid
                      and m.status = com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus.ACTIVE
                    """, Long.class).setParameter("pid", profile.getId()).getSingleResult();
            if (activeStays > 0) {
                throw ApiException.conflict("ACTIVE_TENANCY", "You still live in a property. Move out first.");
            }
            long unpaid = em.createQuery("""
                    select count(b) from TenantBill b
                    where b.membershipId in (select m.id from TenantPropertyMembership m where m.tenantProfileId = :pid)
                      and b.status = com.renterp.domain.billing.entity.TenantBill.Status.ISSUED
                      and b.supersededByBillId is null and b.balanceDue > 0
                    """, Long.class).setParameter("pid", profile.getId()).getSingleResult();
            if (unpaid > 0) {
                throw ApiException.conflict("UNPAID_BILLS", "You still have unpaid bills. Pay them first.");
            }
            profile.setUserId(null);
            profiles.save(profile);
        }

        // H4: an owner's properties close with the account, so they cannot be
        // joined or shared any more. History (bills, payments) stays.
        int closed = closeOwnedProperties(user.getId());

        user.setActive(false);
        user.setPhone("del" + TokenHasher.randomDigits(17));
        user.setEmail(null);
        user.setName("Deleted user");
        user.setPasswordHash(null);
        user.setFcmToken(null);
        users.save(user);
        sessions.revokeAllByUserId(user.getId(), Instant.now());
        deviceTokens.deleteByUserId(user.getId());
        log.info("Account deleted — user: {}, properties closed: {}", user.getId(), closed);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * H4: deactivates every property the user owns, every access grant on
     * those properties and the user's own grants elsewhere, and cancels join
     * requests still waiting at those properties. Only reached when no
     * property of theirs has an active tenant.
     */
    private int closeOwnedProperties(java.util.UUID userId) {
        List<java.util.UUID> owned = em.createQuery(
                        "select p.id from Property p where p.ownerUserId = :uid and p.active = true", java.util.UUID.class)
                .setParameter("uid", userId)
                .getResultList();
        if (!owned.isEmpty()) {
            em.createQuery("update Property p set p.active = false, p.version = p.version + 1 where p.id in :ids")
                    .setParameter("ids", owned).executeUpdate();
            em.createQuery("update PropertyAccess a set a.active = false, a.version = a.version + 1 "
                            + "where a.propertyId in :ids and a.active = true")
                    .setParameter("ids", owned).executeUpdate();
            em.createQuery("update JoinRequest j set j.status = :cancelled, j.version = j.version + 1 "
                            + "where j.propertyId in :ids and j.status = :pending")
                    .setParameter("cancelled", com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus.CANCELLED)
                    .setParameter("pending", com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus.PENDING)
                    .setParameter("ids", owned).executeUpdate();
        }
        em.createQuery("update PropertyAccess a set a.active = false, a.version = a.version + 1 "
                        + "where a.userId = :uid and a.active = true")
                .setParameter("uid", userId).executeUpdate();
        return owned.size();
    }

    /** L2: a value BCrypt cannot hold (> 72 bytes) never matches. */
    private boolean passwordMatches(String raw, String hash) {
        return raw != null && !PasswordPolicy.tooLongForBcrypt(raw) && encoder.matches(raw, hash);
    }

    private User current(AuthUser me) {
        return users.findById(me.userId()).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Please log in again."));
    }

    private int revokeOthers(AuthUser me) {
        int n = 0;
        Instant now = Instant.now();
        for (UserSession s : sessions.findActiveByUserId(me.userId(), now)) {
            if (!s.getId().equals(me.sessionId())) {
                s.setRevokedAt(now);
                sessions.save(s);
                deviceTokens.deleteBySessionId(s.getId());
                n++;
            }
        }
        return n;
    }

    private static String device(UserSession s) {
        Object ua = s.getDeviceInfo() == null ? null : s.getDeviceInfo().get("userAgent");
        return ua == null ? "Unknown device" : ua.toString();
    }
}
