package com.insidergame.insider_api.service;

import com.insidergame.insider_api.dto.PlayerDto;
import com.insidergame.insider_api.dto.RoomUpdateMessage;
import com.insidergame.insider_api.enums.RoleType;
import com.insidergame.insider_api.manager.GameManager;
import com.insidergame.insider_api.manager.RoomManager;
import com.insidergame.insider_api.model.Game;
import com.insidergame.insider_api.model.GamePrivateMessage;
import com.insidergame.insider_api.model.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service to automatically reveal word to all players when game timer expires
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GameTimerService {

    private final GameManager gameManager;
    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;

    // Track which games have already been revealed to prevent duplicate broadcasts
    private final Set<String> revealedGameIds = new HashSet<>();

    /**
     * Check every second for games that have expired and reveal the word
     */
    @Scheduled(fixedRate = 1000)
    public void checkExpiredGames() {
        try {
            // Get all rooms and check their active games
            roomManager.getAllRooms().forEach(room -> {
                String roomCode = room.getRoomCode();
                gameManager.getActiveGame(roomCode).ifPresent(game -> checkAndRevealWord(roomCode, game));
            });
        } catch (Exception ex) {
            log.error("Error checking expired games: {}", ex.getMessage(), ex);
        }
    }

    private void checkAndRevealWord(String roomCode, Game game) {
        try {
            // Skip if already finished or word already revealed
            if (game.isFinished() || game.isWordRevealed()) {
                return;
            }

            // Check if endsAt is set and current time has passed it
            LocalDateTime endsAt = game.getEndsAt();
            if (endsAt == null) {
                return; // Timer hasn't started yet
            }

            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(endsAt)) {
                String gameId = game.getId().toString();

                // Check if we've already revealed this game
                if (revealedGameIds.contains(gameId)) {
                    return;
                }

                log.info("Game timer expired for room {} - revealing word to all players", roomCode);

                // Set wordRevealed to true
                game.setWordRevealed(true);

                // Mark as revealed so we don't process again
                revealedGameIds.add(gameId);

                // Broadcast word reveal to all players in the room
                broadcastWordReveal(roomCode, game);

                // Send updated private messages to all players with the revealed word
                sendRevealedWordToAllPlayers(roomCode, game);
            }
        } catch (Exception ex) {
            log.error("Error revealing word for room {}: {}", roomCode, ex.getMessage(), ex);
        }
    }

    private void broadcastWordReveal(String roomCode, Game game) {
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
                            .build())
                    .collect(Collectors.toList());

            RoomUpdateMessage msg = RoomUpdateMessage.builder()
                    .roomCode(roomCode)
                    .status(room.getStatus())
                    .players(players)
                    .hostUuid(room.getHostUuid())
                    .message("WORD_REVEALED")
                    .build();

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, msg);
            log.info("Broadcasted word reveal to room {}", roomCode);
        } catch (Exception ex) {
            log.error("Error broadcasting word reveal: {}", ex.getMessage(), ex);
        }
    }

    private void sendRevealedWordToAllPlayers(String roomCode, Game game) {
        try {
            var roomOpt = roomManager.getRoom(roomCode);
            if (roomOpt.isEmpty()) {
                return;
            }

            var room = roomOpt.get();
            Map<String, RoleType> roles = game.getRoles();
            if (roles == null) {
                return;
            }

            // Send revealed word to all players
            for (Map.Entry<String, RoleType> entry : roles.entrySet()) {
                String playerUuid = entry.getKey();
                RoleType role = entry.getValue() != null ? entry.getValue() : RoleType.CITIZEN;

                // Find player in room to get sessionId
                Player player = room.getPlayers().stream()
                        .filter(p -> p.getUuid().equals(playerUuid))
                        .findFirst()
                        .orElse(null);

                if (player == null) {
                    continue;
                }

                String sessionId = player.getSessionId();
                if (sessionId == null) {
                    log.warn("No sessionId for player {} - cannot send revealed word", playerUuid);
                    continue;
                }

                // Now everyone can see the word
                GamePrivateMessage pm = new GamePrivateMessage(playerUuid, role, game.getWord());

                // Build headers targeted to sessionId
                SimpMessageHeaderAccessor sha = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                sha.setSessionId(sessionId);
                sha.setLeaveMutable(true);

                log.info("Sending revealed word to session={} playerUuid={} role={}", sessionId, playerUuid, role);
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/game_private", pm, sha.getMessageHeaders());
            }
        } catch (Exception ex) {
            log.error("Error sending revealed word to players: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Clean up revealed game IDs when game is finished (optional cleanup to prevent memory leak)
     */
    public void cleanupRevealedGame(String gameId) {
        revealedGameIds.remove(gameId);
    }
}

