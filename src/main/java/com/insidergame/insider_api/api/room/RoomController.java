package com.insidergame.insider_api.api.room;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.CreateRoomRequest;
import com.insidergame.insider_api.dto.JoinRoomRequest;
import com.insidergame.insider_api.dto.LeaveRoomRequest;
import com.insidergame.insider_api.dto.PlayerDto;
import com.insidergame.insider_api.dto.RoomResponse;
import com.insidergame.insider_api.manager.RoomManager;
import com.insidergame.insider_api.model.Player;
import com.insidergame.insider_api.model.Room;
import com.insidergame.insider_api.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/room")
public class RoomController {

    private final RoomService roomService;
    private final RoomManager roomManager;

    public RoomController(RoomService roomService, RoomManager roomManager) {
        this.roomService = roomService;
        this.roomManager = roomManager;
    }

    /**
     * Create new room
     * POST /api/room/create
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(@RequestBody CreateRoomRequest request) {
        ApiResponse<RoomResponse> response = roomService.createRoom(request);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    /**
     * Join existing room
     * POST /api/room/join
     */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<RoomResponse>> joinRoom(@RequestBody JoinRoomRequest request) {
        ApiResponse<RoomResponse> response = roomService.joinRoom(request);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    /**
     * Leave room
     * POST /api/room/leave
     */
    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<RoomResponse>> leaveRoom(@RequestBody LeaveRoomRequest request) {
        ApiResponse<RoomResponse> response = roomService.leaveRoom(request);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    /**
     * Get room by code
     * GET /api/room/{roomCode}
     */
    @GetMapping("/{roomCode}")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoomByCode(@PathVariable String roomCode) {
        ApiResponse<RoomResponse> response = roomService.getRoomByCode(roomCode);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    /**
     * Get all available rooms (waiting status and not full)
     * GET /api/room/available
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getAvailableRooms() {
        ApiResponse<List<RoomResponse>> response = roomService.getAvailableRooms();
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    /**
     * Get all players in a room
     * GET /api/room/{roomCode}/players
     */
    @GetMapping("/{roomCode}/players")
    public ResponseEntity<ApiResponse<List<PlayerDto>>> getRoomPlayers(@PathVariable String roomCode) {
        Room room = roomManager.getRoom(roomCode).orElse(null);

        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "Room not found", null, HttpStatus.NOT_FOUND));
        }

        List<PlayerDto> players = room.getPlayers().stream()
                .map(this::convertToPlayerDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new ApiResponse<>(true, "Players retrieved successfully", players, HttpStatus.OK));
    }

    /**
     * Delete room (only host can delete)
     * DELETE /api/room/{roomCode}
     */
    @DeleteMapping("/{roomCode}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @PathVariable String roomCode,
            @RequestParam String hostUuid) {
        ApiResponse<Void> response = roomService.deleteRoom(roomCode, hostUuid);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    private PlayerDto convertToPlayerDto(Player player) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return PlayerDto.builder()
                .uuid(player.getUuid())
                .playerName(player.getPlayerName())
                .isHost(player.isHost())
                .isReady(player.isReady())
                .isPlaying(player.isPlaying())
                .joinedAt(player.getJoinedAt().format(formatter))
                .isActive(player.isActive())
                .lastActiveAt(player.getLastActiveAt() == null ? null : player.getLastActiveAt().format(formatter))
                .build();
    }
}
