package com.insidergame.insider_api.api.game;

import com.insidergame.insider_api.dto.PlayerDto;
import com.insidergame.insider_api.dto.RoomUpdateMessage;
import com.insidergame.insider_api.enums.RoomStatus;
import com.insidergame.insider_api.manager.GameManager;
import com.insidergame.insider_api.manager.RoomManager;
import com.insidergame.insider_api.model.Game;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service to handle auto-finishing games after scoring
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GameFinishService {

    private final GameManager gameManager;
    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    // Track scheduled finish tasks so we can cancel if needed
    private final Map<String, ScheduledFuture<?>> scheduledFinishTasks = new ConcurrentHashMap<>();

    /**
     * Schedule game to finish and reset room after 5 seconds
     * Called after finishGameWithScoring completes
     */
    public void scheduleGameFinish(String roomCode) {
        // Cancel any existing scheduled task for this room
        cancelScheduledFinish(roomCode);

        log.info("Scheduling game finish for room {} in 5 seconds", roomCode);

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            try {
                finishAndResetRoom(roomCode);
            } catch (Exception ex) {
                log.error("Error finishing game for room {}: {}", roomCode, ex.getMessage(), ex);
            } finally {
                scheduledFinishTasks.remove(roomCode);
            }
        }, 5, TimeUnit.SECONDS);

        scheduledFinishTasks.put(roomCode, task);
    }

    /**
     * Cancel scheduled finish task for a room
     */
    public void cancelScheduledFinish(String roomCode) {
        ScheduledFuture<?> task = scheduledFinishTasks.remove(roomCode);
        if (task != null && !task.isDone()) {
            task.cancel(false);
            log.info("Cancelled scheduled game finish for room {}", roomCode);
        }
    }

    /**
     * Finish the game and reset the room to WAITING state
     */
    private void finishAndResetRoom(String roomCode) {
        log.info("Finishing game and resetting room {}", roomCode);

        // Finish and archive the game (moves to history)
        Game finishedGame = gameManager.finishAndArchiveGame(roomCode);

        if (finishedGame != null) {
            log.info("Game {} archived for room {}", finishedGame.getId(), roomCode);
        }

        // Reset room status to WAITING
        roomManager.updateRoomStatus(roomCode, RoomStatus.WAITING);

        // Reset all players: isPlaying = false, isReady = false
        roomManager.resetPlayersAfterGame(roomCode);

        // Broadcast room update
        broadcastRoomReset(roomCode);

        log.info("Room {} reset to WAITING state", roomCode);
    }

    /**
     * Broadcast room reset to all players
     */
    private void broadcastRoomReset(String roomCode) {
        try {
            var roomOpt = roomManager.getRoom(roomCode);
            if (roomOpt.isEmpty()) {
                return;
            }

            var room = roomOpt.get();
            var players = room.getPlayers().stream()
                    .map(p -> PlayerDto.builder()
                            .uuid(p.getUuid())
                            .playerName(p.getPlayerName())
                            .isReady(p.isReady())
                            .isActive(p.isActive())
                            .isPlaying(p.isPlaying())

                            .build())
                    .collect(Collectors.toList());

            RoomUpdateMessage msg = RoomUpdateMessage.builder()
                    .roomCode(roomCode)
                    .roomName(room.getRoomName())
                    .maxPlayers(room.getMaxPlayers())
                    .currentPlayers(room.getCurrentPlayers())
                    .status(room.getStatus())
                    .players(players)
                    .hostUuid(room.getHostUuid())
                    .message("ROOM_RESET_AFTER_GAME")
                    .type("ROOM_RESET_AFTER_GAME")
                    .build();

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, msg);
            log.info("Broadcasted room reset to room {}", roomCode);

            // ⭐ Send null game to all players' sessions to clear their activeGame state
            sendNullGameToAllPlayers(room);

        } catch (Exception ex) {
            log.error("Error broadcasting room reset: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Send null game to all players to clear their activeGame state
     */
    private void sendNullGameToAllPlayers(com.insidergame.insider_api.model.Room room) {
        try {
            Map<String, Object> nullGamePayload = new java.util.HashMap<>();
            nullGamePayload.put("game", null);

            for (var player : room.getPlayers()) {
                if (player.getSessionId() != null) {
                    org.springframework.messaging.simp.SimpMessageHeaderAccessor sha =
                        org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
                            org.springframework.messaging.simp.SimpMessageType.MESSAGE);
                    sha.setSessionId(player.getSessionId());
                    sha.setLeaveMutable(true);

                    messagingTemplate.convertAndSendToUser(
                        player.getSessionId(),
                        "/queue/active_game",
                        nullGamePayload,
                        sha.getMessageHeaders()
                    );

                    log.info("Sent null game to player {} session {} after room reset",
                        player.getUuid(), player.getSessionId());
                }
            }
        } catch (Exception ex) {
            log.error("Error sending null game to players: {}", ex.getMessage(), ex);
        }
    }
}

