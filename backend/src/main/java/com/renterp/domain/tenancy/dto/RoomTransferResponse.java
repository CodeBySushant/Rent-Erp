package com.renterp.domain.tenancy.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RoomTransferResponse(UUID membershipId, UUID fromRoomId, UUID toRoomId, String effectiveDateBs,
                                   UUID endedAssignmentId, UUID newAssignmentId, BigDecimal monthlyRent) {
}
