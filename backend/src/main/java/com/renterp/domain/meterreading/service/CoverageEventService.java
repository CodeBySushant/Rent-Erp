package com.renterp.domain.meterreading.service;

import com.renterp.common.exception.DuplicateResourceException;
import com.renterp.common.exception.InvalidOperationException;
import com.renterp.common.exception.ResourceNotFoundException;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.MeterStatus;
import com.renterp.domain.meter.entity.Meter.MeterType;
import com.renterp.domain.meter.entity.MeterRoomCoverage;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.meterreading.dto.CoverageEventResponse;
import com.renterp.domain.meterreading.dto.CreateCoverageEventRequest;
import com.renterp.domain.meterreading.dto.CreateCoverageEventRequest.AffectedMeter;
import com.renterp.domain.meterreading.entity.MeterCoverageEvent;
import com.renterp.domain.meterreading.entity.MeterCoverageEventChange;
import com.renterp.domain.meterreading.entity.MeterReading;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import com.renterp.domain.meterreading.repository.MeterCoverageEventChangeRepository;
import com.renterp.domain.meterreading.repository.MeterCoverageEventRepository;
import com.renterp.domain.structure.repository.RoomRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CoverageEventService {

    private static final Logger log = LogManager.getLogger(CoverageEventService.class);

    private final MeterCoverageEventRepository eventRepository;
    private final MeterCoverageEventChangeRepository changeRepository;
    private final MeterRepository meterRepository;
    private final MeterRoomCoverageRepository coverageRepository;
    private final RoomRepository roomRepository;
    private final MeterReadingService readingService;
    private final com.renterp.domain.meter.service.CoverageRules coverageRules;

    public CoverageEventService(com.renterp.domain.meter.service.CoverageRules coverageRules,
                                 MeterCoverageEventRepository eventRepository,
                                 MeterCoverageEventChangeRepository changeRepository,
                                 MeterRepository meterRepository,
                                 MeterRoomCoverageRepository coverageRepository,
                                 RoomRepository roomRepository,
                                 MeterReadingService readingService) {
        this.coverageRules = coverageRules;
        this.eventRepository = eventRepository;
        this.changeRepository = changeRepository;
        this.meterRepository = meterRepository;
        this.coverageRepository = coverageRepository;
        this.roomRepository = roomRepository;
        this.readingService = readingService;
    }

    @Transactional
    public CoverageEventResponse createEvent(CreateCoverageEventRequest req) {
        log.debug("Creating coverage event — type: {}, date: {}, meters: {}",
                req.getEventType(), req.getEventDateBs(), req.getAffectedMeters().size());

        // 1. Persist the header row.
        MeterCoverageEvent event = MeterCoverageEvent.builder()
                .propertyId(req.getPropertyId())
                .eventType(req.getEventType())
                .eventDateBs(req.getEventDateBs())
                .notes(req.getNotes())
                .build();
        event = eventRepository.saveAndFlush(event);

        // 2. Process the affected meters atomically; any failure rolls back the whole txn.
        //    H15: all removals happen before any addition, so a room can move from
        //    one meter to another inside one event whatever order the meters come in;
        //    every addition then passes CoverageRules (same property, not metered twice).
        List<Meter> meters = new ArrayList<>();
        for (AffectedMeter am : req.getAffectedMeters()) {
            meters.add(validateMeter(req, am));
        }
        for (int i = 0; i < meters.size(); i++) {
            removeRooms(meters.get(i), req, req.getAffectedMeters().get(i));
        }
        for (int i = 0; i < meters.size(); i++) {
            addRooms(meters.get(i), req, req.getAffectedMeters().get(i));
        }
        List<MeterCoverageEventChange> changes = new ArrayList<>();
        for (int i = 0; i < meters.size(); i++) {
            changes.add(recordChange(event, meters.get(i), req, req.getAffectedMeters().get(i)));
        }

        log.info("Coverage event created — id: {}, changes: {}", event.getId(), changes.size());
        return CoverageEventResponse.from(event, changes);
    }

    @Transactional(readOnly = true)
    public CoverageEventResponse getEvent(UUID eventId) {
        MeterCoverageEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("MeterCoverageEvent", "id", eventId));
        return CoverageEventResponse.from(event, changeRepository.findByCoverageEventIdOrderByCreatedAtAsc(eventId));
    }

    @Transactional(readOnly = true)
    public Page<CoverageEventResponse> listByProperty(UUID propertyId, Pageable pageable) {
        return eventRepository.findByPropertyId(propertyId, pageable)
                .map(e -> CoverageEventResponse.from(e, changeRepository.findByCoverageEventIdOrderByCreatedAtAsc(e.getId())));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Meter validateMeter(CreateCoverageEventRequest req, AffectedMeter am) {
        Meter meter = meterRepository.findById(am.getMeterId())
                .orElseThrow(() -> new ResourceNotFoundException("Meter", "id", am.getMeterId()));
        if (meter.getStatus() == MeterStatus.INACTIVE) {
            throw new InvalidOperationException("Meter " + meter.getId() + " is INACTIVE — cannot participate in a coverage event");
        }
        if (!meter.getPropertyId().equals(req.getPropertyId())) {
            throw new InvalidOperationException("Meter " + meter.getId() + " does not belong to property " + req.getPropertyId());
        }
        if (meter.getMeterType() != MeterType.TENANT_SUPPLY) {
            throw new InvalidOperationException(
                    "Coverage events only apply to TENANT_SUPPLY meters — meter " + meter.getId() + " is " + meter.getMeterType());
        }
        return meter;
    }

    private void removeRooms(Meter meter, CreateCoverageEventRequest req, AffectedMeter am) {
        List<UUID> roomsRemoved = am.getRoomsRemoved() == null ? List.of() : am.getRoomsRemoved();
        for (UUID roomId : roomsRemoved) {
            Optional<MeterRoomCoverage> active =
                    coverageRepository.findFirstByMeterIdAndRoomIdAndEffectiveToBsIsNull(meter.getId(), roomId);
            if (active.isEmpty()) {
                throw new InvalidOperationException(
                    "Meter " + meter.getId() + " has no active coverage on room " + roomId + " — cannot remove");
            }
            if (req.getEventDateBs().compareTo(active.get().getEffectiveFromBs()) < 0) {   // H16
                throw new InvalidOperationException("INVALID_DATE",
                        "The event date is before this room's coverage started.");
            }
            active.get().setEffectiveToBs(req.getEventDateBs());
            coverageRepository.saveAndFlush(active.get());
        }
    }

    private void addRooms(Meter meter, CreateCoverageEventRequest req, AffectedMeter am) {
        List<UUID> roomsAdded = am.getRoomsAdded() == null ? List.of() : am.getRoomsAdded();
        for (UUID roomId : roomsAdded) {
            Optional<MeterRoomCoverage> current =
                    coverageRepository.findFirstByMeterIdAndRoomIdAndEffectiveToBsIsNull(meter.getId(), roomId);
            if (current.isPresent()) {
                throw new DuplicateResourceException("MeterRoomCoverage",
                        "meterId+roomId (active)", meter.getId() + "+" + roomId);
            }
            coverageRules.requireCoverable(meter, roomId);   // H15
            coverageRepository.saveAndFlush(MeterRoomCoverage.builder()
                    .meterId(meter.getId()).roomId(roomId)
                    .effectiveFromBs(req.getEventDateBs())
                    .build());
        }
    }

    private MeterCoverageEventChange recordChange(MeterCoverageEvent event, Meter meter,
                                                  CreateCoverageEventRequest req, AffectedMeter am) {
        List<UUID> roomsAdded = am.getRoomsAdded() == null ? List.of() : am.getRoomsAdded();
        List<UUID> roomsRemoved = am.getRoomsRemoved() == null ? List.of() : am.getRoomsRemoved();
        boolean coverageChanged = !roomsAdded.isEmpty() || !roomsRemoved.isEmpty();
        UUID anchorId = null;
        if (am.getAnchorReading() != null) {
            // M6: insertConfirmedInternal now checks the anchor against the
            // meter's last confirmed reading (date and value).
            MeterReading anchor = readingService.insertConfirmedInternal(
                    meter,
                    ReadingType.COVERAGE_ANCHOR,
                    am.getAnchorReading().getReadingValue(),
                    req.getEventDateBs(),
                    false, null,
                    am.getAnchorReading().getPhotoUrl(),
                    am.getAnchorReading().getNotes(),
                    null,
                    event.getId(),
                    /* skipConsumption */ false
            );
            anchorId = anchor.getId();
        } else if (coverageChanged) {
            throw new InvalidOperationException(
                    "anchorReading is required for meter " + meter.getId() + " (coverage changed on this meter, spec §14.1 M9)");
        }

        MeterCoverageEventChange change = MeterCoverageEventChange.builder()
                .coverageEventId(event.getId())
                .meterId(meter.getId())
                .anchorReadingId(anchorId)
                .roomsAdded(new ArrayList<>(roomsAdded))
                .roomsRemoved(new ArrayList<>(roomsRemoved))
                .build();
        return changeRepository.saveAndFlush(change);
    }
}
