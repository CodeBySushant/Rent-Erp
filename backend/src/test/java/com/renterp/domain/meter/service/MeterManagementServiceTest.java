package com.renterp.domain.meter.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.meter.dto.AddMeterRequest;
import com.renterp.domain.meter.dto.CreateMeterRequest;
import com.renterp.domain.meter.dto.CreateMeterRoomCoverageRequest;
import com.renterp.domain.meter.dto.EndMeterRoomCoverageRequest;
import com.renterp.domain.meter.dto.MeterOverviewResponse;
import com.renterp.domain.meter.dto.MeterResponse;
import com.renterp.domain.meter.dto.SetMeterRoomsRequest;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.MeterPurpose;
import com.renterp.domain.meter.entity.Meter.MeterType;
import com.renterp.domain.meter.entity.Meter.ReadingResponsibility;
import com.renterp.domain.meter.entity.MeterRoomCoverage;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import com.renterp.domain.meterreading.service.MeterReadingService;
import com.renterp.domain.property.entity.Property;
import com.renterp.domain.property.repository.PropertyRepository;
import com.renterp.domain.structure.entity.Floor;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.FloorRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Meter Settings on the property page (owner, 28 Nov). */
class MeterManagementServiceTest {

    private final UUID propertyId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();

    private MeterService meterService;
    private MeterReadingService readings;
    private MeterRepository meters;
    private MeterRoomCoverageRepository coverage;
    private RoomRepository rooms;
    private MeterManagementService service;
    private final List<Meter> activeMeters = new ArrayList<>();
    private final List<Room> propertyRooms = new ArrayList<>();

    @BeforeEach
    void setUp() {
        meterService = mock(MeterService.class);
        readings = mock(MeterReadingService.class);
        meters = mock(MeterRepository.class);
        coverage = mock(MeterRoomCoverageRepository.class);
        PropertyRepository properties = mock(PropertyRepository.class);
        FloorRepository floors = mock(FloorRepository.class);
        rooms = mock(RoomRepository.class);
        service = new MeterManagementService(meterService, readings, meters, coverage, properties, floors, rooms);

        Property p = new Property();
        p.setId(propertyId);
        p.setElectricityBillingMode(Property.ElectricityBillingMode.SUB_METERED);
        when(properties.findById(propertyId)).thenReturn(Optional.of(p));

        Floor f = new Floor();
        f.setId(floorId);
        f.setPropertyId(propertyId);
        f.setActive(true);
        when(floors.findAllByPropertyId(propertyId)).thenReturn(List.of(f));
        when(rooms.findByFloorIdIn(any(), any())).thenAnswer(inv -> new PageImpl<>(propertyRooms));
        when(meters.findByPropertyIdAndMeterPurposeAndActiveTrue(propertyId, MeterPurpose.ELECTRICITY))
                .thenReturn(activeMeters);
        when(coverage.findByMeterIdOrderByEffectiveFromBsAsc(any())).thenReturn(List.of());
    }

    private UUID room(String name, boolean active) {
        Room r = new Room();
        r.setId(UUID.randomUUID());
        r.setFloorId(floorId);
        r.setName(name);
        r.setActive(active);
        propertyRooms.add(r);
        return r.getId();
    }

    private Meter meter(String label, MeterType type) {
        Meter m = new Meter();
        m.setId(UUID.randomUUID());
        m.setPropertyId(propertyId);
        m.setLabel(label);
        m.setMeterType(type);
        m.setMeterPurpose(MeterPurpose.ELECTRICITY);
        m.setActive(true);
        when(meters.findById(m.getId())).thenReturn(Optional.of(m));
        return m;
    }

    private MeterRoomCoverage row(Meter m, UUID roomId, String from, String to) {
        MeterRoomCoverage c = new MeterRoomCoverage();
        c.setId(UUID.randomUUID());
        c.setMeterId(m.getId());
        c.setRoomId(roomId);
        c.setEffectiveFromBs(from);
        c.setEffectiveToBs(to);
        return c;
    }

    private static String code(Runnable r) {
        return assertThrows(InvalidOperationException.class, r::run).getCode();
    }

    // ── overview ────────────────────────────────────────────────────────────

    @Test
    void overviewListsMainFirstWithRoomsAndFindsUnmeteredRooms() {
        UUID g1 = room("G1", true);
        UUID g2 = room("G2", true);
        UUID g3 = room("G3", true);
        room("Gone", false);
        Meter sub = meter("Sub B", MeterType.TENANT_SUPPLY);
        Meter main = meter("Main", MeterType.MAIN);
        activeMeters.addAll(List.of(sub, main));
        when(coverage.findByMeterIdOrderByEffectiveFromBsAsc(sub.getId())).thenReturn(List.of(
                row(sub, g1, "2082-01-01", null),
                row(sub, g2, "2082-01-01", "2082-05-01"))); // ended: not current

        MeterOverviewResponse res = service.overview(propertyId);

        assertEquals("SUB_METERED", res.electricityBillingMode());
        assertEquals(List.of("Main", "Sub B"), res.meters().stream().map(MeterOverviewResponse.MeterRooms::label).toList());
        assertEquals(List.of(g1), res.meters().get(1).roomIds());
        assertEquals(List.of(), res.meters().get(0).roomIds());
        assertEquals(List.of(g2, g3), res.unmeteredRoomIds());
    }

