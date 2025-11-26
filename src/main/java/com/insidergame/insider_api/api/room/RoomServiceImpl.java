package com.insidergame.insider_api.api.room;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.CreateRoomRequest;
import com.insidergame.insider_api.dto.JoinRoomRequest;
import com.insidergame.insider_api.dto.LeaveRoomRequest;
import com.insidergame.insider_api.dto.RoomResponse;
import com.insidergame.insider_api.manager.RoomManager;
import com.insidergame.insider_api.model.Player;
import com.insidergame.insider_api.model.Room;
import com.insidergame.insider_api.service.RoomService;
import com.insidergame.insider_api.util.RoomCodeGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomServiceImpl implements RoomService {

    private final RoomManager roomManager;
    private final RoomCodeGenerator roomCodeGenerator;

    public RoomServiceImpl(RoomManager roomManager, RoomCodeGenerator roomCodeGenerator) {
        this.roomManager = roomManager;
        this.roomCodeGenerator = roomCodeGenerator;
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

            // Check if room is full
            if (room.isFull()) {
                return new ApiResponse<>(false, "Room is full", null, HttpStatus.CONFLICT);
            }

            // Check room status
            if (!"WAITING".equals(room.getStatus())) {
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

            roomManager.addPlayerToRoom(request.getRoomCode(), player);

            // Build response AFTER adding player (so currentPlayers count is updated)
            RoomResponse response = buildRoomResponse(room);

            return new ApiResponse<>(true, "Joined room successfully", response, HttpStatus.OK);

        } catch (Exception e) {
            return new ApiResponse<>(false, "Error joining room: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<RoomResponse> leaveRoom(String roomCode, String playerUuid) {
        try {
            Room room = roomManager.getRoom(roomCode).orElse(null);

            if (room == null) {
                return new ApiResponse<>(false, "Room not found", null, HttpStatus.NOT_FOUND);
            }

            // Remove player from room
            boolean roomDeleted = roomManager.removePlayerFromRoom(roomCode, playerUuid);

            if (roomDeleted) {
                // Room was deleted because it's empty
                return new ApiResponse<>(true, "Left room successfully (room deleted - empty)", null, HttpStatus.OK);
            }

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

