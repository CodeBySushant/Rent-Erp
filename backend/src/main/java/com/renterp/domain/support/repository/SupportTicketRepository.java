package com.renterp.domain.support.repository;

import com.renterp.domain.support.entity.SupportTicket;
import com.renterp.domain.support.entity.SupportTicket.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(Status status, Pageable pageable);

    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByAttachmentFileId(UUID attachmentFileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.id = :id")
    Optional<SupportTicket> findByIdForUpdate(@Param("id") UUID id);
}
