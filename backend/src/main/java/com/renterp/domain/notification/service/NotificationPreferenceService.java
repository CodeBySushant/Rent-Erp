package com.renterp.domain.notification.service;

import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.notification.dto.NotificationPreferencesDto;
import com.renterp.domain.notification.entity.Notification.Type;
import com.renterp.domain.notification.entity.NotificationPreferences;
import com.renterp.domain.notification.repository.NotificationPreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Profile → Notification Setting (V31).
 *
 * <p>Push toggles decide which notifications are also pushed to the phone;
 * the in-app list (bell) always gets every notification, so nothing is lost
 * by turning a push off. Mapping of notification types to toggles:
 * <ul>
 *   <li>Rent Reminders: BILL_ISSUED</li>
 *   <li>Payment Received: PAYMENT_RECORDED, PAYMENT_PROOF_WAITING,
 *       PAYMENT_APPROVED, PAYMENT_REJECTED</li>
 *   <li>New Tenant: JOIN_REQUESTED, JOIN_ACCEPTED, JOIN_REJECTED</li>
 *   <li>Maintenance Requests: REQUEST_CREATED, REQUEST_DECIDED</li>
 *   <li>Always pushed (account-critical): readings, move-out, agreement,
 *       support replies. Payment Overdue and General Updates have no
 *       notification type yet; their choices are stored for when they do.</li>
 * </ul>
 * Email toggles are stored only: Rentlo does not send those emails yet.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferencesRepository repository;
    private final AccessGuard guard;

    public NotificationPreferenceService(NotificationPreferencesRepository repository, AccessGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    /** The caller's choices (defaults when never changed). */
    @Transactional(readOnly = true)
    public NotificationPreferencesDto mine() {
        UUID me = guard.requireUser().userId();
        return NotificationPreferencesDto.from(of(me));
    }

    /** Saves the given fields (null = unchanged) and returns all choices. */
    @Transactional
    public NotificationPreferencesDto update(NotificationPreferencesDto req) {
        UUID me = guard.requireUser().userId();
        NotificationPreferences p = repository.findById(me).orElseGet(() -> NotificationPreferences.defaults(me));
        req.applyTo(p);
        return NotificationPreferencesDto.from(repository.save(p));
    }

    /** Whether a notification of [type] to [userId] should also be pushed. */
    @Transactional(readOnly = true)
    public boolean pushAllowed(UUID userId, Type type) {
        return pushAllowed(of(userId), type);
    }

    static boolean pushAllowed(NotificationPreferences p, Type type) {
        return switch (type) {
            case BILL_ISSUED -> p.isPushRentReminders();
            case PAYMENT_RECORDED, PAYMENT_PROOF_WAITING, PAYMENT_APPROVED, PAYMENT_REJECTED ->
                    p.isPushPaymentReceived();
            case JOIN_REQUESTED, JOIN_ACCEPTED, JOIN_REJECTED -> p.isPushNewTenant();
            case REQUEST_CREATED, REQUEST_DECIDED -> p.isPushMaintenance();
            default -> true;
        };
    }

    private NotificationPreferences of(UUID userId) {
        return repository.findById(userId).orElseGet(() -> NotificationPreferences.defaults(userId));
    }
}
