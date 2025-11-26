package com.insidergame.insider_api.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomUpdateMessage {

    private String type; // "PLAYER_JOINED", "PLAYER_LEFT", "PLAYER_READY", "ROOM_UPDATE"
    private String roomCode;
    private String roomName;
    private Integer maxPlayers;
    private Integer currentPlayers;
    private String status;
    private List<PlayerDto> players;
    private String message;
}

