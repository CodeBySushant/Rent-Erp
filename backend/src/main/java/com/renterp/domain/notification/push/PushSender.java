package com.renterp.domain.notification.push;

import java.util.Map;
import java.util.UUID;

/**
 * M12: delivers a push message to a user's registered devices. Called after
 * the notification row has been committed - the in-app inbox is the record
 * (spec §16.1), push is only a convenience, and a failed push never loses a
 * notification.
 *
 * The only implementation today is {@link LoggingPushSender} (logs, sends
 * nothing). Adding Firebase means one new class implementing this interface
 * (marked {@code @Primary}) that reads the tokens from {@code device_tokens}.
 */
public interface PushSender {

    void send(UUID userId, String title, String body, Map<String, String> data);
}
