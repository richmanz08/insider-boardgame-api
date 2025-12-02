# Game History Feature

## สรุป
ระบบ **มีการเก็บประวัติเกมทุกเกมของแต่ละห้องแล้ว** ผ่าน `GameManager` และมี API endpoint สำหรับดึงข้อมูลประวัติออกมาแล้ว

## โครงสร้างการเก็บประวัติ

### GameManager
```java
// In-memory storage
private final Map<String, List<Game>> gamesByRoom = new ConcurrentHashMap<>();
private final Map<String, Game> activeGameByRoom = new ConcurrentHashMap<>();
```

- **`gamesByRoom`**: เก็บประวัติเกม**ทั้งหมด** (active + archived) ของแต่ละห้อง
- **`activeGameByRoom`**: เก็บเกมที่กำลังเล่นอยู่ในปัจจุบัน

### การทำงาน

1. **สร้างเกมใหม่** → `createGame()` 
   - เพิ่มเกมใหม่เข้า `gamesByRoom`
   - ตั้งค่าเป็น active game ใน `activeGameByRoom`

2. **เกมจบ** → `finishAndArchiveGame()`
   - ลบออกจาก `activeGameByRoom`
   - **เกมยังคงอยู่ใน `gamesByRoom`** (archived)
   - ตั้งค่า `finished = true` และ `wordRevealed = true`

3. **ดึงประวัติ** → `getGamesForRoom(roomCode)`
   - Return `List<Game>` ทั้งหมดของห้องนั้น

## API Endpoints

### 1. GET /api/game/{roomCode}/history
ดึงประวัติเกมทั้งหมดของห้อง

**Request:**
```bash
GET /api/game/ABCD1234/history
```

**Response:**
```json
{
  "success": true,
  "message": "Game history retrieved",
  "data": [
    {
      "id": "uuid-1",
      "roomCode": "ABCD1234",
      "word": "แมว",
      "wordRevealed": true,
      "startedAt": "2025-12-02T10:00:00",
      "endsAt": "2025-12-02T10:05:00",
      "durationSeconds": 300,
      "finished": true,
      "players": [
        {
          "uuid": "player-1-uuid",
          "playerName": "Alice"
        },
        {
          "uuid": "player-2-uuid",
          "playerName": "Bob"
        }
      ],
      "roles": {
        "player-1-uuid": "MASTER",
        "player-2-uuid": "INSIDER",
        "player-3-uuid": "PLAYER"
      },
      "cardOpened": {
        "player-1-uuid": true,
        "player-2-uuid": true,
        "player-3-uuid": true
      },
      "votes": {
        "player-1-uuid": "player-2-uuid",
        "player-3-uuid": "player-2-uuid"
      },
      "scores": {
        "player-1-uuid": 10,
        "player-2-uuid": 0,
        "player-3-uuid": 10
      },
      "voteResult": {
        "insiderUuid": "player-2-uuid",
        "mostVotedUuid": "player-2-uuid",
        "mostVotedCount": 2,
        "voteTally": {
          "player-2-uuid": 2
        }
      },
      "gameOutcome": "INSIDER_FOUND"
    }
  ],
  "status": "OK"
}
```

### 2. GET /api/game/{roomCode}/active
ดึงเกมที่กำลังเล่นอยู่ในปัจจุบัน (existing endpoint)

## GameHistoryDto

DTO สำหรับแสดงประวัติเกมที่มีข้อมูลครบถ้วน:

```java
@Data
@Builder
public class GameHistoryDto {
    private UUID id;
    private String roomCode;
    private String word;              // คำที่ถูกเปิดเผย
    private boolean wordRevealed;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private Integer durationSeconds;
    private boolean finished;
    
    private List<PlayerInGame> players;        // ผู้เล่นในเกมนี้
    private Map<String, RoleType> roles;       // บทบาทของแต่ละคน
    private Map<String, Boolean> cardOpened;   // สถานะการเปิดไพ่
    private Map<String, String> votes;         // การโหวต
    private Map<String, Integer> scores;       // คะแนน
    
    private VoteResultDto voteResult;          // สรุปผลการโหวต
    private String gameOutcome;                // ผลลัพธ์ของเกม
}
```

### Game Outcome Types
- **`INSIDER_FOUND`**: โหวตถูกต้อง - ผู้เล่นทั่วไปชนะ
- **`INSIDER_HIDDEN`**: โหวตผิด - Insider ชนะ
- **`NO_INSIDER`**: ไม่มี Insider ในเกม

## Vote Result Details

```java
public static class VoteResultDto {
    private String insiderUuid;           // UUID ของ Insider
    private String mostVotedUuid;         // UUID ของคนที่โดนโหวตมากที่สุด
    private Integer mostVotedCount;       // จำนวนโหวตที่มากที่สุด
    private Map<String, Integer> voteTally; // สรุปคะแนนโหวตทั้งหมด
}
```

