# 🎮 Insider Board Game API - Summary

## ✅ APIs ที่สร้างเสร็จแล้ว

### 1. Player Session API (Stateless)
📁 **Files:** 
- `PlayerController.java`
- `PlayerService.java`
- `JwtUtil.java`
- `PlayerRegisterRequest.java`
- `PlayerResponse.java`

**Endpoints:**
- `POST /api/player/register` - สร้าง player session (generate UUID + JWT token)
- `GET /api/player/validate` - ตรวจสอบ token

**Features:**
- ✅ Generate UUID อัตโนมัติ
- ✅ Generate JWT token (expires in 24h)
- ✅ Stateless (ไม่เก็บ player ลง DB)
- ✅ Token validation

📄 **Docs:** `PLAYER_API.md`

---

### 2. Room Management API
📁 **Files:**
- `RoomController.java`
- `RoomService.java`
- `RoomRepository.java`
- `RoomEntity.java`
- `RoomCodeGenerator.java`
- `CreateRoomRequest.java`
- `JoinRoomRequest.java`
- `RoomResponse.java`

**Endpoints:**
- `POST /api/room/create` - สร้างห้องใหม่
- `POST /api/room/join` - เข้าร่วมห้อง
- `GET /api/room/{roomCode}` - ดูข้อมูลห้อง
- `GET /api/room/available` - ดูห้องที่พร้อมเข้าร่วม
- `DELETE /api/room/{roomCode}` - ลบห้อง (host only)

**Features:**
- ✅ Auto-generate 6-character room code (e.g., ABC123)
- ✅ Max 12 players per room
- ✅ Optional password protection
- ✅ Room status tracking (WAITING, PLAYING, FINISHED)
- ✅ Host privileges
- ✅ Full room detection
- ✅ Validation

📄 **Docs:** `ROOM_API.md`

---

### 3. Category API
📁 **Files:**
- `CategoryController.java`
- `CategoryService.java`
- `CategoryRepository.java`
- `CategoryEntity.java`

**Database Table:**
- `category` (id, category_name, image_url)

---

## 🗄️ Database Tables

### 1. category
```sql
CREATE TABLE category (
    id BIGSERIAL PRIMARY KEY,
    category_name VARCHAR(255) NOT NULL UNIQUE,
    image_url VARCHAR(500) NOT NULL
);
```

### 2. room
```sql
CREATE TABLE room (
    id BIGSERIAL PRIMARY KEY,
    room_code VARCHAR(6) NOT NULL UNIQUE,
    room_name VARCHAR(255) NOT NULL,
    max_players INTEGER NOT NULL,
    password VARCHAR(255),
    current_players INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    host_uuid VARCHAR(255) NOT NULL,
    host_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
```

---

## 📦 Dependencies

### Core
- Spring Boot 4.0.0
- Spring Data JPA
- Spring Web MVC
- Spring WebSocket
- PostgreSQL Driver

### Additional
- Lombok
- JWT (jjwt 0.12.6)
- Spring Boot Validation
- Spring Boot DevTools

---

## 🚀 วิธีรัน

### 1. เริ่ม PostgreSQL
```bash
docker-compose up -d
```

### 2. รัน Spring Boot
```bash
./mvnw spring-boot:run
```

### 3. ทดสอบ API
```bash
# Create player session
curl -X POST http://localhost:8080/api/player/register \
  -H "Content-Type: application/json" \
  -d '{"playerName": "Alice"}'

# Create room
curl -X POST http://localhost:8080/api/room/create \
  -H "Content-Type: application/json" \
  -d '{
    "roomName": "My Room",
    "maxPlayers": 8,
    "password": "secret",
    "hostUuid": "xxx",
    "hostName": "Alice"
  }'

# Get available rooms
curl http://localhost:8080/api/room/available
```

---

## 🔑 Environment Variables (Production)

สำหรับ production ควรเก็บค่าต่อไปนี้ใน environment variables:

```properties
# Database
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}

# JWT Secret
jwt.secret=${JWT_SECRET_KEY}
jwt.expiration=${JWT_EXPIRATION_MS}
```

---

## 📝 Next Steps

### Suggested Features:
1. **WebSocket** - Real-time room updates
2. **Player in Room** - Track players in each room
3. **Game Logic** - Insider game mechanics
4. **Chat System** - In-room chat
5. **Room History** - Track completed games
6. **Leaderboard** - Player statistics

---

## 📚 Documentation Files

- `PLAYER_API.md` - Player session API documentation
- `ROOM_API.md` - Room management API documentation  
- `DATABASE.md` - Database setup guide
- `README.md` - Project overview

---

## 🎯 Current Status

✅ Player Registration (Stateless with JWT)  
✅ Room Management (Create, Join, List, Delete)  
✅ Category Management  
✅ Database Integration (PostgreSQL)  
✅ Docker Support  
✅ API Documentation  

🔜 Next: WebSocket for real-time updates

