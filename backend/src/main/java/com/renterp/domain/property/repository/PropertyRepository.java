package com.renterp.domain.property.repository;

import com.renterp.domain.property.entity.Property;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    Page<Property> findByOwnerUserId(UUID ownerUserId, Pageable pageable);

    // Concurrency layer 2 of B10 (double-tap Generate / two devices): the billing engine
    // takes a PESSIMISTIC_WRITE lock on the property row before creating a run, so two
    // concurrent generate requests for the same property serialise here rather than racing
    // to insert. Layers 1/3/4 are the idempotency key, the unique DB index and the UI.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Property p where p.id = :id")
    Optional<Property> findByIdForUpdate(@Param("id") UUID id);
}
