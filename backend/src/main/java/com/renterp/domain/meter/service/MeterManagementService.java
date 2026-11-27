package com.renterp.domain.meter.service;

import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.meter.dto.AddMeterRequest;
import com.renterp.domain.meter.dto.CreateMeterRequest;
import com.renterp.domain.meter.dto.CreateMeterRoomCoverageRequest;
import com.renterp.domain.meter.dto.EndMeterRoomCoverageRequest;
import com.renterp.domain.meter.dto.MeterOverviewResponse;
import com.renterp.domain.meter.dto.MeterOverviewResponse.MeterRooms;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Managing electricity meters after setup (owner, 28 Nov): the property page's
 * Meter Settings tab. Each operation is one transaction built on the existing
 * {@link MeterService} rules (one main meter per property, unique serials,
 * coverage only on sub meters, rooms of the same property only).
 *
 * Coverage dates: billing counts a room on a meter when
 * {@code from <= day < to} (see {@code findActiveAsOf}), so a room taken off a
 * meter today and put on another from today is billed by exactly one of them.
 */
@Service
public class MeterManagementService {

    private static final Logger log = LogManager.getLogger(MeterManagementService.class);

    private final MeterService meterService;
    private final MeterReadingService readingService;
    private final MeterRepository meters;
    private final MeterRoomCoverageRepository coverage;
    private final PropertyRepository properties;
    private final FloorRepository floors;
    private final RoomRepository rooms;

    public MeterManagementService(MeterService meterService, MeterReadingService readingService,
                                  MeterRepository meters, MeterRoomCoverageRepository coverage,
                                  PropertyRepository properties, FloorRepository floors, RoomRepository rooms) {
        this.meterService = meterService;
        this.readingService = readingService;
        this.meters = meters;
        this.coverage = coverage;
        this.properties = properties;
        this.floors = floors;
        this.rooms = rooms;
    }

    // ── Overview ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MeterOverviewResponse overview(UUID propertyId) {
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        List<Meter> active = new ArrayList<>(
                meters.findByPropertyIdAndMeterPurposeAndActiveTrue(propertyId, MeterPurpose.ELECTRICITY));
        active.sort(Comparator.comparing((Meter m) -> m.getMeterType() == MeterType.MAIN ? 0 : 1)
                .thenComparing(m -> m.getLabel() == null ? "" : m.getLabel(), String.CASE_INSENSITIVE_ORDER));

        Set<UUID> metered = new HashSet<>();
        List<MeterRooms> out = new ArrayList<>();
        for (Meter m : active) {
            List<UUID> roomIds = openRoomIds(m.getId());
            if (m.getMeterType() == MeterType.TENANT_SUPPLY) {
                metered.addAll(roomIds);
            }
            out.add(new MeterRooms(m.getId(), m.getLabel(), m.getSerialNumber(), m.getMeterType(), roomIds));
        }

