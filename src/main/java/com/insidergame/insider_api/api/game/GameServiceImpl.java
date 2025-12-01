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
            Map<String, RoleType> roles = assignRolesV2(players);

            // Create game with 60 seconds duration
            int durationSeconds = 60;
            Game game = gameManager.createGame(roomCode, word, durationSeconds, roles);

            // Return created game; controller will handle broadcasting and scheduling finish
            return new ApiResponse<>(true, "Game started", game, null);
        } catch (Exception e) {
            return new ApiResponse<>(false, "Error starting game: " + e.getMessage(), null, null);
        }
    }

    // Helper: assign roles randomly from the given players list
    private Map<String, RoleType> assignRoles(List<Player> players) {
        // Build uuid list and map for quick lookup
        List<String> uuids = players.stream().map(Player::getUuid).collect(Collectors.toList());
        Map<String, String> uuidToName = new HashMap<>();
        for (Player p : players) {
            if (p != null) uuidToName.put(p.getUuid(), p.getPlayerName());
        }

        Map<String, RoleType> roles = new HashMap<>();

        // If there's a player whose name is exactly "masterplayer", fix them as MASTER
        Optional<String> fixedMaster = uuidToName.entrySet().stream()
                .filter(e -> "masterplayer".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();

        List<String> remaining = new ArrayList<>(uuids);
        if (fixedMaster.isPresent()) {
            String masterUuid = fixedMaster.get();
            // remove master from remaining and assign
            remaining.remove(masterUuid);
            roles.put(masterUuid, RoleType.MASTER);
        }

        // Shuffle remaining and assign INSIDER + CITIZEN
        Collections.shuffle(remaining);
        if (!roles.containsValue(RoleType.MASTER)) {
            // no fixed master -> first of remaining becomes MASTER
            if (!remaining.isEmpty()) {
                roles.put(remaining.get(0), RoleType.MASTER);
                remaining.remove(0);
            }
        }

        // assign INSIDER if available
        if (!remaining.isEmpty()) {
            roles.put(remaining.get(0), RoleType.INSIDER);
            remaining.remove(0);
        }

        // rest are CITIZEN
        for (String u : remaining) {
            roles.put(u, RoleType.CITIZEN);
        }

        return roles;
    }

    private  Map<String, RoleType> assignRolesV2(List<Player> players) {
        Map<String, RoleType> roles = new HashMap<>();
        List<String> uuids = players.stream().map(Player::getUuid).collect(Collectors.toList());
        Collections.shuffle(uuids);
        if (!uuids.isEmpty()) {
            roles.put(uuids.get(0), RoleType.MASTER);
        }
        if (uuids.size() >= 2) {
            roles.put(uuids.get(1), RoleType.INSIDER);
        }
        for (int i = 2; i < uuids.size(); i++) {
            roles.put(uuids.get(i), RoleType.CITIZEN);
        }
        return roles;
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

    @Override
    public ApiResponse<Boolean> castVote(String roomCode, String voterUuid, String targetUuid) {
        try {
            var tally = gameManager.recordVote(roomCode, voterUuid, targetUuid);
            if (tally == null || tally.isEmpty()) {
                return new ApiResponse<>(false, "Failed to record vote", false, null);
            }
            return new ApiResponse<>(true, "Vote cast", true, null);
        } catch (Exception ex) {
            return new ApiResponse<>(false, ex.getMessage(), false, null);
        }
    }

    @Override
    public ApiResponse<Game> finishGameWithScoring(String roomCode) {
        try {
            var gameOpt = gameManager.getActiveGame(roomCode);
            if (gameOpt.isEmpty()) {
                return new ApiResponse<>(false, "No active game found", null, null);
            }

            Game game = gameOpt.get();

            // Calculate scores and create summary
            var summary = calculateGameSummary(game);
            game.setSummary(summary);

            // Mark game as finished
//            gameManager.finishGame(roomCode);
//            game.setFinished(true);

            return new ApiResponse<>(true, "Game finished with scoring", game, null);
        } catch (Exception ex) {
            return new ApiResponse<>(false, "Error finishing game: " + ex.getMessage(), null, null);
        }
    }

    private com.insidergame.insider_api.model.GameSummary calculateGameSummary(Game game) {
        Map<String, Integer> scores = new HashMap<>();
        Map<String, Integer> voteTally = new HashMap<>();

        // Find INSIDER and MASTER
        String insiderUuid = null;
        String masterUuid = null;
        List<String> citizenUuids = new ArrayList<>();

        for (Map.Entry<String, RoleType> entry : game.getRoles().entrySet()) {
            String uuid = entry.getKey();
            RoleType role = entry.getValue();

            scores.put(uuid, 0); // Initialize all scores to 0

            if (role == RoleType.INSIDER) {
                insiderUuid = uuid;
            } else if (role == RoleType.MASTER) {
                masterUuid = uuid;
            } else if (role == RoleType.CITIZEN) {
                citizenUuids.add(uuid);
            }
        }

        // Calculate vote tally (exclude MASTER's vote from citizen vote count)
        for (Map.Entry<String, String> vote : game.getVotes().entrySet()) {
            String target = vote.getValue();
            voteTally.put(target, voteTally.getOrDefault(target, 0) + 1);
        }

        // Find most voted player(s)
        int maxVotes = voteTally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> mostVoted = voteTally.entrySet().stream()
            .filter(e -> e.getValue() == maxVotes)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        // Check if INSIDER was caught (is most voted)
        boolean insiderCaught = mostVoted.contains(insiderUuid);

        // TODO: Check if CITIZENS answered correctly - this needs to be tracked separately
        // For now, assume false (you need to add this logic based on your game flow)
        boolean citizensAnsweredCorrectly = false;

        // Calculate CITIZEN scores
        int citizenVotesForInsider = 0;
        for (String citizenUuid : citizenUuids) {
            String votedFor = game.getVotes().get(citizenUuid);
            if (votedFor != null && votedFor.equals(insiderUuid)) {
                citizenVotesForInsider++;
            }
        }

        // CITIZEN scoring:
        // +1 if more than half of CITIZENS voted for INSIDER
        if (citizenUuids.size() > 0 && citizenVotesForInsider > citizenUuids.size() / 2.0) {
            for (String citizenUuid : citizenUuids) {
                scores.put(citizenUuid, scores.get(citizenUuid) + 1);
            }
        }

        // +1 if CITIZENS answered the word correctly
        if (citizensAnsweredCorrectly) {
            for (String citizenUuid : citizenUuids) {
                scores.put(citizenUuid, scores.get(citizenUuid) + 1);
            }
        }

        // INSIDER scoring:
        if (insiderUuid != null) {
            // +1 if helped CITIZENS answer correctly
            if (citizensAnsweredCorrectly) {
                scores.put(insiderUuid, scores.get(insiderUuid) + 1);
            }

            // +1 if not caught (>= half of CITIZENS didn't vote for INSIDER)
            if (citizenUuids.size() > 0 && citizenVotesForInsider < citizenUuids.size() / 2.0) {
                scores.put(insiderUuid, scores.get(insiderUuid) + 1);
            }
        }

        // MASTER scoring:
        if (masterUuid != null) {
            // +1 base score
            scores.put(masterUuid, scores.get(masterUuid) + 1);

            // +1 if caught INSIDER (INSIDER is most voted)
            if (insiderCaught) {
                scores.put(masterUuid, scores.get(masterUuid) + 1);
            }
        }

        return com.insidergame.insider_api.model.GameSummary.builder()
            .scores(scores)
            .voteTally(voteTally)
            .mostVoted(mostVoted)
            .insiderCaught(insiderCaught)
            .citizensAnsweredCorrectly(citizensAnsweredCorrectly)
            .insiderUuid(insiderUuid)
            .masterUuid(masterUuid)
            .word(game.getWord())
            .build();
    }

}
