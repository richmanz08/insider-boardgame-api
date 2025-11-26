package com.insidergame.insider_api.service;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.CreateRoomRequest;
import com.insidergame.insider_api.dto.JoinRoomRequest;
import com.insidergame.insider_api.dto.LeaveRoomRequest;
import com.insidergame.insider_api.dto.RoomResponse;

import java.util.List;

public interface RoomService {

    ApiResponse<RoomResponse> createRoom(CreateRoomRequest request);

    ApiResponse<RoomResponse> joinRoom(JoinRoomRequest request);

    ApiResponse<RoomResponse> leaveRoom(String roomCode, String playerUuid);

    ApiResponse<RoomResponse> getRoomByCode(String roomCode);

    ApiResponse<List<RoomResponse>> getAvailableRooms();

    ApiResponse<Void> deleteRoom(String roomCode, String hostUuid);
}

