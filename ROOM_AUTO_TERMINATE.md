# Room Auto-Termination Feature

## ✨ Auto-Terminate Room

เมื่อผู้เล่นคนสุดท้ายออกจากห้อง ห้องจะถูก **terminate (ลบ) อัตโนมัติ**

---

## 🔄 How It Works

### 1. **Room.isEmpty()**
```java
public boolean isEmpty() {
    return players.isEmpty();
}
```
ตรวจสอบว่าห้องว่างเปล่าหรือไม่ (ไม่มีผู้เล่นเหลือเลย)

---

### 2. **RoomManager.removePlayerFromRoom()**
```java
public boolean removePlayerFromRoom(String roomCode, String playerUuid) {
    Room room = rooms.get(roomCode);
    if (room != null) {
        room.removePlayer(playerUuid);
        
        // 🔥 AUTO-TERMINATE: If room is empty, delete it
        if (room.isEmpty()) {
            rooms.remove(roomCode);
            return true; // Room deleted
        }
        
        // If host left but room not empty, assign new host
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
```

**Logic:**
1. ลบผู้เล่นออกจากห้อง
2. ตรวจสอบว่าห้องว่างหรือไม่
3. ถ้าว่าง → **ลบห้องทันที** และ return `true`
4. ถ้ายังมีคนอยู่ → มอบหมาย host ใหม่ (ถ้า host ออก)

---

### 3. **RoomServiceImpl.leaveRoom()**
```java
@Override
public ApiResponse<RoomResponse> leaveRoom(String roomCode, String playerUuid) {
    // Remove player from room
    boolean roomDeleted = roomManager.removePlayerFromRoom(roomCode, playerUuid);
    
    if (roomDeleted) {
        // ✅ Room was deleted because it's empty
        return new ApiResponse<>(
            true, 
            "Left room successfully (room deleted - empty)", 
            null, 
            HttpStatus.OK
        );
    }
    
    // Room still exists, return updated room info
    RoomResponse response = buildRoomResponse(room);
    return new ApiResponse<>(
        true, 
        "Left room successfully", 
        response, 
        HttpStatus.OK
    );
}
```

---

## 🧪 Test Scenarios

### Scenario 1: Last Player Leaves (Room Deleted)
```bash
# Step 1: Create room (1 player - host)
POST /api/room/create
{
  "roomName": "Test Room",
  "maxPlayers": 4,
  "hostUuid": "host-123",
  "hostName": "Host"
}
Response: currentPlayers = 1

# Step 2: Host leaves (last player)
POST /api/room/leave?roomCode=ABC123&playerUuid=host-123
Response: {
  "success": true,
  "message": "Left room successfully (room deleted - empty)",
  "data": null  ← ห้องถูกลบแล้ว
}

# Step 3: Try to get room
GET /api/room/ABC123
Response: {
  "success": false,
  "message": "Room not found"  ← ห้องไม่มีอยู่แล้ว
}
```

---

### Scenario 2: Non-Last Player Leaves (Room Still Exists)
```bash
# Step 1: Create room
POST /api/room/create
Response: currentPlayers = 1 (host)

# Step 2: Player joins
POST /api/room/join
{
  "roomCode": "ABC123",
  "playerUuid": "player-1",
  "playerName": "Player1"
}
Response: currentPlayers = 2

# Step 3: Another player joins
POST /api/room/join
{
  "roomCode": "ABC123",
  "playerUuid": "player-2",
  "playerName": "Player2"
}
Response: currentPlayers = 3

# Step 4: Player 1 leaves (NOT last player)
POST /api/room/leave?roomCode=ABC123&playerUuid=player-1
Response: {
  "success": true,
  "message": "Left room successfully",
  "data": {
    "roomCode": "ABC123",
    "currentPlayers": 2,  ← ลดลง แต่ห้องยังอยู่
    ...
  }
}

# Step 5: Player 2 leaves
POST /api/room/leave?roomCode=ABC123&playerUuid=player-2
Response: currentPlayers = 1 (เหลือแค่ host)

# Step 6: Host leaves (LAST player)
POST /api/room/leave?roomCode=ABC123&playerUuid=host-123
Response: {
  "message": "Left room successfully (room deleted - empty)",
  "data": null  ← ห้องถูกลบ
}
```

