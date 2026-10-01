package com.renterp.domain.auth.security;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.propertyaccess.entity.PropertyAccess;
import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import com.renterp.domain.propertyaccess.repository.PropertyAccessRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side authorization. Services call this with the id of the resource
 * being touched; the caller always comes from the access token, never from the
 * request. Rejections are 403 and never say whether the resource exists.
 *
 * Property rights come from {@code property_access}: OWNER > MANAGER > VIEW_ONLY.
 * ADMIN passes every check.
 *
 * {@code app.auth.enforce=false} (AUTH_ENFORCED=false in a developer's .env)
 * keeps the pre-auth Postman collections runnable: a request with no token is
 * then allowed through as it was before. A request that does carry a token is
 * still checked. Enforcement is on unless that variable says otherwise.
 */
@Component
public class AccessGuard {

    private final PropertyAccessRepository accessRepository;
    private final boolean enforced;

    public AccessGuard(PropertyAccessRepository accessRepository,
                       @Value("${app.auth.enforce:true}") boolean enforced) {
        this.accessRepository = accessRepository;
        this.enforced = enforced;
    }

    public boolean isEnforced() {
        return enforced;
    }

    /** The caller, if the request carries a valid token. */
    public Optional<AuthUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** The caller, or 401. */
    public AuthUser requireUser() {
        return current().orElseThrow(() ->
                ApiException.unauthorized("UNAUTHENTICATED", "Please log in to continue."));
    }

    /** Whether this request is subject to checks (see {@link #shouldCheck()}). */
    public boolean checking() {
        return shouldCheck();
    }

    /**
     * True when the request should be checked: there is a caller, or
     * enforcement is on (in which case a missing caller is a 401).
     */
    private boolean shouldCheck() {
        return enforced || current().isPresent();
    }

    public void requireAdmin() {
        if (!shouldCheck()) {
            return;
        }
        if (!requireUser().isAdmin()) {
            throw ApiException.forbidden("Only an administrator can do this.");
        }
    }

    /** The caller is this user, or an admin. */
    public void requireSelfOrAdmin(UUID userId) {
        if (!shouldCheck()) {
            return;
        }
        AuthUser user = requireUser();
        if (!user.isAdmin() && !user.userId().equals(userId)) {
            throw ApiException.forbidden("You can only access your own account.");
        }
    }

    /** The caller holds at least {@code minimum} on this property (or is an admin). */
    public void requirePropertyAccess(UUID propertyId, AccessRole minimum) {
        if (!shouldCheck()) {
            return;
        }
        AuthUser user = requireUser();
        if (user.isAdmin()) {
            return;
        }
        Optional<PropertyAccess> grant = propertyId == null ? Optional.empty()
                : accessRepository.findFirstByPropertyIdAndUserIdAndActiveTrue(propertyId, user.userId());
        if (grant.isEmpty() || rank(grant.get().getRole()) < rank(minimum)) {
            throw ApiException.forbidden("You do not have access to this property.");
        }
    }

    /** Whether the caller holds at least {@code minimum} on this property, without throwing. */
    public boolean hasPropertyAccess(UUID propertyId, AccessRole minimum) {
        Optional<AuthUser> user = current();
        if (user.isEmpty()) {
            return !enforced;
        }
        if (user.get().isAdmin()) {
            return true;
        }
        return accessRepository.findFirstByPropertyIdAndUserIdAndActiveTrue(propertyId, user.get().userId())
                .map(a -> rank(a.getRole()) >= rank(minimum))
                .orElse(false);
    }

    /**
     * For list endpoints: the property ids the caller may see, or empty when
     * the caller is an admin or the request is an unenforced dev request (no
     * restriction).
     */
    public Optional<List<UUID>> visiblePropertyIds() {
        if (!shouldCheck()) {
            return Optional.empty();
        }
        AuthUser user = requireUser();
        if (user.isAdmin()) {
            return Optional.empty();
        }
        return Optional.of(accessRepository.findActivePropertyIdsByUserId(user.userId()));
    }

    private static int rank(AccessRole role) {
        return switch (role) {
            case OWNER -> 3;
            case MANAGER -> 2;
            case VIEW_ONLY -> 1;
        };
    }
}
