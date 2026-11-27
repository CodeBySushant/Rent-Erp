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
import com.renterp.domain.tenancy.dto.RoomAssignmentResponse;
import com.renterp.domain.tenancy.dto.TenantProfileResponse;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenantfinance.dto.AdvanceRentResponse;
import com.renterp.domain.tenantfinance.dto.CreateAdvanceRentRequest;
import com.renterp.domain.tenantfinance.dto.CreateDepositRequest;
import com.renterp.domain.tenantfinance.dto.CreateOpeningBalanceRequest;
import com.renterp.domain.tenantfinance.dto.DepositResponse;
import com.renterp.domain.tenantfinance.dto.OpeningBalanceResponse;
import com.renterp.domain.tenantfinance.entity.TenantOpeningBalance.Direction;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Add Tenant with deposit, advance and opening balance in the one form (owner, 28 Nov). */
class TenantOnboardingFinanceTest {

    private final UUID propertyId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();

    private RoomRepository rooms;
    private RoomAssignmentRepository assignments;
    private MembershipService membershipService;
    private TenantDepositService deposits;
    private TenantAdvanceRentService advances;
    private TenantOpeningBalanceService openings;
    private TenantOnboardingService service;
    private TenantPropertyMembership membership;

    @BeforeEach
    void setUp() {
        PropertyRepository properties = mock(PropertyRepository.class);
        FloorRepository floors = mock(FloorRepository.class);
        TenantProfileService profiles = mock(TenantProfileService.class);
        UserRepository users = mock(UserRepository.class);
        rooms = mock(RoomRepository.class);
        assignments = mock(RoomAssignmentRepository.class);
        membershipService = mock(MembershipService.class);
        deposits = mock(TenantDepositService.class);
        advances = mock(TenantAdvanceRentService.class);
        openings = mock(TenantOpeningBalanceService.class);
        service = new TenantOnboardingService(properties, rooms, floors, assignments,
                mock(TenantPropertyMembershipRepository.class), profiles, membershipService,
                deposits, advances, openings, users, mock(TenantProfileLinker.class));

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
        DepositResponse dep = mock(DepositResponse.class);
        when(dep.getId()).thenReturn(UUID.randomUUID());
        when(deposits.create(any(), any())).thenReturn(dep);
        AdvanceRentResponse adv = mock(AdvanceRentResponse.class);
        when(adv.getId()).thenReturn(UUID.randomUUID());
        when(advances.create(any(), any())).thenReturn(adv);
        OpeningBalanceResponse open = mock(OpeningBalanceResponse.class);
        when(open.getId()).thenReturn(UUID.randomUUID());
        when(openings.create(any(), any())).thenReturn(open);
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

    private AddTenantRequest request(String rent, UUID... roomIds) {
        AddTenantRequest req = new AddTenantRequest();
        req.setFullName("Sita Sharma");
        req.setPhone("9812345678");
        req.setRoomIds(List.of(roomIds));
        req.setMoveInDateBs("2082-08-01");
        req.setMonthlyRent(new BigDecimal(rent));
        return req;
    }

    private static BigDecimal rs(String v) {
        return new BigDecimal(v);
    }

    private void assertNothingCreated() {
        verify(membershipService, never()).createInternal(any(), any(), any(), any(), any());
        verify(deposits, never()).create(any(), any());
        verify(advances, never()).create(any(), any());
        verify(openings, never()).create(any(), any());
    }

    @Test
    void everythingIsSavedInTheOneCall() {
        AddTenantRequest req = request("10000", room("A101"), room("A102"));
        req.setAgreedDepositAmount(rs("20000"));
        req.setDepositAmount(rs("15000"));
        req.setAgreedAdvanceAmount(rs("20000"));
        req.setAdvanceAmount(rs("20000"));
        req.setOpeningBalanceAmount(rs("2500"));
        req.setOpeningBalanceDirection(Direction.OWED_BY_TENANT);

        AddTenantResponse res = service.addTenant(propertyId, req);

        assertEquals(rs("20000"), membership.getAgreedDepositAmount());
        assertEquals(rs("20000"), membership.getAgreedAdvanceAmount());

        ArgumentCaptor<CreateDepositRequest> dep = ArgumentCaptor.forClass(CreateDepositRequest.class);
        verify(deposits).create(eq(membership.getId()), dep.capture());
        assertEquals(rs("15000"), dep.getValue().getAmount());
        assertEquals("2082-08-01", dep.getValue().getReceivedAtBs());

        ArgumentCaptor<CreateAdvanceRentRequest> adv = ArgumentCaptor.forClass(CreateAdvanceRentRequest.class);
        verify(advances).create(eq(membership.getId()), adv.capture());
        assertEquals(rs("20000"), adv.getValue().getAmount());
        assertEquals((short) 2, adv.getValue().getMonthsCovered());
        assertEquals("2082-08-01", adv.getValue().getCoveredFromBs());

        ArgumentCaptor<CreateOpeningBalanceRequest> open = ArgumentCaptor.forClass(CreateOpeningBalanceRequest.class);
        verify(openings).create(eq(membership.getId()), open.capture());
        assertEquals(rs("2500"), open.getValue().getAmount());
        assertEquals(Direction.OWED_BY_TENANT, open.getValue().getDirection());
        assertEquals("2082-08-01", open.getValue().getAsOfBs());

        assertNotNull(res.depositId());
        assertNotNull(res.advanceRentId());
        assertNotNull(res.openingBalanceId());
    }

    @Test
    void noMoneyGivenMakesNoMoneyRecords() {
        AddTenantResponse res = service.addTenant(propertyId, request("8000", room("A101")));

        verify(deposits, never()).create(any(), any());
        verify(advances, never()).create(any(), any());
        verify(openings, never()).create(any(), any());
        assertNull(membership.getAgreedDepositAmount());
        assertNull(membership.getAgreedAdvanceAmount());
        assertNull(res.depositId());
        assertNull(res.advanceRentId());
        assertNull(res.openingBalanceId());
    }

    @Test
    void zeroAmountsMeanNone() {
        AddTenantRequest req = request("8000", room("A101"));
        req.setDepositAmount(BigDecimal.ZERO);
        req.setAdvanceAmount(BigDecimal.ZERO);
        req.setOpeningBalanceAmount(BigDecimal.ZERO);

        service.addTenant(propertyId, req);

        verify(deposits, never()).create(any(), any());
        verify(advances, never()).create(any(), any());
        verify(openings, never()).create(any(), any());
    }

    @Test
    void agreedButNothingCollectedYetKeepsTheAgreedFigures() {
        AddTenantRequest req = request("8000", room("A101"));
        req.setAgreedDepositAmount(rs("16000"));
        req.setAgreedAdvanceAmount(rs("8000"));

        service.addTenant(propertyId, req);

        assertEquals(rs("16000"), membership.getAgreedDepositAmount());
        assertEquals(rs("8000"), membership.getAgreedAdvanceAmount());
        verify(deposits, never()).create(any(), any());
        verify(advances, never()).create(any(), any());
    }

    @Test
    void collectedWithoutAgreedIsTakenAsTheAgreedAmount() {
        AddTenantRequest req = request("8000", room("A101"));
        req.setDepositAmount(rs("10000"));

        service.addTenant(propertyId, req);

        assertEquals(rs("10000"), membership.getAgreedDepositAmount());
    }

    @Test
    void depositCollectedMoreThanAgreedIsRefused() {
        AddTenantRequest req = request("8000", room("A101"));
        req.setAgreedDepositAmount(rs("10000"));
        req.setDepositAmount(rs("10000.01"));
        assertEquals("DEPOSIT_MORE_THAN_AGREED",
                assertThrows(ApiException.class, () -> service.addTenant(propertyId, req)).getCode());
        assertNothingCreated();
    }

    @Test
    void advanceCollectedMoreThanAgreedIsRefused() {
        AddTenantRequest req = request("8000", room("A101"));
        req.setAgreedAdvanceAmount(rs("8000"));
        req.setAdvanceAmount(rs("16000"));
        assertEquals("ADVANCE_MORE_THAN_AGREED",
                assertThrows(ApiException.class, () -> service.addTenant(propertyId, req)).getCode());
        assertNothingCreated();
    }

    @Test
    void openingBalanceNeedsADirection() {
        AddTenantRequest req = request("8000", room("A101"));
        req.setOpeningBalanceAmount(rs("500"));
        assertEquals("OPENING_BALANCE_DIRECTION_REQUIRED",
                assertThrows(ApiException.class, () -> service.addTenant(propertyId, req)).getCode());
        assertNothingCreated();
    }

    @Test
    void creditToTenantIsSavedThatWay() {
        AddTenantRequest req = request("8000", room("A101"));
        req.setOpeningBalanceAmount(rs("1200"));
        req.setOpeningBalanceDirection(Direction.CREDIT_TO_TENANT);

        service.addTenant(propertyId, req);

        ArgumentCaptor<CreateOpeningBalanceRequest> open = ArgumentCaptor.forClass(CreateOpeningBalanceRequest.class);
        verify(openings).create(eq(membership.getId()), open.capture());
        assertEquals(Direction.CREDIT_TO_TENANT, open.getValue().getDirection());
    }

    @Test
    void occupiedRoomStillRefusesEverythingIncludingMoney() {
        UUID a = room("A101");
        when(assignments.findFirstByRoomIdAndEffectiveToBsIsNull(a))
                .thenReturn(Optional.of(new com.renterp.domain.tenancy.entity.RoomAssignment()));
        AddTenantRequest req = request("8000", a);
        req.setDepositAmount(rs("10000"));
        req.setAdvanceAmount(rs("8000"));
        assertEquals("ROOM_OCCUPIED",
                assertThrows(ApiException.class, () -> service.addTenant(propertyId, req)).getCode());
        assertNothingCreated();
    }

    // ── advanceMonths ────────────────────────────────────────────────────────

    @Test
    void advanceMonthsRoundsUpAndStaysInRange() {
        assertEquals(1, TenantOnboardingService.advanceMonths(rs("10000"), rs("10000")));
        assertEquals(2, TenantOnboardingService.advanceMonths(rs("15000"), rs("10000")));
        assertEquals(3, TenantOnboardingService.advanceMonths(rs("30000"), rs("10000")));
        assertEquals(1, TenantOnboardingService.advanceMonths(rs("500"), rs("10000")));
        assertEquals(1, TenantOnboardingService.advanceMonths(rs("500"), BigDecimal.ZERO));
        assertEquals(TenantOnboardingService.MAX_ADVANCE_MONTHS,
                TenantOnboardingService.advanceMonths(rs("99999999"), rs("1")));
    }

    @Test
    void moreThanOnlyWhenBothGiven() {
        assertEquals(false, TenantOnboardingService.moreThan(null, rs("1")));
        assertEquals(false, TenantOnboardingService.moreThan(rs("5"), null));
        assertEquals(false, TenantOnboardingService.moreThan(rs("5"), rs("5")));
        assertEquals(true, TenantOnboardingService.moreThan(rs("5.01"), rs("5")));
    }
}
