package com.renterp.domain.support.service;

import com.renterp.common.exception.ApiException;
import com.renterp.common.mail.MailGateway;
import com.renterp.common.ratelimit.RateLimiter;
import com.renterp.domain.auth.entity.User;
import com.renterp.domain.auth.entity.User.UserRole;
import com.renterp.domain.auth.repository.UserRepository;
import com.renterp.domain.auth.security.AccessGuard;
import com.renterp.domain.auth.security.AuthUser;
import com.renterp.domain.file.entity.StoredFile;
import com.renterp.domain.file.entity.StoredFile.FilePurpose;
import com.renterp.domain.file.repository.StoredFileRepository;
import com.renterp.domain.notification.entity.Notification;
import com.renterp.domain.notification.service.NotificationService;
import com.renterp.domain.support.dto.AdminSupportTicketResponse;
import com.renterp.domain.support.dto.SupportTicketRequests;
import com.renterp.domain.support.dto.SupportTicketResponse;
import com.renterp.domain.support.entity.SupportTicket;
import com.renterp.domain.support.entity.SupportTicket.Status;
import com.renterp.domain.support.entity.SupportTicket.Topic;
import com.renterp.domain.support.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contact Support tickets: what a user can send, and the admin resolve. */
class SupportTicketServiceTest {

    private final UUID me = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();

    private SupportTicketRepository tickets;
    private StoredFileRepository files;
    private UserRepository users;
    private AccessGuard guard;
    private RateLimiter rateLimiter;
    private NotificationService notifications;
    private MailGateway mail;
    private User user;

