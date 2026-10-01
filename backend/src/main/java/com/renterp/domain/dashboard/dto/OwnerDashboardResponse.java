package com.renterp.domain.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/** Totals across every property the caller can see, plus each property's summary. */
public record OwnerDashboardResponse(
        String todayBs,
        int properties,
        int totalRooms,
        int occupiedRooms,
        int vacantRooms,
        int activeTenants,
        int pendingJoinRequests,
        int overdueTenants,
        int readingsPending,
        BigDecimal outstanding,
        BigDecimal billedThisPeriod,
        BigDecimal collectedThisPeriod,
        List<PropertySummaryResponse> items) {
}
