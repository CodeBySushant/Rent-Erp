package com.renterp.domain.structure.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateFloorRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Min(value = -5, message = "Floor number must be between -5 and 200")
    @Max(value = 200, message = "Floor number must be between -5 and 200")
    private Short floorNumber;

    // propertyId is not updatable here — moving a floor to a different property isn't a
    // plain field edit, same reasoning as ownerUserId being fixed on PropertyController.
}
