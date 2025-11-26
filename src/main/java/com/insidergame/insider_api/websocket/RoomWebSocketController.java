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
                    log.info("Player {} is now ready: {}", player.getPlayerName(), player.isReady());
                });

        // Broadcast update to all players in room
        broadcastRoomUpdate(roomCode, "PLAYER_READY");
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
                .joinedAt(player.getJoinedAt().format(formatter))
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
}

