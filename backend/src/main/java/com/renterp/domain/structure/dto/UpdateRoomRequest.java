package com.renterp.domain.structure.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRoomRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    // floorId is not updatable here — a room's physical floor doesn't change after
    // construction. Don't confuse this with "Room Transfer" (spec §12.3), which is a
    // tenant moving between two already-existing rooms, not a room moving between floors.
}
