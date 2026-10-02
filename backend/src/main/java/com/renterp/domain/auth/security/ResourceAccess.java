package com.renterp.domain.auth.security;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.billing.repository.BillingRunRepository;
import com.renterp.domain.billing.repository.TenantBillRepository;
import com.renterp.domain.charge.repository.ChargeTemplateRepository;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.meterreading.repository.MeterCoverageEventRepository;
import com.renterp.domain.meterreading.repository.MeterReadingRepository;
import com.renterp.domain.moveout.repository.MoveOutRepository;
import com.renterp.domain.payment.repository.PaymentRepository;
import com.renterp.domain.propertyaccess.entity.PropertyAccess.AccessRole;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.repository.JoinRequestRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.repository.RentIncrementRepository;
import com.renterp.domain.tenantfinance.repository.TenantAdvanceRentRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authorization for every resource that hangs off a property. Controllers call
 * one of these before handing the request to the service, so the services keep
 * their internal calls to each other unchanged.
 *
 * <p>Each method resolves the resource to its property and asks
 * {@link AccessGuard}: {@link Level#READ} needs VIEW_ONLY, {@link Level#WRITE}
 * needs MANAGER (an OWNER passes both). A tenant with an app account may read
 * their own membership and everything under it (bills, deposit, advance rent,
 * rent increments, room assignments) but never write through these endpoints.
 *
 * <p>An id that does not exist passes here and the service answers 404 as
 * before; a resource that exists but belongs to someone else is 403.
 */
@Component
public class ResourceAccess {

    public enum Level {
        READ(AccessRole.VIEW_ONLY), WRITE(AccessRole.MANAGER);

        final AccessRole role;

        Level(AccessRole role) {
            this.role = role;
        }
    }

    private final AccessGuard guard;
    private final FloorRepository floors;
    private final RoomRepository rooms;
    private final ChargeTemplateRepository charges;
    private final MeterRepository meters;
    private final MeterReadingRepository readings;
    private final MeterCoverageEventRepository coverageEvents;
    private final TenantProfileRepository profiles;
    private final TenantPropertyMembershipRepository memberships;
    private final JoinRequestRepository joinRequests;
    private final TenantAdvanceRentRepository advances;
    private final RentIncrementRepository increments;
    private final BillingRunRepository runs;
    private final TenantBillRepository bills;
    private final PaymentRepository payments;
    private final MoveOutRepository moveOuts;

    public ResourceAccess(AccessGuard guard,
                          FloorRepository floors,
                          RoomRepository rooms,
                          ChargeTemplateRepository charges,
                          MeterRepository meters,
                          MeterReadingRepository readings,
                          MeterCoverageEventRepository coverageEvents,
                          TenantProfileRepository profiles,
                          TenantPropertyMembershipRepository memberships,
                          JoinRequestRepository joinRequests,
                          TenantAdvanceRentRepository advances,
                          RentIncrementRepository increments,
                          BillingRunRepository runs,
                          TenantBillRepository bills,
                          PaymentRepository payments,
                          MoveOutRepository moveOuts) {
        this.guard = guard;
        this.floors = floors;
        this.rooms = rooms;
        this.charges = charges;
        this.meters = meters;
        this.readings = readings;
        this.coverageEvents = coverageEvents;
        this.profiles = profiles;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.advances = advances;
        this.increments = increments;
        this.runs = runs;
        this.bills = bills;
        this.payments = payments;
        this.moveOuts = moveOuts;
    }

    // ── Property and lists ──────────────────────────────────────────────────

    public void property(UUID propertyId, Level level) {
        guard.requirePropertyAccess(propertyId, level.role);
    }

    /**
     * For list endpoints whose property filter is optional. A checked caller
     * who is not an admin must name a property they can see; admins and
     * unenforced developer requests keep the unfiltered list.
     */
    public void propertyList(UUID propertyId) {
        if (!guard.checking()) {
            return;
        }
        if (propertyId != null) {
            property(propertyId, Level.READ);
            return;
        }
        if (!guard.requireUser().isAdmin()) {
            throw ApiException.badRequest("PROPERTY_ID_REQUIRED",
                    "Choose a property: propertyId is required.");
        }
    }

    // ── Structure, charges, meters, readings ────────────────────────────────

    public void floor(UUID floorId, Level level) {
        floors.findById(floorId).ifPresent(f -> property(f.getPropertyId(), level));
    }

    public void room(UUID roomId, Level level) {
        rooms.findById(roomId).ifPresent(r -> floor(r.getFloorId(), level));
    }

    public void chargeTemplate(UUID id, Level level) {
        charges.findById(id).ifPresent(c -> property(c.getPropertyId(), level));
    }

    public void meter(UUID meterId, Level level) {
        meters.findById(meterId).ifPresent(m -> property(m.getPropertyId(), level));
    }

    public void reading(UUID readingId, Level level) {
        readings.findById(readingId).ifPresent(r -> property(r.getPropertyId(), level));
    }

    public void coverageEvent(UUID eventId, Level level) {
        coverageEvents.findById(eventId).ifPresent(e -> property(e.getPropertyId(), level));
    }

    // ── Tenancy ─────────────────────────────────────────────────────────────

    /** True when the caller is the app account linked to this tenant profile. */
    public boolean isSelfProfile(UUID tenantProfileId) {
        Optional<AuthUser> user = guard.current();
        if (user.isEmpty() || tenantProfileId == null) {
            return false;
        }
        return profiles.findById(tenantProfileId)
                .map(p -> user.get().userId().equals(p.getUserId()))
                .orElse(false);
    }

    /** The caller's own tenant profile, or 403. Used where only the tenant may act. */
    public void requireSelfProfile(UUID tenantProfileId) {
        if (!guard.checking()) {
            return;
        }
        AuthUser user = guard.requireUser();
        if (user.isAdmin()) {
            return;
        }
        if (!isSelfProfile(tenantProfileId)) {
            throw ApiException.forbidden("You can only act for your own tenant profile.");
        }
    }

    /**
     * A tenant profile: its own account and its creator always pass; anyone
     * else needs the level on a property where the profile has a membership or
     * a join request.
     */
    public void tenantProfile(UUID tenantProfileId, Level level) {
        if (!guard.checking()) {
            return;
        }
        AuthUser user = guard.requireUser();
        if (user.isAdmin()) {
            return;
        }
        Optional<TenantProfile> profile = profiles.findById(tenantProfileId);
        if (profile.isEmpty()) {
            return;
        }
        TenantProfile p = profile.get();
        if (user.userId().equals(p.getUserId()) || user.userId().equals(p.getCreatedBy())) {
            return;
        }
        Set<UUID> propertyIds = new LinkedHashSet<>(memberships.findPropertyIdsByTenantProfileId(tenantProfileId));
        propertyIds.addAll(joinRequests.findPropertyIdsByTenantProfileId(tenantProfileId));
        for (UUID propertyId : propertyIds) {
            if (guard.hasPropertyAccess(propertyId, level.role)) {
                return;
            }
        }
        throw ApiException.forbidden("You do not have access to this tenant.");
    }

    /**
     * Approving, rejecting or flagging a tenant's KYC: someone who manages the
     * tenant (see {@link #tenantProfile}), never the tenant themself.
     */
    public void tenantProfileDecide(UUID tenantProfileId) {
        if (!guard.checking()) {
            return;
        }
        if (!guard.requireUser().isAdmin() && isSelfProfile(tenantProfileId)) {
            throw ApiException.forbidden("You cannot verify your own identity documents.");
        }
        tenantProfile(tenantProfileId, Level.WRITE);
    }

    /**
     * Creating a profile: a non-admin may create an unlinked profile, or one
     * linked to their own account - never one linked to someone else's.
     */
    public void createTenantProfile(UUID linkedUserId) {
        if (!guard.checking() || linkedUserId == null) {
            return;
        }
        AuthUser user = guard.requireUser();
        if (!user.isAdmin() && !user.userId().equals(linkedUserId)) {
            throw ApiException.forbidden("A tenant profile can only be linked to your own account.");
        }
    }

    /** A membership: property access at the level, or (read only) the tenant themself. */
    public void membership(UUID membershipId, Level level) {
        memberships.findById(membershipId).ifPresent(m -> {
            if (level == Level.READ && isSelfProfile(m.getTenantProfileId())) {
                return;
            }
            property(m.getPropertyId(), level);
        });
    }

    /**
     * Membership / join-request lists: by property (property access), or by
     * tenant profile when it is the caller's own; otherwise a property is required.
     */
    public void tenancyList(UUID propertyId, UUID tenantProfileId) {
        if (propertyId != null) {
            property(propertyId, Level.READ);
            return;
        }
        if (tenantProfileId != null && guard.checking() && !guard.requireUser().isAdmin()) {
            requireSelfProfile(tenantProfileId);
            return;
        }
        propertyList(null);
    }

    /** Reading a join request: the requesting tenant or anyone who can view the property. */
    public void joinRequestRead(UUID joinRequestId) {
        joinRequests.findById(joinRequestId).ifPresent(j -> {
            if (!isSelfProfile(j.getTenantProfileId())) {
                property(j.getPropertyId(), Level.READ);
            }
        });
    }

    /** Accepting or rejecting a join request: a manager of the property. */
    public void joinRequestDecide(UUID joinRequestId) {
        joinRequests.findById(joinRequestId).ifPresent(j -> property(j.getPropertyId(), Level.WRITE));
    }

    /** Withdrawing a join request: the requesting tenant, or a manager of the property. */
    public void joinRequestCancel(UUID joinRequestId) {
        joinRequests.findById(joinRequestId).ifPresent(j -> {
            if (!isSelfProfile(j.getTenantProfileId())) {
                property(j.getPropertyId(), Level.WRITE);
            }
        });
    }

    /**
     * Creating a join request: the tenant asking for their own profile, or a
     * manager of the property entering it for an unlinked tenant.
     */
    public void createJoinRequest(UUID tenantProfileId, UUID propertyId) {
        if (!guard.checking() || isSelfProfile(tenantProfileId)) {
            return;
        }
        property(propertyId, Level.WRITE);
    }

    // ── Finance and billing ─────────────────────────────────────────────────

    public void advanceRent(UUID id, Level level) {
        advances.findById(id).ifPresent(a -> membership(a.getMembershipId(), level));
    }

    public void rentIncrement(UUID id, Level level) {
        increments.findById(id).ifPresent(i -> membership(i.getMembershipId(), level));
    }

    public void billingRun(UUID runId, Level level) {
        runs.findById(runId).ifPresent(r -> property(r.getPropertyId(), level));
    }

    /** A bill: the property at the level, or (read only) the tenant it was issued to. */
    public void tenantBill(UUID billId, Level level) {
        bills.findById(billId).ifPresent(b -> {
            if (level == Level.READ) {
                membership(b.getMembershipId(), Level.READ);
            } else {
                property(b.getPropertyId(), level);
            }
        });
    }

    /** Sending payment proof: only the tenant the bill was issued to. */
    public void billOwnTenant(UUID billId) {
        if (!guard.checking()) {
            return;
        }
        guard.requireUser();
        bills.findById(billId).ifPresent(b -> memberships.findById(b.getMembershipId()).ifPresent(m -> {
            if (!isSelfProfile(m.getTenantProfileId())) {
                throw ApiException.forbidden("Only the tenant of this bill can send payment proof.");
            }
        }));
    }

    /** A payment: the property at the level, or (read only) the tenant it belongs to. */
    public void payment(UUID paymentId, Level level) {
        payments.findById(paymentId).ifPresent(p -> {
            if (level == Level.READ) {
                membership(p.getMembershipId(), Level.READ);
            } else {
                property(p.getPropertyId(), level);
            }
        });
    }

    /** Withdrawing a proof: only the tenant who sent it. */
    public void paymentOwnTenant(UUID paymentId) {
        if (!guard.checking()) {
            return;
        }
        AuthUser user = guard.requireUser();
        payments.findById(paymentId).ifPresent(p -> {
            if (!user.isAdmin() && !user.userId().equals(p.getSubmittedBy())) {
                throw ApiException.forbidden("Only the tenant who sent this payment can withdraw it.");
            }
        });
    }

    /** The tenant themself, or a manager of the membership's property. */
    public void membershipSelfOrManager(UUID membershipId) {
        memberships.findById(membershipId).ifPresent(m -> {
            if (!isSelfProfile(m.getTenantProfileId())) {
                property(m.getPropertyId(), Level.WRITE);
            }
        });
    }

    /**
     * A move-out: managers of the property; with {@code allowTenant} also the
     * tenant whose tenancy it is (withdrawing their own notice).
     */
    public void moveOut(UUID moveOutId, boolean allowTenant) {
        moveOuts.findById(moveOutId).ifPresent(mo -> {
            if (allowTenant) {
                membershipSelfOrManager(mo.getMembershipId());
            } else {
                property(mo.getPropertyId(), Level.WRITE);
            }
        });
    }

    /** National reference data (tariffs): anyone signed in reads, only an admin writes. */
    public void requireAdmin() {
        guard.requireAdmin();
    }
}
