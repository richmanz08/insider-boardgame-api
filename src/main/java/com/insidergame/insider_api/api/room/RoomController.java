package com.insidergame.insider_api.api.room;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.dto.CreateRoomRequest;
import com.insidergame.insider_api.dto.JoinRoomRequest;
import com.insidergame.insider_api.dto.RoomResponse;
import com.insidergame.insider_api.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/room")
public class RoomController {

   RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
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
    public ResponseEntity<ApiResponse<RoomResponse>> leaveRoom(
            @RequestParam String roomCode,
            @RequestParam String playerUuid) {
        ApiResponse<RoomResponse> response = roomService.leaveRoom(roomCode, playerUuid);
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
}

