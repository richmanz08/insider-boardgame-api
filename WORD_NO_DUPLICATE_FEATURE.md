# Random Word Without Duplication Feature

## สรุป
ระบบจะไม่ random คำซ้ำกับคำที่เคยใช้ในห้องนั้นๆ แล้ว จนกว่าคำทั้งหมดจะถูกใช้หมด แล้วค่อย reset และเริ่มใช้ใหม่

## การทำงาน

### Before (เดิม)
```java
// Pick random word - อาจซ้ำกับเกมก่อนหน้า
CategoryEntity pick = categories.get(new Random().nextInt(categories.size()));
String word = pick.getCategoryName();
```

### After (แก้ไขแล้ว)
```java
// 1. ดึงประวัติเกมในห้องนี้
List<Game> previousGames = gameManager.getGamesForRoom(roomCode);

// 2. เก็บ Set ของคำที่เคยใช้แล้ว
Set<String> usedWords = previousGames.stream()
    .map(Game::getWord)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());

// 3. กรองเอาเฉพาะคำที่ยังไม่เคยใช้
List<CategoryEntity> availableCategories = categories.stream()
    .filter(cat -> !usedWords.contains(cat.getCategoryName()))
    .collect(Collectors.toList());

// 4. ถ้าคำหมดแล้ว (ใช้ครบทุกคำ) → reset ให้ใช้ได้ทั้งหมด
if (availableCategories.isEmpty()) {
    availableCategories = new ArrayList<>(categories);
}

// 5. Random จากคำที่พร้อมใช้งาน
CategoryEntity pick = availableCategories.get(new Random().nextInt(availableCategories.size()));
String word = pick.getCategoryName();
```

## Logic Flow

```
┌─────────────────────────────────────────┐
│  Start Game in Room ABCD                │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Get all categories (total: 100 words)  │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Get game history for room ABCD         │
│  - Game 1: word = "แมว"                 │
│  - Game 2: word = "หมา"                 │
│  - Game 3: word = "ปลา"                 │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Create usedWords Set:                  │
│  { "แมว", "หมา", "ปลา" }              │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Filter available categories            │
│  100 - 3 = 97 words available           │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  Random from 97 available words         │
│  Selected: "นก" (not used before)       │
└─────────────────────────────────────────┘
```

## Edge Cases

### Case 1: ห้องใหม่ (ไม่มีประวัติ)
```
previousGames = []
usedWords = {}
availableCategories = all categories (100 words)
→ Random ได้ทั้ง 100 คำ
```

### Case 2: เล่นไปแล้ว 50 เกม
```
previousGames = [Game1, Game2, ..., Game50]
usedWords = {50 unique words}
availableCategories = 100 - 50 = 50 words
→ Random ได้เฉพาะ 50 คำที่ยังไม่ใช้
```

### Case 3: ใช้คำหมดแล้ว (100 เกม)
```
previousGames = [Game1, Game2, ..., Game100]
usedWords = {100 words} (all used)
availableCategories = [] (empty!)

→ Reset: availableCategories = all categories (100 words)
→ Random ได้ทั้ง 100 คำอีกครั้ง (เริ่มรอบใหม่)
```

### Case 4: คำซ้ำในประวัติ (แต่ละเกมอาจมีคำเดิมหลายครั้ง)
```
previousGames:
  - Game 1: word = "แมว"
  - Game 2: word = "หมา"
  - Game 3: word = "แมว"  ← ซ้ำกับ Game 1
  
usedWords = {"แมว", "หมา"}  ← Set ทำให้ไม่ซ้ำ
→ Count เป็น 2 คำที่ใช้แล้ว (ไม่ใช่ 3)
```

## Benefits

### ✅ ไม่ซ้ำคำในห้องเดียวกัน
- ผู้เล่นจะได้เล่นคำใหม่ๆ ในแต่ละเกม
- ไม่น่าเบื่อ ไม่ซ้ำซาก

### ✅ Reset อัตโนมัติ
- เมื่อใช้คำหมดแล้ว จะ reset ให้เล่นได้ใหม่
- ไม่ต้อง manual reset

### ✅ แยกตามห้อง
- ห้อง A ใช้คำ "แมว" → ห้อง B ยังใช้ "แมว" ได้
- ประวัติแยกกันตามห้อง

### ✅ Performance ดี
- ใช้ `Set` สำหรับ lookup → O(1)
- Filter ด้วย Stream → efficient

## Example Scenarios

### Scenario 1: เริ่มเล่นครั้งแรก
```
Room: TEST123
Total categories: 100
Previous games: 0

→ Random from all 100 words
→ Selected: "แมว"
```

