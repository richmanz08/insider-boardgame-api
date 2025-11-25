package com.insidergame.insider_api.api.player;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.PlayerRegisterRequest;
import com.insidergame.insider_api.dto.PlayerResponse;
import com.insidergame.insider_api.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/player")
public class PlayerController {

 PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /**
     * Create new player session (generate UUID and JWT token)
     * POST /api/player/register
     * Body: { "playerName": "John Doe" }
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<PlayerResponse>> registerPlayer(@RequestBody PlayerRegisterRequest request) {
        ApiResponse<PlayerResponse> response = playerService.registerPlayer(request);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    /**
     * Validate JWT token
     * GET /api/player/validate
     * Header: Authorization: Bearer {token}
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<PlayerResponse>> validateToken(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        ApiResponse<PlayerResponse> response = playerService.validateToken(token);
        return ResponseEntity.status(response.getStatus()).body(response);
    }
}

