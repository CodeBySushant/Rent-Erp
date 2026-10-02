package com.renterp.domain.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** One signed-in device. {@code current} is the session making the request. */
public record SessionResponse(UUID id, String device, Instant createdAt, Instant lastUsedAt, Instant expiresAt,
                              boolean current) {
}
