# Auto Game Finish and Room Reset Feature - Implementation Summary

## Overview
เพิ่มฟีเจอร์การจบเกมอัตโนมัติและรีเซ็ตห้องหลังจากคำนวณคะแนนเสร็จ โดยมีการทำงานดังนี้:

1. ✅ หลังจาก `finishGameWithScoring()` เสร็จสิ้น รอ 5 วินาที
2. ✅ เกมจะถูกเก็บเข้า history ของห้องนั้น (gamesByRoom)
3. ✅ ปรับสถานะห้องกลับเป็น WAITING
4. ✅ รีเซ็ตผู้เล่น: `isPlaying = false` และ `isReady = false`
5. ✅ เมื่อห้องถูกลบ history ของเกมจะถูกลบไปด้วย

## Changes Made

### 1. Created GameFinishService.java
**Path:** `/src/main/java/com/insidergame/insider_api/service/GameFinishService.java`

**Features:**
- ✅ `scheduleGameFinish(String roomCode)` - กำหนดเวลาจบเกมและรีเซ็ตห้องหลัง 5 วินาที
- ✅ `cancelScheduledFinish(String roomCode)` - ยกเลิกการจบเกมที่กำหนดไว้
- ✅ `finishAndResetRoom(String roomCode)` - จบเกม, เก็บเข้า history, และรีเซ็ตห้อง
- ✅ `broadcastRoomReset(String roomCode)` - ส่ง broadcast ว่าห้องถูกรีเซ็ตแล้ว

**Key Logic:**
```java
// After finishGameWithScoring completes:
gameFinishService.scheduleGameFinish(roomCode);

// After 5 seconds:
1. gameManager.finishAndArchiveGame(roomCode)  // Mark game as finished, keep in history
2. roomManager.updateRoomStatus(roomCode, WAITING)  // Reset room status
3. roomManager.resetPlayersAfterGame(roomCode)  // Reset player states
4. broadcastRoomReset(roomCode)  // Notify clients
```

### 2. Updated GameManager.java
**Path:** `/src/main/java/com/insidergame/insider_api/manager/GameManager.java`

**New Methods:**
```java
// Finish game and move to history (keeps in gamesByRoom list)
public Game finishAndArchiveGame(String roomCode)

// Clear all games for a room (called when room is deleted)
public void clearGamesForRoom(String roomCode)
```

**How Game History Works:**
- `gamesByRoom` - Map ที่เก็บ list ของเกมทั้งหมดสำหรับแต่ละห้อง (history)
- `activeGameByRoom` - Map ที่เก็บเกมที่กำลังเล่นอยู่
- เมื่อเกมจบ จะถูก remove จาก `activeGameByRoom` แต่ยังคงอยู่ใน `gamesByRoom`
- สามารถดู history ได้จาก `getGamesForRoom(roomCode)`

### 3. Updated RoomManager.java
**Path:** `/src/main/java/com/insidergame/insider_api/manager/RoomManager.java`

**New Method:**
```java
// Reset all players in room after game ends
public void resetPlayersAfterGame(String roomCode) {
    // Set isPlaying = false
    // Set isReady = false
}
```

### 4. Updated RoomServiceImpl.java
**Path:** `/src/main/java/com/insidergame/insider_api/api/room/RoomServiceImpl.java`

**Changes:**
- ✅ เพิ่ม `GameManager` injection
- ✅ อัพเดต `deleteRoom()` เพื่อลบ game history ด้วย

```java
// When room is deleted:
gameManager.clearGamesForRoom(roomCode);  // Clear game history
roomManager.deleteRoom(roomCode);  // Delete room
```

### 5. Updated RoomWebSocketController.java
**Path:** `/src/main/java/com/insidergame/insider_api/websocket/RoomWebSocketController.java`

**Changes:**
- ✅ เพิ่ม `GameFinishService` injection
- ✅ เรียก `scheduleGameFinish()` หลังจาก `finishGameWithScoring()` เสร็จ

```java
// After all players vote:
var finishResp = gameService.finishGameWithScoring(roomCode);
if (finishResp != null && finishResp.isSuccess()) {
    broadcastRoomUpdate(roomCode, "GAME_FINISHED_WITH_SCORING");
    gameFinishService.scheduleGameFinish(roomCode);  // Schedule finish after 5s
}
```

### 6. Updated RoomUpdateMessage.java
**Path:** `/src/main/java/com/insidergame/insider_api/dto/RoomUpdateMessage.java`

**Added Fields:**
```java
private String hostUuid;
private Game activeGame;
```

## Flow Diagram

### Complete Game Flow
```
1. All players vote
   ↓
2. finishGameWithScoring() - คำนวณคะแนน
   ↓
3. Broadcast "GAME_FINISHED_WITH_SCORING"
   ↓
4. scheduleGameFinish(roomCode)
   ↓
5. Wait 5 seconds...
   ↓
6. finishAndArchiveGame()
   - game.setFinished(true)
   - game.setWordRevealed(true)
   - Remove from activeGameByRoom
   - Keep in gamesByRoom (history)
   ↓
7. updateRoomStatus(WAITING)
   ↓
8. resetPlayersAfterGame()
   - isPlaying = false
   - isReady = false
   ↓
9. Broadcast "ROOM_RESET_AFTER_GAME"
   ↓
10. Room ready for next game!
```

### Room Deletion Flow
```
1. Host deletes room
   ↓
2. gameManager.clearGamesForRoom(roomCode)
   - Remove from activeGameByRoom
   - Remove from gamesByRoom (clear history)
   ↓
3. roomManager.deleteRoom(roomCode)
   ↓
4. All data cleared
```