        List<UUID> unmetered = activeRooms(propertyId).stream()
                .map(Room::getId)
                .filter(id -> !metered.contains(id))
                .toList();
        String mode = property.getElectricityBillingMode() == null ? null : property.getElectricityBillingMode().name();
        return new MeterOverviewResponse(mode, out, unmetered);
    }

    // ── Add ─────────────────────────────────────────────────────────────────

    @Transactional
    public MeterResponse addMeter(UUID propertyId, AddMeterRequest req) {
        if (req.getMeterType() != MeterType.MAIN && req.getMeterType() != MeterType.TENANT_SUPPLY) {
            throw new InvalidOperationException("METER_TYPE_NOT_ALLOWED", "Choose main meter or sub meter.");
        }
        List<UUID> roomIds = distinct(req.getRoomIds());
        if (req.getMeterType() == MeterType.MAIN && !roomIds.isEmpty()) {
            throw new InvalidOperationException("MAIN_METER_HAS_NO_ROOMS",
                    "A main meter covers the whole property. Choose rooms only for a sub meter.");
        }
        String today = BsCalendar.today();
        String readingDate = req.getReadingDateBs() == null ? today : req.getReadingDateBs();
        if (readingDate.compareTo(today) > 0) {
            throw new InvalidOperationException("READING_IN_FUTURE", "The opening reading date cannot be in the future.");
        }

        CreateMeterRequest create = new CreateMeterRequest();
        create.setPropertyId(propertyId);
        create.setLabel(req.getLabel().trim());
        create.setSerialNumber(req.getSerialNumber() == null ? null : req.getSerialNumber().trim());
        create.setMeterPurpose(MeterPurpose.ELECTRICITY);
        create.setMeterType(req.getMeterType());
        // Same as setup: the owner reads the main meter; anyone on a sub meter may.
        create.setReadingResponsibility(req.getMeterType() == MeterType.MAIN
                ? ReadingResponsibility.LANDLORD_ONLY : ReadingResponsibility.FIRST_SUBMISSION_WINS);
        MeterResponse created = meterService.createMeter(create);

        Meter meter = requireMeter(created.getId());
        readingService.insertConfirmedInternal(meter, ReadingType.INITIAL, req.getOpeningReading(), readingDate,
                false, null, null, "Opening reading (meter added from Meter Settings)", null, null, true);

        for (UUID roomId : roomIds) {
            CreateMeterRoomCoverageRequest add = new CreateMeterRoomCoverageRequest();
            add.setRoomId(roomId);
            add.setEffectiveFromBs(readingDate);
            meterService.addRoomCoverage(meter.getId(), add);
        }
        log.info("Meter added from Meter Settings — id: {}, property: {}, type: {}, rooms: {}",
                meter.getId(), propertyId, meter.getMeterType(), roomIds.size());
        return created;
    }

    // ── Rooms ───────────────────────────────────────────────────────────────

    /** Makes the meter's current rooms exactly {@code req.roomIds}; returns them. */
    @Transactional
    public List<UUID> setRooms(UUID meterId, SetMeterRoomsRequest req) {
        Meter meter = requireMeter(meterId);
        if (!meter.isActive()) {
            throw new InvalidOperationException("METER_INACTIVE", "This meter has been removed.");
        }
        List<UUID> wanted = distinct(req.getRoomIds());
        if (meter.getMeterType() != MeterType.TENANT_SUPPLY && !wanted.isEmpty()) {
            throw new InvalidOperationException("MAIN_METER_HAS_NO_ROOMS",
                    "Only a sub meter covers chosen rooms.");
        }
        String day = req.getEffectiveBs() == null ? BsCalendar.today() : req.getEffectiveBs();

        Set<UUID> keep = new HashSet<>(wanted);
        Set<UUID> current = new HashSet<>();
        int removed = 0;
        for (MeterRoomCoverage c : coverage.findByMeterIdOrderByEffectiveFromBsAsc(meterId)) {
            if (c.getEffectiveToBs() != null) {
                continue;
            }
            if (keep.contains(c.getRoomId())) {
                current.add(c.getRoomId());
            } else {
                endCoverage(meterId, c, day);
                removed++;
            }
        }
        int added = 0;
        for (UUID roomId : wanted) {
            if (current.contains(roomId)) {
                continue;
            }
            CreateMeterRoomCoverageRequest add = new CreateMeterRoomCoverageRequest();
            add.setRoomId(roomId);
            add.setEffectiveFromBs(day);
            meterService.addRoomCoverage(meterId, add);
            added++;
        }
        log.info("Meter rooms set — meter: {}, rooms: {}, added: {}, removed: {}",
                meterId, wanted.size(), added, removed);
        return wanted;
    }

    // ── Remove ──────────────────────────────────────────────────────────────

    /**
     * Takes every room off a sub meter today and deactivates it. Its readings
     * and past bills stay. The main meter is not removed this way.
     */
    @Transactional
    public void removeMeter(UUID meterId) {
        Meter meter = requireMeter(meterId);
        if (meter.getMeterType() == MeterType.MAIN) {
            throw new InvalidOperationException("MAIN_METER_REQUIRED",
                    "The main meter cannot be removed. Rename it, or replace it from Readings.");
        }
        String today = BsCalendar.today();
        for (MeterRoomCoverage c : coverage.findByMeterIdOrderByEffectiveFromBsAsc(meterId)) {
            if (c.getEffectiveToBs() == null) {
                endCoverage(meterId, c, today);
            }
        }
        meterService.deactivateMeter(meterId);
        log.info("Meter removed from Meter Settings — id: {}", meterId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Ends an open row on {@code day}, or on its own start day if that is later. */
    private void endCoverage(UUID meterId, MeterRoomCoverage c, String day) {
        EndMeterRoomCoverageRequest end = new EndMeterRoomCoverageRequest();
        end.setEffectiveToBs(day.compareTo(c.getEffectiveFromBs()) < 0 ? c.getEffectiveFromBs() : day);
        meterService.endRoomCoverage(meterId, c.getId(), end);
    }

    private List<UUID> openRoomIds(UUID meterId) {
        List<UUID> ids = new ArrayList<>();
        for (MeterRoomCoverage c : coverage.findByMeterIdOrderByEffectiveFromBsAsc(meterId)) {
            if (c.getEffectiveToBs() == null && !ids.contains(c.getRoomId())) {
                ids.add(c.getRoomId());
            }
        }
        return ids;
    }

    private List<Room> activeRooms(UUID propertyId) {
        List<UUID> floorIds = floors.findAllByPropertyId(propertyId).stream()
                .filter(Floor::isActive).map(Floor::getId).toList();
        if (floorIds.isEmpty()) {
            return List.of();
        }
        return rooms.findByFloorIdIn(floorIds, Pageable.unpaged()).getContent().stream()
                .filter(Room::isActive).toList();
    }

    private Meter requireMeter(UUID id) {
        return meters.findById(id).orElseThrow(() -> new ResourceNotFoundException("Meter", "id", id));
    }

    static List<UUID> distinct(List<UUID> ids) {
        return ids == null ? List.of() : List.copyOf(new LinkedHashSet<>(ids));
    }
}
