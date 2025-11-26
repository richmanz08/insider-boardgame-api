package com.insidergame.insider_api.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    private String roomName;
    private Integer maxPlayers;
    private String password; // Optional
    private String hostUuid;
    private String hostName;
}

