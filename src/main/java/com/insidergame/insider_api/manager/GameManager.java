package com.insidergame.insider_api.manager;

import com.insidergame.insider_api.model.Game;
import com.insidergame.insider_api.enums.RoleType;
import com.insidergame.insider_api.model.Player;
import com.insidergame.insider_api.model.PlayerInGame;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GameManager {
    // roomCode -> list of games
    private final Map<String, List<Game>> gamesByRoom = new ConcurrentHashMap<>();
    // active game per room
    private final Map<String, Game> activeGameByRoom = new ConcurrentHashMap<>();

    // Inject RoomManager to access players in a room (needed to detect bots)
    private final RoomManager roomManager;

    public Optional<Game> getActiveGame(String roomCode) {
        return Optional.ofNullable(activeGameByRoom.get(roomCode));
    }

    public Game createGame(String roomCode, String word, int durationSeconds, Map<String, RoleType> roles) {

        List<Player> readyPlayPlayer = roomManager.getReadyToPlayPlayers(roomCode);


        List<PlayerInGame> playerInGameList = readyPlayPlayer.stream()
                .map(player -> PlayerInGame.builder()
                        .uuid(player.getUuid())
                        .playerName(player.getPlayerName())
                        .build()
                )
                .collect(Collectors.toList());


        // convert roles map into model type (RoleType) stored in Game
        Game game = Game.builder()
                .id(UUID.randomUUID())
                .roomCode(roomCode)
                .word(word)
                .wordRevealed(false) // Word is hidden until MASTER ends the game
                .roles(new HashMap<>(roles))
                .startedAt(null)
                .durationSeconds(durationSeconds)
                .endsAt(null)
                .finished(false)
                .cardOpened(new HashMap<>())
                .playerInGame(playerInGameList)
                .votes(new HashMap<>())
                .build();

        // initialize cardOpened map for all players
        for (String uuid : roles.keySet()) {
            game.getCardOpened().put(uuid, false);
        }

        gamesByRoom.computeIfAbsent(roomCode, k -> new ArrayList<>()).add(game);
        activeGameByRoom.put(roomCode, game);
        return game;
    }

    // Start the countdown for an active game (set startedAt and endsAt)
    public Optional<Game> startCountdown(String roomCode) {
        Game g = activeGameByRoom.get(roomCode);
        if (g == null) return Optional.empty();

        // Only start countdown when all players have opened their cards
        Map<String, Boolean> cardOpened = g.getCardOpened();
        if (cardOpened == null || cardOpened.isEmpty()) return Optional.empty();
        boolean allOpened = cardOpened.values().stream().allMatch(Boolean::booleanValue);
        if (!allOpened) {
            // Not all players opened yet - do not start countdown
            return Optional.empty();
        }

        // If countdown already started, return existing game
        if (g.getStartedAt() != null && g.getEndsAt() != null) {
            return Optional.of(g);
        }

        // Start countdown now and persist times on the Game model so subsequent
        // active_game requests (e.g. after refresh) won't reset the timer.
        LocalDateTime now = LocalDateTime.now();
        g.setStartedAt(now);
        g.setEndsAt(now.plusSeconds(g.getDurationSeconds()));

        return Optional.of(g);
    }

    // mark a player's card as opened, return true if changed
    public boolean markCardOpened(String roomCode, String playerUuid) {
        Game g = activeGameByRoom.get(roomCode);
        if (g == null) return false;
        Map<String, Boolean> map = g.getCardOpened();
        if (map == null || !map.containsKey(playerUuid)) return false;
        if (Boolean.TRUE.equals(map.get(playerUuid))) return false;
        map.put(playerUuid, true);

        // If the player who opened is the room host, also mark bots' cards as opened
        try {
            var roomOpt = roomManager.getRoom(roomCode);
            if (roomOpt.isPresent()) {
                var room = roomOpt.get();
                String hostUuid = room.getHostUuid();
                if (hostUuid != null && hostUuid.equals(playerUuid)) {
                    // Consider players whose name starts with "Bot " as bots (mocked in RoomServiceImpl)
                    for (Player p : room.getPlayers()) {
                        if (p == null) continue;
                        String name = p.getPlayerName();
                        if (name != null && name.startsWith("Bot ")) {
                            String botUuid = p.getUuid();
                            if (botUuid != null && map.containsKey(botUuid) && !Boolean.TRUE.equals(map.get(botUuid))) {
                                map.put(botUuid, true);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Don't fail card open if room lookup fails
        }

        return true;
    }


    public void finishGame(String roomCode) {
        Game g = activeGameByRoom.remove(roomCode);
        if (g != null) {
            g.setFinished(true);
        }
    }

    // Finish game and move to history
    public Game finishAndArchiveGame(String roomCode) {
        Game g = activeGameByRoom.remove(roomCode);
        if (g != null) {
            g.setFinished(true);
            g.setWordRevealed(true); // Ensure word is revealed when game is archived
            // Game is already in gamesByRoom list, just mark as finished
        }
        return g;
    }

    // Clear all games for a room (called when room is deleted)
    public void clearGamesForRoom(String roomCode) {
        activeGameByRoom.remove(roomCode);
        gamesByRoom.remove(roomCode);
    }

    public List<Game> getGamesForRoom(String roomCode) {
        return gamesByRoom.getOrDefault(roomCode, Collections.emptyList());
    }

    // Record a vote during voting phase: voterUuid votes for targetUuid. Returns current tally map.
    public Map<String, Integer> recordVote(String roomCode, String voterUuid, String targetUuid) {
        Game g = activeGameByRoom.get(roomCode);
        if (g == null) return Collections.emptyMap();
        if (g.getVotes() == null) g.setVotes(new HashMap<>());
        g.getVotes().put(voterUuid, targetUuid);

        // compute tally
        Map<String, Integer> tally = new HashMap<>();
        for (String t : g.getVotes().values()) {
            tally.put(t, tally.getOrDefault(t, 0) + 1);
        }

        return tally;
    }
}
