package com.renterp.domain.tenancy.service;

import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.tenancy.entity.JoinRequest;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import com.renterp.domain.tenancy.entity.PropertyBlockedTenant;
import com.renterp.domain.tenancy.entity.TenantKyc;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.JoinRequestRepository;
import com.renterp.domain.tenancy.repository.PropertyBlockedTenantRepository;
import com.renterp.domain.tenancy.repository.TenantKycRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One person, one tenant profile (C8).
 *
 * An owner can add a tenant by name and phone before that person has the app;
 * that creates an unlinked profile. Before this class existed, signing up later
 * (and then joining by code or verifying ID) created a second profile, so the
 * tenant never saw the bills the owner had already issued.
 *
 * {@link #linkForUser} runs after sign-up and every login (and before a profile
 * would be created for a signed-in user). For the user's verified phone it:
 * <ol>
 *   <li>keeps the user's own profile if one exists, otherwise links the best
 *       unlinked profile with that phone (one with an active tenancy, newest
 *       first);</li>
 *   <li>moves every other unlinked profile's tenancies, join requests, blocks,
 *       designated meters and (if the kept profile has none) identity record
 *       onto the kept profile, then deactivates the empty profile. Nothing is
 *       deleted.</li>
 * </ol>
 * A tenancy that would clash (both profiles active at the same property) is
 * left where it is and logged; the profile holding it stays unlinked for
 * support to resolve.
 *
 * The phone was proven with a one-time code, so the person signing in owns the
 * number the owner typed.
 */
@Service
public class TenantProfileLinker {

    private static final Logger log = LogManager.getLogger(TenantProfileLinker.class);

    private final TenantProfileRepository profiles;
    private final TenantPropertyMembershipRepository memberships;
    private final JoinRequestRepository joinRequests;
    private final PropertyBlockedTenantRepository blocks;
    private final TenantKycRepository kycs;
    private final MeterRepository meters;

    public TenantProfileLinker(TenantProfileRepository profiles, TenantPropertyMembershipRepository memberships,
                               JoinRequestRepository joinRequests, PropertyBlockedTenantRepository blocks,
                               TenantKycRepository kycs, MeterRepository meters) {
        this.profiles = profiles;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.blocks = blocks;
        this.kycs = kycs;
        this.meters = meters;
    }

    /**
     * Links (and merges) the unlinked profiles carrying {@code phone} to
     * {@code userId}. Returns the user's profile afterwards, if they have one.
     */
    @Transactional
    public Optional<TenantProfile> linkForUser(UUID userId, String phone) {
        Optional<TenantProfile> own = profiles.findByUserId(userId);
        if (phone == null || phone.isBlank()) {
            return own;
        }
        List<TenantProfile> orphans = new ArrayList<>(
                profiles.findByPhoneAndUserIdIsNullAndActiveTrueOrderByCreatedAtDesc(phone));
        if (orphans.isEmpty()) {
            return own;
        }

        TenantProfile primary;
        if (own.isPresent()) {
            primary = own.get();
        } else {
            primary = pickPrimary(orphans);
            orphans.remove(primary);
            primary.setUserId(userId);
            primary = profiles.saveAndFlush(primary);
            log.info("Tenant profile linked to account — profile: {}, user: {}", primary.getId(), userId);
        }
        for (TenantProfile other : orphans) {
            absorb(primary, other);
        }
        return Optional.of(primary);
    }

    /** An unlinked profile with an active tenancy wins; then the newest. */
    private TenantProfile pickPrimary(List<TenantProfile> candidates) {
        return candidates.stream()
                .sorted(Comparator
                        .comparing((TenantProfile p) -> !memberships.existsByTenantProfileIdAndStatus(
                                p.getId(), MembershipStatus.ACTIVE))
                        .thenComparing(TenantProfile::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElseThrow();
    }

    /** Moves everything of {@code other} onto {@code primary}; deactivates {@code other} when empty. */
    private void absorb(TenantProfile primary, TenantProfile other) {
        boolean clash = false;

        for (TenantPropertyMembership m : memberships.findByTenantProfileId(other.getId(), Pageable.unpaged())) {
            if (m.getStatus() == MembershipStatus.ACTIVE && memberships.findByTenantProfileIdAndPropertyIdAndStatus(
                    primary.getId(), m.getPropertyId(), MembershipStatus.ACTIVE).isPresent()) {
                log.warn("Profile merge clash — tenancy {} stays on profile {} (already active at property {})",
                        m.getId(), other.getId(), m.getPropertyId());
                clash = true;
                continue;
            }
            m.setTenantProfileId(primary.getId());
            memberships.save(m);
        }

        for (JoinRequest j : joinRequests.findByTenantProfileId(other.getId(), Pageable.unpaged())) {
            if (j.getStatus() == JoinRequestStatus.PENDING && joinRequests.findByTenantProfileIdAndPropertyIdAndStatus(
                    primary.getId(), j.getPropertyId(), JoinRequestStatus.PENDING).isPresent()) {
                j.setStatus(JoinRequestStatus.CANCELLED);
                j.setRespondedAt(Instant.now());
            }
            j.setTenantProfileId(primary.getId());
            joinRequests.save(j);
        }

        for (PropertyBlockedTenant b : blocks.findByTenantProfileIdAndActive(other.getId(), true)) {
            if (blocks.existsByPropertyIdAndTenantProfileIdAndActive(b.getPropertyId(), primary.getId(), true)) {
                continue;
            }
            b.setTenantProfileId(primary.getId());
            blocks.save(b);
        }

        meters.moveDesignatedTenant(other.getId(), primary.getId());

        Optional<TenantKyc> otherKyc = kycs.findByTenantProfileId(other.getId());
        if (otherKyc.isPresent() && kycs.findByTenantProfileId(primary.getId()).isEmpty()) {
            otherKyc.get().setTenantProfileId(primary.getId());
            kycs.save(otherKyc.get());
        }

        if (!clash) {
            other.setActive(false);
            profiles.save(other);
            log.info("Tenant profile merged — {} into {}", other.getId(), primary.getId());
        }
    }
}
