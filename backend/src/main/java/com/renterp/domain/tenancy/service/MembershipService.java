package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.dto.*;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MembershipService {

    private static final Logger log = LogManager.getLogger(MembershipService.class);

    private final TenantPropertyMembershipRepository membershipRepository;
    private final RoomAssignmentRepository assignmentRepository;
    private final TenantProfileRepository profileRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;

    public MembershipService(TenantPropertyMembershipRepository membershipRepository,
                              RoomAssignmentRepository assignmentRepository,
                              TenantProfileRepository profileRepository,
                              PropertyRepository propertyRepository,
                              RoomRepository roomRepository,
                              FloorRepository floorRepository) {
        this.membershipRepository = membershipRepository;
        this.assignmentRepository = assignmentRepository;
        this.profileRepository = profileRepository;
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.floorRepository = floorRepository;
    }

    // ── Membership ──────────────────────────────────────────────────────────

    @Transactional
    public MembershipResponse createDirect(CreateMembershipRequest req) {
        // Direct-create path used by unlinked/existing-tenant onboarding (spec §10.3 / T7).
        // The join-request-accept path calls createInternal() instead so the linkedFromJoinRequestId
        // is populated.
        return MembershipResponse.from(createInternal(req.getTenantProfileId(), req.getPropertyId(),
                req.getStartedAtBs(), req.getPaymentModelOverride(), null));
    }

    // Package-visible — invoked by JoinRequestService.accept().
    @Transactional
    public TenantPropertyMembership createInternal(UUID tenantProfileId, UUID propertyId, String startedAtBs,
                                                     TenantPropertyMembership.PaymentModelOverride paymentModelOverride,
                                                     UUID linkedFromJoinRequestId) {
        if (!profileRepository.existsById(tenantProfileId)) {
            throw new ResourceNotFoundException("TenantProfile", "id", tenantProfileId);
        }
        if (!propertyRepository.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property", "id", propertyId);
        }
        // Room-level assignments enforce single-occupancy separately; here we enforce
        // that this tenant is in this property at most once at any time.
        if (membershipRepository.findByTenantProfileIdAndPropertyIdAndStatus(tenantProfileId, propertyId, MembershipStatus.ACTIVE).isPresent()) {
            throw new DuplicateResourceException("TenantPropertyMembership",
                    "tenantProfileId+propertyId (active)", tenantProfileId + "+" + propertyId);
        }

        TenantPropertyMembership m = TenantPropertyMembership.builder()
                .tenantProfileId(tenantProfileId)
                .propertyId(propertyId)
                .status(MembershipStatus.ACTIVE)
                .paymentModelOverride(paymentModelOverride)
                .linkedFromJoinRequestId(linkedFromJoinRequestId)
                .startedAtBs(startedAtBs)
                .build();
        TenantPropertyMembership saved = membershipRepository.saveAndFlush(m);
        log.info("Membership created — id: {}, tenant: {}, property: {}", saved.getId(), tenantProfileId, propertyId);
        return saved;
    }

    @Transactional(readOnly = true)
    public MembershipResponse getById(UUID id) { return MembershipResponse.from(requireMembership(id)); }

    @Transactional(readOnly = true)
    public Page<MembershipResponse> list(UUID propertyId, UUID tenantProfileId, MembershipStatus status, Pageable pageable) {
        Page<TenantPropertyMembership> page;
        if (propertyId != null && status != null) {
            page = membershipRepository.findByPropertyIdAndStatus(propertyId, status, pageable);
        } else if (propertyId != null) {
            page = membershipRepository.findByPropertyId(propertyId, pageable);
        } else if (tenantProfileId != null) {
            page = membershipRepository.findByTenantProfileId(tenantProfileId, pageable);
        } else {
            page = membershipRepository.findAll(pageable);
        }
        return page.map(MembershipResponse::from);
    }

    @Transactional
    public MembershipResponse update(UUID id, UpdateMembershipRequest req) {
        TenantPropertyMembership m = requireMembership(id);
        if (m.getStatus() != MembershipStatus.ACTIVE) {
            throw new InvalidOperationException("Only ACTIVE memberships may be updated");
        }
        // paymentModelOverride is nullable — null in the request means "no change". To
        // explicitly clear the override (fall back to property default), the client
        // sends the update endpoint's dedicated clear path in a later commit. For now
        // we only permit setting/changing; clearing is a rare admin op we haven't scoped.
        if (req.getPaymentModelOverride() != null) {
            m.setPaymentModelOverride(req.getPaymentModelOverride());
        }
        return MembershipResponse.from(membershipRepository.saveAndFlush(m));
    }

    @Transactional
    public MembershipResponse terminate(UUID id, TerminateMembershipRequest req) {
        TenantPropertyMembership m = requireMembership(id);
        if (m.getStatus() == MembershipStatus.TERMINATED) {
            throw new InvalidOperationException("Membership is already TERMINATED");
        }
        // Also end every active room_assignment on the same date. The tenant's rooms
        // logically release when the membership ends.
        for (RoomAssignment a : assignmentRepository.findByMembershipIdOrderByEffectiveFromBsAsc(id)) {
            if (a.getEffectiveToBs() == null) {
                a.setEffectiveToBs(req.getEndedAtBs());
                assignmentRepository.saveAndFlush(a);
            }
        }
        m.setStatus(MembershipStatus.TERMINATED);
        m.setEndedAtBs(req.getEndedAtBs());
        m.setTerminationReason(req.getReason());
        m.setActive(false);
        return MembershipResponse.from(membershipRepository.saveAndFlush(m));
    }

    // ── Room assignments ───────────────────────────────────────────────────

    @Transactional
    public RoomAssignmentResponse assignRoom(UUID membershipId, CreateRoomAssignmentRequest req) {
        TenantPropertyMembership m = requireMembership(membershipId);
        if (m.getStatus() != MembershipStatus.ACTIVE) {
            throw new InvalidOperationException("Cannot assign a room to a TERMINATED membership");
        }
        Room room = roomRepository.findById(req.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", req.getRoomId()));
        // Cross-property check — the room must belong to the membership's property. Rooms
        // hang off floors, so resolve floor → property.
        Floor floor = floorRepository.findById(room.getFloorId())
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", room.getFloorId()));
        if (!floor.getPropertyId().equals(m.getPropertyId())) {
            throw new InvalidOperationException("Room " + room.getId() + " does not belong to membership's property " + m.getPropertyId());
        }

        // Room-single-occupancy invariant (spec §10.2) — no other active assignment
        // may exist for this room, from any membership.
        Optional<RoomAssignment> otherActive = assignmentRepository.findFirstByRoomIdAndEffectiveToBsIsNull(room.getId());
        if (otherActive.isPresent()) {
            throw new DuplicateResourceException("RoomAssignment",
                    "roomId (active)", room.getId().toString());
        }
        // Also block the same (membership, room) pair from double-assign (same shape as
        // MeterRoomCoverage's active-pair rule).
        if (assignmentRepository.findFirstByMembershipIdAndRoomIdAndEffectiveToBsIsNull(membershipId, room.getId()).isPresent()) {
            throw new DuplicateResourceException("RoomAssignment",
                    "membershipId+roomId (active)", membershipId + "+" + room.getId());
        }

        RoomAssignment a = RoomAssignment.builder()
                .membershipId(membershipId)
                .roomId(room.getId())
                .effectiveFromBs(req.getEffectiveFromBs())
                .monthlyRent(req.getMonthlyRent())
                .build();
        return RoomAssignmentResponse.from(assignmentRepository.saveAndFlush(a));
    }

    @Transactional(readOnly = true)
    public List<RoomAssignmentResponse> listAssignments(UUID membershipId) {
        requireMembership(membershipId);
        return assignmentRepository.findByMembershipIdOrderByEffectiveFromBsAsc(membershipId)
                .stream().map(RoomAssignmentResponse::from).toList();
    }

    @Transactional
    public RoomAssignmentResponse endAssignment(UUID membershipId, UUID assignmentId, EndRoomAssignmentRequest req) {
        RoomAssignment a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomAssignment", "id", assignmentId));
        if (!a.getMembershipId().equals(membershipId)) {
            // Same shape as MeterController — mismatched parent id → 404, not 400.
            throw new ResourceNotFoundException("RoomAssignment", "id", assignmentId);
        }
        if (a.getEffectiveToBs() != null) {
            throw new InvalidOperationException("Room assignment already ended");
        }
        a.setEffectiveToBs(req.getEffectiveToBs());
        return RoomAssignmentResponse.from(assignmentRepository.saveAndFlush(a));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TenantPropertyMembership requireMembership(UUID id) {
        return membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantPropertyMembership", "id", id));
    }
}
