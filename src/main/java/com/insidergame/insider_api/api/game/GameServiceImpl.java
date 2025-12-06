package com.insidergame.insider_api.api.game;

import com.insidergame.insider_api.dto.GameHistoryDto;
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
import org.springframework.http.HttpStatus;

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
            if (room == null) return new ApiResponse<>(false, "Room not found", null, HttpStatus.NOT_FOUND);

            // Prevent starting if a game already active in this room
            if (gameManager.getActiveGame(roomCode).isPresent()) {
                return new ApiResponse<>(false, "Game already running", null, HttpStatus.CONFLICT);
            }

            // Collect players currently in room
            List<Player> players = new ArrayList<>(room.getPlayers());
            if (players.size() < 2) {
                return new ApiResponse<>(false, "Not enough players to start", null, HttpStatus.BAD_REQUEST);
            }

            // Pick random category/word that hasn't been used in this room before
            List<CategoryEntity> categories = categoryService.getAllCategoriesService().getData();
            if (categories == null || categories.isEmpty()) {
                return new ApiResponse<>(false, "No categories available", null, HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Get previously used words in this room
            List<Game> previousGames = gameManager.getGamesForRoom(roomCode);
            Set<String> usedWords = previousGames.stream()
                    .map(Game::getWord)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Filter out used words
            List<CategoryEntity> availableCategories = categories.stream()
                    .filter(cat -> !usedWords.contains(cat.getCategoryName()))
                    .collect(Collectors.toList());

            // If all words have been used, allow reusing them (reset)
            if (availableCategories.isEmpty()) {
                availableCategories = new ArrayList<>(categories);
            }

            // Pick random word from available categories
            CategoryEntity pick = availableCategories.get(new Random().nextInt(availableCategories.size()));
            String word = pick.getCategoryName();

            // Assign roles: one MASTER, one INSIDER, rest CITIZEN
            Map<String, RoleType> roles = assignRolesV2(players);

            // Create game with 60 seconds duration
            int durationSeconds = 423;
            Game game = gameManager.createGame(roomCode, word, durationSeconds, roles);

            // Return created game; controller will handle broadcasting and scheduling finish
            return new ApiResponse<>(true, "Game started", game, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ApiResponse<>(false, "Error starting game: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
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
            return new ApiResponse<>(true, "Game finished", null, HttpStatus.OK);
        } catch (Exception e) {
            return new ApiResponse<>(false, "Error finishing game: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<Game> getActiveGame(String roomCode) {
        return new ApiResponse<>(true, "", gameManager.getActiveGame(roomCode).orElse(null), HttpStatus.OK);
    }

    @Override
    public ApiResponse<List<Game>> getGamesForRoom(String roomCode) {
        List<Game> games = gameManager.getGamesForRoom(roomCode);
        if (games == null) games = Collections.emptyList();
        return new ApiResponse<>(true, "", games, HttpStatus.OK);
    }

    @Override
    public ApiResponse<Boolean> markCardOpened(String roomCode, String playerUuid) {
        try {
            boolean changed = gameManager.markCardOpened(roomCode, playerUuid);
            if (changed) {
                return new ApiResponse<>(true, "Card opened", true, HttpStatus.OK);
            } else {
                return new ApiResponse<>(false, "Not changed or no active game", false, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception ex) {
            return new ApiResponse<>(false, ex.getMessage(), false, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<Game> startCountdown(String roomCode) {
        try {
            var opt = gameManager.startCountdown(roomCode);
            if (opt.isPresent()) {
                return new ApiResponse<>(true, "Countdown started", opt.get(), HttpStatus.OK);
            } else {
                return new ApiResponse<>(false, "No active game to start countdown", null, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception ex) {
            return new ApiResponse<>(false, ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<Boolean> castVote(String roomCode, String voterUuid, String targetUuid) {
        try {
            var tally = gameManager.recordVote(roomCode, voterUuid, targetUuid);
            if (tally == null || tally.isEmpty()) {
                return new ApiResponse<>(false, "Failed to record vote", false, HttpStatus.BAD_REQUEST);
            }
            return new ApiResponse<>(true, "Vote cast", true, HttpStatus.OK);
        } catch (Exception ex) {
            return new ApiResponse<>(false, ex.getMessage(), false, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<Game> finishGameWithScoring(String roomCode) {
        try {
            var gameOpt = gameManager.getActiveGame(roomCode);
            if (gameOpt.isEmpty()) {
                return new ApiResponse<>(false, "No active game found", null, HttpStatus.NOT_FOUND);
            }

            Game game = gameOpt.get();

            // Calculate scores and create summary
            var summary = calculateGameSummary(game);
            game.setSummary(summary);

            // Mark game as finished
//            gameManager.finishGame(roomCode);
//            game.setFinished(true);

            return new ApiResponse<>(true, "Game finished with scoring", game, HttpStatus.OK);
        } catch (Exception ex) {
            return new ApiResponse<>(false, "Error finishing game: " + ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ApiResponse<List<GameHistoryDto>> getGameHistory(String roomCode) {
        try {
            List<Game> games = gameManager.getGamesForRoom(roomCode);
            if (games == null) games = Collections.emptyList();
            List<GameHistoryDto> history = games.stream()
                    .map(this::convertToHistoryDto)
                    .collect(Collectors.toList());
            return new ApiResponse<>(true, "Game history retrieved", history, HttpStatus.OK);
        } catch (Exception ex) {
            return new ApiResponse<>(false, "Error retrieving game history: " + ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    /**
     * Convert Game model to GameHistoryDto
     */
    private GameHistoryDto convertToHistoryDto(Game game) {
        // Calculate scores from game summary if available
        Map<String, Integer> scores = null;
        if (game.getSummary() != null && game.getSummary().getScores() != null) {
            scores = game.getSummary().getScores();
        }

        GameHistoryDto dto = GameHistoryDto.builder()
                .id(game.getId())
                .roomCode(game.getRoomCode())
                .word(game.getWord())
                .wordRevealed(game.isWordRevealed())
                .startedAt(game.getStartedAt())
                .endsAt(game.getEndsAt())
                .durationSeconds(game.getDurationSeconds())
                .finished(game.isFinished())
                .players(game.getPlayerInGame())
                .roles(game.getRoles())
                .cardOpened(game.getCardOpened())
                .votes(game.getVotes())
                .scores(scores)
                .build();

        // Calculate vote result if votes exist
        if (game.getVotes() != null && !game.getVotes().isEmpty()) {
            Map<String, Integer> voteTally = new HashMap<>();
            for (String targetUuid : game.getVotes().values()) {
                voteTally.put(targetUuid, voteTally.getOrDefault(targetUuid, 0) + 1);
            }

            // Find most voted player
            String mostVotedUuid = null;
            int mostVotedCount = 0;
            for (Map.Entry<String, Integer> entry : voteTally.entrySet()) {
                if (entry.getValue() > mostVotedCount) {
                    mostVotedCount = entry.getValue();
                    mostVotedUuid = entry.getKey();
                }
            }

            // Find insider
            String insiderUuid = null;
            if (game.getRoles() != null) {
                for (Map.Entry<String, RoleType> entry : game.getRoles().entrySet()) {
                    if (entry.getValue() == RoleType.INSIDER) {
                        insiderUuid = entry.getKey();
                        break;
                    }
                }
            }

            GameHistoryDto.VoteResultDto voteResult = GameHistoryDto.VoteResultDto.builder()
                    .insiderUuid(insiderUuid)
                    .mostVotedUuid(mostVotedUuid)
                    .mostVotedCount(mostVotedCount)
                    .voteTally(voteTally)
                    .build();

            dto.setVoteResult(voteResult);

            // Determine game outcome
            if (insiderUuid != null && insiderUuid.equals(mostVotedUuid)) {
                dto.setGameOutcome("INSIDER_FOUND");
            } else if (insiderUuid != null) {
                dto.setGameOutcome("INSIDER_HIDDEN");
            } else {
                dto.setGameOutcome("NO_INSIDER");
            }
        }

        return dto;
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
