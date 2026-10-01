package com.renterp.domain.tenancy.repository;

import com.renterp.domain.tenancy.entity.RoomAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, UUID> {

    List<RoomAssignment> findByMembershipIdOrderByEffectiveFromBsAsc(UUID membershipId);

    // Used for the room-single-occupancy invariant: check if any active assignment
    // exists for this room (across ALL memberships, not just this one).
    Optional<RoomAssignment> findFirstByRoomIdAndEffectiveToBsIsNull(UUID roomId);

    Optional<RoomAssignment> findFirstByMembershipIdAndRoomIdAndEffectiveToBsIsNull(UUID membershipId, UUID roomId);

    boolean existsByMembershipIdAndEffectiveToBsIsNull(UUID membershipId);

    // Billing engine: a membership's currently-active room assignments (rent = sum of their
    // monthly_rent; room count = weight for ROOM_WEIGHTED splits).
    List<RoomAssignment> findByMembershipIdAndEffectiveToBsIsNull(UUID membershipId);

    /** Current (open-ended) assignments of these memberships. */
    List<RoomAssignment> findByMembershipIdInAndEffectiveToBsIsNull(java.util.Collection<UUID> membershipIds);
}
