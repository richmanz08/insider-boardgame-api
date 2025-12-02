package com.insidergame.insider_api.dto;

import com.insidergame.insider_api.enums.RoleType;
import com.insidergame.insider_api.model.PlayerInGame;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for displaying game history
 * Contains all game information including roles, scores, and results
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameHistoryDto {

    private UUID id;
    private String roomCode;
    private String word;
    private boolean wordRevealed;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private Integer durationSeconds;
    private boolean finished;

    // Players in this game
    private List<PlayerInGame> players;

    // Roles assigned to each player (uuid -> role)
    private Map<String, RoleType> roles;

    // Card opened status (uuid -> opened)
    private Map<String, Boolean> cardOpened;

    // Votes (voterUuid -> targetUuid)
    private Map<String, String> votes;

    // Scores (uuid -> score)
    private Map<String, Integer> scores;

    // Vote results summary
    private VoteResultDto voteResult;

    // Game outcome
    private String gameOutcome; // "INSIDER_FOUND", "INSIDER_HIDDEN", "MASTER_WIN", etc.

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoteResultDto {
        private String insiderUuid;
        private String mostVotedUuid;
        private Integer mostVotedCount;
        private Map<String, Integer> voteTally; // targetUuid -> vote count
    }
}