## การใช้งาน Frontend

### React/Next.js Example

```typescript
import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

// Fetch game history for a room
async function getGameHistory(roomCode: string) {
  try {
    const response = await axios.get(`${API_URL}/api/game/${roomCode}/history`);
    
    if (response.data.success) {
      const games = response.data.data;
      console.log(`Found ${games.length} games in history`);
      
      games.forEach(game => {
        console.log(`Game ${game.id}:`);
        console.log(`  Word: ${game.word}`);
        console.log(`  Finished: ${game.finished}`);
        console.log(`  Outcome: ${game.gameOutcome}`);
        
        if (game.voteResult) {
          console.log(`  Insider: ${game.voteResult.insiderUuid}`);
          console.log(`  Most Voted: ${game.voteResult.mostVotedUuid}`);
        }
      });
      
      return games;
    }
  } catch (error) {
    console.error('Error fetching game history:', error);
  }
}

// Example: Display in component
function GameHistoryComponent({ roomCode }: { roomCode: string }) {
  const [history, setHistory] = useState([]);
  
  useEffect(() => {
    getGameHistory(roomCode).then(data => setHistory(data || []));
  }, [roomCode]);
  
  return (
    <div>
      <h2>Game History</h2>
      {history.map(game => (
        <div key={game.id}>
          <h3>Game: {game.word}</h3>
          <p>Outcome: {game.gameOutcome}</p>
          <p>Players: {game.players.length}</p>
          {/* Display more details */}
        </div>
      ))}
    </div>
  );
}
```

## ข้อจำกัดปัจจุบัน

### In-Memory Storage
⚠️ **สำคัญ**: ข้อมูลถูกเก็บใน memory เท่านั้น 
- ถ้า restart server → **ประวัติจะหายทั้งหมด**
- ไม่มี persistence (database)

### แนวทางแก้ไข (Future Enhancement)

#### Option 1: เพิ่ม Database (แนะนำ)
```java
// Add JPA Entity
@Entity
@Table(name = "games")
public class GameEntity {
    @Id
    private UUID id;
    
    @Column(name = "room_code")
    private String roomCode;
    
    @Column(name = "word")
    private String word;
    
    // ... other fields
    
    @Column(name = "roles", columnDefinition = "json")
    @Convert(converter = JsonConverter.class)
    private Map<String, RoleType> roles;
}

// Add Repository
@Repository
public interface GameRepository extends JpaRepository<GameEntity, UUID> {
    List<GameEntity> findByRoomCodeOrderByStartedAtDesc(String roomCode);
}
```

#### Option 2: Export to File
```java
// Add export endpoint
@GetMapping("/{roomCode}/history/export")
public ResponseEntity<Resource> exportGameHistory(@PathVariable String roomCode) {
    // Export to JSON/CSV file
}
```

## Files Changed/Created

### New Files
1. **`src/main/java/com/insidergame/insider_api/dto/GameHistoryDto.java`**
   - DTO for game history display
   - Includes vote results and game outcome

### Modified Files
2. **`src/main/java/com/insidergame/insider_api/api/game/GameController.java`**
   - Added `GET /api/game/{roomCode}/history` endpoint
   - Added `convertToHistoryDto()` method for converting Game to DTO

### Existing Files (Already Working)
3. **`src/main/java/com/insidergame/insider_api/manager/GameManager.java`**
   - `gamesByRoom` - stores all games
   - `getGamesForRoom()` - retrieves game history
   - `finishAndArchiveGame()` - archives finished games

4. **`src/main/java/com/insidergame/insider_api/api/game/GameServiceImpl.java`**
   - `getGamesForRoom()` service method (already implemented)

## Testing

### Test Endpoint
```bash
# 1. Create a room and play some games
# ...

# 2. Get game history
curl -X GET http://localhost:8080/api/game/ABCD1234/history

# Expected: List of all games played in room ABCD1234
```

### Test Scenarios
1. ✅ Play multiple games in a room
2. ✅ Finish games with voting
3. ✅ Fetch history - should show all games
4. ✅ Restart server - history will be lost (in-memory)

## Summary

✅ **มีระบบเก็บประวัติแล้ว** - ทำงานผ่าน `GameManager.gamesByRoom`
✅ **มี API ดึงประวัติแล้ว** - `GET /api/game/{roomCode}/history`
✅ **มี DTO สวยงาม** - `GameHistoryDto` พร้อม vote results และ game outcome
⚠️ **ข้อจำกัด** - In-memory เท่านั้น (หายเมื่อ restart)
💡 **แนะนำ** - เพิ่ม database persistence ในอนาคต

