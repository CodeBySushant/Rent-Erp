package com.renterp.domain.tenantfinance.repository;

import com.renterp.domain.tenantfinance.entity.RentIncrement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RentIncrementRepository extends JpaRepository<RentIncrement, UUID> {
    List<RentIncrement> findByMembershipIdOrderByEffectiveBsDesc(UUID membershipId);
    List<RentIncrement> findByRoomAssignmentIdOrderByEffectiveBsDesc(UUID roomAssignmentId);
}
