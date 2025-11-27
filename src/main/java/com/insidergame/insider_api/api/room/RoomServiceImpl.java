package com.insidergame.insider_api.api.room;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.CreateRoomRequest;
import com.insidergame.insider_api.dto.JoinRoomRequest;
import com.insidergame.insider_api.dto.LeaveRoomRequest;
import com.insidergame.insider_api.dto.RoomResponse;
import com.insidergame.insider_api.enums.RoomStatus;
import com.insidergame.insider_api.manager.RoomManager;
import com.insidergame.insider_api.model.Player;
import com.insidergame.insider_api.model.Room;
import com.insidergame.insider_api.service.RoomService;
import com.insidergame.insider_api.util.RoomCodeGenerator;
import com.insidergame.insider_api.websocket.RoomWebSocketController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomServiceImpl implements RoomService {

    private final RoomManager roomManager;
    private final RoomCodeGenerator roomCodeGenerator;
    private final RoomWebSocketController webSocketController;

    public RoomServiceImpl(RoomManager roomManager, RoomCodeGenerator roomCodeGenerator, RoomWebSocketController webSocketController) {
        this.roomManager = roomManager;
        this.roomCodeGenerator = roomCodeGenerator;
        this.webSocketController = webSocketController;
    }

    @Override
    public ApiResponse<RoomResponse> createRoom(CreateRoomRequest request) {
        try {
            // Validate max players
            if (request.getMaxPlayers() < 2 || request.getMaxPlayers() > 12) {
                return new ApiResponse<>(false, "Max players must be between 2 and 12", null, HttpStatus.BAD_REQUEST);
            }

            // Generate unique room code
            String roomCode;
            do {
                roomCode = roomCodeGenerator.generateRoomCode();
            } while (roomManager.roomExists(roomCode));

            // Create room in memory
            Room room = roomManager.createRoom(
                    roomCode,
                    request.getRoomName(),
                    request.getMaxPlayers(),
                    request.getPassword(),
                    request.getHostUuid(),
                    request.getHostName()
            );

            // Broadcast initial room state so subscribers (if any) receive the room snapshot
            try {
                webSocketController.broadcastRoomUpdate(room.getRoomCode(), "ROOM_UPDATE");
            } catch (Exception ignored) {
                // If no subscribers or messaging not ready, ignore - room is still created
            }

            // Build response
            RoomResponse response = buildRoomResponse(room);

            return new ApiResponse<>(true, "Room created successfully", response, HttpStatus.CREATED);

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error creating room: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<RoomResponse> joinRoom(JoinRoomRequest request) {
        try {
            // Find room by code
            Room room = roomManager.getRoom(request.getRoomCode()).orElse(null);

            if (room == null) {
                return new ApiResponse<>(false, "Room not found", null, HttpStatus.NOT_FOUND);
            }

            // Check if player is already in the room
            if (roomManager.isPlayerInRoom(request.getRoomCode(), request.getPlayerUuid())) {
                // Player already in room, just return current room state
                RoomResponse response = buildRoomResponse(room);

                // Ensure subscribers receive current state in case client relies on WS snapshot
                try {
                    webSocketController.broadcastRoomUpdate(request.getRoomCode(), "ROOM_UPDATE");
                } catch (Exception ignored) {
                    // ignore
                }

                return new ApiResponse<>(true, "Player already in room", response, HttpStatus.OK);
            }

            // Check if room is full
            if (room.isFull()) {
                return new ApiResponse<>(false, "Room is full", null, HttpStatus.CONFLICT);
            }

            // Check room status
            if (!RoomStatus.WAITING.equals(room.getStatus())) {
                return new ApiResponse<>(false, "Room is not accepting new players", null, HttpStatus.CONFLICT);
            }

            // Check password if required
            if (room.hasPassword()) {
                if (request.getPassword() == null || !room.getPassword().equals(request.getPassword())) {
                    return new ApiResponse<>(false, "Incorrect password", null, HttpStatus.UNAUTHORIZED);
                }
            }

            // Add player to room
            Player player = Player.builder()
                    .uuid(request.getPlayerUuid())
                    .playerName(request.getPlayerName())
                    .joinedAt(LocalDateTime.now())
                    .isHost(false)
                    .build();

            boolean added = roomManager.addPlayerToRoom(request.getRoomCode(), player);

            if (!added) {
                return new ApiResponse<>(false, "Failed to add player to room", null, HttpStatus.CONFLICT);
            }

            // Build response AFTER adding player (so currentPlayers count is updated)
            RoomResponse response = buildRoomResponse(room);

            // Broadcast to WebSocket subscribers
            webSocketController.broadcastRoomUpdate(request.getRoomCode(), "PLAYER_JOINED");

            return new ApiResponse<>(true, "Joined room successfully", response, HttpStatus.OK);

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error joining room: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<RoomResponse> leaveRoom(LeaveRoomRequest request) {
        try {
            Room room = roomManager.getRoom(request.getRoomCode()).orElse(null);

            if (room == null) {
                return new ApiResponse<>(false, "Room not found", null, HttpStatus.NOT_FOUND);
            }

            // Remove player from room
            boolean roomDeleted = roomManager.removePlayerFromRoom(request.getRoomCode(), request.getPlayerUuid());

            if (roomDeleted) {
                // Room was deleted because it's empty
                return new ApiResponse<>(true, "Left room successfully (room deleted - empty)", null, HttpStatus.OK);
            }

            // Broadcast to WebSocket subscribers
            webSocketController.broadcastRoomUpdate(request.getRoomCode(), "PLAYER_LEFT");

            // Room still exists, return updated room info
            RoomResponse response = buildRoomResponse(room);
            return new ApiResponse<>(true, "Left room successfully", response, HttpStatus.OK);

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error leaving room: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<RoomResponse> getRoomByCode(String roomCode) {
        try {
            Room room = roomManager.getRoom(roomCode).orElse(null);

            if (room == null) {
                return new ApiResponse<>(false, "Room not found", null, HttpStatus.NOT_FOUND);
            }

            RoomResponse response = buildRoomResponse(room);
            return new ApiResponse<>(true, "Room found", response, HttpStatus.OK);

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error fetching room: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<List<RoomResponse>> getAvailableRooms() {
        try {
            List<Room> rooms = roomManager.getAvailableRooms();

            List<RoomResponse> availableRooms = rooms.stream()
                    .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt())) // Sort by createdAt DESC (newest first)
                    .map(this::buildRoomResponse)
                    .collect(Collectors.toList());

            return new ApiResponse<>(true, "Available rooms retrieved successfully", availableRooms, HttpStatus.OK);

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error fetching rooms: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<Void> deleteRoom(String roomCode, String hostUuid) {
        try {
            Room room = roomManager.getRoom(roomCode).orElse(null);

            if (room == null) {
                return new ApiResponse<>(false, "Room not found", null, HttpStatus.NOT_FOUND);
            }

            // Only host can delete room
            if (!room.getHostUuid().equals(hostUuid)) {
                return new ApiResponse<>(false, "Only the host can delete the room", null, HttpStatus.FORBIDDEN);
            }

            roomManager.deleteRoom(roomCode);
            return new ApiResponse<>(true, "Room deleted successfully", null, HttpStatus.OK);

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error deleting room: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private RoomResponse buildRoomResponse(Room room) {
        return RoomResponse.builder()
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .maxPlayers(room.getMaxPlayers())
                .currentPlayers(room.getCurrentPlayers())
                .hasPassword(room.hasPassword())
                .status(room.getStatus())
                .hostUuid(room.getHostUuid())
                .hostName(room.getHostName())
                .createdAt(room.getCreatedAt())
                .build();
    }
}

