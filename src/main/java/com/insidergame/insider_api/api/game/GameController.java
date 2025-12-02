package com.insidergame.insider_api.api.game;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.GameHistoryDto;
import com.insidergame.insider_api.enums.RoleType;
import com.insidergame.insider_api.model.Game;
import com.insidergame.insider_api.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/{roomCode}/active")
    public ResponseEntity<ApiResponse<Game>> getActiveGame(@PathVariable String roomCode) {
        ApiResponse<Game> resp = gameService.getActiveGame(roomCode);
        return ResponseEntity.status(resp.getStatus()).body(resp);
    }

    /**
     * Get game history for a room
     * Returns all games (active and archived) for the room
     */
    @GetMapping("/{roomCode}/history")
    public ResponseEntity<ApiResponse<List<GameHistoryDto>>> getGameHistory(@PathVariable String roomCode) {
        ApiResponse<List<Game>> resp = gameService.getGamesForRoom(roomCode);

        if (!resp.isSuccess() || resp.getData() == null) {
            return ResponseEntity.status(resp.getStatus())
                    .body(new ApiResponse<>(false, resp.getMessage(), null, resp.getStatus()));
        }

        // Convert Game to GameHistoryDto
        List<GameHistoryDto> history = resp.getData().stream()
                .map(this::convertToHistoryDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new ApiResponse<>(true, "Game history retrieved", history, null));
    }

    /**
     * Convert Game model to GameHistoryDto
     */
    private GameHistoryDto convertToHistoryDto(Game game) {
        // Calculate scores from game summary if available
        Map<String, Integer> scores = null;
        if (game.getSummary() != null && game.getSummary().getScores() != null) {
            scores = game.getSummary().getScores();
        }

        GameHistoryDto dto = GameHistoryDto.builder()
                .id(game.getId())
                .roomCode(game.getRoomCode())
                .word(game.getWord())
                .wordRevealed(game.isWordRevealed())
                .startedAt(game.getStartedAt())
                .endsAt(game.getEndsAt())
                .durationSeconds(game.getDurationSeconds())
                .finished(game.isFinished())
                .players(game.getPlayerInGame())
                .roles(game.getRoles())
                .cardOpened(game.getCardOpened())
                .votes(game.getVotes())
                .scores(scores)
                .build();

        // Calculate vote result if votes exist
        if (game.getVotes() != null && !game.getVotes().isEmpty()) {
            Map<String, Integer> voteTally = new HashMap<>();
            for (String targetUuid : game.getVotes().values()) {
                voteTally.put(targetUuid, voteTally.getOrDefault(targetUuid, 0) + 1);
            }

            // Find most voted player
            String mostVotedUuid = null;
            int mostVotedCount = 0;
            for (Map.Entry<String, Integer> entry : voteTally.entrySet()) {
                if (entry.getValue() > mostVotedCount) {
                    mostVotedCount = entry.getValue();
                    mostVotedUuid = entry.getKey();
                }
            }

            // Find insider
            String insiderUuid = null;
            if (game.getRoles() != null) {
                for (Map.Entry<String, RoleType> entry : game.getRoles().entrySet()) {
                    if (entry.getValue() == RoleType.INSIDER) {
                        insiderUuid = entry.getKey();
                        break;
                    }
                }
            }

            GameHistoryDto.VoteResultDto voteResult = GameHistoryDto.VoteResultDto.builder()
                    .insiderUuid(insiderUuid)
                    .mostVotedUuid(mostVotedUuid)
                    .mostVotedCount(mostVotedCount)
                    .voteTally(voteTally)
                    .build();

            dto.setVoteResult(voteResult);

            // Determine game outcome
            if (insiderUuid != null && insiderUuid.equals(mostVotedUuid)) {
                dto.setGameOutcome("INSIDER_FOUND");
            } else if (insiderUuid != null) {
                dto.setGameOutcome("INSIDER_HIDDEN");
            } else {
                dto.setGameOutcome("NO_INSIDER");
            }
        }

        return dto;
    }
}

