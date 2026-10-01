package com.renterp.domain.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One property's dashboard numbers, all computed from the database at request
 * time. Money is NPR; dates are BS.
 *
 * @param totalRooms         active rooms
 * @param occupiedRooms      rooms with a current assignment on an active membership
 * @param activeTenants      active memberships
 * @param overdueTenants     memberships with an issued bill unpaid past its due date
 * @param outstanding        everything still owed on issued bills
 * @param metersActive       active meters
 * @param readingsPending    active meters with no confirmed reading this BS month
 * @param currentBilling     the newest non-cancelled billing run, or null if none
 */
public record PropertySummaryResponse(
        UUID propertyId,
        String propertyName,
        String city,
        int totalRooms,
        int occupiedRooms,
        int vacantRooms,
        int activeTenants,
        int pendingJoinRequests,
        int overdueTenants,
        BigDecimal outstanding,
        int metersActive,
        int readingsPending,
        CurrentBilling currentBilling) {

    /**
     * @param billed     total of the run's live bills
     * @param collected  paid against them
     * @param pending    still owed on them
     */
    public record CurrentBilling(UUID runId, String billingMonthBs, String status,
                                 BigDecimal billed, BigDecimal collected, BigDecimal pending,
                                 int bills, int billsPaid) {
    }
}
