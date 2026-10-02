package com.renterp.domain.meterreading.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.meter.entity.Meter;
import com.renterp.domain.meter.entity.Meter.MeterStatus;
import com.renterp.domain.meter.entity.Meter.MeterType;
import com.renterp.domain.meter.entity.Meter.ReadingResponsibility;
import com.renterp.domain.meter.entity.MeterRoomCoverage;
import com.renterp.domain.meter.repository.MeterRepository;
import com.renterp.domain.meter.repository.MeterRoomCoverageRepository;
import com.renterp.domain.meterreading.dto.MeterReadingResponse;
import com.renterp.domain.meterreading.dto.ReadingDueResponse;
import com.renterp.domain.meterreading.dto.SubmitReadingRequest;
import com.renterp.domain.meterreading.dto.TenantReadingRequest;
import com.renterp.domain.meterreading.entity.MeterReading;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingStatus;
import com.renterp.domain.meterreading.entity.MeterReading.ReadingType;
import com.renterp.domain.meterreading.repository.MeterReadingRepository;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantProfile;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Readings for the current BS month: which meters still need one (owner),
 * which meters a tenant may read (tenant), and the tenant's own submission.
 *
 * A tenant may submit the monthly reading of a TENANT_SUPPLY meter when they
 * have an active tenancy at its property and the meter's responsibility lets
 * them: DESIGNATED_TENANT → they are the designated tenant; FIRST_SUBMISSION_WINS
 * → the meter covers one of their current rooms; LANDLORD_ONLY → never. Only
 * one reading per meter per month: the first submission wins and later ones
 * are refused. Tenant readings are always PENDING until the owner confirms.
 */
@Service
public class ReadingDueService {

    private final AccessGuard guard;
    private final MeterRepository meters;
    private final MeterRoomCoverageRepository coverage;
    private final MeterReadingRepository readings;
    private final MeterReadingService readingService;
    private final RoomRepository rooms;
    private final TenantProfileRepository profiles;
    private final TenantPropertyMembershipRepository memberships;
    private final RoomAssignmentRepository assignments;
    private final StoredFileRepository files;
    private final EntityManager em;

    public ReadingDueService(AccessGuard guard, MeterRepository meters, MeterRoomCoverageRepository coverage,
                             MeterReadingRepository readings, MeterReadingService readingService,
                             RoomRepository rooms, TenantProfileRepository profiles,
                             TenantPropertyMembershipRepository memberships, RoomAssignmentRepository assignments,
                             StoredFileRepository files, EntityManager em) {
        this.guard = guard;
        this.meters = meters;
        this.coverage = coverage;
        this.readings = readings;
        this.readingService = readingService;
        this.rooms = rooms;
        this.profiles = profiles;
        this.memberships = memberships;
        this.assignments = assignments;
        this.files = files;
        this.em = em;
    }

    /** Owner: every active meter of the property and its state this month. */
    @Transactional(readOnly = true)
    public List<ReadingDueResponse> dueForProperty(UUID propertyId) {
        String monthStart = BsCalendar.monthStart(BsCalendar.today());
        return activeMeters(propertyId).stream()
                .map(m -> row(m, monthStart, true, null))
                .toList();
    }

    /** Tenant: the meters of their active tenancies, with whether they may read each. */
    @Transactional(readOnly = true)
    public List<ReadingDueResponse> myMeters() {
        AuthUser user = guard.requireUser();
        Optional<TenantProfile> profile = profiles.findByUserId(user.userId());
        if (profile.isEmpty()) {
            return List.of();
        }
        String monthStart = BsCalendar.monthStart(BsCalendar.today());
        List<ReadingDueResponse> out = new ArrayList<>();
        for (TenantPropertyMembership m : activeMemberships(profile.get().getId())) {
            Set<UUID> myRooms = currentRooms(m.getId());
            for (Meter meter : activeMeters(m.getPropertyId())) {
                if (meter.getMeterType() != MeterType.TENANT_SUPPLY) {
                    continue;
                }
                boolean designated = profile.get().getId().equals(meter.getDesignatedTenantId());
                boolean covers = coveredRooms(meter.getId()).stream().anyMatch(myRooms::contains);
                if (!designated && !covers) {
                    continue;
                }
                String reason = refusal(meter, designated, covers);
                if (reason == null && readings.findLatestConfirmed(meter.getId()).isEmpty()) {
                    reason = "The owner must record this meter's first reading.";
                }
                out.add(row(meter, monthStart, reason == null, reason));
            }
        }
        return out;
    }

