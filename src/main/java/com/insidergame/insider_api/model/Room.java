package com.insidergame.insider_api.model;

import com.insidergame.insider_api.enums.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    private String roomCode;
    private String roomName;
    private Integer maxPlayers;
    private String password;
    private RoomStatus status; // Changed to RoomStatus enum
    private String hostUuid;
    private String hostName;
    private LocalDateTime createdAt;

    @Builder.Default
    private Set<Player> players = new HashSet<>();

    public int getCurrentPlayers() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(String playerUuid) {
        players.removeIf(p -> p.getUuid().equals(playerUuid));
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }
}

