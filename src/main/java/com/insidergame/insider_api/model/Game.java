package com.insidergame.insider_api.model;

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
    private Map<String, String> roles; // playerUuid -> role (MASTER/INSIDER/PLAYER)
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private int durationSeconds;
    private boolean finished;
}

