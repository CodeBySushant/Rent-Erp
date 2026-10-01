package com.renterp.domain.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One tenant (membership) as the tenant list and tenant detail show it.
 *
 * @param rooms       current room assignments, with names
 * @param monthlyRent sum of the current assignments' rent
 * @param latestBill  newest live bill, or null
 * @param amountOwed  everything still owed on issued bills
 * @param overdue     an issued bill is unpaid past its due date
 */
public record TenantRowResponse(
        UUID membershipId,
        UUID tenantProfileId,
        UUID propertyId,
        String name,
        String phone,
        boolean linked,
        String status,
        String startedAtBs,
        String endedAtBs,
        List<RoomRef> rooms,
        BigDecimal monthlyRent,
        LatestBill latestBill,
        BigDecimal amountOwed,
        boolean overdue) {

    public record RoomRef(UUID assignmentId, UUID roomId, String roomName, String floorName,
                          String effectiveFromBs, BigDecimal monthlyRent) {
    }

    public record LatestBill(UUID billId, String billingMonthBs, String status, String paymentStatus,
                             BigDecimal totalDue, BigDecimal amountPaid, BigDecimal balanceDue,
                             String dueDateBs) {
    }
}
