package com.insidergame.insider_api.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotBlank(message = "Room name is required")
    private String roomName;

    @NotNull(message = "Max players is required")
    @Min(value = 2, message = "Minimum 2 players required")
    @Max(value = 12, message = "Maximum 12 players allowed")
    private Integer maxPlayers;

    private String password; // Optional

    @NotBlank(message = "Host UUID is required")
    private String hostUuid;

    @NotBlank(message = "Host name is required")
    private String hostName;
}

