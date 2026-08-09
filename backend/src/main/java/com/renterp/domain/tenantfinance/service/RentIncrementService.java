package com.renterp.domain.tenantfinance.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.dto.CreateRentIncrementRequest;
import com.renterp.domain.tenantfinance.dto.RentIncrementResponse;
import com.renterp.domain.tenantfinance.entity.RentIncrement;
import com.renterp.domain.tenantfinance.repository.RentIncrementRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RentIncrementService {

    private static final Logger log = LogManager.getLogger(RentIncrementService.class);

    private final RentIncrementRepository incrementRepository;
    private final RoomAssignmentRepository assignmentRepository;
    private final TenantPropertyMembershipRepository membershipRepository;

    public RentIncrementService(RentIncrementRepository incrementRepository,
                                 RoomAssignmentRepository assignmentRepository,
                                 TenantPropertyMembershipRepository membershipRepository) {
        this.incrementRepository = incrementRepository;
        this.assignmentRepository = assignmentRepository;
        this.membershipRepository = membershipRepository;
    }

    // Atomic: append a RentIncrement row + update the referenced room_assignment's monthly_rent.
    // The billing engine (Phase 5) reads this table for mid-cycle segment boundaries (§9.3).
    @Transactional
    public RentIncrementResponse apply(UUID membershipId, CreateRentIncrementRequest req) {
        TenantPropertyMembership m = requireMembership(membershipId);
        if (m.getStatus() != MembershipStatus.ACTIVE) {
            throw new InvalidOperationException("Cannot record a rent increment on a TERMINATED membership");
        }
        RoomAssignment a = assignmentRepository.findById(req.getRoomAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("RoomAssignment", "id", req.getRoomAssignmentId()));
        // Reference-integrity — the assignment must belong to the URL's membership.
        if (!a.getMembershipId().equals(membershipId)) {
            throw new InvalidOperationException(
                    "Room assignment " + a.getId() + " does not belong to membership " + membershipId);
        }
        // Must be currently active — no rent changes on ended assignments.
        if (a.getEffectiveToBs() != null) {
            throw new InvalidOperationException(
                    "Room assignment " + a.getId() + " is no longer active — rent cannot be changed");
        }
        // effectiveBs must be within or after the assignment's active window.
        if (req.getEffectiveBs().compareTo(a.getEffectiveFromBs()) < 0) {
            throw new InvalidOperationException(
                    "effectiveBs " + req.getEffectiveBs()
                    + " predates the assignment's effectiveFromBs " + a.getEffectiveFromBs());
        }

        RentIncrement inc = RentIncrement.builder()
                .membershipId(membershipId)
                .roomAssignmentId(a.getId())
                .previousAmount(a.getMonthlyRent())        // captured from the assignment, never the client
                .newAmount(req.getNewAmount())
                .effectiveBs(req.getEffectiveBs())
                .reason(req.getReason())
                .notifiedTenant(req.isNotifiedTenant())
                .build();
        RentIncrement saved = incrementRepository.saveAndFlush(inc);

        // Mutate the current rent on the assignment. rent_increments retains the history;
        // this column serves quick "what is the current rent?" queries at bill time.
        a.setMonthlyRent(req.getNewAmount());
        assignmentRepository.saveAndFlush(a);

        log.info("Rent increment applied — membership: {}, assignment: {}, {} → {}",
                membershipId, a.getId(), saved.getPreviousAmount(), saved.getNewAmount());
        return RentIncrementResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RentIncrementResponse> listForMembership(UUID membershipId) {
        requireMembership(membershipId);
        return incrementRepository.findByMembershipIdOrderByEffectiveBsDesc(membershipId)
                .stream().map(RentIncrementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RentIncrementResponse getById(UUID id) {
        return RentIncrementResponse.from(incrementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RentIncrement", "id", id)));
    }

    private TenantPropertyMembership requireMembership(UUID id) {
        return membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantPropertyMembership", "id", id));
    }
}
