# Word Reveal Feature - เปิดเผยคำตอบหลัง MASTER จบเกม

## สรุปการแก้ไข ✅

### 1. เพิ่ม Field `wordRevealed` ใน Game Model
```java
private boolean wordRevealed; // True when MASTER ends game - reveal word to all players
```

### 2. Initialize เมื่อสร้างเกม
- `GameManager.createGame()` → set `wordRevealed = false`
- คำตอบจะถูกซ่อนตั้งแต่เริ่มเกม

### 3. เปิดเผยคำตอบเมื่อ MASTER กด `master_end`
```java
@MessageMapping("/room/{roomCode}/master_end")
public void masterEnd(...) {
    // ...existing validation...
    
    // Reveal the word to all players
    g.setWordRevealed(true);
    
    // Broadcast VOTE_STARTED
}
```

### 4. อัพเดต Logic การแสดงคำ
**ก่อนหน้า**:
```java
// แสดงคำเฉพาะ MASTER และ INSIDER
boolean showWord = roleEnum == RoleType.MASTER || roleEnum == RoleType.INSIDER;
```

**หลังแก้ไข**:
```java
// แสดงคำเมื่อ: wordRevealed = true หรือเป็น MASTER/INSIDER
boolean showWord = g.isWordRevealed() || roleEnum == RoleType.MASTER || roleEnum == RoleType.INSIDER;
```

---

## Flow การทำงาน

### ขั้นที่ 1: เริ่มเกม
```
Game created → wordRevealed = false
MASTER/INSIDER: เห็น word
CITIZEN: ไม่เห็น word (word = "")
```

### ขั้นที่ 2: MASTER กด master_end
```
1. ตรวจสอบ requester เป็น MASTER
2. Set wordRevealed = true  ← ตรงนี้!
3. Set endsAt (เริ่มโหวต)
4. Broadcast VOTE_STARTED
5. ส่ง active_game ให้ทุกคน
```

### ขั้นที่ 3: หลัง master_end
```
ทุกคน request /active_game → word จะถูกเปิดเผย
MASTER: เห็น word (เหมือนเดิม)
INSIDER: เห็น word (เหมือนเดิม)
CITIZEN: เห็น word (ใหม่! 🎉)
```

---

## Response Example

### ก่อน master_end (CITIZEN ไม่เห็นคำ):
```json
{
  "game": {
    "word": "",
    "wordRevealed": false,
    "privateMessage": {
      "role": "CITIZEN",
      "word": ""
    }
  }
}
```

### หลัง master_end (CITIZEN เห็นคำ):
```json
{
  "game": {
    "word": "รถยนต์",
    "wordRevealed": true,
    "privateMessage": {
      "role": "CITIZEN",
      "word": "รถยนต์"
    }
  }
}
```

---

## ไฟล์ที่แก้ไข

1. ✅ **Game.java** - เพิ่ม `wordRevealed` field
2. ✅ **GameManager.java** - initialize `wordRevealed = false` เมื่อสร้างเกม
3. ✅ **RoomWebSocketController.java**:
   - `masterEnd()` → set `wordRevealed = true`
   - `currentGame()` → เช็ค `wordRevealed` ก่อนแสดงคำ
   - `masterEnd` snapshot → เช็ค `wordRevealed` ก่อนแสดงคำ
   - เพิ่ม `wordRevealed` ใน response payload

---

## การทดสอบ

### Test 1: ก่อน master_end
```
1. เริ่มเกม
2. CITIZEN ขอ active_game
3. Expected: word = "", wordRevealed = false
```

### Test 2: หลัง master_end
```
1. MASTER กด master_end
2. ทุกคนได้ VOTE_STARTED
3. ทุกคนขอ active_game
4. Expected: 
   - CITIZEN เห็น word = "รถยนต์"
   - wordRevealed = true
```

### Test 3: Refresh หลัง master_end
```
1. CITIZEN refresh หน้า
2. ขอ active_game ใหม่
3. Expected: ยังเห็น word (เพราะ wordRevealed = true อยู่)
```

---

## Frontend Integration

### ตรวจสอบว่าคำถูกเปิดเผยหรือยัง:
```typescript
if (activeGame?.wordRevealed) {
  // แสดงคำตอบให้ทุกคนเห็น
  console.log("Word revealed:", activeGame.word);
}
```

### UI Example:
```tsx
{activeGame?.wordRevealed && (
  <div className="word-reveal">
    <h3>คำตอบคือ: {activeGame.word}</h3>
  </div>
)}
```

---

## สรุป

✅ **สำเร็จ**: หลัง MASTER กด `master_end` แล้ว:
- คำตอบ (word) จะถูกเปิดเผยให้**ทุกคน**เห็น
- รวมถึง CITIZEN ที่เดิมไม่เห็น
- ระบบจำสถานะ `wordRevealed = true` ไว้
- หลัง refresh ก็ยังเห็นคำตอบ

🎯 **ใช้งานได้ทันที**: ไม่มี compile errors, พร้อมทดสอบ!

