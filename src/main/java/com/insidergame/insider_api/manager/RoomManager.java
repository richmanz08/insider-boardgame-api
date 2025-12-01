package com.insidergame.insider_api.manager;

import com.insidergame.insider_api.enums.RoomStatus;
import com.insidergame.insider_api.model.Player;
import com.insidergame.insider_api.model.Room;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RoomManager {

    // In-memory storage for rooms
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /**
     * Create a new room
     */
    public Room createRoom(String roomCode, String roomName, Integer maxPlayers,
                           String password, String hostUuid, String hostName) {

        // Fallback to UUID if hostName is null or empty
        String actualHostName = (hostName == null || hostName.trim().isEmpty()) ? hostUuid : hostName;

        Room room = Room.builder()
                .roomCode(roomCode)
                .roomName(roomName)
                .maxPlayers(maxPlayers)
                .password(password)
                .status(RoomStatus.WAITING)
                .hostUuid(hostUuid)
                .hostName(actualHostName)
                .createdAt(LocalDateTime.now())
                .players(new HashSet<>())
                .build();

        // Add host as first player
        String playerName = actualHostName;

        Player host = Player.builder()
                .uuid(hostUuid)
                .playerName(playerName)
                .joinedAt(LocalDateTime.now())
                .isHost(true)
                .build();

        room.addPlayer(host);
        log.info("Created room {} with host {}", roomCode, hostUuid);

        rooms.put(roomCode, room);
        return room;
    }

    /**
     * Get room by code
     */
    public Optional<Room> getRoom(String roomCode) {
        return Optional.ofNullable(rooms.get(roomCode));
    }

    /**
     * Get all available rooms (WAITING status and not full)
     */
    public List<Room> getAvailableRooms() {
        return rooms.values().stream()
//                .filter(room -> RoomStatus.WAITING.equals(room.getStatus()))
                .filter(room -> !room.isFull())
                .collect(Collectors.toList());
    }

    /**
     * Get all rooms
     */
    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * Add player to room
     */
    public boolean addPlayerToRoom(String roomCode, Player player) {
        Room room = rooms.get(roomCode);

//        if (room != null && !room.isFull() && RoomStatus.WAITING.equals(room.getStatus())) {
        if (room != null && !room.isFull()) {
            // Check if player already exists in the room (by UUID)
            boolean playerExists = room.getPlayers().stream()
                    .anyMatch(p -> p.getUuid().equals(player.getUuid()));

            if (playerExists) {
                log.info("Attempted to add existing player {} to room {} - ignoring", player.getUuid(), roomCode);
                return false; // Player already in room, don't add again
            }

            room.addPlayer(player);
            log.info("Player {} added to room {}", player.getUuid(), roomCode);
            return true;
        }
        return false;
    }

    /**
     * Remove player from room
     * If room becomes empty, delete the room
     * If host leaves, transfer host to the player who joined right after the host
     */
    public boolean removePlayerFromRoom(String roomCode, String playerUuid) {
        Room room = rooms.get(roomCode);
        if (room != null) {
            // Check if the leaving player is the host before removing
            boolean wasHost = playerUuid.equals(room.getHostUuid());

            // Get the host's joinedAt time before removing (needed to find next player)
            LocalDateTime hostJoinedAt = null;
            if (wasHost) {
                hostJoinedAt = room.getPlayers().stream()
                        .filter(p -> p.getUuid().equals(playerUuid))
                        .findFirst()
                        .map(Player::getJoinedAt)
                        .orElse(null);
            }

            // Remove the player
            room.removePlayer(playerUuid);

            // If room is empty, delete it
            if (room.isEmpty()) {
                rooms.remove(roomCode);
                log.info("Room {} deleted (empty after player {} left)", roomCode, playerUuid);
                return true; // Room deleted
            }

            // If host left, assign new host to the player who joined right after the old host
            if (wasHost && !room.isEmpty()) {
                Player newHost = findNextHost(room, hostJoinedAt);

                if (newHost != null) {
                    newHost.setHost(true);
                    room.setHostUuid(newHost.getUuid());
                    room.setHostName(newHost.getPlayerName());
                    log.info("Host transferred in room {} from {} to {} ({})",
                            roomCode, playerUuid, newHost.getUuid(), newHost.getPlayerName());
                } else {
                    // Fallback: if we can't find next host by timestamp, just pick first player
                    Player fallbackHost = room.getPlayers().iterator().next();
                    fallbackHost.setHost(true);
                    room.setHostUuid(fallbackHost.getUuid());
                    room.setHostName(fallbackHost.getPlayerName());
                    log.warn("Host transferred in room {} using fallback method to {}", roomCode, fallbackHost.getUuid());
                }
            }

            return false; // Room still exists
        }
        return false;
    }

    /**
     * Find the next host: the player who joined right after the previous host
     * If hostJoinedAt is null or no suitable player found, return the earliest joined player
     */
    private Player findNextHost(Room room, LocalDateTime hostJoinedAt) {
        if (hostJoinedAt == null) {
            // Fallback: return earliest joined player
            return room.getPlayers().stream()
                    .min((p1, p2) -> {
                        if (p1.getJoinedAt() == null) return 1;
                        if (p2.getJoinedAt() == null) return -1;
                        return p1.getJoinedAt().compareTo(p2.getJoinedAt());
                    })
                    .orElse(null);
        }

        // Find the player who joined right after the host (smallest joinedAt that is greater than hostJoinedAt)
        return room.getPlayers().stream()
                .filter(p -> p.getJoinedAt() != null && p.getJoinedAt().isAfter(hostJoinedAt))
                .min((p1, p2) -> p1.getJoinedAt().compareTo(p2.getJoinedAt()))
                .orElseGet(() -> {
                    // If no player joined after host, return the earliest joined player (wraparound)
                    return room.getPlayers().stream()
                            .min((p1, p2) -> {
                                if (p1.getJoinedAt() == null) return 1;
                                if (p2.getJoinedAt() == null) return -1;
                                return p1.getJoinedAt().compareTo(p2.getJoinedAt());
                            })
                            .orElse(null);
                });
    }

    /**
     * Delete room
     */
    public boolean deleteRoom(String roomCode) {
        return rooms.remove(roomCode) != null;
    }

    /**
     * Reset all players in room after game ends
     */
    public void resetPlayersAfterGame(String roomCode) {
        Room room = rooms.get(roomCode);
        if (room != null) {
            for (Player player : room.getPlayers()) {
                if (player != null) {
                    player.setPlaying(false);
                    player.setReady(false);
                }
            }
        }
    }

    /**
     * Update room status
     */
    public void updateRoomStatus(String roomCode, RoomStatus status) {
        Room room = rooms.get(roomCode);
        if (room != null) {
            room.setStatus(status);
        }
    }

    /**
     * Check if room code exists
     */
    public boolean roomExists(String roomCode) {
        return rooms.containsKey(roomCode);
    }

    /**
     * Get total number of rooms
     */
    public int getTotalRooms() {
        return rooms.size();
    }

    /**
     * Get room by player UUID
     */
    public Optional<Room> getRoomByPlayerUuid(String playerUuid) {
        return rooms.values().stream()
                .filter(room -> room.getPlayers().stream()
                        .anyMatch(player -> player.getUuid().equals(playerUuid)))
                .findFirst();
    }

    /**
     * Check if a player is already in a specific room
     */
    public boolean isPlayerInRoom(String roomCode, String playerUuid) {
        Room room = rooms.get(roomCode);
        if (room == null) {
            return false;
        }
        return room.getPlayers().stream()
                .anyMatch(player -> player.getUuid().equals(playerUuid));
    }


    public List<Player> getReadyToPlayPlayers(String roomCode) {
        Room room = rooms.get(roomCode);
        if (room == null) {
            return Collections.emptyList();
        }
        return room.getPlayers().stream()
                .filter(Player::isReady)
                .collect(Collectors.toList());
    }
}
