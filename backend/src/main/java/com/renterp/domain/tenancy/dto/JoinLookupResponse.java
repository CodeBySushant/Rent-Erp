package com.renterp.domain.tenancy.dto;

import java.util.UUID;

/**
 * What a tenant sees after typing or scanning a join code ("Property found"):
 * enough to recognise the place, nothing private. The landlord's phone is not
 * shared until the request is accepted.
 *
 * @param alreadyMember  the caller already lives here (active membership)
 * @param requestPending the caller already has a pending request here
 */
public record JoinLookupResponse(UUID propertyId, String joinCode, String name, String address, String city,
                                 String landlordName, boolean alreadyMember, boolean requestPending) {
}
