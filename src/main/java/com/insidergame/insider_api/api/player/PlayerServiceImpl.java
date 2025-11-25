package com.insidergame.insider_api.api.player;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.PlayerRegisterRequest;
import com.insidergame.insider_api.dto.PlayerResponse;
import com.insidergame.insider_api.service.PlayerService;
import com.insidergame.insider_api.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlayerServiceImpl implements PlayerService {
    JwtUtil jwtUtil;



    public PlayerServiceImpl(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public ApiResponse<PlayerResponse> registerPlayer(PlayerRegisterRequest request) {
        try {
            // Validate input
            if (request.getPlayerName() == null || request.getPlayerName().trim().isEmpty()) {
                return new ApiResponse<>(false, "Player name is required", null, HttpStatus.BAD_REQUEST);
            }

            // Generate UUID
            String uuid = UUID.randomUUID().toString();

            // Generate JWT token
            String token = jwtUtil.generateToken(uuid, request.getPlayerName());

            // Build response (no database storage)
            PlayerResponse response = PlayerResponse.builder()
                    .uuid(uuid)
                    .playerName(request.getPlayerName())
                    .token(token)
                    .message("Player session created successfully")
                    .build();

            return new ApiResponse<>(true, "Player session created successfully", response, HttpStatus.CREATED);

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error creating player session: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<PlayerResponse> validateToken(String token) {
        try {
            if (jwtUtil.validateToken(token)) {
                String uuid = jwtUtil.extractUuid(token);
                String playerName = jwtUtil.extractPlayerName(token);

                PlayerResponse response = PlayerResponse.builder()
                        .uuid(uuid)
                        .playerName(playerName)
                        .token(token)
                        .message("Token is valid")
                        .build();

                return new ApiResponse<>(true, "Token is valid", response, HttpStatus.OK);
            } else {
                return new ApiResponse<>(false, "Invalid or expired token", null, HttpStatus.UNAUTHORIZED);
            }

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error validating token: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
