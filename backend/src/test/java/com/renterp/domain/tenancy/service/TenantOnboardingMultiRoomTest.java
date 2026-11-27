package com.renterp.domain.tenancy.service;

import com.renterp.common.exception.ApiException;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.dto.AddTenantRequest;
import com.renterp.domain.tenancy.dto.AddTenantResponse;
import com.renterp.domain.tenancy.dto.CreateRoomAssignmentRequest;
import com.renterp.domain.tenancy.dto.RoomAssignmentResponse;
import com.renterp.domain.tenancy.dto.TenantProfileResponse;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.service.TenantAdvanceRentService;
import com.renterp.domain.tenantfinance.service.TenantDepositService;
import com.renterp.domain.tenantfinance.service.TenantOpeningBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Add Tenant with several rooms and a Total Members count (owner, 27 Nov). */
class TenantOnboardingMultiRoomTest {

    private final UUID propertyId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();

    private PropertyRepository properties;
    private RoomRepository rooms;
    private FloorRepository floors;
    private RoomAssignmentRepository assignments;
    private TenantPropertyMembershipRepository memberships;
    private TenantProfileService profiles;
    private MembershipService membershipService;
    private UserRepository users;
    private TenantOnboardingService service;
    private TenantPropertyMembership membership;

    @BeforeEach
    void setUp() {
        properties = mock(PropertyRepository.class);
        rooms = mock(RoomRepository.class);
        floors = mock(FloorRepository.class);
        assignments = mock(RoomAssignmentRepository.class);
        memberships = mock(TenantPropertyMembershipRepository.class);
        profiles = mock(TenantProfileService.class);
        membershipService = mock(MembershipService.class);
        users = mock(UserRepository.class);
        service = new TenantOnboardingService(properties, rooms, floors, assignments, memberships,
                profiles, membershipService, mock(TenantDepositService.class),
                mock(TenantAdvanceRentService.class), mock(TenantOpeningBalanceService.class), users,
                mock(TenantProfileLinker.class));

        Property property = new Property();
        property.setId(propertyId);
        property.setActive(true);
        when(properties.findById(propertyId)).thenReturn(Optional.of(property));

        Floor floor = new Floor();
        floor.setId(floorId);
        floor.setPropertyId(propertyId);
        floor.setActive(true);
        when(floors.findById(floorId)).thenReturn(Optional.of(floor));

        when(users.findByPhoneAndActiveTrue(anyString())).thenReturn(Optional.empty());
        TenantProfileResponse profile = mock(TenantProfileResponse.class);
        when(profile.getId()).thenReturn(UUID.randomUUID());
        when(profiles.createProfile(any())).thenReturn(profile);

        membership = new TenantPropertyMembership();
        membership.setId(UUID.randomUUID());
        when(membershipService.createInternal(any(), eq(propertyId), anyString(), any(), any()))
                .thenReturn(membership);
        when(membershipService.assignRoom(any(), any())).thenAnswer(inv -> {
            RoomAssignmentResponse r = mock(RoomAssignmentResponse.class);
            when(r.getId()).thenReturn(UUID.randomUUID());
            return r;
        });
    }

    private UUID room(String name) {
        Room r = new Room();
        r.setId(UUID.randomUUID());
        r.setFloorId(floorId);
        r.setName(name);
        r.setActive(true);
        when(rooms.findByIdForUpdate(r.getId())).thenReturn(Optional.of(r));
        when(assignments.findFirstByRoomIdAndEffectiveToBsIsNull(r.getId())).thenReturn(Optional.empty());
        return r.getId();
    }

    private AddTenantRequest request(List<UUID> roomIds, String rent) {
        AddTenantRequest req = new AddTenantRequest();
        req.setFullName("Sita Sharma");
        req.setPhone("9812345678");
        req.setRoomIds(roomIds);
        req.setMoveInDateBs("2082-08-01");
        req.setMonthlyRent(new BigDecimal(rent));
        return req;
    }

    private String code(Runnable r) {
        return assertThrows(ApiException.class, r::run).getCode();
    }

    @Test
    void twoRoomsBothAssignedRentSharedAndMembersStored() {
        UUID a = room("A101");
        UUID b = room("A102");
        AddTenantRequest req = request(List.of(a, b), "10001");
        req.setTotalMembers((short) 4);

        AddTenantResponse res = service.addTenant(propertyId, req);

        ArgumentCaptor<CreateRoomAssignmentRequest> cap = ArgumentCaptor.forClass(CreateRoomAssignmentRequest.class);
        verify(membershipService, times(2)).assignRoom(eq(membership.getId()), cap.capture());
        List<CreateRoomAssignmentRequest> made = cap.getAllValues();
        assertEquals(a, made.get(0).getRoomId());
        assertEquals(b, made.get(1).getRoomId());
        // Exactly the rent entered, shared: 5000.50 + 5000.50.
        assertEquals(new BigDecimal("10001.00"), made.get(0).getMonthlyRent().add(made.get(1).getMonthlyRent()));
        assertEquals(List.of(a, b), res.roomIds());
        assertEquals(2, res.roomAssignmentIds().size());
        assertEquals(a, res.roomId());
        assertEquals((short) 4, membership.getTotalMembers());
        assertEquals((short) 4, res.totalMembers());
        verify(memberships).save(membership);
    }

