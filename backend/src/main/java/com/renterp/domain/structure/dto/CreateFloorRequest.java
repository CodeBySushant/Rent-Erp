package com.renterp.domain.structure.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateFloorRequest {

    @NotNull(message = "Property id is required")
    private UUID propertyId;

    @NotBlank(message = "Floor name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    // Ground floor = 0, basements negative. Sanity bounds, not a spec-mandated range.
    @NotNull(message = "Floor number is required")
    @Min(value = -5, message = "Floor number must be between -5 and 200")
    @Max(value = 200, message = "Floor number must be between -5 and 200")
    private Short floorNumber;
}
