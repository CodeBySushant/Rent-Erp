package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.ApiException;
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
import com.renterp.domain.tenantfinance.dto.CreateDepositRequest;
import com.renterp.domain.tenantfinance.dto.DepositResponse;
import com.renterp.domain.tenantfinance.service.TenantDepositService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Add Tenant as one operation. Everything is checked before anything is
 * written, then profile → membership → room assignment → deposit are created
 * in a single transaction through the existing services (so their own rules
 * still apply). If any step fails, nothing is kept: no tenant without a
 * membership, no membership without a room, no deposit without a tenancy.
 *
 * The room row is locked for the transaction, so two owners (or two taps)
 * adding a tenant to the same room are processed one after the other and the
 * second sees the room as taken; V16's unique index backs this up in the
 * database.
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

    public TenantOnboardingService(PropertyRepository properties, RoomRepository rooms, FloorRepository floors,
                                   RoomAssignmentRepository assignments,
                                   TenantPropertyMembershipRepository memberships,
                                   TenantProfileService profileService, MembershipService membershipService,
                                   TenantDepositService depositService) {
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
        if (req.getDepositAmount() != null && !BsCalendar.isValid(depositDate)) {
            throw ApiException.badRequest("INVALID_DATE", "The deposit date is not a valid BS date.");
        }

        Room room = rooms.findByIdForUpdate(req.getRoomId())
                .orElseThrow(() -> ApiException.badRequest("ROOM_NOT_FOUND", "That room does not exist."));
        Floor floor = floors.findById(room.getFloorId())
                .orElseThrow(() -> ApiException.badRequest("ROOM_NOT_FOUND", "That room does not exist."));
        if (!floor.getPropertyId().equals(propertyId)) {
            throw ApiException.badRequest("ROOM_NOT_IN_PROPERTY", "That room belongs to a different property.");
        }
        if (!room.isActive() || !floor.isActive()) {
            throw ApiException.badRequest("ROOM_INACTIVE", "That room has been removed.");
        }
        if (assignments.findFirstByRoomIdAndEffectiveToBsIsNull(room.getId()).isPresent()) {
            throw ApiException.conflict("ROOM_OCCUPIED", "Room " + room.getName() + " already has a tenant.");
        }
        if (memberships.countByPhoneAtProperty(req.getPhone(), propertyId, MembershipStatus.ACTIVE) > 0) {
            throw ApiException.conflict("TENANT_ALREADY_ACTIVE",
                    "A tenant with this phone number already lives at this property.");
        }

        // ── 2. Create everything, or nothing ───────────────────────────────
        CreateTenantProfileRequest profileReq = new CreateTenantProfileRequest();
        profileReq.setFullName(req.getFullName().trim());
        profileReq.setPhone(req.getPhone());
        profileReq.setWhatsappNumber(req.getWhatsappNumber());
        profileReq.setPreferredLanguage(req.getPreferredLanguage() == null ? "en" : req.getPreferredLanguage());
        profileReq.setNumOccupants(req.getNumOccupants());
        profileReq.setEmergencyContactName(req.getEmergencyContactName());
        profileReq.setEmergencyContactPhone(req.getEmergencyContactPhone());
        TenantProfileResponse profile = profileService.createProfile(profileReq);

        TenantPropertyMembership membership = membershipService.createInternal(
                profile.getId(), propertyId, req.getMoveInDateBs(), req.getPaymentModelOverride(), null);

        CreateRoomAssignmentRequest assignReq = new CreateRoomAssignmentRequest();
        assignReq.setRoomId(room.getId());
        assignReq.setEffectiveFromBs(req.getMoveInDateBs());
        assignReq.setMonthlyRent(req.getMonthlyRent());
        RoomAssignmentResponse assignment = membershipService.assignRoom(membership.getId(), assignReq);

        UUID depositId = null;
        if (req.getDepositAmount() != null) {
            CreateDepositRequest depositReq = new CreateDepositRequest();
            depositReq.setAmount(req.getDepositAmount());
            depositReq.setCurrency("NPR");
            depositReq.setReceivedAtBs(depositDate);
            DepositResponse deposit = depositService.create(membership.getId(), depositReq);
            depositId = deposit.getId();
        }

        log.info("Tenant added — property: {}, room: {}, membership: {}", propertyId, room.getId(), membership.getId());
        return new AddTenantResponse(membership.getId(), profile.getId(), assignment.getId(), depositId,
                propertyId, room.getId());
    }
}
