package com.insidergame.insider_api.manager;

import com.insidergame.insider_api.model.Game;
import com.insidergame.insider_api.enums.RoleType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameManager {
    // roomCode -> list of games
    private final Map<String, List<Game>> gamesByRoom = new ConcurrentHashMap<>();
    // active game per room
    private final Map<String, Game> activeGameByRoom = new ConcurrentHashMap<>();

    public Optional<Game> getActiveGame(String roomCode) {
        return Optional.ofNullable(activeGameByRoom.get(roomCode));
    }

    public Game createGame(String roomCode, String word, int durationSeconds, Map<String, RoleType> roles) {
        // convert roles map into model type (RoleType) stored in Game
        Game game = Game.builder()
                .id(UUID.randomUUID())
                .roomCode(roomCode)
                .word(word)
                .roles(new HashMap<>(roles))
                .startedAt(null)
                .durationSeconds(durationSeconds)
                .endsAt(null)
                .finished(false)
                .cardOpened(new HashMap<>())
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
        return true;
    }

    public boolean allCardsOpened(String roomCode) {
        Game g = activeGameByRoom.get(roomCode);
        if (g == null) return false;
        return g.getCardOpened().values().stream().allMatch(Boolean::booleanValue);
    }

    public void finishGame(String roomCode) {
        Game g = activeGameByRoom.remove(roomCode);
        if (g != null) {
            g.setFinished(true);
        }
    }

    public List<Game> getGamesForRoom(String roomCode) {
        return gamesByRoom.getOrDefault(roomCode, Collections.emptyList());
    }
}
