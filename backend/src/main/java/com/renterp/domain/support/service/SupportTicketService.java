package com.renterp.domain.support.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.mail.MailGateway;
import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.notification.entity.Notification;
import com.renterp.domain.notification.service.NotificationService;
import com.renterp.domain.support.dto.AdminSupportTicketResponse;
import com.renterp.domain.support.dto.SupportTicketRequests;
import com.renterp.domain.support.dto.SupportTicketResponse;
import com.renterp.domain.support.entity.SupportTicket;
import com.renterp.domain.support.entity.SupportTicket.Status;
import com.renterp.domain.support.repository.SupportTicketRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contact Support → "Send us a Message".
 *
 * <ul>
 *   <li>Any signed-in user (owner or tenant) can send a ticket: a topic, a
 *       message and optionally one attachment — their own upload with purpose
 *       SUPPORT_ATTACHMENT, used by no other ticket. At most 5 tickets an hour
 *       per account.</li>
 *   <li>Each ticket is stored first; when email is configured
 *       ({@link MailGateway}) and {@code app.support.inbox} is set, a copy goes
 *       to the support inbox after the commit, with Reply-To the user's email,
 *       so support can answer straight from the inbox. A mail failure never
 *       loses or fails the ticket.</li>
 *   <li>Users list their own tickets. Admins list every ticket and resolve one
 *       with an optional note; the user gets an in-app notification.</li>
 * </ul>
 */
@Service
public class SupportTicketService {

    private static final Logger log = LogManager.getLogger(SupportTicketService.class);
    private static final int MIN_MESSAGE = 10;
    private static final int MAX_PER_HOUR = 5;

    private final SupportTicketRepository tickets;
    private final StoredFileRepository files;
    private final UserRepository users;
    private final AccessGuard guard;
    private final RateLimiter rateLimiter;
    private final NotificationService notifications;
    private final MailGateway mail;
    private final String inbox;

    /** Where the inbox email is sent from: a virtual thread, so SMTP never slows the reply. */
    private Executor mailExecutor = task -> Thread.ofVirtual().name("support-mail").start(task);

    public SupportTicketService(SupportTicketRepository tickets, StoredFileRepository files, UserRepository users,
                                AccessGuard guard, RateLimiter rateLimiter, NotificationService notifications,
                                MailGateway mail, @Value("${app.support.inbox:}") String inbox) {
        this.tickets = tickets;
        this.files = files;
        this.users = users;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
        this.notifications = notifications;
        this.mail = mail;
        this.inbox = inbox == null ? "" : inbox.trim();
    }

    /** Tests: run the inbox email on the calling thread. */
    void runMailInline() {
        this.mailExecutor = Runnable::run;
    }

    // ── User ─────────────────────────────────────────────────────────────

    /**
     * 201 with the ticket. 400 MESSAGE_TOO_SHORT (under 10 characters),
     * 400 ATTACHMENT_INVALID (not the caller's SUPPORT_ATTACHMENT upload, or
     * already on a ticket), 429 SUPPORT_RATE_LIMITED.
     */
    @Transactional
    public SupportTicketResponse create(SupportTicketRequests.Create req) {
        AuthUser me = guard.requireUser();
        User user = users.findById(me.userId()).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Please log in again."));
        String message = req.getMessage() == null ? "" : req.getMessage().trim();
        if (message.length() < MIN_MESSAGE) {
            throw ApiException.badRequest("MESSAGE_TOO_SHORT",
                    "Describe the problem in a few more words so we can help.");
        }
        UUID attachment = req.getAttachmentFileId();
        if (attachment != null) {
            boolean ok = files.findByIdAndDeletedAtIsNull(attachment)
                    .filter(f -> me.userId().equals(f.getOwnerUserId())
                            && f.getPurpose() == FilePurpose.SUPPORT_ATTACHMENT)
                    .isPresent();
            if (!ok || tickets.existsByAttachmentFileId(attachment)) {
                throw ApiException.badRequest("ATTACHMENT_INVALID",
                        "The attachment must be a file you just uploaded for this message.");
            }
        }
        rateLimiter.hit("support-user:" + me.userId(), MAX_PER_HOUR, Duration.ofHours(1),
                "SUPPORT_RATE_LIMITED",
                "You have sent several messages in the last hour. We will reply to those first.");

        SupportTicket saved = tickets.save(SupportTicket.builder()
                .userId(me.userId())
                .topic(req.getTopic())
                .message(message)
                .attachmentFileId(attachment)
                .status(Status.OPEN)
                .build());
        log.info("Support ticket {} - user: {}, topic: {}, attachment: {}",
                saved.reference(), me.userId(), saved.getTopic(), attachment != null);
        emailInboxAfterCommit(saved, user);
        return SupportTicketResponse.from(saved);
    }

