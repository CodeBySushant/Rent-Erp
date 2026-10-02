package com.renterp.domain.dashboard.service;

import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.billing.entity.TenantBill;
import com.renterp.domain.dashboard.dto.OwnerDashboardResponse;
import com.renterp.domain.dashboard.dto.PropertySummaryResponse;
import com.renterp.domain.dashboard.dto.PropertySummaryResponse.CurrentBilling;
import com.renterp.domain.dashboard.dto.TenantRowResponse;
import com.renterp.domain.dashboard.dto.TenantRowResponse.LatestBill;
import com.renterp.domain.dashboard.dto.TenantRowResponse.RoomRef;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Screen-shaped reads: the owner dashboard, one property's summary, and the
 * tenant list. Everything is computed from current database state; nothing is
 * cached or estimated. Authorization is done by the controller.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardQueries q;
    private final AccessGuard guard;
    private final PropertyRepository properties;
    private final TenantPropertyMembershipRepository memberships;
    private final TenantProfileRepository profiles;
    private final RoomAssignmentRepository assignments;
    private final RoomRepository rooms;
    private final FloorRepository floors;

    public DashboardService(DashboardQueries q, AccessGuard guard, PropertyRepository properties,
                            TenantPropertyMembershipRepository memberships, TenantProfileRepository profiles,
                            RoomAssignmentRepository assignments, RoomRepository rooms, FloorRepository floors) {
        this.q = q;
        this.guard = guard;
        this.properties = properties;
        this.memberships = memberships;
        this.profiles = profiles;
        this.assignments = assignments;
        this.rooms = rooms;
        this.floors = floors;
    }

    // ── Summaries ───────────────────────────────────────────────────────────

    public PropertySummaryResponse summary(UUID propertyId) {
        Property p = properties.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));
        return summary(p, BsCalendar.today());
    }

    /** Every active property the caller can see (all of them for an admin). */
    public OwnerDashboardResponse dashboard() {
        String today = BsCalendar.today();
        List<Property> visible = guard.visiblePropertyIds()
                .map(properties::findAllById)
                .orElseGet(properties::findAll)
                .stream()
                .filter(Property::isActive)
                .sorted(Comparator.comparing(Property::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<PropertySummaryResponse> items = visible.stream().map(p -> summary(p, today)).toList();
        int rooms = 0, occupied = 0, tenants = 0, joins = 0, overdue = 0, readings = 0, payments = 0;
        BigDecimal outstanding = BigDecimal.ZERO, billed = BigDecimal.ZERO, collected = BigDecimal.ZERO;
        for (PropertySummaryResponse s : items) {
            rooms += s.totalRooms();
            occupied += s.occupiedRooms();
            tenants += s.activeTenants();
            joins += s.pendingJoinRequests();
            overdue += s.overdueTenants();
            readings += s.readingsPending();
            payments += s.pendingPayments();
            outstanding = outstanding.add(s.outstanding());
            if (s.currentBilling() != null) {
                billed = billed.add(s.currentBilling().billed());
                collected = collected.add(s.currentBilling().collected());
            }
        }
        return new OwnerDashboardResponse(today, items.size(), rooms, occupied, Math.max(0, rooms - occupied),
                tenants, joins, overdue, readings, payments, outstanding, billed, collected, items);
    }

    private PropertySummaryResponse summary(Property p, String today) {
        UUID id = p.getId();
        int total = (int) q.activeRooms(id);
        int occupied = (int) q.occupiedRooms(id);
        CurrentBilling current = q.latestRun(id).map(run -> {
            List<TenantBill> bills = q.billsOfRun(run.getId());
            BigDecimal billed = sum(bills, TenantBill::getTotalDue);
            BigDecimal paid = sum(bills, TenantBill::getAmountPaid);
            BigDecimal pending = sum(bills, TenantBill::getBalanceDue);
            int paidCount = (int) bills.stream()
                    .filter(b -> b.getPaymentStatus() == TenantBill.PaymentStatus.PAID).count();
            return new CurrentBilling(run.getId(), run.getBillingMonthBs(), run.getStatus().name(),
                    billed, paid, pending, bills.size(), paidCount);
        }).orElse(null);

        return new PropertySummaryResponse(
                id, p.getName(), p.getCity(),
                total, occupied, Math.max(0, total - occupied),
                (int) q.activeMemberships(id),
                (int) q.pendingJoinRequests(id),
                (int) q.overdueMemberships(id, today),
                q.outstanding(id),
                (int) q.activeMeters(id),
                (int) q.metersWithoutReadingSince(id, BsCalendar.monthStart(today)),
                (int) q.pendingPayments(id),
                current);
    }

    // ── Tenant list ─────────────────────────────────────────────────────────

    /**
     * Tenants of a property with everything the list needs, in a fixed number
     * of queries whatever the tenant count. {@code status} null = active only;
     * "ALL" = every membership.
     */
    public List<TenantRowResponse> tenants(UUID propertyId, String status) {
        if (!properties.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property", "id", propertyId);
        }
        List<TenantPropertyMembership> ms;
        if (status != null && status.equalsIgnoreCase("ALL")) {
            ms = memberships.findByPropertyId(propertyId, Pageable.unpaged()).getContent();
        } else {
            MembershipStatus wanted = status == null ? MembershipStatus.ACTIVE
                    : MembershipStatus.valueOf(status.toUpperCase(Locale.ROOT));
            ms = memberships.findByPropertyIdAndStatus(propertyId, wanted);
        }
        if (ms.isEmpty()) {
            return List.of();
        }
        String today = BsCalendar.today();
        List<UUID> membershipIds = ms.stream().map(TenantPropertyMembership::getId).toList();

        Map<UUID, TenantProfile> profileById = byId(profiles.findAllById(
                ms.stream().map(TenantPropertyMembership::getTenantProfileId).collect(Collectors.toSet())),
                TenantProfile::getId);
        Map<UUID, List<RoomAssignment>> currentByMembership = assignments
                .findByMembershipIdInAndEffectiveToBsIsNull(membershipIds).stream()
                .collect(Collectors.groupingBy(RoomAssignment::getMembershipId));
        Set<UUID> roomIds = currentByMembership.values().stream().flatMap(List::stream)
                .map(RoomAssignment::getRoomId).collect(Collectors.toSet());
        Map<UUID, Room> roomById = byId(rooms.findAllById(roomIds), Room::getId);
        Map<UUID, Floor> floorById = byId(floors.findAllById(
                roomById.values().stream().map(Room::getFloorId).collect(Collectors.toSet())), Floor::getId);

        Map<UUID, BigDecimal> owed = q.owedByMembership(propertyId);
        Map<UUID, TenantBill> latest = new HashMap<>();
        Set<UUID> overdue = new HashSet<>();
        for (TenantBill b : q.liveBills(membershipIds)) {      // newest month first
            latest.putIfAbsent(b.getMembershipId(), b);
            if (b.getStatus() == TenantBill.Status.ISSUED && b.getBalanceDue().signum() > 0
                    && b.getDueDateBs() != null && b.getDueDateBs().compareTo(today) < 0) {
                overdue.add(b.getMembershipId());
            }
        }

        List<TenantRowResponse> rows = new ArrayList<>();
        for (TenantPropertyMembership m : ms) {
            TenantProfile p = profileById.get(m.getTenantProfileId());
            List<RoomRef> refs = currentByMembership.getOrDefault(m.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(RoomAssignment::getEffectiveFromBs))
                    .map(a -> {
                        Room r = roomById.get(a.getRoomId());
                        Floor f = r == null ? null : floorById.get(r.getFloorId());
                        return new RoomRef(a.getId(), a.getRoomId(), r == null ? null : r.getName(),
                                f == null ? null : f.getName(), a.getEffectiveFromBs(), a.getMonthlyRent());
                    }).toList();
            BigDecimal rent = refs.stream().map(RoomRef::monthlyRent).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            TenantBill lb = latest.get(m.getId());
            LatestBill latestBill = lb == null ? null : new LatestBill(lb.getId(), lb.getBillingMonthBs(),
                    lb.getStatus().name(), lb.getPaymentStatus().name(), lb.getTotalDue(), lb.getAmountPaid(),
                    lb.getBalanceDue(), lb.getDueDateBs());
            rows.add(new TenantRowResponse(m.getId(), m.getTenantProfileId(), m.getPropertyId(),
                    p == null ? null : p.getFullName(), p == null ? null : p.getPhone(),
                    p != null && p.getUserId() != null, m.getStatus().name(), m.getStartedAtBs(),
                    m.getEndedAtBs(), refs, rent, latestBill, owed.getOrDefault(m.getId(), BigDecimal.ZERO),
                    overdue.contains(m.getId())));
        }
        rows.sort(Comparator.comparing(r -> r.name() == null ? "" : r.name(), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> f) {
        return items.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <T> Map<UUID, T> byId(Iterable<T> items, Function<T, UUID> id) {
        Map<UUID, T> out = new HashMap<>();
        items.forEach(t -> out.put(id.apply(t), t));
        return out;
    }
}
