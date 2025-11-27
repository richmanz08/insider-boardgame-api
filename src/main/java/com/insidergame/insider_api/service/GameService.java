package com.insidergame.insider_api.service;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.model.Game;

import java.util.List;

public interface GameService {
    ApiResponse<Game> startGame(String roomCode, String triggerByUuid);
    ApiResponse<Void> finishGame(String roomCode);
    ApiResponse<Game> getActiveGame(String roomCode);
    ApiResponse<List<Game>> getGamesForRoom(String roomCode);
}

