package com.renterp.domain.tenancy.repository;

import com.renterp.domain.tenancy.entity.JoinRequest;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
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

    /** Every property this tenant profile has asked to join. */
    @Query(
            "select distinct j.propertyId from JoinRequest j where j.tenantProfileId = :profileId")
    List<UUID> findPropertyIdsByTenantProfileId(
            @Param("profileId") UUID profileId);
}
