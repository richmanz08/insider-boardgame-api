package com.insidergame.insider_api.api.game;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.model.Game;
import com.insidergame.insider_api.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

