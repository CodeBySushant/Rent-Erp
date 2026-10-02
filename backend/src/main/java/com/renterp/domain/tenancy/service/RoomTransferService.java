package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.dto.CreateRoomAssignmentRequest;
import com.renterp.domain.tenancy.dto.RoomAssignmentResponse;
import com.renterp.domain.tenancy.dto.RoomTransferRequest;
import com.renterp.domain.tenancy.dto.RoomTransferResponse;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Room transfer in one transaction: the tenant's current assignment of the
 * old room ends on the transfer date and a new assignment of the vacant room
 * starts that day. Both rooms are locked while it runs, so the new room
 * cannot be given to someone else in between, and a failure leaves the
 * tenant in the old room. History is kept: the old assignment is closed, not
 * deleted, so billing for the period before the date uses the old room.
 */
@Service
public class RoomTransferService {

    private static final Logger log = LogManager.getLogger(RoomTransferService.class);

    private final TenantPropertyMembershipRepository memberships;
    private final RoomAssignmentRepository assignments;
    private final RoomRepository rooms;
    private final FloorRepository floors;
    private final MembershipService membershipService;

    public RoomTransferService(TenantPropertyMembershipRepository memberships, RoomAssignmentRepository assignments,
                               RoomRepository rooms, FloorRepository floors, MembershipService membershipService) {
        this.memberships = memberships;
        this.assignments = assignments;
        this.rooms = rooms;
        this.floors = floors;
        this.membershipService = membershipService;
    }

    @Transactional
    public RoomTransferResponse transfer(UUID membershipId, RoomTransferRequest req) {
        TenantPropertyMembership m = memberships.findById(membershipId)
                .orElseThrow(() -> ApiException.notFound("MEMBERSHIP_NOT_FOUND", "Tenancy not found."));
        if (m.getStatus() != MembershipStatus.ACTIVE) {
            throw ApiException.badRequest("TENANCY_ENDED", "This tenancy has already ended.");
        }
        if (req.getFromRoomId().equals(req.getToRoomId())) {
            throw ApiException.badRequest("SAME_ROOM", "Choose a different room.");
        }
        if (!BsCalendar.isValid(req.getEffectiveDateBs())) {
            throw ApiException.badRequest("INVALID_DATE", "The transfer date is not a valid BS date.");
        }

        // Lock both rooms in a fixed order (by id) so two transfers cannot deadlock.
        UUID first = req.getFromRoomId().compareTo(req.getToRoomId()) < 0 ? req.getFromRoomId() : req.getToRoomId();
        UUID second = first.equals(req.getFromRoomId()) ? req.getToRoomId() : req.getFromRoomId();
        rooms.findByIdForUpdate(first);
        rooms.findByIdForUpdate(second);

        RoomAssignment current = assignments.findByMembershipIdAndEffectiveToBsIsNull(membershipId).stream()
                .filter(a -> a.getRoomId().equals(req.getFromRoomId()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("NOT_IN_ROOM", "The tenant is not currently in that room."));
        if (req.getEffectiveDateBs().compareTo(current.getEffectiveFromBs()) <= 0) {
            throw ApiException.badRequest("INVALID_DATE",
                    "The transfer date must be after the tenant moved into the current room.");
        }

        Room to = rooms.findById(req.getToRoomId())
                .orElseThrow(() -> ApiException.badRequest("ROOM_NOT_FOUND", "That room does not exist."));
        Floor floor = floors.findById(to.getFloorId())
                .orElseThrow(() -> ApiException.badRequest("ROOM_NOT_FOUND", "That room does not exist."));
        if (!floor.getPropertyId().equals(m.getPropertyId())) {
            throw ApiException.badRequest("ROOM_NOT_IN_PROPERTY", "That room belongs to a different property.");
        }
        if (!to.isActive() || !floor.isActive()) {
            throw ApiException.badRequest("ROOM_INACTIVE", "That room has been removed.");
        }
        if (assignments.findFirstByRoomIdAndEffectiveToBsIsNull(to.getId()).isPresent()) {
            throw ApiException.conflict("ROOM_OCCUPIED", "Room " + to.getName() + " already has a tenant.");
        }

        // 1. Close the old room on the transfer date.
        current.setEffectiveToBs(req.getEffectiveDateBs());
        assignments.saveAndFlush(current);

        // 2. Open the new room from the same date (MembershipService re-checks property and vacancy).
        BigDecimal rent = req.getMonthlyRent() != null ? req.getMonthlyRent() : current.getMonthlyRent();
        CreateRoomAssignmentRequest assign = new CreateRoomAssignmentRequest();
        assign.setRoomId(to.getId());
        assign.setEffectiveFromBs(req.getEffectiveDateBs());
        assign.setMonthlyRent(rent);
        RoomAssignmentResponse created = membershipService.assignRoom(membershipId, assign);

        log.info("Room transfer — membership: {}, {} → {} on {}", membershipId, req.getFromRoomId(), to.getId(),
                req.getEffectiveDateBs());
        return new RoomTransferResponse(membershipId, req.getFromRoomId(), to.getId(), req.getEffectiveDateBs(),
                current.getId(), created.getId(), rent);
    }
}
