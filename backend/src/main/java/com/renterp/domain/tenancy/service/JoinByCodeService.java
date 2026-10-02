package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import com.renterp.domain.tenancy.dto.CreateJoinRequestRequest;
import com.renterp.domain.tenancy.dto.CreateTenantProfileRequest;
import com.renterp.domain.tenancy.dto.JoinLookupResponse;
import com.renterp.domain.tenancy.dto.JoinRequestResponse;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.JoinRequestRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Joining a property by its share code. The tenant never handles internal
 * ids: they enter the code, see the property, and send a request; the
 * membership only exists after the owner accepts (JoinRequestService.accept).
 *
 * A signed-in user without a tenant profile gets one, linked to their account
 * and filled from it, the first time they ask to join.
 */
@Service
public class JoinByCodeService {

    /** Look-ups per user in the window: enough for typos, too few to guess codes. */
    private static final int LOOKUPS_PER_WINDOW = 20;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final PropertyRepository properties;
    private final UserRepository users;
    private final TenantProfileRepository profiles;
    private final TenantPropertyMembershipRepository memberships;
    private final JoinRequestRepository joinRequests;
    private final TenantProfileService profileService;
    private final JoinRequestService joinRequestService;
    private final AccessGuard guard;
    private final Map<UUID, Deque<Instant>> lookups = new ConcurrentHashMap<>();

    public JoinByCodeService(PropertyRepository properties, UserRepository users, TenantProfileRepository profiles,
                             TenantPropertyMembershipRepository memberships, JoinRequestRepository joinRequests,
                             TenantProfileService profileService, JoinRequestService joinRequestService,
                             AccessGuard guard) {
        this.properties = properties;
        this.users = users;
        this.profiles = profiles;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.profileService = profileService;
        this.joinRequestService = joinRequestService;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public JoinLookupResponse lookup(String rawCode) {
        AuthUser user = guard.requireUser();
        rateLimit(user.userId());
        Property p = findActive(rawCode);
        Optional<TenantProfile> profile = profiles.findByUserId(user.userId());
        boolean member = profile.map(tp -> memberships.findByTenantProfileIdAndPropertyIdAndStatus(
                tp.getId(), p.getId(), MembershipStatus.ACTIVE).isPresent()).orElse(false);
        boolean pending = profile.map(tp -> joinRequests.findByTenantProfileIdAndPropertyIdAndStatus(
                tp.getId(), p.getId(), JoinRequestStatus.PENDING).isPresent()).orElse(false);
        String landlord = users.findById(p.getOwnerUserId()).map(User::getName).orElse(null);
        return new JoinLookupResponse(p.getId(), p.getJoinCode(), p.getName(), p.getAddress(), p.getCity(),
                landlord, member, pending);
    }

    @Transactional
    public JoinRequestResponse join(String rawCode, String message) {
        AuthUser user = guard.requireUser();
        Property p = findActive(rawCode);
        if (guard.hasPropertyAccess(p.getId(), AccessRole.VIEW_ONLY)) {
            throw ApiException.badRequest("OWN_PROPERTY", "You already manage this property.");
        }
        TenantProfile profile = profiles.findByUserId(user.userId()).orElseGet(() -> createOwnProfile(user));
        if (memberships.findByTenantProfileIdAndPropertyIdAndStatus(
                profile.getId(), p.getId(), MembershipStatus.ACTIVE).isPresent()) {
            throw ApiException.conflict("ALREADY_MEMBER", "You already live at this property.");
        }
        if (joinRequests.findByTenantProfileIdAndPropertyIdAndStatus(
                profile.getId(), p.getId(), JoinRequestStatus.PENDING).isPresent()) {
            throw ApiException.conflict("REQUEST_PENDING",
                    "You have already asked to join. Wait for the owner to respond.");
        }
        CreateJoinRequestRequest req = new CreateJoinRequestRequest();
        req.setTenantProfileId(profile.getId());
        req.setPropertyId(p.getId());
        req.setMessage(message);
        return joinRequestService.create(req);
    }

    private TenantProfile createOwnProfile(AuthUser user) {
        User u = users.findById(user.userId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Please log in to continue."));
        CreateTenantProfileRequest req = new CreateTenantProfileRequest();
        req.setUserId(u.getId());
        req.setFullName(u.getName() == null || u.getName().isBlank() ? u.getPhone() : u.getName());
        req.setPhone(u.getPhone());
        req.setPreferredLanguage(u.getPreferredLanguage());
        UUID id = profileService.createProfile(req).getId();
        return profiles.findById(id).orElseThrow();
    }

    private Property findActive(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        return properties.findByJoinCode(code)
                .filter(Property::isActive)
                .orElseThrow(() -> ApiException.notFound("JOIN_CODE_NOT_FOUND",
                        "No property has this code. Check it with your landlord."));
    }

    private void rateLimit(UUID userId) {
        Instant now = Instant.now();
        Deque<Instant> q = lookups.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) {
            q.pollFirst();
        }
        if (q.size() >= LOOKUPS_PER_WINDOW) {
            throw ApiException.tooManyRequests("JOIN_LOOKUP_LIMIT",
                    "Too many property code look-ups. Try again in a few minutes.");
        }
        q.addLast(now);
    }
}
