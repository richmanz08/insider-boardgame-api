package com.insidergame.insider_api.dto;

import com.insidergame.insider_api.enums.RoomStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomResponse {

    private String roomCode;
    private String roomName;
    private Integer maxPlayers;
    private Integer currentPlayers;
    private Boolean hasPassword;
    private RoomStatus status;
    private String hostUuid;
    private String hostName;
    private LocalDateTime createdAt;
}

