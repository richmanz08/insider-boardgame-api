# Room Management System - In-Memory Design

## 🎯 Architecture Overview

ระบบห้องใช้ **In-Memory Storage** แทน Database เพราะ:

1. **Real-time game** - ห้องมีอายุสั้น ไม่ต้อง persist
2. **Auto cleanup** - ถ้า player ออกหมด ห้องจะถูกลบทันที
3. **WebSocket based** - เหมาะกับ real-time multiplayer
4. **Performance** - เข้าถึงข้อมูลเร็วกว่า database query

---

## 📦 Components

### 1. Room Model (`model/Room.java`)
```java
- roomCode: String (6 characters)
- roomName: String
- maxPlayers: Integer (2-12)
- password: String (optional)
- status: String (WAITING, PLAYING, FINISHED)
- hostUuid: String
- hostName: String
- players: Set<Player>
- createdAt: LocalDateTime
```

**Methods:**
- `getCurrentPlayers()` - จำนวนผู้เล่นปัจจุบัน
- `isFull()` - ตรวจสอบว่าห้องเต็มหรือไม่
- `hasPassword()` - ตรวจสอบว่ามีรหัสผ่านหรือไม่
- `addPlayer(Player)` - เพิ่มผู้เล่น
- `removePlayer(uuid)` - ลบผู้เล่น
- `isEmpty()` - ตรวจสอบว่าห้องว่างหรือไม่

### 2. Player Model (`model/Player.java`)
```java
- uuid: String
- playerName: String
- sessionId: String (WebSocket session)
- joinedAt: LocalDateTime
- isHost: boolean
```

### 3. RoomManager (`manager/RoomManager.java`)

**In-Memory Storage:**
```java
private final Map<String, Room> rooms = new ConcurrentHashMap<>();
```

**Key Methods:**

#### Create Room
```java
Room createRoom(String roomCode, String roomName, Integer maxPlayers, 
                String password, String hostUuid, String hostName)
```
- สร้างห้องใหม่
- เพิ่ม host เป็น player แรก
- เก็บใน ConcurrentHashMap

#### Add Player
```java
boolean addPlayerToRoom(String roomCode, Player player)
```
- เพิ่ม player เข้าห้อง
- ตรวจสอบห้องเต็มหรือไม่

#### Remove Player
```java
boolean removePlayerFromRoom(String roomCode, String playerUuid)
```
- ลบ player ออกจากห้อง
- **Auto cleanup:** ถ้าห้องว่าง → ลบห้องทันที
- **Auto host transfer:** ถ้า host ออก → มอบหมาย host ใหม่

#### Get Available Rooms
```java
List<Room> getAvailableRooms()
```
- คืนห้องที่สถานะ WAITING
- และยังไม่เต็ม

#### Get Room by Player
```java
Optional<Room> getRoomByPlayerUuid(String playerUuid)
```
- หาห้องที่ player อยู่

---

## 🔄 Room Lifecycle

```
1. Create Room
   ↓
2. Players Join (status: WAITING)
   ↓
3. Game Starts (status: PLAYING)
   ↓
4. Game Ends (status: FINISHED)
   ↓
5. Players Leave
   ↓
6. Room Auto-Deleted (when empty)
```

---

## 🎮 Key Features

### ✅ Auto Cleanup
```java
// When player leaves
room.removePlayer(playerUuid);

// If room is empty → delete automatically
if (room.isEmpty()) {
    rooms.remove(roomCode);
}
```

### ✅ Auto Host Transfer
```java
// If host leaves but room not empty
if (playerUuid.equals(room.getHostUuid()) && !room.isEmpty()) {
    Player newHost = room.getPlayers().iterator().next();
    newHost.setHost(true);
    room.setHostUuid(newHost.getUuid());
}
```

### ✅ Thread-Safe
- ใช้ `ConcurrentHashMap` สำหรับ multi-threaded access
- เหมาะกับ WebSocket connections ที่เกิดพร้อมกัน

---

## 🔌 Integration with WebSocket

### WebSocket Events

#### On Player Join
```java
@MessageMapping("/room/join")
public void onPlayerJoin(JoinRoomMessage message, StompHeaderAccessor headers) {
    String sessionId = headers.getSessionId();
    
    Player player = Player.builder()
            .uuid(message.getPlayerUuid())
            .playerName(message.getPlayerName())
            .sessionId(sessionId)
            .joinedAt(LocalDateTime.now())
            .build();
    
    roomManager.addPlayerToRoom(message.getRoomCode(), player);
    
    // Broadcast to all players in room
    messagingTemplate.convertAndSend(
        "/topic/room/" + message.getRoomCode(), 
        new RoomUpdateMessage(room)
    );
}
```

#### On Player Leave / Disconnect
```java
@EventListener
public void onWebSocketDisconnect(SessionDisconnectEvent event) {
    String sessionId = event.getSessionId();
    
    // Find room by player session
    Optional<Room> room = findRoomBySessionId(sessionId);
    
    if (room.isPresent()) {
        Player player = findPlayerBySessionId(sessionId);
        boolean roomDeleted = roomManager.removePlayerFromRoom(
            room.get().getRoomCode(), 
            player.getUuid()
        );
        
        if (roomDeleted) {
            // Room was deleted because it's empty
            logger.info("Room {} deleted - all players left", room.get().getRoomCode());
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

## 🧪 Usage Example

### 1. Create Room (REST API)
```bash
POST /api/room/create
{
  "roomName": "My Game",
  "maxPlayers": 8,
  "password": "secret",
  "hostUuid": "xxx",
  "hostName": "Alice"
}
```

### 2. WebSocket Join
```javascript
stompClient.subscribe('/topic/room/' + roomCode, function(message) {
    // Receive room updates
});

stompClient.send('/app/room/join', {}, JSON.stringify({
    roomCode: roomCode,
    playerUuid: uuid,
    playerName: name
}));
```

### 3. Auto Cleanup on Disconnect
```javascript
// When player closes browser/tab
window.addEventListener('beforeunload', () => {
    stompClient.disconnect(); // Auto triggers onWebSocketDisconnect
});
```

---

## 💡 Advantages

✅ **No Database** - ไม่ต้อง query/update ตลอดเวลา  
✅ **Fast** - อ่านเขียนข้อมูลเร็วมาก  
✅ **Auto Cleanup** - ห้องจะถูกลบเมื่อไม่มีคนเหลือ  
✅ **Thread-Safe** - ใช้ ConcurrentHashMap  
✅ **Simple** - เหมาะกับ session-based game  
✅ **Real-time Ready** - พร้อมใช้กับ WebSocket  

---

## ⚠️ Limitations

❌ **Data Loss on Restart** - ถ้า server restart ห้องทั้งหมดหาย  
❌ **Single Instance** - ไม่สามารถ scale แนวนอนได้ (แก้ด้วย Redis ถ้าต้องการ)  
❌ **No History** - ไม่เก็บประวัติการเล่น  

**Solution for Production:**
- ใช้ Redis สำหรับ shared in-memory storage
- เก็บ game history ลง database หลังเกมจบ

---

## 📊 Monitoring

```java
// Get statistics
int totalRooms = roomManager.getTotalRooms();
List<Room> allRooms = roomManager.getAllRooms();

// Can add metrics
@Scheduled(fixedRate = 60000)
public void logRoomStats() {
    logger.info("Active rooms: {}", roomManager.getTotalRooms());
}
```