---

### Scenario 3: Host Leaves But Not Last (Auto Host Transfer)
```bash
# Current: 3 players (host + player1 + player2)

# Host leaves
POST /api/room/leave?roomCode=ABC123&playerUuid=host-123
Response: {
  "success": true,
  "message": "Left room successfully",
  "data": {
    "roomCode": "ABC123",
    "currentPlayers": 2,
    "hostUuid": "player-1",  ← Host ใหม่
    "hostName": "Player1",
    ...
  }
}
```

---

## ✅ Features Summary

| Feature | Status | Description |
|---------|--------|-------------|
| **Auto-Delete Empty Room** | ✅ | ห้องถูกลบเมื่อไม่มีผู้เล่นเหลือ |
| **Auto Host Transfer** | ✅ | มอบหมาย host ใหม่เมื่อ host ออก (ถ้ามีคนอยู่) |
| **Player Counter** | ✅ | `currentPlayers` ลดลงเมื่อมีคนออก |
| **Room Not Found After Delete** | ✅ | ไม่สามารถหาห้องได้หลังถูกลบ |

---

## 🎯 Benefits

✅ **Memory Efficient** - ไม่เก็บห้องว่างเปล่าใน memory  
✅ **No Orphan Rooms** - ไม่มีห้องที่ไม่มีคนเหลือ  
✅ **Clean Up Automatically** - ไม่ต้อง manual cleanup  
✅ **Real-time** - ลบทันทีเมื่อผู้เล่นคนสุดท้ายออก  

---

## 📊 Flow Diagram

```
Player leaves room
       ↓
Remove player from Set<Player>
       ↓
Check: room.isEmpty()?
       ↓
   ┌───┴───┐
   Yes     No
   ↓       ↓
Delete   Check: Was it host?
Room        ↓
         ┌──┴──┐
        Yes    No
         ↓      ↓
    Assign  Return
   New Host  Updated
            Room Info
```

---

## 🔒 Edge Cases Handled

1. ✅ **Last player leaves** → Room deleted
2. ✅ **Host leaves (not last)** → New host assigned
3. ✅ **Regular player leaves** → Counter decreases
4. ✅ **Try to leave non-existent room** → Error: "Room not found"
5. ✅ **Try to get deleted room** → Error: "Room not found"

---

## 💡 WebSocket Integration (Future)

เมื่อใช้กับ WebSocket ในอนาคต:

```java
@EventListener
public void onWebSocketDisconnect(SessionDisconnectEvent event) {
    String sessionId = event.getSessionId();
    
    // Find room by player session
    Optional<Room> room = roomManager.getRoomBySessionId(sessionId);
    
    if (room.isPresent()) {
        Player player = findPlayerBySessionId(sessionId);
        boolean roomDeleted = roomManager.removePlayerFromRoom(
            room.get().getRoomCode(), 
            player.getUuid()
        );
        
        if (roomDeleted) {
            // ✅ Room auto-deleted because empty
            logger.info("Room {} terminated - all players left", 
                room.get().getRoomCode());
        } else {
            // Notify remaining players
            messagingTemplate.convertAndSend(
                "/topic/room/" + room.get().getRoomCode(),
                new PlayerLeftMessage(player)
            );
        }
    }
}
```

---

## 🎮 Testing Commands

```bash
# Test 1: Last player leaves
curl -X POST "http://localhost:8080/api/room/leave?roomCode=ABC123&playerUuid=host-123"

# Test 2: Check room after delete
curl -X GET "http://localhost:8080/api/room/ABC123"
# Expected: Room not found

# Test 3: Check available rooms
curl -X GET "http://localhost:8080/api/room/available"
# Room should not appear in list
```

