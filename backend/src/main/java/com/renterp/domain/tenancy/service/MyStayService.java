package com.renterp.domain.tenancy.service;

import com.renterp.common.util.BsCalendar;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.billing.entity.TenantBill;
import com.renterp.domain.dashboard.service.DashboardQueries;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.dto.MyStayResponse;
import com.renterp.domain.tenancy.dto.MyStayResponse.*;
import com.renterp.domain.tenancy.entity.JoinRequest;
import com.renterp.domain.tenancy.entity.JoinRequest.JoinRequestStatus;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.JoinRequestRepository;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.repository.TenantDepositRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The tenant's own view: their tenancies with property, landlord, rooms, rent,
 * deposit, notice period, current bill and amount owed, plus pending join
 * requests. Only ever about the caller (their linked tenant profile); a user
 * without one gets empty lists.
 */
@Service
@Transactional(readOnly = true)
public class MyStayService {

    private final AccessGuard guard;
    private final TenantProfileRepository profiles;
    private final TenantPropertyMembershipRepository memberships;
    private final JoinRequestRepository joinRequests;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final RoomAssignmentRepository assignments;
    private final RoomRepository rooms;
    private final FloorRepository floors;
    private final TenantDepositRepository deposits;
    private final DashboardQueries q;

    public MyStayService(AccessGuard guard, TenantProfileRepository profiles,
                         TenantPropertyMembershipRepository memberships, JoinRequestRepository joinRequests,
                         PropertyRepository properties, UserRepository users, RoomAssignmentRepository assignments,
                         RoomRepository rooms, FloorRepository floors, TenantDepositRepository deposits,
                         DashboardQueries q) {
        this.guard = guard;
        this.profiles = profiles;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.properties = properties;
        this.users = users;
        this.assignments = assignments;
        this.rooms = rooms;
        this.floors = floors;
        this.deposits = deposits;
        this.q = q;
    }

    public MyStayResponse myStay() {
        AuthUser user = guard.requireUser();
        String today = BsCalendar.today();
        Optional<TenantProfile> profile = profiles.findByUserId(user.userId());
        if (profile.isEmpty()) {
            return new MyStayResponse(today, List.of(), List.of());
        }
        UUID profileId = profile.get().getId();

        List<TenantPropertyMembership> ms = new ArrayList<>(
                memberships.findByTenantProfileId(profileId, Pageable.unpaged()).getContent());
        // Active first, then most recent.
        ms.sort(Comparator.comparing((TenantPropertyMembership m) -> m.getStatus() != MembershipStatus.ACTIVE)
                .thenComparing(TenantPropertyMembership::getStartedAtBs, Comparator.nullsLast(Comparator.reverseOrder())));

        List<JoinRequest> pending = joinRequests.findByTenantProfileId(profileId, Pageable.unpaged()).getContent()
                .stream().filter(j -> j.getStatus() == JoinRequestStatus.PENDING).toList();

        Set<UUID> propertyIds = new HashSet<>();
        ms.forEach(m -> propertyIds.add(m.getPropertyId()));
        pending.forEach(j -> propertyIds.add(j.getPropertyId()));
        Map<UUID, Property> propertyById = byId(properties.findAllById(propertyIds), Property::getId);
        Map<UUID, User> ownerById = byId(users.findAllById(propertyById.values().stream()
                .map(Property::getOwnerUserId).collect(Collectors.toSet())), User::getId);

        List<UUID> membershipIds = ms.stream().map(TenantPropertyMembership::getId).toList();
        Map<UUID, List<RoomAssignment>> current = membershipIds.isEmpty() ? Map.of()
                : assignments.findByMembershipIdInAndEffectiveToBsIsNull(membershipIds).stream()
                .collect(Collectors.groupingBy(RoomAssignment::getMembershipId));
        Map<UUID, Room> roomById = byId(rooms.findAllById(current.values().stream().flatMap(List::stream)
                .map(RoomAssignment::getRoomId).collect(Collectors.toSet())), Room::getId);
        Map<UUID, Floor> floorById = byId(floors.findAllById(roomById.values().stream()
                .map(Room::getFloorId).collect(Collectors.toSet())), Floor::getId);

        Map<UUID, TenantBill> latest = new HashMap<>();
        Map<UUID, BigDecimal> owed = new HashMap<>();
        Set<UUID> overdue = new HashSet<>();
        for (TenantBill b : q.liveBills(membershipIds)) {          // newest month first
            latest.putIfAbsent(b.getMembershipId(), b);
            if (b.getStatus() == TenantBill.Status.ISSUED) {
                owed.merge(b.getMembershipId(), b.getBalanceDue(), BigDecimal::add);
                if (b.getBalanceDue().signum() > 0 && b.getDueDateBs() != null && b.getDueDateBs().compareTo(today) < 0) {
                    overdue.add(b.getMembershipId());
                }
            }
        }

        List<Stay> stays = new ArrayList<>();
        for (TenantPropertyMembership m : ms) {
            Property p = propertyById.get(m.getPropertyId());
            if (p == null) {
                continue;
            }
            boolean active = m.getStatus() == MembershipStatus.ACTIVE;
            User owner = ownerById.get(p.getOwnerUserId());
            List<RoomRef> refs = current.getOrDefault(m.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(RoomAssignment::getEffectiveFromBs))
                    .map(a -> {
                        Room r = roomById.get(a.getRoomId());
                        Floor f = r == null ? null : floorById.get(r.getFloorId());
                        return new RoomRef(a.getRoomId(), r == null ? null : r.getName(),
                                f == null ? null : f.getName(), a.getMonthlyRent(), a.getEffectiveFromBs());
                    }).toList();
            BigDecimal rent = refs.stream().map(RoomRef::monthlyRent).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            DepositRef deposit = deposits.findByMembershipId(m.getId())
                    .map(d -> new DepositRef(d.getAmount(), d.getStatus().name(), d.getReceivedAtBs()))
                    .orElse(null);
            TenantBill lb = latest.get(m.getId());
            BillRef bill = lb == null ? null : new BillRef(lb.getId(), lb.getBillingMonthBs(), lb.getStatus().name(),
                    lb.getPaymentStatus().name(), lb.getTotalDue(), lb.getAmountPaid(), lb.getBalanceDue(),
                    lb.getDueDateBs());
            stays.add(new Stay(m.getId(), m.getStatus().name(), m.getStartedAtBs(), m.getEndedAtBs(),
                    new PropertyRef(p.getId(), p.getName(), p.getAddress(), p.getCity(), p.getJoinCode()),
                    owner == null ? null : owner.getName(),
                    active && owner != null ? owner.getPhone() : null,
                    refs, rent, deposit, p.getVacancyNoticePeriodDays(), p.getBillingDay(), p.getGracePeriodDays(),
                    bill, owed.getOrDefault(m.getId(), BigDecimal.ZERO), overdue.contains(m.getId())));
        }

        List<PendingJoin> waiting = pending.stream().map(j -> {
            Property p = propertyById.get(j.getPropertyId());
            return new PendingJoin(j.getId(), j.getPropertyId(), p == null ? null : p.getName(),
                    p == null ? null : p.getJoinCode(), j.getRequestedAt());
        }).toList();
        return new MyStayResponse(today, stays, waiting);
    }

    private static <T> Map<UUID, T> byId(Iterable<T> items, Function<T, UUID> id) {
        Map<UUID, T> out = new HashMap<>();
        items.forEach(t -> out.put(id.apply(t), t));
        return out;
    }
}
