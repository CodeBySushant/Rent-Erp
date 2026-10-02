package com.renterp.domain.payment.repository;

import com.renterp.domain.payment.entity.Payment;
import com.renterp.domain.payment.entity.Payment.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** The payment, locked for the rest of the transaction (approve / reject / cancel). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    Optional<Payment> findBySubmittedByAndIdempotencyKey(UUID submittedBy, String idempotencyKey);

    List<Payment> findByBillIdOrderByCreatedAtDesc(UUID billId);

    Page<Payment> findByPropertyIdAndStatus(UUID propertyId, Status status, Pageable pageable);

    Page<Payment> findByPropertyId(UUID propertyId, Pageable pageable);

    Page<Payment> findByMembershipIdIn(Collection<UUID> membershipIds, Pageable pageable);

    boolean existsByBillIdAndStatus(UUID billId, Status status);

    /** Payments in the given statuses against bills of this billing run. */
    @Query("select count(p) from Payment p, TenantBill b where p.billId = b.id and b.billingRunId = :runId "
            + "and p.status in :statuses")
    long countForRun(@Param("runId") UUID runId, @Param("statuses") Collection<Status> statuses);

    long countByPropertyIdAndStatus(UUID propertyId, Status status);

    boolean existsByMembershipIdAndStatus(UUID membershipId, Status status);
}