    // ── add ─────────────────────────────────────────────────────────────────

    private AddMeterRequest add(MeterType type, List<UUID> roomIds) {
        AddMeterRequest r = new AddMeterRequest();
        r.setLabel("  Sub 3 ");
        r.setSerialNumber(" SN-9 ");
        r.setMeterType(type);
        r.setOpeningReading(new BigDecimal("1520"));
        r.setReadingDateBs("2082-08-01");
        r.setRoomIds(roomIds);
        return r;
    }

    @Test
    void addSubMeterCreatesMeterOpeningReadingAndRoomsOnce() {
        UUID g1 = room("G1", true);
        UUID g2 = room("G2", true);
        Meter created = meter("Sub 3", MeterType.TENANT_SUPPLY);
        MeterResponse resp = mock(MeterResponse.class);
        when(resp.getId()).thenReturn(created.getId());
        when(meterService.createMeter(any())).thenReturn(resp);

        service.addMeter(propertyId, add(MeterType.TENANT_SUPPLY, List.of(g1, g2, g1)));

        ArgumentCaptor<CreateMeterRequest> c = ArgumentCaptor.forClass(CreateMeterRequest.class);
        verify(meterService).createMeter(c.capture());
        assertEquals("Sub 3", c.getValue().getLabel());
        assertEquals("SN-9", c.getValue().getSerialNumber());
        assertEquals(MeterPurpose.ELECTRICITY, c.getValue().getMeterPurpose());
        assertEquals(ReadingResponsibility.FIRST_SUBMISSION_WINS, c.getValue().getReadingResponsibility());
        verify(readings).insertConfirmedInternal(eq(created), eq(ReadingType.INITIAL), eq(new BigDecimal("1520")),
                eq("2082-08-01"), anyBoolean(), any(), any(), any(), any(), any(), eq(true));
        ArgumentCaptor<CreateMeterRoomCoverageRequest> cov = ArgumentCaptor.forClass(CreateMeterRoomCoverageRequest.class);
        verify(meterService, times(2)).addRoomCoverage(eq(created.getId()), cov.capture());
        assertEquals(List.of(g1, g2), cov.getAllValues().stream().map(CreateMeterRoomCoverageRequest::getRoomId).toList());
        assertEquals("2082-08-01", cov.getValue().getEffectiveFromBs());
    }

    @Test
    void addMainMeterIsReadByTheOwnerAndHasNoRooms() {
        Meter created = meter("Main", MeterType.MAIN);
        MeterResponse resp = mock(MeterResponse.class);
        when(resp.getId()).thenReturn(created.getId());
        when(meterService.createMeter(any())).thenReturn(resp);

        service.addMeter(propertyId, add(MeterType.MAIN, null));

        ArgumentCaptor<CreateMeterRequest> c = ArgumentCaptor.forClass(CreateMeterRequest.class);
        verify(meterService).createMeter(c.capture());
        assertEquals(ReadingResponsibility.LANDLORD_ONLY, c.getValue().getReadingResponsibility());
        verify(meterService, never()).addRoomCoverage(any(), any());
    }

    @Test
    void mainMeterWithRoomsIsRefusedBeforeAnythingIsMade() {
        UUID g1 = room("G1", true);
        assertEquals("MAIN_METER_HAS_NO_ROOMS", code(() -> service.addMeter(propertyId, add(MeterType.MAIN, List.of(g1)))));
        verify(meterService, never()).createMeter(any());
    }

    @Test
    void infrastructureTypeIsNotOfferedHere() {
        assertEquals("METER_TYPE_NOT_ALLOWED",
                code(() -> service.addMeter(propertyId, add(MeterType.INFRASTRUCTURE, null))));
    }

    @Test
    void openingReadingInTheFutureIsRefused() {
        AddMeterRequest r = add(MeterType.TENANT_SUPPLY, null);
        r.setReadingDateBs(BsCalendar.addDays(BsCalendar.today(), 1));
        assertEquals("READING_IN_FUTURE", code(() -> service.addMeter(propertyId, r)));
        verify(meterService, never()).createMeter(any());
    }

    // ── set rooms ───────────────────────────────────────────────────────────

