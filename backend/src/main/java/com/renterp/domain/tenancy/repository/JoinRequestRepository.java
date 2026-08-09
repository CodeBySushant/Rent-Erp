package com.renterp.domain.tenancy.repository;

import com.renterp.domain.tenancy.entity.JoinRequest;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    // The active-PENDING partial-unique index enforces at most one row with these
    // criteria. Used for T2 re-request check and idempotency on POST.
    Optional<JoinRequest> findByTenantProfileIdAndPropertyIdAndStatus(
            UUID tenantProfileId, UUID propertyId, JoinRequestStatus status);

    Page<JoinRequest> findByPropertyId(UUID propertyId, Pageable pageable);
    Page<JoinRequest> findByPropertyIdAndStatus(UUID propertyId, JoinRequestStatus status, Pageable pageable);
    Page<JoinRequest> findByTenantProfileId(UUID tenantProfileId, Pageable pageable);
}