## WebSocket Messages

### After Scoring Complete
```json
{
  "message": "GAME_FINISHED_WITH_SCORING",
  "roomCode": "ABC123",
  "activeGame": {
    "finished": false,
    "summary": {
      "scores": { "uuid1": 2, "uuid2": 1 },
      "voteTally": { "uuid1": 3, "uuid2": 2 },
      "insiderCaught": true
    }
  }
}
```

### After 5 Seconds - Room Reset
```json
{
  "message": "ROOM_RESET_AFTER_GAME",
  "roomCode": "ABC123",
  "status": "WAITING",
  "players": [
    {
      "uuid": "...",
      "playerName": "...",
      "isReady": false,
      "isPlaying": false,
      "isHost": true
    }
  ],
  "activeGame": null
}
```

## Frontend Integration

### Subscribe to Room Updates
```javascript
stompClient.subscribe(`/topic/room/${roomCode}`, (message) => {
  const data = JSON.parse(message.body);
  
  switch(data.message) {
    case 'GAME_FINISHED_WITH_SCORING':
      // Show scores and summary
      displayGameResults(data.activeGame.summary);
      // Show countdown: "Returning to lobby in 5 seconds..."
      startLobbyCountdown(5);
      break;
      
    case 'ROOM_RESET_AFTER_GAME':
      // Reset UI to waiting room
      // Clear game state
      // Show "Ready" button again
      resetToWaitingRoom(data);
      break;
  }
});
```

### Example UI Flow
```javascript
// After all votes cast:
function onGameFinished(summary) {
  // 1. Show results modal
  showGameResults(summary);
  
  // 2. Show countdown timer
  let countdown = 5;
  const timer = setInterval(() => {
    updateCountdownDisplay(countdown);
    countdown--;
    
    if (countdown < 0) {
      clearInterval(timer);
    }
  }, 1000);
  
  // 3. Wait for ROOM_RESET_AFTER_GAME message
  // (will be received automatically after 5 seconds)
}

function onRoomReset(data) {
  // Close results modal
  closeGameResults();
  
  // Reset to waiting room UI
  showWaitingRoom(data.players);
  
  // Enable ready button
  enableReadyButton();
}
```

## Testing

### Test Case 1: Normal Game Flow
1. ✅ สร้างห้องและเริ่มเกม
2. ✅ ผู้เล่นทุกคนโหวต
3. ✅ ตรวจสอบว่าได้รับ "GAME_FINISHED_WITH_SCORING"
4. ✅ รอ 5 วินาที
5. ✅ ตรวจสอบว่าได้รับ "ROOM_RESET_AFTER_GAME"
6. ✅ ตรวจสอบว่า room status = WAITING
7. ✅ ตรวจสอบว่าผู้เล่นทุกคน isReady = false, isPlaying = false
8. ✅ ตรวจสอบว่า activeGame = null
9. ✅ ตรวจสอบว่าเกมยังอยู่ใน history (call getGamesForRoom API)

### Test Case 2: Game History
1. ✅ เล่นเกมหลายรอบ
2. ✅ ตรวจสอบว่า history มีเกมเพิ่มขึ้นตาม
3. ✅ Call API: GET /api/rooms/{roomCode}/games
4. ✅ ตรวจสอบว่าเกมทั้งหมดถูกเก็บไว้

### Test Case 3: Room Deletion
1. ✅ เล่นเกม 2-3 รอบ
2. ✅ Host ลบห้อง
3. ✅ ตรวจสอบว่า game history ถูกลบไปด้วย
4. ✅ Call API: GET /api/rooms/{roomCode}/games (ควรได้ 404 หรือ empty)

### Test Case 4: Cancel Scenario
1. ✅ เล่นเกมจนจบ (after scoring)
2. ✅ ก่อนครบ 5 วินาที มีคนออกจากห้อง
3. ✅ ตรวจสอบว่า room reset ยังคงทำงาน

## API Endpoints

### Get Game History
```
GET /api/rooms/{roomCode}/games
Response: List<Game>
```

### Get Active Game
```
WS: /app/room/{roomCode}/active_game
Response: Game (or null if no active game)
```

## Configuration

### Scheduler Settings
- **Check interval:** Every 1 second (for GameTimerService)
- **Finish delay:** 5 seconds after scoring complete
- **Thread pool:** 5 threads (configurable in GameFinishService)

## Notes
- ✅ Game history เก็บใน memory (gamesByRoom Map)
- ✅ เมื่อ server restart history จะหายไป (ถ้าต้องการเก็บถาวรต้องใช้ database)
- ✅ สามารถ cancel scheduled finish ได้โดยเรียก `cancelScheduledFinish(roomCode)`
- ✅ Room reset ไม่ kick ผู้เล่นออก แค่รีเซ็ตสถานะ
- ✅ ผู้เล่นสามารถกด ready ใหม่เพื่อเริ่มเกมรอบต่อไป

## Related Files
- `GameFinishService.java` - Auto-finish logic
- `GameManager.java` - Game lifecycle management
- `RoomManager.java` - Room and player state management
- `RoomServiceImpl.java` - Room deletion with history cleanup
- `RoomWebSocketController.java` - WebSocket event handling
- `GameTimerService.java` - Auto word reveal (existing feature)

## Future Enhancements
- [ ] เพิ่ม config สำหรับ delay time (ตอนนี้ hard-code 5 วินาที)
- [ ] เพิ่ม API สำหรับดู game history
- [ ] เก็บ game history ลง database เพื่อความถาวร
- [ ] เพิ่ม pagination สำหรับ game history
- [ ] เพิ่ม statistics (win rate, average score, etc.)

