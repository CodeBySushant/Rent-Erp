package com.renterp.domain.tenancy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/me/stay — everything the tenant home needs in one call: each
 * tenancy of the signed-in user (active first, then past), and join requests
 * still waiting for an owner.
 */
public record MyStayResponse(String todayBs, List<Stay> stays, List<PendingJoin> pendingRequests) {

    /**
     * @param landlordPhone shared only while the tenancy is active
     * @param amountOwed    everything still owed on issued bills of this tenancy
     * @param overdue       an issued bill is unpaid past its due date
     */
    public record Stay(UUID membershipId, String status, String startedAtBs, String endedAtBs,
                       PropertyRef property, String landlordName, String landlordPhone,
                       List<RoomRef> rooms, BigDecimal monthlyRent, DepositRef deposit,
                       int noticePeriodDays, int billingDay, int gracePeriodDays,
                       BillRef currentBill, BigDecimal amountOwed, boolean overdue) {
    }

    public record PropertyRef(UUID id, String name, String address, String city, String joinCode) {
    }

    public record RoomRef(UUID roomId, String roomName, String floorName, BigDecimal monthlyRent,
                          String effectiveFromBs) {
    }

    public record DepositRef(BigDecimal amount, String status, String receivedAtBs) {
    }

    public record BillRef(UUID billId, String billingMonthBs, String status, String paymentStatus,
                          BigDecimal totalDue, BigDecimal amountPaid, BigDecimal balanceDue, String dueDateBs) {
    }

    public record PendingJoin(UUID joinRequestId, UUID propertyId, String propertyName, String joinCode,
                              Instant requestedAt) {
    }
}
