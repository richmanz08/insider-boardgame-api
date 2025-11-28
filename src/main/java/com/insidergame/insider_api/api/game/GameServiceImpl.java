package com.insidergame.insider_api.api.game;

import com.insidergame.insider_api.enums.RoleType;
import com.insidergame.insider_api.api.category.CategoryServiceImpl;
import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.entity.CategoryEntity;
import com.insidergame.insider_api.manager.GameManager;
import com.insidergame.insider_api.manager.RoomManager;
import com.insidergame.insider_api.model.Game;
import com.insidergame.insider_api.model.Player;
import com.insidergame.insider_api.model.Room;
import com.insidergame.insider_api.service.GameService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service("gameServiceApi")
public class GameServiceImpl implements GameService {

    private final GameManager gameManager;
    private final RoomManager roomManager;
    private final CategoryServiceImpl categoryService; // to fetch categories (used as source of words)

    public GameServiceImpl(GameManager gameManager, RoomManager roomManager, CategoryServiceImpl categoryService) {
        this.gameManager = gameManager;
        this.roomManager = roomManager;
        this.categoryService = categoryService;
    }

    @Override
    public ApiResponse<Game> startGame(String roomCode, String triggerByUuid) {
        try {
            Room room = roomManager.getRoom(roomCode).orElse(null);
            if (room == null) return new ApiResponse<>(false, "Room not found", null, null);

            // Prevent starting if a game already active in this room
            if (gameManager.getActiveGame(roomCode).isPresent()) {
                return new ApiResponse<>(false, "Game already running", null, null);
            }

            // Collect players currently in room
            List<Player> players = new ArrayList<>(room.getPlayers());
            if (players.size() < 2) {
                return new ApiResponse<>(false, "Not enough players to start", null, null);
            }

            // Pick random category/word
            List<CategoryEntity> categories = categoryService.getAllCategoriesService().getData();
            if (categories == null || categories.isEmpty()) {
                return new ApiResponse<>(false, "No categories available", null, null);
            }

            CategoryEntity pick = categories.get(new Random().nextInt(categories.size()));
            String word = pick.getCategoryName();

            // Assign roles: one MASTER, one INSIDER, rest CITIZEN
            List<String> uuids = players.stream().map(Player::getUuid).collect(Collectors.toList());
            Collections.shuffle(uuids);
            Map<String, RoleType> roles = new HashMap<>();
            // First is MASTER
            roles.put(uuids.get(0), RoleType.MASTER);
            // Second is INSIDER
            if (uuids.size() >= 2) {
                roles.put(uuids.get(1), RoleType.INSIDER);
            }
            // the rest are CITIZEN
            for (int i = 2; i < uuids.size(); i++) {
                roles.put(uuids.get(i), RoleType.CITIZEN);
            }

            // Create game with 60 seconds duration
            int durationSeconds = 60;
            Game game = gameManager.createGame(roomCode, word, durationSeconds, roles);

            // Return created game; controller will handle broadcasting and scheduling finish
            return new ApiResponse<>(true, "Game started", game, null);
        } catch (Exception e) {
            return new ApiResponse<>(false, "Error starting game: " + e.getMessage(), null, null);
        }
    }

    @Override
    public ApiResponse<Void> finishGame(String roomCode) {
        try {
            gameManager.finishGame(roomCode);
            return new ApiResponse<>(true, "Game finished", null, null);
        } catch (Exception e) {
            return new ApiResponse<>(false, "Error finishing game: " + e.getMessage(), null, null);
        }
    }

    @Override
    public ApiResponse<Game> getActiveGame(String roomCode) {
        return new ApiResponse<>(true, "", gameManager.getActiveGame(roomCode).orElse(null), null);
    }

    @Override
    public ApiResponse<List<Game>> getGamesForRoom(String roomCode) {
        return new ApiResponse<>(true, "", gameManager.getGamesForRoom(roomCode), null);
    }

    @Override
    public ApiResponse<Boolean> markCardOpened(String roomCode, String playerUuid) {
        try {
            boolean changed = gameManager.markCardOpened(roomCode, playerUuid);
            if (changed) {
                return new ApiResponse<>(true, "Card opened", true, null);
            } else {
                return new ApiResponse<>(false, "Not changed or no active game", false, null);
            }
        } catch (Exception ex) {
            return new ApiResponse<>(false, ex.getMessage(), false, null);
        }
    }

    @Override
    public ApiResponse<Game> startCountdown(String roomCode) {
        try {
            var opt = gameManager.startCountdown(roomCode);
            if (opt.isPresent()) {
                return new ApiResponse<>(true, "Countdown started", opt.get(), null);
            } else {
                return new ApiResponse<>(false, "No active game to start countdown", null, null);
            }
        } catch (Exception ex) {
            return new ApiResponse<>(false, ex.getMessage(), null, null);
        }
    }


}
