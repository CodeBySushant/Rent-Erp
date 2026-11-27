package com.renterp.domain.meter.dto;

import com.renterp.common.validation.BsDate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * PUT /api/v1/meters/{id}/rooms — the rooms a sub meter covers from now on.
 * Rooms left out stop being covered from {@code effectiveBs}; new rooms are
 * covered from it. An empty list takes every room off the meter.
 */
@Getter
@Setter
public class SetMeterRoomsRequest {

    @NotNull(message = "roomIds is required")
    @Size(max = 200, message = "Too many rooms")
    private List<@NotNull UUID> roomIds;

    /** Defaults to today. */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "effectiveBs must be YYYY-MM-DD (BS)")
    @BsDate
    private String effectiveBs;
}
