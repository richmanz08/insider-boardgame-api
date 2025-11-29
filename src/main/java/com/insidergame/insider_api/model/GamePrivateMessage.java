package com.insidergame.insider_api.model;

import com.insidergame.insider_api.enums.RoleType;
import com.insidergame.insider_api.websocket.RoomWebSocketController;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamePrivateMessage {
    private String playerUuid;
    private RoleType role;
    private String word;
}