### Scenario 2: เล่นเกมที่ 2
```
Room: TEST123
Total categories: 100
Previous games: 1
  - Game 1: "แมว"

→ Exclude: "แมว"
→ Available: 99 words
→ Random from 99 words
→ Selected: "หมา"
```

### Scenario 3: เล่นเกมที่ 101 (ใช้ครบแล้ว)
```
Room: TEST123
Total categories: 100
Previous games: 100
  - Used all 100 words

→ Available: 0 words (empty!)
→ Reset to all 100 words
→ Random from 100 words
→ Selected: "แมว" (can reuse now)
```

## Code Changes

### Modified File
`src/main/java/com/insidergame/insider_api/api/game/GameServiceImpl.java`

#### Changes:
1. **Line 47-71**: Added logic to:
   - Get game history for room
   - Build set of used words
   - Filter available categories
   - Reset if all words used
   - Random from available words

## Testing

### Test Case 1: New Room
```bash
# Setup: 10 words in database
# Room: NEW001 (no history)

POST /api/room/NEW001/start
→ Should get random word from all 10 words
→ e.g., "แมว"
```

### Test Case 2: Play Multiple Games
```bash
# Play 5 games in same room
POST /api/room/NEW001/start  # Game 1 → "แมว"
POST /api/room/NEW001/start  # Game 2 → "หมา" (not "แมว")
POST /api/room/NEW001/start  # Game 3 → "ปลา" (not "แมว" or "หมา")
POST /api/room/NEW001/start  # Game 4 → "นก"
POST /api/room/NEW001/start  # Game 5 → "ช้าง"

# Check history
GET /api/game/NEW001/history
→ Should show 5 games with 5 different words
```

### Test Case 3: Use All Words (Reset Test)
```bash
# Setup: Only 3 words in database
# Play 4 games

POST /api/room/NEW001/start  # Game 1 → "แมว"
POST /api/room/NEW001/start  # Game 2 → "หมา"
POST /api/room/NEW001/start  # Game 3 → "ปลา"
POST /api/room/NEW001/start  # Game 4 → "แมว" or "หมา" or "ปลา" (reset!)

# Should allow reusing words after all are used
```

### Test Case 4: Different Rooms (Isolation Test)
```bash
# Room A and Room B should have independent histories

POST /api/room/ROOM_A/start  # Game 1 → "แมว"
POST /api/room/ROOM_B/start  # Game 1 → "แมว" (OK! Different room)

POST /api/room/ROOM_A/start  # Game 2 → "หมา" (not "แมว")
POST /api/room/ROOM_B/start  # Game 2 → "หมา" (OK! Different room)
```

## Database Requirements

ระบบนี้ทำงานกับข้อมูลที่มีอยู่แล้ว ไม่ต้องเพิ่ม table หรือ field ใหม่:

- ✅ ใช้ `GameManager.getGamesForRoom()` - มีอยู่แล้ว
- ✅ ใช้ `Game.getWord()` - มีอยู่แล้ว
- ✅ ใช้ `CategoryEntity.getCategoryName()` - มีอยู่แล้ว

## Performance Considerations

### Memory Usage
- `Set<String> usedWords` - O(n) where n = number of games played
- ถ้าเล่นไป 1000 เกม → usedWords จะมี ~1000 entries
- แต่ละ entry เป็น String (คำ) → ประมาณ 50 bytes/คำ
- **Total**: ~50KB สำหรับ 1000 เกม → **ยอมรับได้**

### Time Complexity
- Get game history: O(n)
- Build usedWords Set: O(n)
- Filter categories: O(m) where m = total categories
- Random selection: O(1)
- **Total**: O(n + m) → **Very fast**

### Optimization (ถ้าจำเป็น)
ถ้าห้องมีเกมเยอะมาก (10,000+ games):
```java
// Cache usedWords in RoomManager
private final Map<String, Set<String>> cachedUsedWords = new ConcurrentHashMap<>();

// Clear cache when game finishes
public void onGameFinished(String roomCode, String word) {
    cachedUsedWords.computeIfAbsent(roomCode, k -> new HashSet<>()).add(word);
}
```

## Summary

✅ **เสร็จแล้ว**: ระบบ random คำไม่ซ้ำในห้องเดียวกัน
✅ **Auto-reset**: เมื่อใช้คำครบจะ reset อัตโนมัติ
✅ **แยกตามห้อง**: แต่ละห้องมีประวัติของตัวเอง
✅ **Performance ดี**: ใช้ Set และ Stream efficiently
✅ **No DB changes**: ใช้ structure ที่มีอยู่แล้ว

**File Changed:**
- `src/main/java/com/insidergame/insider_api/api/game/GameServiceImpl.java`

