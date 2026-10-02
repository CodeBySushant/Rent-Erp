package com.renterp.domain.request.repository;

import com.renterp.domain.request.entity.TenantRequest;
import com.renterp.domain.request.entity.TenantRequest.Status;
import com.renterp.domain.request.entity.TenantRequest.Type;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRequestRepository extends JpaRepository<TenantRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TenantRequest r where r.id = :id")
    Optional<TenantRequest> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByMembershipIdAndTypeAndStatusIn(UUID membershipId, Type type, Collection<Status> statuses);

    List<TenantRequest> findByMembershipIdOrderByCreatedAtDesc(UUID membershipId);

    List<TenantRequest> findByMembershipIdInOrderByCreatedAtDesc(Collection<UUID> membershipIds);

    List<TenantRequest> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);

    List<TenantRequest> findByPropertyIdAndStatusOrderByCreatedAtDesc(UUID propertyId, Status status);

    long countByPropertyIdAndStatus(UUID propertyId, Status status);
}