    /** The caller's own tickets, newest first. */
    @Transactional(readOnly = true)
    public Page<SupportTicketResponse> mine(int page, int size) {
        AuthUser me = guard.requireUser();
        return tickets.findByUserIdOrderByCreatedAtDesc(me.userId(), page(page, size))
                .map(SupportTicketResponse::from);
    }

    // ── Admin ────────────────────────────────────────────────────────────

    /** ADMIN: every ticket, newest first; {@code status} OPEN or RESOLVED filters. */
    @Transactional(readOnly = true)
    public Page<AdminSupportTicketResponse> adminList(String status, int page, int size) {
        guard.requireAdmin();
        Page<SupportTicket> rows = status == null || status.isBlank()
                ? tickets.findAllByOrderByCreatedAtDesc(page(page, size))
                : tickets.findByStatusOrderByCreatedAtDesc(parseStatus(status), page(page, size));
        Map<UUID, User> authors = users.findAllById(rows.map(SupportTicket::getUserId).toSet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.map(t -> AdminSupportTicketResponse.of(t, authors.get(t.getUserId())));
    }

    /**
     * ADMIN: marks a ticket resolved with an optional note the user sees, and
     * notifies them in the app. 404 SUPPORT_TICKET_NOT_FOUND,
     * 409 TICKET_ALREADY_RESOLVED.
     */
    @Transactional
    public AdminSupportTicketResponse resolve(UUID id, SupportTicketRequests.Resolve req) {
        guard.requireAdmin();
        AuthUser admin = guard.requireUser();
        SupportTicket t = tickets.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found."));
        if (t.getStatus() == Status.RESOLVED) {
            throw ApiException.conflict("TICKET_ALREADY_RESOLVED", "This ticket is already resolved.");
        }
        String note = req == null || req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim();
        t.setStatus(Status.RESOLVED);
        t.setResolutionNote(note);
        t.setResolvedBy(admin.userId());
        t.setResolvedAt(Instant.now());
        SupportTicket saved = tickets.save(t);
        notifications.toUser(t.getUserId(), Notification.Type.SUPPORT_TICKET_RESOLVED,
                note == null ? saved.reference() : saved.reference() + " · " + note,
                "SUPPORT_TICKET", saved.getId());
        log.info("Support ticket {} resolved by {}", saved.reference(), admin.userId());
        return AdminSupportTicketResponse.of(saved, users.findById(saved.getUserId()).orElse(null));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void emailInboxAfterCommit(SupportTicket t, User u) {
        if (inbox.isEmpty() || !mail.isConfigured()) {
            return;
        }
        String subject = "[Rentlo support] " + t.reference() + " · " + topicLabel(t.getTopic());
        String body = "From: " + nullToDash(u.getName()) + " (" + u.getRole().name().toLowerCase(Locale.ROOT) + ")\n"
                + "Phone: " + nullToDash(u.getPhone()) + "\n"
                + "Email: " + nullToDash(u.getEmail()) + "\n"
                + "Account id: " + u.getId() + "\n"
                + "Ticket: " + t.reference() + " (" + t.getId() + ")\n"
                + "Topic: " + topicLabel(t.getTopic()) + "\n"
                + (t.getAttachmentFileId() == null ? ""
                : "Attachment: GET /api/v1/files/" + t.getAttachmentFileId() + "/content (admin token)\n")
                + "\n" + t.getMessage() + "\n";
        String replyTo = u.getEmail();
        Runnable send = () -> mailExecutor.execute(() -> {
            if (!mail.sendQuietly(inbox, replyTo, subject, body)) {
                log.warn("Support ticket {} saved but not emailed to the inbox", t.reference());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    static String topicLabel(SupportTicket.Topic topic) {
        return switch (topic) {
            case ACCOUNT -> "Account & Login";
            case PROPERTY -> "Property Management";
            case BILLING -> "Billing & Payments";
            case TENANTS -> "Tenants & Leases";
            case OTHER -> "Something else";
        };
    }

    private static Status parseStatus(String status) {
        try {
            return Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS", "status must be OPEN or RESOLVED.");
        }
    }

    private static PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