    @BeforeEach
    void setUp() {
        tickets = mock(SupportTicketRepository.class);
        files = mock(StoredFileRepository.class);
        users = mock(UserRepository.class);
        guard = mock(AccessGuard.class);
        rateLimiter = mock(RateLimiter.class);
        notifications = mock(NotificationService.class);
        mail = mock(MailGateway.class);
        when(guard.requireUser()).thenReturn(new AuthUser(me, UUID.randomUUID(), UserRole.TENANT));
        user = User.builder().id(me).name("Sita Sharma").phone("9800000001").email("sita@example.com")
                .role(UserRole.TENANT).build();
        when(users.findById(me)).thenReturn(Optional.of(user));
        when(tickets.save(any(SupportTicket.class))).thenAnswer(i -> {
            SupportTicket t = i.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.fromString("abcdef12-0000-0000-0000-000000000000"));
            }
            return t;
        });
    }

    private SupportTicketService service(String inbox) {
        SupportTicketService s = new SupportTicketService(tickets, files, users, guard, rateLimiter,
                notifications, mail, inbox);
        s.runMailInline();
        return s;
    }

    private static SupportTicketRequests.Create create(String message, UUID attachment) {
        SupportTicketRequests.Create c = new SupportTicketRequests.Create();
        c.setTopic(Topic.BILLING);
        c.setMessage(message);
        c.setAttachmentFileId(attachment);
        return c;
    }

    private void stubFile(UUID owner, FilePurpose purpose) {
        when(files.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(
                StoredFile.builder().id(fileId).ownerUserId(owner).purpose(purpose).build()));
    }

    @Test
    void ticketIsStoredOpenWithReferenceAndAttachment() {
        stubFile(me, FilePurpose.SUPPORT_ATTACHMENT);
        SupportTicketResponse r = service("").create(create("  My bill shows the wrong amount.  ", fileId));
        assertEquals("T-ABCDEF12", r.reference());
        assertEquals("OPEN", r.status());
        assertEquals("BILLING", r.topic());
        assertEquals("My bill shows the wrong amount.", r.message());
        assertEquals("/api/v1/files/" + fileId + "/content", r.attachmentUrl());
        verify(rateLimiter).hit(eq("support-user:" + me), eq(5), any(), eq("SUPPORT_RATE_LIMITED"), anyString());
    }

    @Test
    void tooShortMessageIsRefused() {
        ApiException e = assertThrows(ApiException.class, () -> service("").create(create("help", null)));
        assertEquals("MESSAGE_TOO_SHORT", e.getCode());
        verify(tickets, never()).save(any());
    }

    @Test
    void attachmentMustBeOwnSupportUploadUsedOnce() {
        stubFile(UUID.randomUUID(), FilePurpose.SUPPORT_ATTACHMENT);
        assertEquals("ATTACHMENT_INVALID", assertThrows(ApiException.class,
                () -> service("").create(create("Someone else's file here", fileId))).getCode());

        stubFile(me, FilePurpose.PROFILE_PHOTO);
        assertEquals("ATTACHMENT_INVALID", assertThrows(ApiException.class,
                () -> service("").create(create("Wrong kind of upload here", fileId))).getCode());

        stubFile(me, FilePurpose.SUPPORT_ATTACHMENT);
        when(tickets.existsByAttachmentFileId(fileId)).thenReturn(true);
        assertEquals("ATTACHMENT_INVALID", assertThrows(ApiException.class,
                () -> service("").create(create("Same file a second time", fileId))).getCode());
        verify(tickets, never()).save(any());
    }

    @Test
    void rateLimitStopsTheTicket() {
        doThrow(ApiException.tooManyRequests("SUPPORT_RATE_LIMITED", "x"))
                .when(rateLimiter).hit(anyString(), eq(5), any(), anyString(), anyString());
        assertThrows(ApiException.class, () -> service("").create(create("Sixth message this hour", null)));
        verify(tickets, never()).save(any());
    }

    @Test
    void inboxGetsACopyWithReplyToWhenEmailIsConfigured() {
        when(mail.isConfigured()).thenReturn(true);
        when(mail.sendQuietly(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        service("help@rentlo.app").create(create("The meter reading is stuck.", null));
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).sendQuietly(eq("help@rentlo.app"), eq("sita@example.com"), subject.capture(), body.capture());
        assertEquals("[Rentlo support] T-ABCDEF12 · Billing & Payments", subject.getValue());
        assertTrue(body.getValue().contains("Sita Sharma (tenant)"));
        assertTrue(body.getValue().contains("The meter reading is stuck."));
    }

    @Test
    void noInboxOrNoEmailMeansStoredOnly() {
        when(mail.isConfigured()).thenReturn(true);
        service("").create(create("Stored but not emailed.", null));
        when(mail.isConfigured()).thenReturn(false);
        service("help@rentlo.app").create(create("Stored but not emailed.", null));
        verify(mail, never()).sendQuietly(anyString(), any(), anyString(), anyString());
    }

    @Test
    void adminResolvesAndTheUserIsNotified() {
        UUID admin = UUID.randomUUID();
        when(guard.requireUser()).thenReturn(new AuthUser(admin, UUID.randomUUID(), UserRole.ADMIN));
        UUID id = UUID.fromString("abcdef12-0000-0000-0000-000000000001");
        SupportTicket t = SupportTicket.builder().id(id).userId(me).topic(Topic.ACCOUNT)
                .message("Cannot log in").status(Status.OPEN).build();
        when(tickets.findByIdForUpdate(id)).thenReturn(Optional.of(t));
        SupportTicketRequests.Resolve req = new SupportTicketRequests.Resolve();
        req.setNote(" Reset your password from Log In. ");

        AdminSupportTicketResponse r = service("").resolve(id, req);
        assertEquals("RESOLVED", r.status());
        assertEquals("Reset your password from Log In.", r.resolutionNote());
        assertEquals(admin, t.getResolvedBy());
        assertEquals("Sita Sharma", r.userName());
        verify(guard).requireAdmin();
        verify(notifications).toUser(me, Notification.Type.SUPPORT_TICKET_RESOLVED,
                "T-ABCDEF12 · Reset your password from Log In.", "SUPPORT_TICKET", id);
    }

    @Test
    void resolvingTwiceIsAConflict() {
        UUID id = UUID.randomUUID();
        when(tickets.findByIdForUpdate(id)).thenReturn(Optional.of(SupportTicket.builder().id(id).userId(me)
                .topic(Topic.OTHER).message("x").status(Status.RESOLVED).build()));
        ApiException e = assertThrows(ApiException.class, () -> service("").resolve(id, null));
        assertEquals("TICKET_ALREADY_RESOLVED", e.getCode());
        verify(notifications, never()).toUser(any(), any(), any(), any(), any());
    }

    @Test
    void referenceIsShortAndUpperCase() {
        assertEquals("T-0A1B2C3D", SupportTicket.reference(UUID.fromString("0a1b2c3d-4e5f-6789-abcd-ef0123456789")));
        assertNull(SupportTicket.reference(null));
    }
}
