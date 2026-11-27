package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.dto.AddTenantRequest;
import com.renterp.domain.tenancy.dto.AddTenantResponse;
import com.renterp.domain.tenancy.dto.CreateRoomAssignmentRequest;
import com.renterp.domain.tenancy.dto.CreateTenantProfileRequest;
import com.renterp.domain.tenancy.dto.RoomAssignmentResponse;
import com.renterp.domain.tenancy.dto.TenantProfileResponse;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.dto.CreateAdvanceRentRequest;
import com.renterp.domain.tenantfinance.dto.CreateDepositRequest;
import com.renterp.domain.tenantfinance.dto.CreateOpeningBalanceRequest;
import com.renterp.domain.tenantfinance.dto.DepositResponse;
import com.renterp.domain.tenantfinance.service.TenantAdvanceRentService;
import com.renterp.domain.tenantfinance.service.TenantDepositService;
import com.renterp.domain.tenantfinance.service.TenantOpeningBalanceService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Add Tenant as one operation. Everything is checked before anything is
 * written, then profile → membership → room assignments → deposit → advance
 * rent → opening balance are created in a single transaction through the
 * existing services (so their own rules still apply). If any step fails,
 * nothing is kept: no tenant without a membership, no membership without a
 * room, no money record without a tenancy.
 *
 * Money (owner, 28 Nov): every tenant, new or already living there, can be
 * given the deposit and advance agreed and what of each is collected now,
 * plus an opening balance (owed by or credited to the tenant). The agreed
 * figures are kept on the tenancy; what was collected goes into the deposit
 * and advance-rent records, which billing already uses.
 *
 * A tenant can be given several rooms at once. Every room row is locked for
 * the transaction (in id order, so two requests never wait on each other), so
 * two owners (or two taps) adding a tenant to the same room are processed one
 * after the other and the second sees the room as taken; V16's unique index
 * backs this up in the database. The monthly rent is the tenant's total: it is
 * shared across the rooms (see {@link #splitRent}), and billing adds the rooms
 * back up, so the bill shows exactly the rent that was entered.
 */
@Service
public class TenantOnboardingService {

    private static final Logger log = LogManager.getLogger(TenantOnboardingService.class);

    private final PropertyRepository properties;
    private final RoomRepository rooms;
    private final FloorRepository floors;
    private final RoomAssignmentRepository assignments;
    private final TenantPropertyMembershipRepository memberships;
    private final TenantProfileService profileService;
    private final MembershipService membershipService;
    private final TenantDepositService depositService;
    private final TenantAdvanceRentService advanceService;
    private final TenantOpeningBalanceService openingBalanceService;
    private final UserRepository users;
    private final TenantProfileLinker linker;

    public TenantOnboardingService(PropertyRepository properties, RoomRepository rooms, FloorRepository floors,
                                   RoomAssignmentRepository assignments,
                                   TenantPropertyMembershipRepository memberships,
                                   TenantProfileService profileService, MembershipService membershipService,
                                   TenantDepositService depositService,
                                   TenantAdvanceRentService advanceService,
                                   TenantOpeningBalanceService openingBalanceService,
                                   UserRepository users, TenantProfileLinker linker) {
        this.advanceService = advanceService;
        this.openingBalanceService = openingBalanceService;
        this.users = users;
        this.linker = linker;
        this.properties = properties;
        this.rooms = rooms;
        this.floors = floors;
        this.assignments = assignments;
        this.memberships = memberships;
        this.profileService = profileService;
        this.membershipService = membershipService;
        this.depositService = depositService;
    }

    @Transactional
    public AddTenantResponse addTenant(UUID propertyId, AddTenantRequest req) {
        // ── 1. Check everything first ──────────────────────────────────────
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));
        if (!property.isActive()) {
            throw ApiException.badRequest("PROPERTY_INACTIVE", "This property is no longer active.");
        }
        if (!BsCalendar.isValid(req.getMoveInDateBs())) {
            throw ApiException.badRequest("INVALID_DATE", "The move-in date is not a valid BS date.");
        }
        String depositDate = req.getDepositReceivedAtBs() != null ? req.getDepositReceivedAtBs() : req.getMoveInDateBs();
        BigDecimal depositCollected = AddTenantRequest.positiveOrNull(req.getDepositAmount());
        BigDecimal advanceCollected = AddTenantRequest.positiveOrNull(req.getAdvanceAmount());
        BigDecimal opening = AddTenantRequest.positiveOrNull(req.getOpeningBalanceAmount());
        if (depositCollected != null && !BsCalendar.isValid(depositDate)) {
            throw ApiException.badRequest("INVALID_DATE", "The deposit date is not a valid BS date.");
        }
        if (moreThan(depositCollected, req.getAgreedDepositAmount())) {
            throw ApiException.badRequest("DEPOSIT_MORE_THAN_AGREED",
                    "The deposit collected cannot be more than the deposit agreed.");
        }
        if (moreThan(advanceCollected, req.getAgreedAdvanceAmount())) {
            throw ApiException.badRequest("ADVANCE_MORE_THAN_AGREED",
                    "The advance collected cannot be more than the advance agreed.");
        }
        if (opening != null && req.getOpeningBalanceDirection() == null) {
            throw ApiException.badRequest("OPENING_BALANCE_DIRECTION_REQUIRED",
                    "Say whether the opening balance is owed by the tenant or a credit to the tenant.");
        }

        List<UUID> roomIds = req.chosenRoomIds();
        if (roomIds.isEmpty()) {
            throw ApiException.badRequest("ROOM_REQUIRED", "Choose at least one room.");
        }
        if (new HashSet<>(roomIds).size() != roomIds.size()) {
            throw ApiException.badRequest("DUPLICATE_ROOM", "The same room was chosen more than once.");
        }
        // Lock in id order (deadlock-free), then keep the owner's order.
        List<UUID> lockOrder = new ArrayList<>(roomIds);
        lockOrder.sort(Comparator.naturalOrder());
        java.util.Map<UUID, Room> locked = new java.util.HashMap<>();
        for (UUID id : lockOrder) {
            Room room = rooms.findByIdForUpdate(id)
                    .orElseThrow(() -> ApiException.badRequest("ROOM_NOT_FOUND", "A chosen room does not exist."));
            Floor floor = floors.findById(room.getFloorId())
                    .orElseThrow(() -> ApiException.badRequest("ROOM_NOT_FOUND", "A chosen room does not exist."));
            if (!floor.getPropertyId().equals(propertyId)) {
                throw ApiException.badRequest("ROOM_NOT_IN_PROPERTY", "Room " + room.getName() + " belongs to a different property.");
            }
            if (!room.isActive() || !floor.isActive()) {
                throw ApiException.badRequest("ROOM_INACTIVE", "Room " + room.getName() + " has been removed.");
            }
            if (assignments.findFirstByRoomIdAndEffectiveToBsIsNull(room.getId()).isPresent()) {
                throw ApiException.conflict("ROOM_OCCUPIED", "Room " + room.getName() + " already has a tenant.");
            }
            locked.put(id, room);
        }
        List<Room> chosen = roomIds.stream().map(locked::get).toList();
        if (memberships.countByPhoneAtProperty(req.getPhone(), propertyId, MembershipStatus.ACTIVE) > 0) {
            throw ApiException.conflict("TENANT_ALREADY_ACTIVE",
                    "A tenant with this phone number already lives at this property.");
        }

        // ── 2. Create everything, or nothing ───────────────────────────────
        // C8: when the phone already belongs to an app account, the tenancy goes
        // on that person's own profile (so they see it in the app at once)
        // instead of a second, unlinked profile.
        Optional<User> appUser = users.findByPhoneAndActiveTrue(req.getPhone());
        UUID profileId = null;
        if (appUser.isPresent()) {
            Optional<TenantProfile> own = linker.linkForUser(appUser.get().getId(), req.getPhone());
            if (own.isPresent()) {
                profileId = own.get().getId();
            }
        }
        if (profileId == null) {
            profileId = createProfile(req, appUser.map(User::getId).orElse(null));
        }

        TenantPropertyMembership membership = membershipService.createInternal(
                profileId, propertyId, req.getMoveInDateBs(), req.getPaymentModelOverride(), null);
        membership.setTotalMembers(req.chosenTotalMembers());
        membership.setAgreedDepositAmount(agreedOr(req.getAgreedDepositAmount(), depositCollected));
        membership.setAgreedAdvanceAmount(agreedOr(req.getAgreedAdvanceAmount(), advanceCollected));
        memberships.save(membership);

        List<BigDecimal> rents = splitRent(req.getMonthlyRent(), chosen.size());
        List<UUID> assignmentIds = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            CreateRoomAssignmentRequest assignReq = new CreateRoomAssignmentRequest();
            assignReq.setRoomId(chosen.get(i).getId());
            assignReq.setEffectiveFromBs(req.getMoveInDateBs());
            assignReq.setMonthlyRent(rents.get(i));
            RoomAssignmentResponse assignment = membershipService.assignRoom(membership.getId(), assignReq);
            assignmentIds.add(assignment.getId());
        }

        UUID depositId = null;
        if (depositCollected != null) {
            CreateDepositRequest depositReq = new CreateDepositRequest();
            depositReq.setAmount(depositCollected);
            depositReq.setCurrency("NPR");
            depositReq.setReceivedAtBs(depositDate);
            DepositResponse deposit = depositService.create(membership.getId(), depositReq);
            depositId = deposit.getId();
        }

        UUID advanceId = null;
        if (advanceCollected != null) {
            short months = advanceMonths(advanceCollected, req.getMonthlyRent());
            CreateAdvanceRentRequest advanceReq = new CreateAdvanceRentRequest();
            advanceReq.setAmount(advanceCollected);
            advanceReq.setMonthsCovered(months);
            advanceReq.setCoveredFromBs(req.getMoveInDateBs());
            advanceReq.setCoveredToBs(BsCalendar.addDays(req.getMoveInDateBs(), 30 * months - 1));
            advanceReq.setNotes("Collected at Add Tenant");
            advanceId = advanceService.create(membership.getId(), advanceReq).getId();
        }

        UUID openingId = null;
        if (opening != null) {
            CreateOpeningBalanceRequest openingReq = new CreateOpeningBalanceRequest();
            openingReq.setAmount(opening);
            openingReq.setDirection(req.getOpeningBalanceDirection());
            openingReq.setAsOfBs(req.getMoveInDateBs());
            openingReq.setNotes("Entered at Add Tenant");
            openingId = openingBalanceService.create(membership.getId(), openingReq).getId();
        }

        log.info("Tenant added — membership: {}, rooms: {}, members: {}, deposit: {}, advance: {}, opening: {}",
                membership.getId(), chosen.size(), membership.getTotalMembers(),
                depositId != null, advanceId != null, openingId != null);
        return new AddTenantResponse(membership.getId(), profileId, assignmentIds.get(0), depositId,
                propertyId, roomIds.get(0), List.copyOf(assignmentIds), List.copyOf(roomIds),
                membership.getTotalMembers(), advanceId, openingId);
    }

    /** True when both are given and {@code collected} is more than {@code agreed}. */
    static boolean moreThan(BigDecimal collected, BigDecimal agreed) {
        return collected != null && agreed != null && collected.compareTo(agreed) > 0;
    }

    /** The agreed amount when given, else what was collected (or null). */
    static BigDecimal agreedOr(BigDecimal agreed, BigDecimal collected) {
        return agreed != null ? agreed : collected;
    }

    /**
     * Months an advance covers from the move-in date: the advance ÷ the monthly
     * rent, rounded up (a part month counts), at least 1 and at most
     * {@value #MAX_ADVANCE_MONTHS}. Billing uses the advance amount itself; the
     * months are what the record shows.
     */
    static short advanceMonths(BigDecimal advance, BigDecimal monthlyRent) {
        if (monthlyRent == null || monthlyRent.signum() <= 0) {
            return 1;
        }
        BigDecimal months = advance.divide(monthlyRent, 0, RoundingMode.CEILING);
        return (short) Math.max(1, Math.min(MAX_ADVANCE_MONTHS, months.intValue()));
    }

    static final int MAX_ADVANCE_MONTHS = 60;

    /**
     * Shares a monthly rent across {@code rooms} rooms: equal parts rounded
     * down to the paisa, the leftover paisa on the first room, so the parts
     * always add up to exactly {@code total}. One room gets all of it.
     */
    static List<BigDecimal> splitRent(BigDecimal total, int rooms) {
        if (rooms <= 0) {
            throw new IllegalArgumentException("rooms must be at least 1");
        }
        BigDecimal amount = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal part = amount.divide(BigDecimal.valueOf(rooms), 2, RoundingMode.DOWN);
        BigDecimal first = amount.subtract(part.multiply(BigDecimal.valueOf(rooms - 1L)));
        List<BigDecimal> parts = new ArrayList<>(rooms);
        parts.add(first);
        for (int i = 1; i < rooms; i++) {
            parts.add(part);
        }
        return parts;
    }

    /** A new profile from the Add Tenant form, linked to {@code userId} when given. */
    private UUID createProfile(AddTenantRequest req, UUID userId) {
        CreateTenantProfileRequest profileReq = new CreateTenantProfileRequest();
        profileReq.setUserId(userId);
        profileReq.setFullName(req.getFullName().trim());
        profileReq.setPhone(req.getPhone());
        profileReq.setWhatsappNumber(req.getWhatsappNumber());
        profileReq.setPreferredLanguage(req.getPreferredLanguage() == null ? "en" : req.getPreferredLanguage());
        profileReq.setNumOccupants(req.chosenTotalMembers());
        profileReq.setEmergencyContactName(req.getEmergencyContactName());
        profileReq.setEmergencyContactPhone(req.getEmergencyContactPhone());
        return profileService.createProfile(profileReq).getId();
    }
}
