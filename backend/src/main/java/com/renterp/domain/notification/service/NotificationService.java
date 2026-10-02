package com.renterp.domain.notification.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.notification.dto.DeviceTokenRequest;
import com.renterp.domain.notification.dto.NotificationResponse;
import com.renterp.domain.notification.entity.DeviceToken;
import com.renterp.domain.notification.entity.Notification;
import com.renterp.domain.notification.entity.Notification.Type;
import com.renterp.domain.notification.repository.DeviceTokenRepository;
import com.renterp.domain.notification.repository.NotificationRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates and serves in-app notifications.
 *
 * <p>The {@code to*} methods are called by the services that make a change and
 * join their transaction ({@link Propagation#MANDATORY}): the notification is
 * written with the change, so a change that rolls back never notifies. The
 * person who made the change is never notified of it.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final TenantPropertyMembershipRepository memberships;
    private final TenantProfileRepository profiles;
    private final AccessGuard guard;
    private final EntityManager em;

    public NotificationService(NotificationRepository notifications, DeviceTokenRepository devices,
                               TenantPropertyMembershipRepository memberships, TenantProfileRepository profiles,
                               AccessGuard guard, EntityManager em) {
        this.notifications = notifications;
        this.devices = devices;
        this.memberships = memberships;
        this.profiles = profiles;
        this.guard = guard;
        this.em = em;
    }

    // ── Sending (called by other services) ───────────────────────────────

    /** Everyone with access to the property (owner and managers). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void toPropertyStaff(UUID propertyId, Type type, String body, String entityType, UUID entityId) {
        List<UUID> staff = em.createQuery(
                        "select distinct pa.userId from PropertyAccess pa where pa.propertyId = :pid and pa.active = true",
                        UUID.class)
                .setParameter("pid", propertyId)
                .getResultList();
        for (UUID u : staff) {
            save(u, type, body, propertyId, entityType, entityId);
        }
    }

    /** The tenant of a tenancy, when their profile is linked to an account. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void toTenant(UUID membershipId, Type type, String body, String entityType, UUID entityId) {
        memberships.findById(membershipId).ifPresent(m ->
                toProfile(m.getTenantProfileId(), m.getPropertyId(), type, body, entityType, entityId));
    }

    /** The account linked to a tenant profile, if any. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void toProfile(UUID tenantProfileId, UUID propertyId, Type type, String body, String entityType, UUID entityId) {
        profiles.findById(tenantProfileId)
                .map(p -> p.getUserId())
                .filter(Objects::nonNull)
                .ifPresent(u -> save(u, type, body, propertyId, entityType, entityId));
    }

    private void save(UUID userId, Type type, String body, UUID propertyId, String entityType, UUID entityId) {
        UUID actor = guard.current().map(AuthUser::userId).orElse(null);
        if (userId.equals(actor)) {
            return;
        }
        notifications.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(type.title)
                .body(body == null || body.length() <= 500 ? body : body.substring(0, 500))
                .propertyId(propertyId)
                .entityType(entityType)
                .entityId(entityId)
                .build());
    }

    /** "Rs 1,05,000" (Nepali grouping, no paisa when whole). Never the rupee sign. */
    public static String rs(java.math.BigDecimal amount) {
        if (amount == null) {
            return "Rs 0";
        }
        java.math.BigDecimal a = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        String sign = a.signum() < 0 ? "-" : "";
        a = a.abs();
        String whole = a.toBigInteger().toString();
        String paisa = a.remainder(java.math.BigDecimal.ONE).movePointRight(2).intValue() == 0
                ? "" : "." + String.format("%02d", a.remainder(java.math.BigDecimal.ONE).movePointRight(2).intValue());
        StringBuilder out = new StringBuilder();
        if (whole.length() <= 3) {
            out.append(whole);
        } else {
            String head = whole.substring(0, whole.length() - 3);
            String tail = whole.substring(whole.length() - 3);
            StringBuilder h = new StringBuilder();
            for (int i = head.length(); i > 0; i -= 2) {
                h.insert(0, head.substring(Math.max(0, i - 2), i));
                if (i - 2 > 0) {
                    h.insert(0, ",");
                }
            }
            out.append(h).append(",").append(tail);
        }
        return "Rs " + sign + out + paisa;
    }

    // ── Inbox (the signed-in user's own) ──────────────────────────────────

    @Transactional(readOnly = true)
    public Page<NotificationResponse> mine(int page, int size) {
        AuthUser me = guard.requireUser();
        return notifications.findByUserIdOrderByCreatedAtDesc(me.userId(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))).map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByUserIdAndReadAtIsNull(guard.requireUser().userId());
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        AuthUser me = guard.requireUser();
        Notification n = notifications.findById(id)
                .filter(x -> x.getUserId().equals(me.userId()))
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found."));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            notifications.save(n);
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(guard.requireUser().userId(), Instant.now());
    }

    /** Registers this device's push token for the signed-in user (moves it if another user had it). */
    @Transactional
    public void registerDevice(DeviceTokenRequest req) {
        AuthUser me = guard.requireUser();
        DeviceToken t = devices.findByToken(req.getToken())
                .orElseGet(() -> DeviceToken.builder().token(req.getToken()).build());
        t.setUserId(me.userId());
        t.setPlatform(req.getPlatform());
        devices.save(t);
    }
}
