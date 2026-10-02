package com.renterp.domain.request.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.util.BsCalendar;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.moveout.dto.MoveOutNoticeRequest;
import com.renterp.domain.moveout.entity.MoveOut;
import com.renterp.domain.moveout.repository.MoveOutRepository;
import com.renterp.domain.moveout.service.MoveOutService;
import com.renterp.domain.request.dto.CreateRequestRequest;
import com.renterp.domain.request.dto.DecideRequestRequest;
import com.renterp.domain.request.dto.RequestResponse;
import com.renterp.domain.request.entity.TenantRequest;
import com.renterp.domain.request.entity.TenantRequest.Status;
import com.renterp.domain.request.entity.TenantRequest.Type;
import com.renterp.domain.request.repository.TenantRequestRepository;
import com.renterp.domain.structure.entity.Room;
import com.renterp.domain.structure.repository.RoomRepository;
import com.renterp.domain.tenancy.dto.RoomTransferRequest;
import com.renterp.domain.tenancy.entity.RoomAssignment;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership;
import com.renterp.domain.tenancy.entity.TenantPropertyMembership.MembershipStatus;
import com.renterp.domain.tenancy.repository.RoomAssignmentRepository;
import com.renterp.domain.tenancy.repository.TenantProfileRepository;
import com.renterp.domain.tenancy.repository.TenantPropertyMembershipRepository;
import com.renterp.domain.tenancy.service.RoomTransferService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant requests: raise, approve / reject, complete, withdraw.
 *
 * Approving a VACATE opens the move-out notice (on the requested date, or the
 * approval note's date); approving a ROOM_CHANGE with a target room performs
 * the room transfer in the same transaction and completes the request. Every
 * move between statuses is checked under a row lock, so a request is decided
 * once.
 */
@Service
public class TenantRequestService {

    private static final List<Status> OPEN = List.of(Status.PENDING, Status.APPROVED);

    private final TenantRequestRepository requests;
    private final TenantPropertyMembershipRepository memberships;
    private final TenantProfileRepository profiles;
    private final StoredFileRepository files;
    private final MoveOutService moveOutService;
    private final MoveOutRepository moveOuts;
    private final RoomTransferService transfers;
    private final RoomAssignmentRepository assignments;
    private final RoomRepository rooms;
    private final AccessGuard guard;

    public TenantRequestService(TenantRequestRepository requests, TenantPropertyMembershipRepository memberships,
                                TenantProfileRepository profiles, StoredFileRepository files,
                                MoveOutService moveOutService, MoveOutRepository moveOuts,
                                RoomTransferService transfers, RoomAssignmentRepository assignments,
                                RoomRepository rooms, AccessGuard guard) {
        this.requests = requests;
        this.memberships = memberships;
        this.profiles = profiles;
        this.files = files;
        this.moveOutService = moveOutService;
        this.moveOuts = moveOuts;
        this.transfers = transfers;
        this.assignments = assignments;
        this.rooms = rooms;
        this.guard = guard;
    }

    @Transactional
    public RequestResponse create(UUID membershipId, CreateRequestRequest req) {
        AuthUser user = guard.requireUser();
        TenantPropertyMembership m = memberships.findById(membershipId)
                .orElseThrow(() -> ApiException.notFound("MEMBERSHIP_NOT_FOUND", "Tenancy not found."));
        if (m.getStatus() != MembershipStatus.ACTIVE) {
            throw ApiException.badRequest("TENANCY_ENDED", "This tenancy has already ended.");
        }
        if ((req.getType() == Type.ROOM_CHANGE || req.getType() == Type.VACATE)
                && requests.existsByMembershipIdAndTypeAndStatusIn(membershipId, req.getType(), OPEN)) {
            throw ApiException.conflict("REQUEST_ALREADY_OPEN", "A request of this kind is already open.");
        }
        if (req.getType() == Type.VACATE && req.getPreferredDateBs() == null) {
            throw ApiException.badRequest("DATE_REQUIRED", "Choose the date you want to move out.");
        }
        if (req.getPreferredDateBs() != null) {
            if (!BsCalendar.isValid(req.getPreferredDateBs())) {
                throw ApiException.badRequest("INVALID_DATE", "That is not a valid BS date.");
            }
            if (req.getPreferredDateBs().compareTo(BsCalendar.today()) < 0) {
                throw ApiException.badRequest("DATE_IN_PAST", "The date cannot be in the past.");
            }
        }
        if (req.getPhotoFileId() != null) {
            files.findByIdAndDeletedAtIsNull(req.getPhotoFileId())
                    .filter(f -> f.getOwnerUserId().equals(user.userId()) && f.getPurpose() == FilePurpose.REQUEST_PHOTO)
                    .orElseThrow(() -> ApiException.badRequest("PHOTO_INVALID", "The photo must be one you uploaded for this request."));
        }
        boolean byTenant = profiles.findById(m.getTenantProfileId())
                .map(tp -> user.userId().equals(tp.getUserId())).orElse(false);
        TenantRequest saved = requests.save(TenantRequest.builder()
                .membershipId(membershipId)
                .propertyId(m.getPropertyId())
                .type(req.getType())
                .status(Status.PENDING)
                .title(req.getTitle().trim())
                .description(req.getDescription())
                .preferredDateBs(req.getPreferredDateBs())
                .photoFileId(req.getPhotoFileId())
                .createdBy(user.userId())
                .byTenant(byTenant)
                .build());
        return response(saved);
    }

    @Transactional
    public RequestResponse approve(UUID id, DecideRequestRequest req) {
        AuthUser user = guard.requireUser();
        TenantRequest r = lock(id, Status.PENDING);
        r.setStatus(Status.APPROVED);
        r.setOwnerNote(req.getNote());
        r.setDecidedBy(user.userId());
        r.setDecidedAt(Instant.now());

        if (r.getType() == Type.VACATE) {
            boolean open = moveOuts.findFirstByMembershipIdAndStatus(r.getMembershipId(), MoveOut.Status.NOTICE_GIVEN).isPresent();
            if (!open) {
                MoveOutNoticeRequest notice = new MoveOutNoticeRequest();
                notice.setPlannedMoveOutBs(req.getEffectiveDateBs() != null ? req.getEffectiveDateBs() : r.getPreferredDateBs());
                notice.setReason(r.getTitle());
                moveOutService.giveNotice(r.getMembershipId(), notice);
            }
        } else if (r.getType() == Type.ROOM_CHANGE && req.getToRoomId() != null) {
            if (req.getEffectiveDateBs() == null) {
                throw ApiException.badRequest("DATE_REQUIRED", "Choose the date of the room change.");
            }
            RoomTransferRequest t = new RoomTransferRequest();
            t.setFromRoomId(req.getFromRoomId() != null ? req.getFromRoomId() : onlyRoom(r.getMembershipId()));
            t.setToRoomId(req.getToRoomId());
            t.setEffectiveDateBs(req.getEffectiveDateBs());
            transfers.transfer(r.getMembershipId(), t);
            r.setStatus(Status.COMPLETED);
            r.setCompletedAt(Instant.now());
        }
        return response(requests.save(r));
    }

    @Transactional
    public RequestResponse reject(UUID id, DecideRequestRequest req) {
        AuthUser user = guard.requireUser();
        if (req.getNote() == null || req.getNote().isBlank()) {
            throw ApiException.badRequest("NOTE_REQUIRED", "Tell the tenant why the request is rejected.");
        }
        TenantRequest r = lock(id, Status.PENDING);
        r.setStatus(Status.REJECTED);
        r.setOwnerNote(req.getNote().trim());
        r.setDecidedBy(user.userId());
        r.setDecidedAt(Instant.now());
        return response(requests.save(r));
    }

    @Transactional
    public RequestResponse complete(UUID id, DecideRequestRequest req) {
        TenantRequest r = lock(id, Status.APPROVED);
        r.setStatus(Status.COMPLETED);
        if (req != null && req.getNote() != null && !req.getNote().isBlank()) {
            r.setOwnerNote(req.getNote().trim());
        }
        r.setCompletedAt(Instant.now());
        return response(requests.save(r));
    }

    @Transactional
    public RequestResponse cancel(UUID id) {
        TenantRequest r = lock(id, Status.PENDING);
        r.setStatus(Status.CANCELLED);
        r.setCancelledAt(Instant.now());
        return response(requests.save(r));
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> forMembership(UUID membershipId) {
        return requests.findByMembershipIdOrderByCreatedAtDesc(membershipId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> forProperty(UUID propertyId, Status status) {
        List<TenantRequest> list = status == null ? requests.findByPropertyIdOrderByCreatedAtDesc(propertyId)
                : requests.findByPropertyIdAndStatusOrderByCreatedAtDesc(propertyId, status);
        return list.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> mine() {
        AuthUser user = guard.requireUser();
        List<UUID> ids = profiles.findByUserId(user.userId())
                .map(tp -> memberships.findByTenantProfileId(tp.getId(), Pageable.unpaged()).getContent()
                        .stream().map(TenantPropertyMembership::getId).toList())
                .orElse(List.of());
        return ids.isEmpty() ? List.of()
                : requests.findByMembershipIdInOrderByCreatedAtDesc(ids).stream().map(this::response).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private TenantRequest lock(UUID id, Status expected) {
        TenantRequest r = requests.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Request not found."));
        if (r.getStatus() != expected) {
            throw ApiException.conflict("REQUEST_ALREADY_DECIDED",
                    "This request is already " + r.getStatus().name().toLowerCase() + ".");
        }
        return r;
    }

    private UUID onlyRoom(UUID membershipId) {
        List<RoomAssignment> current = assignments.findByMembershipIdAndEffectiveToBsIsNull(membershipId);
        if (current.size() != 1) {
            throw ApiException.badRequest("FROM_ROOM_REQUIRED", "Choose which of the tenant's rooms they are leaving.");
        }
        return current.get(0).getRoomId();
    }

    private RequestResponse response(TenantRequest r) {
        TenantPropertyMembership m = memberships.findById(r.getMembershipId()).orElse(null);
        String name = m == null ? null : profiles.findById(m.getTenantProfileId()).map(tp -> tp.getFullName()).orElse(null);
        String roomNames = rooms.findAllById(assignments.findByMembershipIdAndEffectiveToBsIsNull(r.getMembershipId())
                        .stream().map(RoomAssignment::getRoomId).toList())
                .stream().map(Room::getName).sorted().collect(Collectors.joining(", "));
        return RequestResponse.of(r, name, roomNames.isEmpty() ? null : roomNames);
    }
}
