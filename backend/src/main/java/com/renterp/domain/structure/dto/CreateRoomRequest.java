package com.renterp.domain.structure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateRoomRequest {

    @NotNull(message = "Floor id is required")
    private UUID floorId;

    @NotBlank(message = "Room name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
}
