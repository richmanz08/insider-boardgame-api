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
        ApiResponse<List<GameHistoryDto>> resp = gameService.getGameHistory(roomCode);
        return ResponseEntity.status(resp.getStatus()).body(resp);
    }


}

