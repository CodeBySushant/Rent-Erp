package com.renterp.domain.moveout.repository;

import com.renterp.domain.moveout.entity.MoveOut;
import com.renterp.domain.moveout.entity.MoveOut.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MoveOutRepository extends JpaRepository<MoveOut, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MoveOut m where m.id = :id")
    Optional<MoveOut> findByIdForUpdate(@Param("id") UUID id);

    Optional<MoveOut> findFirstByMembershipIdOrderByCreatedAtDesc(UUID membershipId);

    Optional<MoveOut> findFirstByMembershipIdAndStatus(UUID membershipId, Status status);

    List<MoveOut> findByPropertyIdAndStatusOrderByPlannedMoveOutBsAsc(UUID propertyId, Status status);

    List<MoveOut> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);
}
