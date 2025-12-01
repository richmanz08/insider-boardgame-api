# Implementation Summary - Auto Game Finish & Word Reveal

## ✅ Completed Features

### 1. Auto Word Reveal (AUTO_WORD_REVEAL.md)
เมื่อเวลาเกมหมด (time now > endsAt) จะเปิดเผยคำตอบให้ผู้เล่นทุกคนเห็นอัตโนมัติ

**Files Created:**
- `GameTimerService.java` - ตรวจสอบเกมที่หมดเวลาทุก 1 วินาที

**Files Modified:**
- `InsiderApiApplication.java` - เพิ่ม @EnableScheduling
- `RoomUpdateMessage.java` - เพิ่ม hostUuid และ activeGame fields

**Key Features:**
- ✅ ตรวจสอบอัตโนมัติทุก 1 วินาที
- ✅ เมื่อ now > endsAt จะตั้ง wordRevealed = true
- ✅ ส่ง broadcast และ private message ให้ทุกคน
- ✅ ป้องกันการส่งซ้ำ

### 2. Auto Game Finish & Room Reset (AUTO_GAME_FINISH.md)
หลังจากคำนวณคะแนนเสร็จ รอ 5 วินาที แล้วจบเกมและรีเซ็ตห้อง

**Files Created:**
- `GameFinishService.java` - จัดการการจบเกมและรีเซ็ตห้อง

**Files Modified:**
- `GameManager.java` - เพิ่ม finishAndArchiveGame() และ clearGamesForRoom()
- `RoomManager.java` - เพิ่ม resetPlayersAfterGame()
- `RoomServiceImpl.java` - ลบ game history เมื่อห้องถูกลบ
- `RoomWebSocketController.java` - เรียก scheduleGameFinish() หลัง scoring

**Key Features:**
- ✅ รอ 5 วินาทีหลังคำนวณคะแนน
- ✅ เกมถูกเก็บเข้า history (gamesByRoom)
- ✅ รีเซ็ต room status เป็น WAITING
- ✅ รีเซ็ตผู้เล่น: isPlaying = false, isReady = false
- ✅ เมื่อลบห้อง history จะถูกลบด้วย

## File Structure

```
src/main/java/com/insidergame/insider_api/
├── service/
│   ├── GameTimerService.java       [NEW] - Auto word reveal
│   └── GameFinishService.java      [NEW] - Auto game finish & reset
├── manager/
│   ├── GameManager.java            [MODIFIED] - Added history methods
│   └── RoomManager.java            [MODIFIED] - Added reset method
├── api/
│   └── room/
│       └── RoomServiceImpl.java    [MODIFIED] - Clear history on delete
├── websocket/
│   └── RoomWebSocketController.java [MODIFIED] - Schedule game finish
├── dto/
│   └── RoomUpdateMessage.java      [MODIFIED] - Added fields
└── InsiderApiApplication.java      [MODIFIED] - Enable scheduling
```

## WebSocket Messages

### Message Types
1. `WORD_REVEALED` - เมื่อคำตอบถูกเปิดเผย (time expired)
2. `GAME_FINISHED_WITH_SCORING` - เมื่อคำนวณคะแนนเสร็จ
3. `ROOM_RESET_AFTER_GAME` - เมื่อห้องถูกรีเซ็ต (หลัง 5 วินาที)

## Timeline Flow

```
Game Start
    ↓
All cards opened
    ↓
Timer starts (60 seconds)
    ↓
[GameTimerService checks every 1s]
    ↓
Time expires (now > endsAt)
    ↓
🎯 WORD_REVEALED - คำตอบเปิดเผยให้ทุกคน
    ↓
Players discuss and vote
    ↓
All votes cast
    ↓
🎯 GAME_FINISHED_WITH_SCORING - แสดงคะแนน
    ↓
[Wait 5 seconds]
    ↓
🎯 ROOM_RESET_AFTER_GAME - กลับสู่ห้องรอ
    ↓
Ready for next game!
```

## Testing Checklist

### Auto Word Reveal
- [ ] เริ่มเกมและรอให้ timer หมด
- [ ] ตรวจสอบว่าคำตอบแสดงให้ CITIZEN เห็น
- [ ] ตรวจสอบ broadcast message "WORD_REVEALED"
- [ ] ตรวจสอบ private message มี word

### Auto Game Finish
- [ ] เล่นเกมจนครบทุกโหวต
- [ ] ตรวจสอบ "GAME_FINISHED_WITH_SCORING"
- [ ] รอ 5 วินาที
- [ ] ตรวจสอบ "ROOM_RESET_AFTER_GAME"
- [ ] ตรวจสอบ room status = WAITING
- [ ] ตรวจสอบ isReady = false, isPlaying = false

### Game History
- [ ] เล่นหลายรอบ
- [ ] ตรวจสอบว่า history เพิ่มขึ้น
- [ ] ลบห้อง
- [ ] ตรวจสอบว่า history ถูกลบด้วย

## Configuration

### Timers
- Word reveal check: **Every 1 second**
- Game finish delay: **5 seconds after scoring**
- Thread pool size: **5 threads**

### Changeable Values
To change timers, modify these files:
- `GameTimerService.java` - Line 40: `@Scheduled(fixedRate = 1000)` 
- `GameFinishService.java` - Line 46: `5, TimeUnit.SECONDS`

## Dependencies
- Spring Boot Scheduling (`@EnableScheduling`)
- WebSocket (STOMP)
- Lombok

## Build & Run

```bash
# Compile
./mvnw clean compile

# Run
./mvnw spring-boot:run

# Or with Docker
docker-compose up --build
```

## Status: ✅ READY FOR TESTING

All features are implemented and ready for integration testing!

