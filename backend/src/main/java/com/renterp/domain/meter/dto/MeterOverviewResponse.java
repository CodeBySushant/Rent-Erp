package com.renterp.domain.meter.dto;

import com.renterp.domain.meter.entity.Meter.MeterType;

import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/properties/{propertyId}/meter-overview — the property's active
 * electricity meters with the rooms each covers now, and the active rooms no
 * sub meter covers (their electricity is billed to nobody when the property
 * is sub-metered).
 *
 * @param electricityBillingMode the property's mode, e.g. SUB_METERED
 * @param unmeteredRoomIds       active rooms on no active sub meter
 */
public record MeterOverviewResponse(String electricityBillingMode, List<MeterRooms> meters,
                                    List<UUID> unmeteredRoomIds) {

    /** One meter and the rooms it covers today (empty for a main meter). */
    public record MeterRooms(UUID id, String label, String serialNumber, MeterType meterType,
                             List<UUID> roomIds) {
    }
}
