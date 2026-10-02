package com.renterp.domain.auth.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.dto.AccountRequests;
import com.renterp.domain.auth.dto.OtpChallengeResponse;
import com.renterp.domain.auth.dto.SessionResponse;
import com.renterp.domain.auth.dto.UserResponse;
import com.renterp.domain.auth.entity.OtpAttempt;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.UserSession;
import com.renterp.domain.auth.repository.OtpAttemptRepository;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.repository.UserSessionRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.auth.security.TokenHasher;
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
import java.util.Locale;
import java.util.UUID;

/**
 * Account self-service for the signed-in user: password, phone and email
 * (each new phone / email proven with a code sent to it), signed-in devices,
 * and deleting the account.
 *
 * <ul>
 *   <li>Password change needs the current password when one is set, follows
 *       {@link PasswordPolicy}, and signs out every other device.</li>
 *   <li>A change code is bound to the user who asked for it, so one user's
 *       code cannot change another's account; the address is re-checked for
 *       being free at confirm time.</li>
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
    private final OtpService otp;
    private final OtpAttemptRepository attempts;
    private final TenantProfileRepository profiles;
    private final AccessGuard guard;
    private final EntityManager em;

    public AccountService(UserRepository users, UserSessionRepository sessions, PasswordEncoder encoder,
                          OtpService otp, OtpAttemptRepository attempts, TenantProfileRepository profiles, AccessGuard guard, EntityManager em) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.otp = otp;
        this.attempts = attempts;
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
        if (user.getPasswordHash() != null) {
            if (req.getCurrentPassword() == null || !encoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
                throw ApiException.badRequest("PASSWORD_INCORRECT", "Your current password is not correct.");
            }
            if (encoder.matches(req.getNewPassword(), user.getPasswordHash())) {
                throw ApiException.badRequest("PASSWORD_UNCHANGED", "Choose a password different from the current one.");
            }
        }
        String problem = PasswordPolicy.problem(req.getNewPassword());
        if (problem != null) {
            throw ApiException.badRequest("WEAK_PASSWORD", problem);
        }
        user.setPasswordHash(encoder.encode(req.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        users.save(user);
        int ended = revokeOthers(me);
        log.info("Password changed — user: {}, other sessions ended: {}", user.getId(), ended);
        return ended;
    }

    // ── Phone ────────────────────────────────────────────────────────────

    @Transactional
    public OtpChallengeResponse requestPhoneChange(AccountRequests.NewPhone req) {
        AuthUser me = guard.requireUser();
        User user = current(me);
        if (req.getPhone().equals(user.getPhone())) {
            throw ApiException.badRequest("SAME_PHONE", "This is already your number.");
        }
        requirePhoneFree(req.getPhone(), user.getId());
        return otp.issue(req.getPhone(), OtpAttempt.Purpose.CHANGE_PHONE, user.getId());
    }

    @Transactional
    public UserResponse confirmPhoneChange(AccountRequests.ConfirmCode req) {
        AuthUser me = guard.requireUser();
        requireOwnChallenge(req.getVerificationId(), me);
        OtpAttempt a = otp.check(req.getVerificationId(), req.getCode(), OtpAttempt.Purpose.CHANGE_PHONE);
        User user = current(me);
        requirePhoneFree(a.getPhone(), user.getId());
        String old = user.getPhone();
        user.setPhone(a.getPhone());
        user.setPhoneVerifiedAt(Instant.now());
        users.save(user);
        // Keep the user's own tenant profile on the same number.
        profiles.findByUserId(user.getId()).ifPresent(p -> {
            p.setPhone(a.getPhone());
            profiles.save(p);
        });
        log.info("Phone changed — user: {} ({} → {})", user.getId(), mask(old), mask(a.getPhone()));
        return UserResponse.from(user);
    }

    // ── Email ────────────────────────────────────────────────────────────

    @Transactional
    public OtpChallengeResponse requestEmailChange(AccountRequests.NewEmail req) {
        AuthUser me = guard.requireUser();
        User user = current(me);
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail())) {
            throw ApiException.badRequest("SAME_EMAIL", "This is already your email.");
        }
        requireEmailFree(email, user.getId());
        return otp.issue(email, OtpAttempt.Purpose.CHANGE_EMAIL, user.getId());
    }

    @Transactional
    public UserResponse confirmEmailChange(AccountRequests.ConfirmCode req) {
        AuthUser me = guard.requireUser();
        requireOwnChallenge(req.getVerificationId(), me);
        OtpAttempt a = otp.check(req.getVerificationId(), req.getCode(), OtpAttempt.Purpose.CHANGE_EMAIL);
        User user = current(me);
        requireEmailFree(a.getPhone(), user.getId());
        user.setEmail(a.getPhone());
        user.setEmailVerifiedAt(Instant.now());
        users.save(user);
        log.info("Email changed — user: {}", user.getId());
        return UserResponse.from(user);
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
        if (user.getPasswordHash() != null
                && (req.getPassword() == null || !encoder.matches(req.getPassword(), user.getPasswordHash()))) {
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

        user.setActive(false);
        user.setPhone("del" + TokenHasher.randomDigits(17));
        user.setEmail(null);
        user.setName("Deleted user");
        user.setPasswordHash(null);
        user.setFcmToken(null);
        users.save(user);
        sessions.revokeAllByUserId(user.getId(), Instant.now());
        log.info("Account deleted — user: {}", user.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private User current(AuthUser me) {
        return users.findById(me.userId()).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Please log in again."));
    }

    /**
     * A change code belongs to the user who asked for it. Checked before the
     * code is tried, so someone else's attempt neither works nor uses it up.
     */
    private void requireOwnChallenge(UUID verificationId, AuthUser me) {
        if (attempts.findById(verificationId).filter(a -> me.userId().equals(a.getUserId())).isEmpty()) {
            throw ApiException.gone("OTP_EXPIRED", "That code has expired. Ask for a new one.");
        }
    }

    private int revokeOthers(AuthUser me) {
        int n = 0;
        Instant now = Instant.now();
        for (UserSession s : sessions.findActiveByUserId(me.userId(), now)) {
            if (!s.getId().equals(me.sessionId())) {
                s.setRevokedAt(now);
                sessions.save(s);
                n++;
            }
        }
        return n;
    }

    private void requirePhoneFree(String phone, UUID self) {
        users.findByPhone(phone).filter(u -> !u.getId().equals(self)).ifPresent(u -> {
            throw ApiException.conflict("PHONE_TAKEN", "This number is already used by another account.");
        });
    }

    private void requireEmailFree(String email, UUID self) {
        users.findByEmailIgnoreCase(email).filter(u -> !u.getId().equals(self)).ifPresent(u -> {
            throw ApiException.conflict("EMAIL_TAKEN", "This email is already used by another account.");
        });
    }

    private static String device(UserSession s) {
        Object ua = s.getDeviceInfo() == null ? null : s.getDeviceInfo().get("userAgent");
        return ua == null ? "Unknown device" : ua.toString();
    }

    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "?" : "******" + phone.substring(phone.length() - 4);
    }
}
