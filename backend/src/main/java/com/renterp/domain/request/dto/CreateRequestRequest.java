package com.renterp.domain.request.dto;

import com.renterp.domain.request.entity.TenantRequest.Type;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateRequestRequest {

    @NotNull(message = "type is required (ROOM_CHANGE, VACATE, MAINTENANCE or OTHER)")
    private Type type;

    @NotBlank(message = "title is required")
    @Size(max = 150)
    private String title;

    @Size(max = 2000)
    private String description;

    /** Room change / vacate: when. */
    @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "preferredDateBs must be YYYY-MM-DD (BS)")
    private String preferredDateBs;

    /** Maintenance: a photo uploaded with purpose REQUEST_PHOTO. */
    private UUID photoFileId;
}