    @Test
    void olderAppsSingleRoomIdStillWorks() {
        UUID a = room("A101");
        AddTenantRequest req = request(null, "8000");
        req.setRoomId(a);

        AddTenantResponse res = service.addTenant(propertyId, req);

        assertEquals(List.of(a), res.roomIds());
        // No Total Members sent: the old numOccupants default (1).
        assertEquals((short) 1, membership.getTotalMembers());
    }

    @Test
    void noRoomIsRefused() {
        assertEquals("ROOM_REQUIRED", code(() -> service.addTenant(propertyId, request(List.of(), "8000"))));
        verify(membershipService, never()).createInternal(any(), any(), any(), any(), any());
    }

    @Test
    void sameRoomTwiceIsRefused() {
        UUID a = room("A101");
        assertEquals("DUPLICATE_ROOM", code(() -> service.addTenant(propertyId, request(List.of(a, a), "8000"))));
        verify(membershipService, never()).createInternal(any(), any(), any(), any(), any());
    }

    @Test
    void oneOccupiedRoomRefusesAllAndCreatesNothing() {
        UUID a = room("A101");
        UUID b = room("A102");
        when(assignments.findFirstByRoomIdAndEffectiveToBsIsNull(b)).thenReturn(Optional.of(new RoomAssignment()));
        assertEquals("ROOM_OCCUPIED", code(() -> service.addTenant(propertyId, request(List.of(a, b), "8000"))));
        verify(membershipService, never()).createInternal(any(), any(), any(), any(), any());
        verify(membershipService, never()).assignRoom(any(), any());
    }

    @Test
    void roomOfAnotherPropertyIsRefused() {
        UUID a = room("A101");
        UUID otherFloor = UUID.randomUUID();
        Floor f = new Floor();
        f.setId(otherFloor);
        f.setPropertyId(UUID.randomUUID());
        f.setActive(true);
        when(floors.findById(otherFloor)).thenReturn(Optional.of(f));
        Room r = new Room();
        r.setId(UUID.randomUUID());
        r.setFloorId(otherFloor);
        r.setName("X1");
        r.setActive(true);
        when(rooms.findByIdForUpdate(r.getId())).thenReturn(Optional.of(r));
        assertEquals("ROOM_NOT_IN_PROPERTY",
                code(() -> service.addTenant(propertyId, request(List.of(a, r.getId()), "8000"))));
    }

    @Test
    void removedRoomIsRefused() {
        UUID a = room("A101");
        Room gone = new Room();
        gone.setId(UUID.randomUUID());
        gone.setFloorId(floorId);
        gone.setName("A103");
        gone.setActive(false);
        when(rooms.findByIdForUpdate(gone.getId())).thenReturn(Optional.of(gone));
        assertEquals("ROOM_INACTIVE",
                code(() -> service.addTenant(propertyId, request(List.of(a, gone.getId()), "8000"))));
    }

    @Test
    void unknownRoomIsRefused() {
        UUID a = room("A101");
        UUID missing = UUID.randomUUID();
        when(rooms.findByIdForUpdate(missing)).thenReturn(Optional.empty());
        assertEquals("ROOM_NOT_FOUND",
                code(() -> service.addTenant(propertyId, request(List.of(a, missing), "8000"))));
    }

    // ── splitRent ────────────────────────────────────────────────────────────

    private static BigDecimal sum(List<BigDecimal> parts) {
        return parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void splitOneRoomKeepsTheWholeRent() {
        assertEquals(List.of(new BigDecimal("8000.00")),
                TenantOnboardingService.splitRent(new BigDecimal("8000"), 1));
    }

    @Test
    void splitEvenly() {
        assertEquals(List.of(new BigDecimal("5000.00"), new BigDecimal("5000.00")),
                TenantOnboardingService.splitRent(new BigDecimal("10000"), 2));
    }

    @Test
    void splitLeftoverPaisaGoesToTheFirstRoom() {
        List<BigDecimal> parts = TenantOnboardingService.splitRent(new BigDecimal("10000"), 3);
        assertEquals(List.of(new BigDecimal("3333.34"), new BigDecimal("3333.33"), new BigDecimal("3333.33")), parts);
        assertEquals(new BigDecimal("10000.00"), sum(parts));
    }

    @Test
    void splitAlwaysAddsUpExactly() {
        for (String total : List.of("0", "0.01", "1", "999.99", "12345.67", "99999999.99")) {
            for (int n = 1; n <= 10; n++) {
                List<BigDecimal> parts = TenantOnboardingService.splitRent(new BigDecimal(total), n);
                assertEquals(n, parts.size());
                assertEquals(new BigDecimal(total).setScale(2), sum(parts), total + " / " + n);
                parts.forEach(p -> assertEquals(true, p.signum() >= 0, "no negative part"));
            }
        }
    }

    @Test
    void splitRefusesZeroRooms() {
        assertThrows(IllegalArgumentException.class,
                () -> TenantOnboardingService.splitRent(BigDecimal.ONE, 0));
    }
}
