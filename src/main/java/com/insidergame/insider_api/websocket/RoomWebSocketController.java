package com.insidergame.insider_api.websocket;

import com.insidergame.insider_api.dto.PlayerDto;
import com.insidergame.insider_api.dto.RoomUpdateMessage;
import com.insidergame.insider_api.manager.RoomManager;
import com.insidergame.insider_api.model.Player;
import com.insidergame.insider_api.model.Room;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class RoomWebSocketController {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomWebSocketController(RoomManager roomManager, SimpMessagingTemplate messagingTemplate) {
        this.roomManager = roomManager;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Player toggles ready status
     * Client sends: /app/room/{roomCode}/ready
     */
    @MessageMapping("/room/{roomCode}/ready")
    public void toggleReady(@DestinationVariable String roomCode, @Payload ReadyRequest request) {
        log.info("Player {} toggling ready status in room {}", request.getPlayerUuid(), roomCode);

        Room room = roomManager.getRoom(roomCode).orElse(null);
        if (room == null) {
            log.warn("Room {} not found", roomCode);
            return;
        }

        // Find player and toggle ready status
        room.getPlayers().stream()
                .filter(p -> p.getUuid().equals(request.getPlayerUuid()))
                .findFirst()
                .ifPresent(player -> {
                    player.setReady(!player.isReady());
                    player.setActive(true);
                    log.info("Player {} is now ready: {}", player.getPlayerName(), player.isReady());
                });

        // Broadcast update to all players in room
        broadcastRoomUpdate(roomCode, "PLAYER_READY");
    }

    /**
     * Presence ping from client to mark active
     * Client sends: /app/room/{roomCode}/presence
     * Payload: { playerUuid }
     */
    @MessageMapping("/room/{roomCode}/presence")
    public void presencePing(@DestinationVariable String roomCode, @Payload PresenceRequest request) {
        log.debug("Presence ping from {} in room {}", request.getPlayerUuid(), roomCode);
        Room room = roomManager.getRoom(roomCode).orElse(null);
        if (room == null) return;

        room.getPlayers().stream()
                .filter(p -> p.getUuid().equals(request.getPlayerUuid()))
                .findFirst()
                .ifPresent(player -> {
                    player.setActive(true);
                    player.setLastActiveAt(LocalDateTime.now());
                });

        // Broadcast a light ROOM_UPDATE so others can know active status
        broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
    }

    /**
     * Player requests join via WebSocket
     * Client sends: /app/room/{roomCode}/join
     * Payload: { playerUuid, playerName }
     * playerName is optional; if missing we fall back to playerUuid as name
     */
    @MessageMapping("/room/{roomCode}/join")
    public void joinRoom(@DestinationVariable String roomCode, @Payload JoinRequest request) {
        log.info("WS join request: player {} (name={}) joining room {}", request.getPlayerUuid(), request.getPlayerName(), roomCode);

        Room room = roomManager.getRoom(roomCode).orElse(null);
        if (room == null) {
            log.warn("Room {} not found (WS join)", roomCode);
            return;
        }

        // If player already in room, just broadcast current snapshot
        if (roomManager.isPlayerInRoom(roomCode, request.getPlayerUuid())) {
            log.info("Player {} already in room {} (WS join) - sending snapshot", request.getPlayerUuid(), roomCode);
            broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
            return;
        }

        // If room not accepting new players or full, just broadcast snapshot (client can handle UI)
        if (room.isFull()) {
            log.warn("Room {} is full - cannot join via WS: {}", roomCode, request.getPlayerUuid());
            broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
            return;
        }

        if (!"WAITING".equals(room.getStatus())) {
            log.warn("Room {} is not accepting players (status={}) - cannot join via WS", roomCode, room.getStatus());
            broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
            return;
        }

        String playerName = request.getPlayerName();
        if (playerName == null || playerName.isEmpty()) {
            playerName = request.getPlayerUuid();
        }

        // Build player and add to room via RoomManager (which already guards duplicates)
        Player player = Player.builder()
                .uuid(request.getPlayerUuid())
                .playerName(playerName)
                .joinedAt(LocalDateTime.now())
                .isHost(false)
                .isActive(true)
                .lastActiveAt(LocalDateTime.now())
                .build();

        boolean added = roomManager.addPlayerToRoom(roomCode, player);
        if (added) {
            log.info("Player {} added to room {} via WS", request.getPlayerUuid(), roomCode);
            broadcastRoomUpdate(roomCode, "PLAYER_JOINED");
        } else {
            log.warn("Failed to add player {} to room {} via WS", request.getPlayerUuid(), roomCode);
            broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
        }
    }

    /**
     * Player notifies leaving via WebSocket
     * Client sends: /app/room/{roomCode}/leave
     * Payload: { playerUuid }
     */
    @MessageMapping("/room/{roomCode}/leave")
    public void leaveRoom(@DestinationVariable String roomCode, @Payload LeaveRequest request) {
        log.info("WS leave request: player {} leaving room {}", request.getPlayerUuid(), roomCode);

        Room room = roomManager.getRoom(roomCode).orElse(null);
        if (room == null) {
            log.warn("Room {} not found (WS leave)", roomCode);
            return;
        }

        boolean roomDeleted = roomManager.removePlayerFromRoom(roomCode, request.getPlayerUuid());

        if (roomDeleted) {
            log.info("Player {} left room {} and room deleted (empty)", request.getPlayerUuid(), roomCode);
            // Broadcast a ROOM_UPDATE so subscribers know the room state changed (it may be removed)
            broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
        } else {
            log.info("Player {} left room {}", request.getPlayerUuid(), roomCode);
            broadcastRoomUpdate(roomCode, "PLAYER_LEFT");
        }
    }

    /**
     * Client sends page visibility / status updates
     * Client sends: /app/room/{roomCode}/status
     * Payload: { playerUuid, active }
     */
    @MessageMapping("/room/{roomCode}/status")
    public void statusUpdate(@DestinationVariable String roomCode, @Payload StatusRequest request) {
        log.info("WS status update: player {} active={} in room {}", request.getPlayerUuid(), request.isActive(), roomCode);

        Room room = roomManager.getRoom(roomCode).orElse(null);
        if (room == null) {
            log.warn("Room {} not found (WS status)", roomCode);
            return;
        }

        room.getPlayers().stream()
                .filter(p -> p.getUuid().equals(request.getPlayerUuid()))
                .findFirst()
                .ifPresent(player -> {
                    player.setActive(request.isActive());
                    if (request.isActive()) {
                        player.setLastActiveAt(LocalDateTime.now());
                    }
                    if(player.isReady()){
                        player.setReady(false);
                    }
                });

        // Broadcast update so other clients see active/inactive change
        broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
    }

    /**
     * Broadcast room update to all subscribers
     */
    public void broadcastRoomUpdate(String roomCode, String updateType) {
        Room room = roomManager.getRoom(roomCode).orElse(null);
        if (room == null) {
            return;
        }

        RoomUpdateMessage message = buildRoomUpdateMessage(room, updateType);

        // Send to /topic/room/{roomCode}
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, message);

        log.info("Broadcasted {} to room {}", updateType, roomCode);
    }

    private RoomUpdateMessage buildRoomUpdateMessage(Room room, String type) {
        List<PlayerDto> playerDtos = room.getPlayers().stream()
                .map(this::convertToPlayerDto)
                .collect(Collectors.toList());

        return RoomUpdateMessage.builder()
                .type(type)
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .maxPlayers(room.getMaxPlayers())
                .currentPlayers(room.getCurrentPlayers())
                .status(room.getStatus())
                .players(playerDtos)
                .message(getMessageForType(type))
                .build();
    }

    private PlayerDto convertToPlayerDto(Player player) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return PlayerDto.builder()
                .uuid(player.getUuid())
                .playerName(player.getPlayerName())
                .isHost(player.isHost())
                .isReady(player.isReady())
                .joinedAt(player.getJoinedAt() == null ? null : player.getJoinedAt().format(formatter))
                .isActive(player.isActive())
                .lastActiveAt(player.getLastActiveAt() == null ? null : player.getLastActiveAt().format(formatter))
                .build();
    }

    private String getMessageForType(String type) {
        return switch (type) {
            case "PLAYER_JOINED" -> "A player joined the room";
            case "PLAYER_LEFT" -> "A player left the room";
            case "PLAYER_READY" -> "A player updated ready status";
            case "ROOM_UPDATE" -> "Room updated";
            default -> "Room state changed";
        };
    }

    // Inner class for request payload
    @lombok.Data
    public static class ReadyRequest {
        private String playerUuid;
    }

    // New inner class for join request
    @lombok.Data
    public static class JoinRequest {
        private String playerUuid;
        private String playerName;
    }

    // New inner class for leave request
    @lombok.Data
    public static class LeaveRequest {
        private String playerUuid;
    }

    // New inner class for presence request
    @lombok.Data
    public static class PresenceRequest {
        private String playerUuid;
    }

    // New inner class for status request
    @lombok.Data
    public static class StatusRequest {
        private String playerUuid;
        // Accept JSON property 'active' from client
        private boolean active;

        // Provide convenience getter used above
        public boolean isActive() {
            return active;
        }
    }

}
