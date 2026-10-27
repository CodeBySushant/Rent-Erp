package com.renterp.domain.file.repository;

import com.renterp.domain.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndDeletedAtIsNull(UUID id);

    // M21: upload quotas per user.
    long countByOwnerUserIdAndCreatedAtAfter(UUID ownerUserId, java.time.Instant since);

    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(f.sizeBytes), 0) from StoredFile f where f.ownerUserId = :owner and f.deletedAt is null")
    long totalBytesOf(@org.springframework.data.repository.query.Param("owner") UUID ownerUserId);
}
