package com.insidergame.insider_api.model;

import com.insidergame.insider_api.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
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
    private boolean wordRevealed; // True when MASTER ends game - reveal word to all players
    private Map<String, RoleType> roles; // playerUuid -> role (MASTER/INSIDER/PLAYER)
    private List<PlayerInGame> playerInGame;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private int durationSeconds;
    private boolean finished;
    // Track whether each player has opened their card: playerUuid -> opened
    private Map<String, Boolean> cardOpened;
    // Votes during voting phase: voterUuid -> targetPlayerUuid
    private Map<String, String> votes;
    private GamePrivateMessage privateMessage;
    private GameSummary summary;



}
