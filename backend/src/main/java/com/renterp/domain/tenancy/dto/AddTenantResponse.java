package com.renterp.domain.tenancy.dto;

import java.util.UUID;

/** Everything Add Tenant created; {@code depositId} is null when no deposit was given. */
public record AddTenantResponse(UUID membershipId, UUID tenantProfileId, UUID roomAssignmentId,
                                UUID depositId, UUID propertyId, UUID roomId) {
}
