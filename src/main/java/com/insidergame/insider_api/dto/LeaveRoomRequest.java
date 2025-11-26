package com.insidergame.insider_api.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRoomRequest {

    private String roomCode;
    private String playerUuid;
}

