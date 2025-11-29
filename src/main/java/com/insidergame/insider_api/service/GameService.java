package com.insidergame.insider_api.service;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.model.Game;

import java.util.List;

public interface GameService {
    ApiResponse<Game> startGame(String roomCode, String triggerByUuid);
    ApiResponse<Void> finishGame(String roomCode);
    ApiResponse<Game> getActiveGame(String roomCode);
    ApiResponse<List<Game>> getGamesForRoom(String roomCode);
    // Mark player's card as opened via WS flow
    ApiResponse<Boolean> markCardOpened(String roomCode, String playerUuid);

    // Start countdown for active game (set startedAt/endsAt) and return the started Game
    ApiResponse<Game> startCountdown(String roomCode);

    // Cast a vote during voting phase
    ApiResponse<Boolean> castVote(String roomCode, String voterUuid, String targetUuid);
}
