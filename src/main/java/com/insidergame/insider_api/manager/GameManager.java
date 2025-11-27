package com.insidergame.insider_api.manager;

import com.insidergame.insider_api.model.Game;
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

    public Game createGame(String roomCode, String word, int durationSeconds, Map<String, String> roles) {
        Game game = Game.builder()
                .id(UUID.randomUUID())
                .roomCode(roomCode)
                .word(word)
                .roles(new HashMap<>(roles))
                .startedAt(LocalDateTime.now())
                .durationSeconds(durationSeconds)
                .endsAt(LocalDateTime.now().plusSeconds(durationSeconds))
                .finished(false)
                .build();

        gamesByRoom.computeIfAbsent(roomCode, k -> new ArrayList<>()).add(game);
        activeGameByRoom.put(roomCode, game);
        return game;
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

