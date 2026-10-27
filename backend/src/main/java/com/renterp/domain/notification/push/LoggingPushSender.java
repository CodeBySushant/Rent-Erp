package com.renterp.domain.notification.push;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Placeholder push sender (M12): no push provider is configured yet, so it
 * only logs at DEBUG. Replace with a Firebase sender when FCM is set up.
 */
@Component
public class LoggingPushSender implements PushSender {

    private static final Logger log = LogManager.getLogger(LoggingPushSender.class);

    @Override
    public void send(UUID userId, String title, String body, Map<String, String> data) {
        log.debug("Push not configured - would notify user {}: {}", userId, title);
    }
}
