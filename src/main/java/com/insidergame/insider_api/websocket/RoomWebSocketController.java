package com.insidergame.insider_api.websocket;

import com.insidergame.insider_api.dto.PlayerDto;
import com.insidergame.insider_api.dto.RoomUpdateMessage;
import com.insidergame.insider_api.enums.RoleType;
import com.insidergame.insider_api.enums.RoomStatus;
import com.insidergame.insider_api.manager.RoomManager;
import com.insidergame.insider_api.model.Game;
import com.insidergame.insider_api.model.GamePrivateMessage;
import com.insidergame.insider_api.model.Player;
import com.insidergame.insider_api.model.Room;
import com.insidergame.insider_api.service.GameService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
@Slf4j
@SuppressWarnings("unused")
public class RoomWebSocketController {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // Track pending scheduled "set room to PLAYING" tasks so we can cancel if someone un-readies
    private final Map<String, ScheduledFuture<?>> pendingPlayTasks = new ConcurrentHashMap<>();

    public RoomWebSocketController(RoomManager roomManager, SimpMessagingTemplate messagingTemplate, GameService gameService) {
        this.roomManager = roomManager;
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
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

        // If all players are ready and room is still WAITING => schedule transition to PLAYING in 5 seconds.
        try {
            boolean allReady = room.getPlayers().stream().allMatch(Player::isReady);
            if (allReady && room.getStatus() == RoomStatus.WAITING) {
                // Schedule only if not already scheduled
                if (!pendingPlayTasks.containsKey(roomCode)) {
                    ScheduledFuture<?> f = scheduler.schedule(() -> {
                        try {
                            roomManager.updateRoomStatus(roomCode, RoomStatus.PLAYING);
                            broadcastRoomUpdate(roomCode, "ROOM_PLAYING");
                            log.info("Room {} auto-transitioned to PLAYING after all ready", roomCode);
                        } catch (Exception ex) {
                            log.error("Error auto-starting room {}: {}", roomCode, ex.getMessage(), ex);
                        } finally {
                            pendingPlayTasks.remove(roomCode);
                        }
                    }, 5, TimeUnit.SECONDS);
                    pendingPlayTasks.put(roomCode, f);
                }
            } else {
                // Not all ready anymore - cancel pending task if any
                ScheduledFuture<?> prev = pendingPlayTasks.remove(roomCode);
                if (prev != null) {
                    prev.cancel(false);
                    log.info("Cancelled pending auto-play for room {} because not all players are ready", roomCode);
                }
            }
        } catch (Exception ignored) {}

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
                    player.setLastActiveAt(java.time.LocalDateTime.now());
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
    public void joinRoom(@DestinationVariable String roomCode, @Payload JoinRequest request, MessageHeaders headers) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
        log.info("WS join request: player {} (name={}) joining room {} sessionId={}", request.getPlayerUuid(), request.getPlayerName(), roomCode, sessionId);

        Room room = roomManager.getRoom(roomCode).orElse(null);
        if (room == null) {
            log.warn("Room {} not found (WS join)", roomCode);
            return;
        }

        // If player already in room, update sessionId/active and send snapshot
        if (roomManager.isPlayerInRoom(roomCode, request.getPlayerUuid())) {
            // update existing player's sessionId and activity so they can receive private messages
            room.getPlayers().stream()
                    .filter(p -> p.getUuid().equals(request.getPlayerUuid()))
                    .findFirst()
                    .ifPresent(existing -> {
                        existing.setSessionId(sessionId);
                        existing.setActive(true);
                        existing.setLastActiveAt(java.time.LocalDateTime.now());
                        // optionally update name if provided
                        if (request.getPlayerName() != null && !request.getPlayerName().isEmpty()) {
                            existing.setPlayerName(request.getPlayerName());
                        }
                        log.info("Updated existing player {} with session {}", existing.getUuid(), sessionId);
                    });

            broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
            return;
        }

        // If room not accepting new players or full, just broadcast snapshot (client can handle UI)
        if (room.isFull()) {
            log.warn("Room {} is full - cannot join via WS: {}", roomCode, request.getPlayerUuid());
            broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
            return;
        }

        // Build player and add to room via RoomManager (which already guards duplicates)
        Player player = Player.builder()
                .uuid(request.getPlayerUuid())
                .playerName(request.getPlayerName())
                .joinedAt(java.time.LocalDateTime.now())
                .isHost(false)
                .isActive(true)
                .lastActiveAt(java.time.LocalDateTime.now())
                .sessionId(sessionId)
                .build();

        boolean added = roomManager.addPlayerToRoom(roomCode, player);
        if (added) {
            log.info("Player {} added to room {} via WS (session={})", request.getPlayerUuid(), roomCode, sessionId);
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
                        player.setLastActiveAt(java.time.LocalDateTime.now());
                    }
//                    if(player.isReady()){
//                        player.setReady(false);
//                    }
                });

        // Broadcast update so other clients see active/inactive change
        broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
    }

    /**
     * Start the game in the room
     * Client sends: /app/room/{roomCode}/start
     * Payload: { triggerByUuid }
     */
    @MessageMapping("/room/{roomCode}/start")
    public void startGame(@DestinationVariable String roomCode, @Payload StartRequest request) {
        log.info("WS start game requested by {} in room {}", request.getTriggerByUuid(), roomCode);

        try {
            var resp = gameService.startGame(roomCode, request.getTriggerByUuid());
            if (!resp.isSuccess() || resp.getData() == null) {
                log.warn("Failed to start game in room {}: {}", roomCode, resp.getMessage());
                broadcastRoomUpdate(roomCode, "ROOM_UPDATE");
                return;
            }

            Game game = resp.getData();

            // Mark participating players as playing and ready in the room so clients see updated state
            try {
                var roomOpt = roomManager.getRoom(roomCode);
                if (roomOpt.isPresent()) {
                    var room = roomOpt.get();
                    var participants = game.getRoles() == null ? java.util.Collections.<String>emptySet() : game.getRoles().keySet();
                    for (Player p : room.getPlayers()) {
                        if (p == null) continue;
                        if (participants.contains(p.getUuid())) {
                            p.setPlaying(true);
                           if(p.isReady()) p.setReady(false);
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Broadcast general game started update (includes activeGame in RoomUpdateMessage)
            broadcastRoomUpdate(roomCode, "GAME_STARTED");

            // Send private info to MASTER and INSIDER only using their sessionId
            Map<String, RoleType> roles = game.getRoles();

            // NOTE: We no longer broadcast role-only private info to a topic. Private info is
            // delivered per-user via /user/queue/game_private and via active_game snapshot on reconnect.

            for (Map.Entry<String, RoleType> e : roles.entrySet()) {
                String playerUuid = e.getKey();
                RoleType role = e.getValue() == null ? RoleType.CITIZEN : e.getValue();

                // find player in room to get sessionId
                Player player = roomManager.getRoom(roomCode).orElseThrow().getPlayers().stream()
                        .filter(p -> p.getUuid().equals(playerUuid)).findFirst().orElse(null);
                if (player == null) continue;

                String sessionId = player.getSessionId();
                if (sessionId == null) {
                    log.warn("No sessionId for player {} - cannot send private word", playerUuid);
                    continue; // can't send private
                }

                String word = (role == RoleType.MASTER || role == RoleType.INSIDER) ? game.getWord() : ""; // empty string instead of null

                GamePrivateMessage pm = new GamePrivateMessage(playerUuid, role, word);

                // Build headers targeted to sessionId
                SimpMessageHeaderAccessor sha = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                sha.setSessionId(sessionId);
                sha.setLeaveMutable(true);

                log.info("Sending private game message to session={} playerUuid={} role={}", sessionId, playerUuid, role);
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/game_private", pm, sha.getMessageHeaders());
            }

            // Schedule finish via controller scheduler
            int duration = game.getDurationSeconds();
            scheduler.schedule(() -> {
                try {
                    gameService.finishGame(roomCode);
                    broadcastRoomUpdate(roomCode, "GAME_FINISHED");
                } catch (Exception ex) {
                    log.error("Error finishing game scheduled: {}", ex.getMessage(), ex);
                }
            }, duration, TimeUnit.SECONDS);

        } catch (Exception ex) {
            log.error("Error handling start game WS: {}", ex.getMessage(), ex);
        }
    }



    /**
     * Player opens their role card
     * Client sends: /app/room/{roomCode}/open_card
     * Payload: { playerUuid }
     */
    @MessageMapping("/room/{roomCode}/open_card")
    public void openCard(@DestinationVariable String roomCode, @Payload CardOpenRequest request) {
        log.info("Card open request from player={} in room={}", request.getPlayerUuid(), roomCode);

        try {
            var resp = gameService.markCardOpened(roomCode, request.getPlayerUuid());
            if (resp == null || !resp.isSuccess()) {
                log.warn("markCardOpened failed for room={} player={}", roomCode, request.getPlayerUuid());
            }
        } catch (Exception ex) {
            log.error("Error marking card opened: {}", ex.getMessage(), ex);
        }

        // Broadcast CARD_OPENED update so clients see who opened
        broadcastRoomUpdate(roomCode, "CARD_OPENED");

        // If all opened -> start countdown via gameService.startCountdown
        try {
            var startResp = gameService.startCountdown(roomCode);
            if (startResp != null && startResp.isSuccess() && startResp.getData() != null) {
                Game g = startResp.getData();
                // Broadcast game started so clients receive activeGame with endsAt
                broadcastRoomUpdate(roomCode, "GAME_STARTED");

                // Schedule finish
                long millis = java.time.Duration.between(java.time.LocalDateTime.now(), g.getEndsAt()).toMillis();
                if (millis > 0) {
                    scheduler.schedule(() -> {
                        try {
                            gameService.finishGame(roomCode);
                            broadcastRoomUpdate(roomCode, "GAME_FINISHED");
                        } catch (Exception ex) {
                            log.error("Error finishing scheduled game: {}", ex.getMessage(), ex);
                        }
                    }, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception ignored) {}
    }


    @MessageMapping("/room/{roomCode}/active_game")
    public void currentGame(@DestinationVariable String roomCode, @Payload ActiveGameRequest request, MessageHeaders headers) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
        log.info("Active game requested by {} in room={} session={}", request.getPlayerUuid(), roomCode, sessionId);

        try {
            if (sessionId == null) {
                log.warn("No sessionId present for active_game request from {} in room={}; skipping reply", request.getPlayerUuid(), roomCode);
                return;
            }

            var resp = gameService.getActiveGame(roomCode);
            Map<String, Object> payload = new java.util.HashMap<>();
            if (resp == null || !resp.isSuccess() || resp.getData() == null) {
                // explicit null game allowed in a mutable map
                payload.put("game", null);
            } else {
                Game g = resp.getData();

                // Only return active game to participants (players who have roles in the active game).
                // If requester is not a participant (e.g., a spectator), do not reveal active game data.
                java.util.Set<String> participants = g.getRoles() == null ? java.util.Collections.emptySet() : g.getRoles().keySet();
                if (!participants.contains(request.getPlayerUuid())) {
                    // requester not part of the active game -> deny active game payload
                    payload.put("game", null);
                } else {
                    RoleType roleEnum = null;
                    if (g.getRoles() != null) {
                        roleEnum = g.getRoles().get(request.getPlayerUuid());
                    }
                    if (roleEnum == null) roleEnum = RoleType.CITIZEN; // fallback
                    boolean showWord = roleEnum == RoleType.MASTER || roleEnum == RoleType.INSIDER;
                    boolean allIsOpened = g.getCardOpened() != null && g.getCardOpened().values().stream().allMatch(Boolean::booleanValue);

                    // Build a serializable map for the game payload using mutable map (allows null values)
                    Map<String, Object> gameMap = new java.util.HashMap<>();
                    gameMap.put("id", g.getId() == null ? null : g.getId().toString());
                    gameMap.put("roomCode", g.getRoomCode());
                    gameMap.put("word", showWord ? g.getWord() : "");
                    gameMap.put("roles", g.getRoles());
                    gameMap.put("startedAt", g.getStartedAt() == null ? null : g.getStartedAt().toString());
                    gameMap.put("endsAt", g.getEndsAt() == null ? null : g.getEndsAt().toString());
                    gameMap.put("durationSeconds", g.getDurationSeconds());
                    gameMap.put("finished", g.isFinished());
                    gameMap.put("cardOpened", g.getCardOpened());

                    // Include per-user private info (role + word when applicable) so clients who reconnect
                    // can receive their private GamePrivateMessage together with the active game snapshot.
                    try {
                        GamePrivateMessage pm = new GamePrivateMessage(request.getPlayerUuid(), roleEnum, showWord ? g.getWord() : "");
                        gameMap.put("privateMessage", pm);
                    } catch (Exception ignored) {}

                    // Do not override startedAt/endsAt here. They are persisted by GameManager.startCountdown
                    // so refresh/reconnect won't reset the timer. Keep whatever is stored in the Game model.

                    payload.put("game", gameMap);
                }
            }

            // Send to specific session
            SimpMessageHeaderAccessor sha = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            sha.setSessionId(sessionId);
            sha.setLeaveMutable(true);
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/active_game", payload, sha.getMessageHeaders());
            log.info("Sent active_game to session={} (room={})", sessionId, roomCode);
        } catch (Exception ex) {
            log.error("Error handling active_game request: {}", ex.getMessage(), ex);
        }
    }


    /**
     * Broadcast room update to all subscribers
     */
    public void broadcastRoomUpdate(String roomCode, String updateType) {
        Room room = roomManager.getRoom(roomCode).orElse(null);
        if (room == null) return;

        RoomUpdateMessage message = buildRoomUpdateMessage(room, updateType);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, message);
        log.info("Broadcasted {} to room {}", updateType, roomCode);
    }

    private RoomUpdateMessage buildRoomUpdateMessage(Room room, String type) {
        List<PlayerDto> playerDtos = room.getPlayers().stream().map(this::convertToPlayerDto).collect(Collectors.toList());

        RoomUpdateMessage.RoomUpdateMessageBuilder builder = RoomUpdateMessage.builder()
                .type(type)
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .maxPlayers(room.getMaxPlayers())
                .currentPlayers(room.getCurrentPlayers())
                .status(null)
                .players(playerDtos)
                .message(getMessageForType(type));


        // map room.status (RoomStatus) directly
        try {
            builder.status(room.getStatus());
        } catch (Exception ignored) {
            // leave status null if mapping fails
        }

        // include active game summary if available
//        try {
//            var gr = gameService.getActiveGame(room.getRoomCode());
//            if (gr != null && gr.getData() != null) {
//                Game g = gr.getData();
//                GameSummaryDto summary = GameSummaryDto.builder()
//                        .id(g.getId() == null ? null : g.getId().toString())
//                        .word(null) // Do not broadcast word publicly
//                        .startedAt(g.getStartedAt() == null ? null : g.getStartedAt().toString())
//                        .endsAt(g.getEndsAt() == null ? null : g.getEndsAt().toString())
//                        .durationSeconds(g.getDurationSeconds())
//                        .finished(g.isFinished())
//                        .cardOpened(g.getCardOpened())
//                        .build();
//                builder.activeGame(summary);
//            }
//        } catch (Exception ignored) {}

        return builder.build();
    }

    private PlayerDto convertToPlayerDto(Player player) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return PlayerDto.builder()
                .uuid(player.getUuid())
                .playerName(player.getPlayerName())
                .isHost(player.isHost())
                .isReady(player.isReady())
                .isPlaying(player.isPlaying())
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
        // Provide convenience getter used above
        // Accept JSON property 'active' from client
        @Getter
        private boolean active;

    }

    @lombok.Data
    public static class StartRequest {
        private String triggerByUuid;
    }


    @lombok.Data
    public static class CardOpenRequest {
        private String playerUuid;
    }

    @lombok.Data
    public static class ActiveGameRequest {
        private String playerUuid;
    }

    /**
     * MASTER can end the play early to trigger voting
     * Client sends: /app/room/{roomCode}/master_end
     * Payload: { playerUuid }
     */
    @MessageMapping("/room/{roomCode}/master_end")
    public void masterEnd(@DestinationVariable String roomCode, @Payload ActiveGameRequest request) {
        log.info("Master end requested by {} in room {}", request.getPlayerUuid(), roomCode);

        try {
            var resp = gameService.getActiveGame(roomCode);
            if (resp == null || !resp.isSuccess() || resp.getData() == null) {
                log.warn("No active game in room {} to end", roomCode);
                return;
            }

            Game g = resp.getData();
            if (g.getRoles() == null) {
                log.warn("Active game in room {} has no roles", roomCode);
                return;
            }

            // Ensure requester is MASTER
            RoleType role = g.getRoles().get(request.getPlayerUuid());
            if (role != RoleType.MASTER) {
                log.warn("Player {} is not MASTER in room {} - cannot end game", request.getPlayerUuid(), roomCode);
                return;
            }

            // Move endsAt earlier to start voting period; choose a short delay (e.g., 10 seconds) to allow clients to prepare
            int voteDelaySeconds = 3;
            var now = java.time.LocalDateTime.now();
            g.setEndsAt(now.plusSeconds(voteDelaySeconds));

//            // Update room status to VOTING
//            roomManager.updateRoomStatus(roomCode, RoomStatus.VOTING);

            // Broadcast that voting started (so clients switch UI)
            broadcastRoomUpdate(roomCode, "VOTE_STARTED");

            // Schedule finish at new endsAt
//            long millis = java.time.Duration.between(java.time.LocalDateTime.now(), g.getEndsAt()).toMillis();
//            if (millis > 0) {
//                scheduler.schedule(() -> {
//                    try {
//                        gameService.finishGame(roomCode);
//                        broadcastRoomUpdate(roomCode, "GAME_FINISHED");
//                    } catch (Exception ex) {
//                        log.error("Error finishing scheduled game after master_end: {}", ex.getMessage(), ex);
//                    }
//                }, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
//            }

            // Also send active_game snapshot to participants so they see new endsAt/private info
            try {
                // reuse currentGame logic by publishing directly to each player's session via messagingTemplate
                for (String playerUuid : g.getRoles().keySet()) {
                    Player p = roomManager.getRoom(roomCode).orElseThrow().getPlayers().stream().filter(pl -> pl.getUuid().equals(playerUuid)).findFirst().orElse(null);
                    if (p == null || p.getSessionId() == null) continue;
                    SimpMessageHeaderAccessor sha = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                    sha.setSessionId(p.getSessionId());
                    sha.setLeaveMutable(true);

                    // Build per-player payload similar to currentGame
                    RoleType playerRole = g.getRoles().get(playerUuid);
                    boolean showWord = playerRole == RoleType.MASTER || playerRole == RoleType.INSIDER;
                    Map<String, Object> gameMap = new java.util.HashMap<>();
                    gameMap.put("id", g.getId() == null ? null : g.getId().toString());
                    gameMap.put("roomCode", g.getRoomCode());
                    gameMap.put("word", showWord ? g.getWord() : "");
                    gameMap.put("roles", g.getRoles());
                    gameMap.put("startedAt", g.getStartedAt() == null ? null : g.getStartedAt().toString());
                    gameMap.put("endsAt", g.getEndsAt() == null ? null : g.getEndsAt().toString());
                    gameMap.put("durationSeconds", g.getDurationSeconds());
                    gameMap.put("finished", g.isFinished());
                    gameMap.put("cardOpened", g.getCardOpened());

                    GamePrivateMessage pm = new GamePrivateMessage(playerUuid, playerRole, showWord ? g.getWord() : "");
                    gameMap.put("privateMessage", pm);

                    Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("game", gameMap);

                    messagingTemplate.convertAndSendToUser(p.getSessionId(), "/queue/active_game", payload, sha.getMessageHeaders());
                }
            } catch (Exception ignored) {}

        } catch (Exception ex) {
            log.error("Error handling master_end: {}", ex.getMessage(), ex);
        }
    }

}
