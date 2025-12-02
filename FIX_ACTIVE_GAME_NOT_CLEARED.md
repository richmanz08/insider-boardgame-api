# Fix: ActiveGame Not Cleared After Game Finish

## ปัญหา
หลังจากเกมจบและทำ `GAME_FINISHED_WITH_SCORING` แล้ว client ยังคงมี `activeGame` state อยู่ ไม่เป็น `null` ทำให้ต้อง refresh หน้าเว็บ

## สาเหตุ
1. Backend ทำการ archive game และ reset room หลังจาก 5 วินาที
2. Backend broadcast `ROOM_RESET_AFTER_GAME` ไปยัง clients
3. Clients รับ message และ request `/active_game` อีกครั้ง
4. Backend response `{ game: null }` กลับไป
5. **แต่ client ไม่ได้ clear `activeGame` state** เพราะ logic ใน `useRoomWebSocket` มีปัญหา

## การแก้ไข

### 1. Backend: GameFinishService.java
เพิ่ม method `sendNullGameToAllPlayers()` เพื่อส่ง `{ game: null }` ไปยัง session ของ player ทุกคนหลังจาก room reset:

```java
private void broadcastRoomReset(String roomCode) {
    // ...existing code...
    
    messagingTemplate.convertAndSend("/topic/room/" + roomCode, msg);
    
    // ⭐ Send null game to all players' sessions to clear their activeGame state
    sendNullGameToAllPlayers(roomCode, room);
}

private void sendNullGameToAllPlayers(String roomCode, Room room) {
    Map<String, Object> nullGamePayload = new HashMap<>();
    nullGamePayload.put("game", null);
    
    for (var player : room.getPlayers()) {
        if (player.getSessionId() != null) {
            SimpMessageHeaderAccessor sha = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            sha.setSessionId(player.getSessionId());
            sha.setLeaveMutable(true);
            
            messagingTemplate.convertAndSendToUser(
                player.getSessionId(), 
                "/queue/active_game", 
                nullGamePayload, 
                sha.getMessageHeaders()
            );
        }
    }
}
```

### 2. Frontend: useRoomWebSocket.ts (แนะนำให้แก้ด้วย)
แก้ logic การ handle `active_game` response ให้รองรับ `null` อย่างถูกต้อง:

```typescript
// ❌ เดิม - ไม่ handle null ได้ดี
client.subscribe("/user/queue/active_game", (message: IMessage) => {
  const payload = JSON.parse(message.body);
  const game = payload && payload.game ? payload.game : payload;
  if (isUndefined(game?.game)) {
    setActiveGame(game);  // ❌ ถ้า game = null จะไม่ทำงาน!
  }
});

// ✅ แก้ไข - handle null ได้ถูกต้อง
client.subscribe("/user/queue/active_game", (message: IMessage) => {
  const payload = JSON.parse(message.body);
  const game = payload && payload.game !== undefined ? payload.game : payload;
  console.log("🎮 active_game (user queue) received:", game);
  
  // Set activeGame ไม่ว่า game จะเป็น object หรือ null
  setActiveGame(game);
});
```

### 3. การทำงานหลังแก้ไข

**Flow หลังจบเกม:**
```
1. All players vote → finishGameWithScoring()
2. Broadcast "GAME_FINISHED_WITH_SCORING"
3. Schedule game finish in 5 seconds
4. (After 5 seconds)
5. finishAndArchiveGame() → game moved to history
6. resetPlayersAfterGame() → all players: isPlaying=false, isReady=false
7. broadcastRoomReset()
   ├─ Broadcast "ROOM_RESET_AFTER_GAME" to /topic/room/{roomCode}
   └─ ⭐ Send { game: null } to /user/{sessionId}/queue/active_game for each player
8. Client receives null game → setActiveGame(null) ✅
9. UI updates: activeGame is null → show waiting screen
```

## ผลลัพธ์
- ✅ Client ไม่ต้อง refresh หน้าเว็บหลังเกมจบ
- ✅ `activeGame` state จะเป็น `null` อัตโนมัติ
- ✅ UI จะกลับไปที่หน้า waiting room ทันที
- ✅ Players สามารถเริ่มเกมใหม่ได้ทันที

## Testing
1. เริ่มเกมใหม่และเล่นจนจบ
2. ตรวจสอบว่า client ได้รับ message:
   - `GAME_FINISHED_WITH_SCORING`
   - `ROOM_RESET_AFTER_GAME`
   - `{ game: null }` ใน `/user/queue/active_game`
3. ตรวจสอบว่า `activeGame` state เป็น `null`
4. ตรวจสอบว่า UI แสดงหน้า waiting room
5. ลองเริ่มเกมใหม่ โดยไม่ต้อง refresh

## Files Changed
- `src/main/java/com/insidergame/insider_api/api/game/GameFinishService.java`
  - เพิ่ม `sendNullGameToAllPlayers()` method
  - แก้ `broadcastRoomReset()` เพื่อเรียก send null game
  
## Related Files (แนะนำให้แก้)
- `hooks/useRoomWebSocket.ts`
  - แก้ logic subscription `/user/queue/active_game`
  - ให้ handle `null` game อย่างถูกต้อง

