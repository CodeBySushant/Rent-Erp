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
 * @param photoUrl    the tenant's profile photo ({@code /api/v1/files/{id}/content}),
 *                    or null when they have none or no app account
 * @param totalMembers         people living in the tenancy (V32)
 * @param agreedDepositAmount  deposit agreed at Add Tenant, or null (V33)
 * @param agreedAdvanceAmount  advance rent agreed at Add Tenant, or null (V33)
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
        boolean overdue,
        String photoUrl,
        short totalMembers,
        BigDecimal agreedDepositAmount,
        BigDecimal agreedAdvanceAmount) {

    public record RoomRef(UUID assignmentId, UUID roomId, String roomName, String floorName,
                          String effectiveFromBs, BigDecimal monthlyRent) {
    }

    public record LatestBill(UUID billId, String billingMonthBs, String status, String paymentStatus,
                             BigDecimal totalDue, BigDecimal amountPaid, BigDecimal balanceDue,
                             String dueDateBs) {
    }
}
