package com.renterp.domain.notification.service;

import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.notification.dto.NotificationPreferencesDto;
import com.renterp.domain.notification.entity.Notification.Type;
import com.renterp.domain.notification.entity.NotificationPreferences;
import com.renterp.domain.notification.repository.NotificationPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notification Setting: defaults, partial saves, and which pushes go out. */
class NotificationPreferenceServiceTest {

    private final UUID me = UUID.randomUUID();
    private NotificationPreferencesRepository repository;
    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationPreferencesRepository.class);
        AccessGuard guard = mock(AccessGuard.class);
        when(guard.requireUser()).thenReturn(new AuthUser(me, UUID.randomUUID(), UserRole.LANDLORD));
        when(repository.save(any(NotificationPreferences.class))).thenAnswer(i -> i.getArgument(0));
        service = new NotificationPreferenceService(repository, guard);
    }

    private static NotificationPreferencesDto only(Boolean maintenance, Boolean rent) {
        return new NotificationPreferencesDto(rent, null, null, null, maintenance, null, null, null, null, null);
    }

    @Test
    void neverChangedGivesTheDefaults() {
        NotificationPreferencesDto d = service.mine();
        assertEquals(true, d.pushRentReminders());
        assertEquals(true, d.pushPaymentReceived());
        assertEquals(true, d.pushNewTenant());
        assertEquals(false, d.pushMaintenance());
        assertEquals(false, d.pushGeneralUpdates());
        assertEquals(true, d.emailImportantAlerts());
        assertEquals(false, d.emailMarketing());
    }

    @Test
    void firstSaveCreatesTheRowAndKeepsUnsentFields() {
        NotificationPreferencesDto saved = service.update(only(true, false));
        ArgumentCaptor<NotificationPreferences> row = ArgumentCaptor.forClass(NotificationPreferences.class);
        verify(repository).save(row.capture());
        assertEquals(me, row.getValue().getUserId());
        assertEquals(true, saved.pushMaintenance());
        assertEquals(false, saved.pushRentReminders());
        assertEquals(true, saved.pushPaymentReceived());   // not sent, default kept
        assertEquals(true, saved.emailMonthlyStatements());
    }

    @Test
    void laterSaveChangesOnlyWhatWasSent() {
        NotificationPreferences existing = NotificationPreferences.defaults(me);
        existing.setPushNewTenant(false);
        when(repository.findById(me)).thenReturn(Optional.of(existing));
        NotificationPreferencesDto saved = service.update(only(true, null));
        assertEquals(true, saved.pushMaintenance());
        assertEquals(false, saved.pushNewTenant());
        assertEquals(true, saved.pushRentReminders());
    }

    @Test
    void pushFollowsTheMatchingToggle() {
        NotificationPreferences p = NotificationPreferences.defaults(me);
        assertTrue(NotificationPreferenceService.pushAllowed(p, Type.BILL_ISSUED));
        assertFalse(NotificationPreferenceService.pushAllowed(p, Type.REQUEST_CREATED)); // maintenance off by default
        p.setPushRentReminders(false);
        p.setPushPaymentReceived(false);
        p.setPushNewTenant(false);
        assertFalse(NotificationPreferenceService.pushAllowed(p, Type.BILL_ISSUED));
        assertFalse(NotificationPreferenceService.pushAllowed(p, Type.PAYMENT_PROOF_WAITING));
        assertFalse(NotificationPreferenceService.pushAllowed(p, Type.JOIN_REQUESTED));
    }

    @Test
    void accountCriticalTypesAreAlwaysPushed() {
        NotificationPreferences p = NotificationPreferences.defaults(me);
        p.setPushGeneralUpdates(false);
        p.setPushMaintenance(false);
        assertTrue(NotificationPreferenceService.pushAllowed(p, Type.MOVE_OUT_NOTICE));
        assertTrue(NotificationPreferenceService.pushAllowed(p, Type.AGREEMENT_ADDED));
        assertTrue(NotificationPreferenceService.pushAllowed(p, Type.SUPPORT_TICKET_RESOLVED));
        assertTrue(NotificationPreferenceService.pushAllowed(p, Type.READING_SUBMITTED));
    }

    @Test
    void storedChoicesAreUsedForPush() {
        NotificationPreferences p = NotificationPreferences.defaults(me);
        p.setPushPaymentReceived(false);
        when(repository.findById(me)).thenReturn(Optional.of(p));
        assertFalse(service.pushAllowed(me, Type.PAYMENT_APPROVED));
        assertTrue(service.pushAllowed(UUID.randomUUID(), Type.PAYMENT_APPROVED)); // no row: default on
    }
}
