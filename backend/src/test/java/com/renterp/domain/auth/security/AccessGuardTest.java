package com.renterp.domain.auth.security;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.propertyaccess.entity.PropertyAccess;
import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import com.renterp.domain.propertyaccess.repository.PropertyAccessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IDOR rules at the service boundary: whatever ids arrive in the request, the
 * caller from the token decides.
 */
class AccessGuardTest {

    private final PropertyAccessRepository repo = mock(PropertyAccessRepository.class);
    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();
    private final UUID propertyOfA = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(UUID userId, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthUser(userId, UUID.randomUUID(), role), null, List.of()));
    }

    private void grant(UUID user, UUID property, AccessRole role) {
        PropertyAccess a = PropertyAccess.builder().propertyId(property).userId(user).role(role).active(true).build();
        when(repo.findFirstByPropertyIdAndUserIdAndActiveTrue(property, user)).thenReturn(Optional.of(a));
    }

    @Test
    void ownerReachesOwnProperty() {
        grant(ownerA, propertyOfA, AccessRole.OWNER);
        loginAs(ownerA, UserRole.LANDLORD);
        assertDoesNotThrow(() -> new AccessGuard(repo, true).requirePropertyAccess(propertyOfA, AccessRole.OWNER));
    }

    @Test
    void otherOwnerIsForbidden() {
        grant(ownerA, propertyOfA, AccessRole.OWNER);
        when(repo.findFirstByPropertyIdAndUserIdAndActiveTrue(any(), any())).thenReturn(Optional.empty());
        loginAs(ownerB, UserRole.LANDLORD);
        ApiException ex = assertThrows(ApiException.class,
                () -> new AccessGuard(repo, true).requirePropertyAccess(propertyOfA, AccessRole.VIEW_ONLY));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void viewerCannotManage() {
        grant(ownerB, propertyOfA, AccessRole.VIEW_ONLY);
        loginAs(ownerB, UserRole.LANDLORD);
        AccessGuard guard = new AccessGuard(repo, true);
        assertDoesNotThrow(() -> guard.requirePropertyAccess(propertyOfA, AccessRole.VIEW_ONLY));
        assertThrows(ApiException.class, () -> guard.requirePropertyAccess(propertyOfA, AccessRole.MANAGER));
    }

    @Test
    void userCannotTouchAnotherAccount() {
        loginAs(ownerB, UserRole.TENANT);
        AccessGuard guard = new AccessGuard(repo, true);
        assertDoesNotThrow(() -> guard.requireSelfOrAdmin(ownerB));
        assertThrows(ApiException.class, () -> guard.requireSelfOrAdmin(ownerA));
        assertThrows(ApiException.class, guard::requireAdmin);
    }

    @Test
    void noTokenIs401WhenEnforced() {
        ApiException ex = assertThrows(ApiException.class,
                () -> new AccessGuard(repo, true).requirePropertyAccess(propertyOfA, AccessRole.VIEW_ONLY));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void noTokenPassesOnlyWhenEnforcementIsOff() {
        assertDoesNotThrow(() -> new AccessGuard(repo, false).requirePropertyAccess(propertyOfA, AccessRole.OWNER));
    }

    @Test
    void tokenIsStillCheckedWhenEnforcementIsOff() {
        when(repo.findFirstByPropertyIdAndUserIdAndActiveTrue(any(), any())).thenReturn(Optional.empty());
        loginAs(ownerB, UserRole.LANDLORD);
        assertThrows(ApiException.class,
                () -> new AccessGuard(repo, false).requirePropertyAccess(propertyOfA, AccessRole.VIEW_ONLY));
    }

    @Test
    void adminPassesEverything() {
        loginAs(UUID.randomUUID(), UserRole.ADMIN);
        AccessGuard guard = new AccessGuard(repo, true);
        assertDoesNotThrow(() -> guard.requirePropertyAccess(propertyOfA, AccessRole.OWNER));
        assertDoesNotThrow(() -> guard.requireSelfOrAdmin(ownerA));
        assertEquals(Optional.empty(), guard.visiblePropertyIds());
    }
}