    @Test
    void setRoomsKeepsAddsAndEndsOnlyWhatChanged() {
        UUID g1 = room("G1", true);
        UUID g2 = room("G2", true);
        UUID g3 = room("G3", true);
        Meter sub = meter("Sub 1", MeterType.TENANT_SUPPLY);
        MeterRoomCoverage keep = row(sub, g1, "2082-01-01", null);
        MeterRoomCoverage drop = row(sub, g2, "2082-01-01", null);
        when(coverage.findByMeterIdOrderByEffectiveFromBsAsc(sub.getId())).thenReturn(List.of(
                keep, drop, row(sub, g3, "2081-01-01", "2081-06-01")));
        SetMeterRoomsRequest req = new SetMeterRoomsRequest();
        req.setRoomIds(List.of(g1, g3));
        req.setEffectiveBs("2082-08-10");

        assertEquals(List.of(g1, g3), service.setRooms(sub.getId(), req));

        ArgumentCaptor<EndMeterRoomCoverageRequest> end = ArgumentCaptor.forClass(EndMeterRoomCoverageRequest.class);
        verify(meterService).endRoomCoverage(eq(sub.getId()), eq(drop.getId()), end.capture());
        assertEquals("2082-08-10", end.getValue().getEffectiveToBs());
        verify(meterService, never()).endRoomCoverage(eq(sub.getId()), eq(keep.getId()), any());
        ArgumentCaptor<CreateMeterRoomCoverageRequest> add = ArgumentCaptor.forClass(CreateMeterRoomCoverageRequest.class);
        verify(meterService).addRoomCoverage(eq(sub.getId()), add.capture());
        assertEquals(g3, add.getValue().getRoomId());
        assertEquals("2082-08-10", add.getValue().getEffectiveFromBs());
    }

    @Test
    void aRowStartingLaterThanTheDayEndsOnItsOwnStart() {
        UUID g1 = room("G1", true);
        Meter sub = meter("Sub 1", MeterType.TENANT_SUPPLY);
        MeterRoomCoverage future = row(sub, g1, "2082-09-01", null);
        when(coverage.findByMeterIdOrderByEffectiveFromBsAsc(sub.getId())).thenReturn(List.of(future));
        SetMeterRoomsRequest req = new SetMeterRoomsRequest();
        req.setRoomIds(List.of());
        req.setEffectiveBs("2082-08-10");

        service.setRooms(sub.getId(), req);

        ArgumentCaptor<EndMeterRoomCoverageRequest> end = ArgumentCaptor.forClass(EndMeterRoomCoverageRequest.class);
        verify(meterService).endRoomCoverage(eq(sub.getId()), eq(future.getId()), end.capture());
        assertEquals("2082-09-01", end.getValue().getEffectiveToBs());
    }

    @Test
    void mainMeterCannotBeGivenRooms() {
        UUID g1 = room("G1", true);
        Meter main = meter("Main", MeterType.MAIN);
        SetMeterRoomsRequest req = new SetMeterRoomsRequest();
        req.setRoomIds(List.of(g1));
        assertEquals("MAIN_METER_HAS_NO_ROOMS", code(() -> service.setRooms(main.getId(), req)));
    }

    @Test
    void removedMeterCannotBeGivenRooms() {
        Meter sub = meter("Sub 1", MeterType.TENANT_SUPPLY);
        sub.setActive(false);
        SetMeterRoomsRequest req = new SetMeterRoomsRequest();
        req.setRoomIds(List.of());
        assertEquals("METER_INACTIVE", code(() -> service.setRooms(sub.getId(), req)));
    }

    // ── remove ──────────────────────────────────────────────────────────────

    @Test
    void removeEndsOpenRoomsThenDeactivates() {
        UUID g1 = room("G1", true);
        Meter sub = meter("Sub 1", MeterType.TENANT_SUPPLY);
        MeterRoomCoverage open = row(sub, g1, "2082-01-01", null);
        when(coverage.findByMeterIdOrderByEffectiveFromBsAsc(sub.getId())).thenReturn(List.of(
                open, row(sub, g1, "2081-01-01", "2081-06-01")));

        service.removeMeter(sub.getId());

        verify(meterService, times(1)).endRoomCoverage(eq(sub.getId()), eq(open.getId()), any());
        verify(meterService).deactivateMeter(sub.getId());
    }

    @Test
    void mainMeterIsNotRemoved() {
        Meter main = meter("Main", MeterType.MAIN);
        assertEquals("MAIN_METER_REQUIRED", code(() -> service.removeMeter(main.getId())));
        verify(meterService, never()).deactivateMeter(any());
    }

    @Test
    void distinctKeepsOrderAndAcceptsNull() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(List.of(a, b), MeterManagementService.distinct(List.of(a, b, a)));
        assertEquals(List.of(), MeterManagementService.distinct(null));
    }
}