    /** Tenant submits this month's reading of a meter they may read. */
    @Transactional
    public MeterReadingResponse submitOwn(UUID meterId, TenantReadingRequest req) {
        AuthUser user = guard.requireUser();
        TenantProfile profile = profiles.findByUserId(user.userId())
                .orElseThrow(() -> ApiException.forbidden("Only a tenant of this property can submit this reading."));
        Meter meter = meters.findById(meterId)
                .filter(x -> x.isActive() && x.getStatus() == MeterStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("METER_NOT_FOUND", "Meter not found."));
        TenantPropertyMembership membership = activeMemberships(profile.getId()).stream()
                .filter(m -> m.getPropertyId().equals(meter.getPropertyId()))
                .findFirst()
                .orElseThrow(() -> ApiException.forbidden("Only a tenant of this property can submit this reading."));
        boolean designated = profile.getId().equals(meter.getDesignatedTenantId());
        boolean covers = coveredRooms(meterId).stream().anyMatch(currentRooms(membership.getId())::contains);
        String reason = refusal(meter, designated, covers);
        if (reason != null) {
            throw ApiException.forbidden(reason);
        }
        if (readings.findLatestConfirmed(meterId).isEmpty()) {
            throw ApiException.badRequest("NO_FIRST_READING", "The owner must record this meter's first reading.");
        }
        String today = BsCalendar.today();
        if (thisMonth(meterId, BsCalendar.monthStart(today)).isPresent()) {
            throw ApiException.conflict("READING_ALREADY_SUBMITTED",
                    "This month's reading for this meter has already been submitted.");
        }

        SubmitReadingRequest s = new SubmitReadingRequest();
        s.setReadingType(ReadingType.BILLING_RUN);
        s.setReadingValue(req.getReadingValue());
        s.setReadingDateBs(today);
        s.setSubmissionDateBs(today);
        s.setNotes(req.getNotes());
        if (req.getPhotoFileId() != null) {
            StoredFile photo = files.findByIdAndDeletedAtIsNull(req.getPhotoFileId())
                    .filter(f -> f.getOwnerUserId().equals(user.userId()) && f.getPurpose() == FilePurpose.METER_PHOTO)
                    .orElseThrow(() -> ApiException.badRequest("PHOTO_INVALID",
                            "The meter photo must be a meter photo you uploaded."));
            s.setPhotoUrl("/api/v1/files/" + photo.getId() + "/content");
        }
        MeterReadingResponse saved = readingService.submitReading(meterId, s);
        readings.findById(saved.getId()).ifPresent(r -> {
            r.setSubmittedBy(user.userId());
            readings.save(r);
        });
        return saved;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Null when the tenant may read the meter, else the reason. */
    private static String refusal(Meter meter, boolean designated, boolean covers) {
        if (meter.getMeterType() != MeterType.TENANT_SUPPLY) {
            return "Only the owner records this meter.";
        }
        return switch (meter.getReadingResponsibility()) {
            case LANDLORD_ONLY -> "Only the owner records this meter.";
            case DESIGNATED_TENANT -> designated ? null : "Another tenant is responsible for this meter.";
            case FIRST_SUBMISSION_WINS -> covers ? null : "This meter does not cover your room.";
        };
    }

    private ReadingDueResponse row(Meter m, String monthStart, boolean canSubmit, String reason) {
        Optional<MeterReading> last = readings.findLatestConfirmed(m.getId());
        Optional<MeterReading> now = thisMonth(m.getId(), monthStart);
        List<String> roomNames = rooms.findAllById(coveredRooms(m.getId())).stream()
                .map(Room::getName).sorted().toList();
        boolean byTenant = now.map(r -> r.getSubmittedBy() != null
                && profiles.findByUserId(r.getSubmittedBy()).isPresent()).orElse(false);
        boolean open = now.isEmpty();
        return new ReadingDueResponse(m.getId(), m.getPropertyId(), m.getLabel(), m.getSerialNumber(),
                m.getMeterType().name(), m.getMeterPurpose().name(), m.getReadingResponsibility().name(), roomNames,
                last.map(MeterReading::getReadingValue).orElse(null),
                last.map(MeterReading::getReadingDateBs).orElse(null),
                now.map(r -> r.getStatus().name()).orElse("NONE"),
                now.map(MeterReading::getId).orElse(null),
                now.map(MeterReading::getReadingValue).orElse(null),
                now.map(MeterReading::getPhotoUrl).orElse(null),
                byTenant,
                canSubmit && open,
                !canSubmit ? reason : (open ? null : "This month's reading has already been submitted."));
    }

    /** The meter's BILLING_RUN reading (pending or confirmed) dated this month, if any. */
    private Optional<MeterReading> thisMonth(UUID meterId, String monthStart) {
        return em.createQuery("""
                select r from MeterReading r
                where r.meterId = :meter and r.readingType = :type and r.readingDateBs >= :since
                  and r.status in (:statuses)
                order by r.readingDateBs desc
                """, MeterReading.class)
                .setParameter("meter", meterId)
                .setParameter("type", ReadingType.BILLING_RUN)
                .setParameter("since", monthStart)
                .setParameter("statuses", List.of(ReadingStatus.PENDING, ReadingStatus.CONFIRMED))
                .setMaxResults(1)
                .getResultStream().findFirst();
    }

    private List<Meter> activeMeters(UUID propertyId) {
        return em.createQuery("""
                select m from Meter m where m.propertyId = :pid and m.active = true and m.status = :active
                order by m.label
                """, Meter.class)
                .setParameter("pid", propertyId)
                .setParameter("active", MeterStatus.ACTIVE)
                .getResultList();
    }

    private Set<UUID> coveredRooms(UUID meterId) {
        return coverage.findActiveAsOf(meterId, BsCalendar.today()).stream()
                .map(MeterRoomCoverage::getRoomId).collect(Collectors.toSet());
    }

    private List<TenantPropertyMembership> activeMemberships(UUID profileId) {
        return memberships.findByTenantProfileId(profileId, Pageable.unpaged()).getContent().stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE).toList();
    }

    private Set<UUID> currentRooms(UUID membershipId) {
        return assignments.findByMembershipIdAndEffectiveToBsIsNull(membershipId).stream()
                .map(RoomAssignment::getRoomId).collect(Collectors.toSet());
    }
}
