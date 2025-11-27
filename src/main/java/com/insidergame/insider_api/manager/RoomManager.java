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

        Room room = Room.builder()
                .roomCode(roomCode)
                .roomName(roomName)
                .maxPlayers(maxPlayers)
                .password(password)
                .status(RoomStatus.WAITING)
                .hostUuid(hostUuid)
                .hostName(hostName)
                .createdAt(LocalDateTime.now())
                .players(new HashSet<>())
                .build();

        // Add host as first player
        Player host = Player.builder()
                .uuid(hostUuid)
                .playerName(hostName)
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
                .filter(room -> RoomStatus.WAITING.equals(room.getStatus()))
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
        if (room != null && !room.isFull() && RoomStatus.WAITING.equals(room.getStatus())) {
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
     */
    public boolean removePlayerFromRoom(String roomCode, String playerUuid) {
        Room room = rooms.get(roomCode);
        if (room != null) {
            room.removePlayer(playerUuid);

            // If room is empty, delete it
            if (room.isEmpty()) {
                rooms.remove(roomCode);
                return true; // Room deleted
            }

            // If host left, assign new host
            if (playerUuid.equals(room.getHostUuid()) && !room.isEmpty()) {
                Player newHost = room.getPlayers().iterator().next();
                newHost.setHost(true);
                room.setHostUuid(newHost.getUuid());
                room.setHostName(newHost.getPlayerName());
            }

            return false; // Room still exists
        }
        return false;
    }

    /**
     * Delete room
     */
    public boolean deleteRoom(String roomCode) {
        return rooms.remove(roomCode) != null;
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
}
