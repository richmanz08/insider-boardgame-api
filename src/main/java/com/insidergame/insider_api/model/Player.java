package com.insidergame.insider_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    private String uuid;
    private String playerName;
    private String sessionId; // WebSocket session ID
    private LocalDateTime joinedAt;
    private boolean isHost;

    @Builder.Default
    private boolean isReady = false; // Ready status for game start
}

