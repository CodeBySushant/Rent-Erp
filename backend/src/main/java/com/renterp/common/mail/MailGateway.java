package com.renterp.common.mail;

import com.renterp.common.exception.ApiException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Plain-text email over SMTP: one-time codes sent to an email address
 * (password reset, email change) and new support tickets to the support inbox.
 *
 * Configured with MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD /
 * MAIL_FROM (application.yml, app.mail.*). With no MAIL_HOST nothing is
 * created and {@link #isConfigured()} is false: callers fall back (codes go
 * to the OtpSender, ticket emails are skipped). The sender is built here
 * rather than by Spring Boot's mail auto-configuration, so an unconfigured
 * server has no mail bean and no mail health check that could mark it DOWN.
 */
@Component
public class MailGateway {

    private static final Logger log = LogManager.getLogger(MailGateway.class);
    private static final String TIMEOUT_MS = "10000";

    /** Null when MAIL_HOST is not set. */
    private final JavaMailSenderImpl sender;
    private final String from;

    public MailGateway(@Value("${app.mail.host:}") String host,
                       @Value("${app.mail.port:587}") int port,
                       @Value("${app.mail.username:}") String username,
                       @Value("${app.mail.password:}") String password,
                       @Value("${app.mail.from:}") String from,
                       @Value("${app.mail.starttls:true}") boolean starttls,
                       @Value("${app.mail.ssl:false}") boolean ssl) {
        String h = host == null ? "" : host.trim();
        this.from = from == null ? "" : from.trim();
        if (h.isEmpty()) {
            this.sender = null;
            log.info("Email is not configured (MAIL_HOST empty): codes to email addresses use the OTP sender, "
                    + "support tickets are not emailed.");
            return;
        }
        if (this.from.isEmpty()) {
            throw new IllegalStateException("MAIL_HOST is set but MAIL_FROM is empty. Set the sender address, "
                    + "e.g. MAIL_FROM=Rentlo <no-reply@rentlo.app>.");
        }
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(h);
        s.setPort(port);
        s.setDefaultEncoding("UTF-8");
        boolean auth = username != null && !username.isBlank();
        if (auth) {
            s.setUsername(username.trim());
            s.setPassword(password);
        }
        Properties p = s.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", String.valueOf(auth));
        p.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        p.put("mail.smtp.starttls.required", String.valueOf(starttls));
        p.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        p.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        p.put("mail.smtp.timeout", TIMEOUT_MS);
        p.put("mail.smtp.writetimeout", TIMEOUT_MS);
        this.sender = s;
        log.info("Email configured — host: {}, port: {}, from: {}", h, port, this.from);
    }

    /** True when MAIL_HOST is set, so {@link #send} can deliver. */
    public boolean isConfigured() {
        return sender != null;
    }

    /**
     * Sends one plain-text email. 503 EMAIL_NOT_CONFIGURED without MAIL_HOST,
     * 503 EMAIL_NOT_SENT when the SMTP server refuses or cannot be reached.
     */
    public void send(String to, String subject, String text) {
        send(to, null, subject, text);
    }

    /** {@link #send(String, String, String)} with a Reply-To address (null for none). */
    public void send(String to, String replyTo, String subject, String text) {
        if (sender == null) {
            throw ApiException.unavailable("EMAIL_NOT_CONFIGURED", "Emails cannot be sent yet.");
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        if (replyTo != null && !replyTo.isBlank()) {
            m.setReplyTo(replyTo);
        }
        m.setSubject(subject);
        m.setText(text);
        try {
            sender.send(m);
        } catch (MailException e) {
            log.error("Email not sent — subject: {}", subject, e);
            throw ApiException.unavailable("EMAIL_NOT_SENT",
                    "The email could not be sent. Try again in a few minutes.");
        }
    }

    /** Like {@link #send}, but never throws: false when it was not sent. */
    public boolean sendQuietly(String to, String subject, String text) {
        return sendQuietly(to, null, subject, text);
    }

    /** Like {@link #send(String, String, String, String)}, but never throws. */
    public boolean sendQuietly(String to, String replyTo, String subject, String text) {
        if (sender == null || to == null || to.isBlank()) {
            return false;
        }
        try {
            send(to, replyTo, subject, text);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
