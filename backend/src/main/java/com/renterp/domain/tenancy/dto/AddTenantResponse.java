package com.renterp.domain.tenancy.dto;

import java.util.List;
import java.util.UUID;

/**
 * Everything Add Tenant created; {@code depositId} is null when no deposit was
 * given. {@code roomAssignmentId} / {@code roomId} are the first room's (kept
 * for older apps); {@code roomAssignmentIds} / {@code roomIds} list every room
 * in the order they were chosen.
 */
public record AddTenantResponse(UUID membershipId, UUID tenantProfileId, UUID roomAssignmentId,
                                UUID depositId, UUID propertyId, UUID roomId,
                                List<UUID> roomAssignmentIds, List<UUID> roomIds, short totalMembers) {
}
