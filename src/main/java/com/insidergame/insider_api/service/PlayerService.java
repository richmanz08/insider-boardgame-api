package com.insidergame.insider_api.service;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.PlayerRegisterRequest;
import com.insidergame.insider_api.dto.PlayerResponse;

public interface PlayerService
{
    ApiResponse<PlayerResponse> registerPlayer(PlayerRegisterRequest request);
    ApiResponse<PlayerResponse> validateToken(String token);
}
