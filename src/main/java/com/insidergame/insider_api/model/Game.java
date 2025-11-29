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
public class Game {
    private UUID id;
    private String roomCode;
    private String word;
    private Map<String, RoleType> roles; // playerUuid -> role (MASTER/INSIDER/PLAYER)
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private int durationSeconds;
    private boolean finished;
    // Track whether each player has opened their card: playerUuid -> opened
    private Map<String, Boolean> cardOpened;
    private GamePrivateMessage privateMessage;
}
