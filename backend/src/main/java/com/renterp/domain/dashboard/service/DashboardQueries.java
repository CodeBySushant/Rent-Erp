package com.renterp.domain.dashboard.service;

import com.renterp.domain.billing.entity.BillingRun;
import com.renterp.domain.billing.entity.TenantBill;
import com.renterp.domain.meter.entity.Meter.MeterStatus;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingStatus;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only aggregate queries behind the dashboard and tenant list. Each one is
 * a single SQL round trip for a whole property, so a screen never needs one
 * call per room or per tenant. Kept apart from the domain repositories, which
 * stay one-per-entity.
 *
 * "Owed" always means ISSUED bills that have not been superseded by a
 * correction; DRAFT bills (an unconfirmed run) owe nothing yet.
 */
@Repository
public class DashboardQueries {

    private final EntityManager em;

    public DashboardQueries(EntityManager em) {
        this.em = em;
    }

    public long activeRooms(UUID propertyId) {
        return em.createQuery("""
                select count(r) from Room r, Floor f
                where r.floorId = f.id and f.propertyId = :pid and r.active = true and f.active = true
                """, Long.class).setParameter("pid", propertyId).getSingleResult();
    }

    /** Rooms with a current assignment on an active membership. */
    public long occupiedRooms(UUID propertyId) {
        return em.createQuery("""
                select count(distinct a.roomId) from RoomAssignment a, TenantPropertyMembership m
                where a.membershipId = m.id and m.propertyId = :pid
                  and m.status = :active and a.effectiveToBs is null
                """, Long.class)
                .setParameter("pid", propertyId)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getSingleResult();
    }

    public long activeMemberships(UUID propertyId) {
        return em.createQuery("""
                select count(m) from TenantPropertyMembership m
                where m.propertyId = :pid and m.status = :active
                """, Long.class)
                .setParameter("pid", propertyId)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getSingleResult();
    }

    public long pendingJoinRequests(UUID propertyId) {
        return em.createQuery("""
                select count(j) from JoinRequest j where j.propertyId = :pid and j.status = :pending
                """, Long.class)
                .setParameter("pid", propertyId)
                .setParameter("pending", JoinRequestStatus.PENDING)
                .getSingleResult();
    }

    /** The newest billing run that is not cancelled, if any. */
    public Optional<BillingRun> latestRun(UUID propertyId) {
        return em.createQuery("""
                select r from BillingRun r
                where r.propertyId = :pid and r.status <> :cancelled
                order by r.billingMonthBs desc, r.createdAt desc
                """, BillingRun.class)
                .setParameter("pid", propertyId)
                .setParameter("cancelled", BillingRun.Status.CANCELLED)
                .setMaxResults(1)
                .getResultStream().findFirst();
    }

    public List<TenantBill> billsOfRun(UUID runId) {
        return em.createQuery("""
                select b from TenantBill b
                where b.billingRunId = :run and b.status <> :cancelled and b.supersededByBillId is null
                """, TenantBill.class)
                .setParameter("run", runId)
                .setParameter("cancelled", TenantBill.Status.CANCELLED)
                .getResultList();
    }

    /** Sum still owed on issued bills of the property. */
    public BigDecimal outstanding(UUID propertyId) {
        BigDecimal v = em.createQuery("""
                select sum(b.balanceDue) from TenantBill b
                where b.propertyId = :pid and b.status = :issued and b.supersededByBillId is null
                """, BigDecimal.class)
                .setParameter("pid", propertyId)
                .setParameter("issued", TenantBill.Status.ISSUED)
                .getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Tenants (memberships) with an issued, unpaid bill past its due date. */
    public long overdueMemberships(UUID propertyId, String todayBs) {
        return em.createQuery("""
                select count(distinct b.membershipId) from TenantBill b
                where b.propertyId = :pid and b.status = :issued and b.supersededByBillId is null
                  and b.balanceDue > 0 and b.dueDateBs < :today
                """, Long.class)
                .setParameter("pid", propertyId)
                .setParameter("issued", TenantBill.Status.ISSUED)
                .setParameter("today", todayBs)
                .getSingleResult();
    }

    public long activeMeters(UUID propertyId) {
        return em.createQuery("""
                select count(m) from Meter m
                where m.propertyId = :pid and m.active = true and m.status = :active
                """, Long.class)
                .setParameter("pid", propertyId)
                .setParameter("active", MeterStatus.ACTIVE)
                .getSingleResult();
    }

    /** Active meters with no confirmed reading dated on or after {@code sinceBs}. */
    public long metersWithoutReadingSince(UUID propertyId, String sinceBs) {
        return em.createQuery("""
                select count(m) from Meter m
                where m.propertyId = :pid and m.active = true and m.status = :active
                  and not exists (select r.id from MeterReading r
                                  where r.meterId = m.id and r.status = :confirmed
                                    and r.readingDateBs >= :since)
                """, Long.class)
                .setParameter("pid", propertyId)
                .setParameter("active", MeterStatus.ACTIVE)
                .setParameter("confirmed", ReadingStatus.CONFIRMED)
                .setParameter("since", sinceBs)
                .getSingleResult();
    }

    /** Amount owed per membership (issued, not superseded) for one property. */
    public Map<UUID, BigDecimal> owedByMembership(UUID propertyId) {
        List<Object[]> rows = em.createQuery("""
                select b.membershipId, sum(b.balanceDue) from TenantBill b
                where b.propertyId = :pid and b.status = :issued and b.supersededByBillId is null
                group by b.membershipId
                """, Object[].class)
                .setParameter("pid", propertyId)
                .setParameter("issued", TenantBill.Status.ISSUED)
                .getResultList();
        Map<UUID, BigDecimal> out = new HashMap<>();
        for (Object[] r : rows) {
            out.put((UUID) r[0], r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1]);
        }
        return out;
    }

    /** Every live (not cancelled, not superseded) bill of the given memberships, newest month first. */
    public List<TenantBill> liveBills(List<UUID> membershipIds) {
        if (membershipIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("""
                select b from TenantBill b
                where b.membershipId in :ids and b.status <> :cancelled and b.supersededByBillId is null
                order by b.billingMonthBs desc, b.createdAt desc
                """, TenantBill.class)
                .setParameter("ids", membershipIds)
                .setParameter("cancelled", TenantBill.Status.CANCELLED)
                .getResultList();
    }
}
