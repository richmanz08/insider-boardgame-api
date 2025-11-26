# แก้ไขปัญหาผู้เล่นซ้ำในห้อง (Fix Duplicate Player in Room)

## ปัญหาที่พบ (Problem Found)
ระบบมีปัญหาผู้เล่นซ้ำในห้อง โดยเฉพาะผู้สร้างห้อง (host) เนื่องจาก:

1. เมื่อสร้างห้อง (`createRoom`) ระบบจะเพิ่ม host เป็นผู้เล่นคนแรกในห้องทันที
2. หาก frontend เรียก `joinRoom` อีกครั้งด้วย UUID เดียวกัน จะทำให้ host ซ้ำในห้อง
3. `Player` model ไม่มี `equals()` และ `hashCode()` ที่ override มา ทำให้ `HashSet` ไม่สามารถตรวจสอบผู้เล่นซ้ำได้

## การแก้ไข (Solutions Applied)

### 1. เพิ่ม equals() และ hashCode() ใน Player model
**ไฟล์:** `src/main/java/com/insidergame/insider_api/model/Player.java`

เพิ่ม methods สำหรับเปรียบเทียบผู้เล่นโดยใช้ UUID:

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Player player = (Player) o;
    return Objects.equals(uuid, player.uuid);
}

@Override
public int hashCode() {
    return Objects.hash(uuid);
}
```

### 2. เพิ่มการตรวจสอบผู้เล่นซ้ำใน RoomManager
**ไฟล์:** `src/main/java/com/insidergame/insider_api/manager/RoomManager.java`

#### 2.1 ปรับปรุง `addPlayerToRoom()` method
เพิ่มการเช็คว่าผู้เล่นอยู่ในห้องแล้วหรือไม่ก่อนเพิ่ม:

```java
public boolean addPlayerToRoom(String roomCode, Player player) {
    Room room = rooms.get(roomCode);
    if (room != null && !room.isFull() && "WAITING".equals(room.getStatus())) {
        // Check if player already exists in the room (by UUID)
        boolean playerExists = room.getPlayers().stream()
                .anyMatch(p -> p.getUuid().equals(player.getUuid()));
        
        if (playerExists) {
            return false; // Player already in room, don't add again
        }
        
        room.addPlayer(player);
        return true;
    }
    return false;
}
```

#### 2.2 เพิ่ม helper method `isPlayerInRoom()`
เพิ่ม method สำหรับเช็คว่าผู้เล่นอยู่ในห้องแล้วหรือไม่:

```java
public boolean isPlayerInRoom(String roomCode, String playerUuid) {
    Room room = rooms.get(roomCode);
    if (room == null) {
        return false;
    }
    return room.getPlayers().stream()
            .anyMatch(player -> player.getUuid().equals(playerUuid));
}
```

### 3. ปรับปรุง joinRoom() ใน RoomServiceImpl
**ไฟล์:** `src/main/java/com/insidergame/insider_api/api/room/RoomServiceImpl.java`

เพิ่มการเช็คผู้เล่นซ้ำก่อน join ห้อง:

```java
// Check if player is already in the room
if (roomManager.isPlayerInRoom(request.getRoomCode(), request.getPlayerUuid())) {
    // Player already in room, just return current room state
    RoomResponse response = buildRoomResponse(room);
    return new ApiResponse<>(true, "Player already in room", response, HttpStatus.OK);
}
```

และเช็คผลลัพธ์จาก `addPlayerToRoom()`:

```java
boolean added = roomManager.addPlayerToRoom(request.getRoomCode(), player);

if (!added) {
    return new ApiResponse<>(false, "Failed to add player to room", null, HttpStatus.CONFLICT);
}
```

## ผลลัพธ์ (Results)

✅ ป้องกันผู้เล่นซ้ำในห้อง  
✅ Host ไม่ถูกเพิ่มซ้ำเมื่อเรียก `joinRoom`  
✅ `HashSet` สามารถตรวจจับผู้เล่นซ้ำได้ด้วย `equals()` และ `hashCode()`  
✅ API response ชัดเจนเมื่อผู้เล่นพยายาม join ห้องที่อยู่แล้ว  

## การทดสอบ (Testing)

### Test Case 1: สร้างห้องและ join ด้วย UUID เดียวกัน
1. สร้างห้องด้วย hostUuid = "abc-123"
2. เรียก joinRoom ด้วย playerUuid = "abc-123"
3. **ผลลัพธ์:** ระบบตอบกลับว่า "Player already in room" และไม่เพิ่มผู้เล่นซ้ำ

### Test Case 2: ผู้เล่นใหม่ join ห้อง
1. สร้างห้องด้วย hostUuid = "abc-123"
2. เรียก joinRoom ด้วย playerUuid = "xyz-456"
3. **ผลลัพธ์:** ระบบเพิ่มผู้เล่นใหม่สำเร็จ, currentPlayers = 2

### Test Case 3: ผู้เล่นพยายาม join ห้องที่เต็มแล้ว
1. สร้างห้อง maxPlayers = 2
2. เพิ่มผู้เล่น 2 คน
3. ผู้เล่นคนที่ 3 พยายาม join
4. **ผลลัพธ์:** ระบบตอบกลับว่า "Room is full"

## สรุป (Summary)

การแก้ไขนี้ช่วยแก้ปัญหาผู้เล่นซ้ำในห้องได้อย่างสมบูรณ์ โดยเพิ่มการตรวจสอบหลายชั้น:
1. **Level 1:** HashSet ใช้ `equals()` และ `hashCode()` ตรวจจับผู้เล่นซ้ำ
2. **Level 2:** `addPlayerToRoom()` เช็คก่อนเพิ่มผู้เล่น
3. **Level 3:** `joinRoom()` API เช็คก่อนพยายามเพิ่มผู้เล่น

Date: 2025-11-26

