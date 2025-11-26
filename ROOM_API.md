# Room Management API

## 🎮 API Endpoints

### 1. Create Room
สร้างห้องใหม่พร้อม room code อัตโนมัติ

**Endpoint:** `POST /api/room/create`

**Request Body:**
```json
{
  "roomName": "My Awesome Room",
  "maxPlayers": 8,
  "password": "secret123",
  "hostUuid": "550e8400-e29b-41d4-a716-446655440000",
  "hostName": "Alice"
}
```

**Fields:**
- `roomName` (required): ชื่อห้อง
- `maxPlayers` (required): จำนวนผู้เล่นสูงสุด (2-12 คน)
- `password` (optional): รหัสผ่านห้อง (ถ้าไม่ใส่จะเป็นห้องเปิด)
- `hostUuid` (required): UUID ของผู้สร้างห้อง
- `hostName` (required): ชื่อผู้สร้างห้อง

**Success Response (201 Created):**
```json
{
  "success": true,
  "message": "Room created successfully",
  "data": {
    "id": 1,
    "roomCode": "ABC123",
    "roomName": "My Awesome Room",
    "maxPlayers": 8,
    "currentPlayers": 1,
    "hasPassword": true,
    "status": "WAITING",
    "hostUuid": "550e8400-e29b-41d4-a716-446655440000",
    "hostName": "Alice",
    "createdAt": "2025-11-26T10:30:00"
  },
  "status": "CREATED"
}
```

---

### 2. Join Room
เข้าร่วมห้องที่มีอยู่

**Endpoint:** `POST /api/room/join`

**Request Body:**
```json
{
  "roomCode": "ABC123",
  "password": "secret123",
  "playerUuid": "660e8400-e29b-41d4-a716-446655440001",
  "playerName": "Bob"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Joined room successfully",
  "data": {
    "id": 1,
    "roomCode": "ABC123",
    "roomName": "My Awesome Room",
    "maxPlayers": 8,
    "currentPlayers": 2,
    "hasPassword": true,
    "status": "WAITING",
    "hostUuid": "550e8400-e29b-41d4-a716-446655440000",
    "hostName": "Alice",
    "createdAt": "2025-11-26T10:30:00"
  },
  "status": "OK"
}
```

**Error Responses:**

❌ **Room not found (404):**
```json
{
  "success": false,
  "message": "Room not found",
  "data": null,
  "status": "NOT_FOUND"
}
```

❌ **Room is full (409):**
```json
{
  "success": false,
  "message": "Room is full",
  "data": null,
  "status": "CONFLICT"
}
```

❌ **Incorrect password (401):**
```json
{
  "success": false,
  "message": "Incorrect password",
  "data": null,
  "status": "UNAUTHORIZED"
}
```

---

### 3. Get Room by Code
ดูข้อมูลห้องด้วย room code

**Endpoint:** `GET /api/room/{roomCode}`

**Example:** `GET /api/room/ABC123`

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Room found",
  "data": {
    "id": 1,
    "roomCode": "ABC123",
    "roomName": "My Awesome Room",
    "maxPlayers": 8,
    "currentPlayers": 2,
    "hasPassword": true,
    "status": "WAITING",
    "hostUuid": "550e8400-e29b-41d4-a716-446655440000",
    "hostName": "Alice",
    "createdAt": "2025-11-26T10:30:00"
  },
  "status": "OK"
}
```

---

### 4. Get Available Rooms
ดูห้องที่พร้อมเข้าร่วม (สถานะ WAITING และยังไม่เต็ม)

**Endpoint:** `GET /api/room/available`

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Available rooms retrieved successfully",
  "data": [
    {
      "id": 1,
      "roomCode": "ABC123",
      "roomName": "My Awesome Room",
      "maxPlayers": 8,
      "currentPlayers": 2,
      "hasPassword": true,
      "status": "WAITING",
      "hostUuid": "550e8400-e29b-41d4-a716-446655440000",
      "hostName": "Alice",
      "createdAt": "2025-11-26T10:30:00"
    },
    {
      "id": 2,
      "roomCode": "XYZ789",
      "roomName": "Public Room",
      "maxPlayers": 4,
      "currentPlayers": 1,
      "hasPassword": false,
      "status": "WAITING",
      "hostUuid": "770e8400-e29b-41d4-a716-446655440002",
      "hostName": "Charlie",
      "createdAt": "2025-11-26T10:35:00"
    }
  ],
  "status": "OK"
}
```

---

### 5. Delete Room
ลบห้อง (เฉพาะ host เท่านั้น)

**Endpoint:** `DELETE /api/room/{roomCode}?hostUuid={hostUuid}`

**Example:** `DELETE /api/room/ABC123?hostUuid=550e8400-e29b-41d4-a716-446655440000`

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Room deleted successfully",
  "data": null,
  "status": "OK"
}
```

**Error Response (403 Forbidden):**
```json
{
  "success": false,
  "message": "Only the host can delete the room",
  "data": null,
  "status": "FORBIDDEN"
}
```

---

## 🗄️ Database Schema

**Table: `room`**

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, Auto) | Primary key |
| room_code | VARCHAR(6) (UNIQUE, NOT NULL) | 6-character room code |
| room_name | VARCHAR (NOT NULL) | ชื่อห้อง |
| max_players | INTEGER (NOT NULL) | จำนวนผู้เล่นสูงสุด (2-12) |
| password | VARCHAR (nullable) | รหัสผ่านห้อง |
| current_players | INTEGER (NOT NULL, default 0) | จำนวนผู้เล่นปัจจุบัน |
| status | VARCHAR (NOT NULL, default 'WAITING') | WAITING, PLAYING, FINISHED |
| host_uuid | VARCHAR (NOT NULL) | UUID ของ host |
| host_name | VARCHAR (NOT NULL) | ชื่อ host |
| created_at | TIMESTAMP (NOT NULL) | วันที่สร้าง |
| updated_at | TIMESTAMP | วันที่แก้ไขล่าสุด |

---

## 🧪 ทดสอบด้วย curl

### 1. Create Room (with password)
```bash
curl -X POST http://localhost:8080/api/room/create \
  -H "Content-Type: application/json" \
  -d '{
    "roomName": "My Room",
    "maxPlayers": 8,
    "password": "secret123",
    "hostUuid": "550e8400-e29b-41d4-a716-446655440000",
    "hostName": "Alice"
  }'
```

### 2. Create Room (without password)
```bash
curl -X POST http://localhost:8080/api/room/create \
  -H "Content-Type: application/json" \
  -d '{
    "roomName": "Public Room",
    "maxPlayers": 4,
    "hostUuid": "550e8400-e29b-41d4-a716-446655440000",
    "hostName": "Alice"
  }'
```

### 3. Join Room
```bash
curl -X POST http://localhost:8080/api/room/join \
  -H "Content-Type: application/json" \
  -d '{
    "roomCode": "ABC123",
    "password": "secret123",
    "playerUuid": "660e8400-e29b-41d4-a716-446655440001",
    "playerName": "Bob"
  }'
```

### 4. Get Room Info
```bash
curl -X GET http://localhost:8080/api/room/ABC123
```

### 5. Get Available Rooms
```bash
curl -X GET http://localhost:8080/api/room/available
```

### 6. Delete Room
```bash
curl -X DELETE "http://localhost:8080/api/room/ABC123?hostUuid=550e8400-e29b-41d4-a716-446655440000"
```

---

## 🎯 Features

✅ **Auto-generate 6-character room code** (e.g., ABC123)  
✅ **Max 12 players per room**  
✅ **Optional password protection**  
✅ **Room status tracking** (WAITING, PLAYING, FINISHED)  
✅ **Host privileges** (only host can delete room)  
✅ **Validation** - max players 2-12, required fields  
✅ **Filter available rooms** - only show joinable rooms  
✅ **Full room detection** - prevent joining full rooms  

---

## 📝 Room Code Format

- **Length**: 6 characters
- **Characters**: A-Z, 0-9
- **Example**: `ABC123`, `XYZ789`, `A1B2C3`
- **Unique**: Every room code is unique

---

## 🔒 Room States

1. **WAITING** - รอผู้เล่นเข้าร่วม (สามารถเข้าได้)
2. **PLAYING** - กำลังเล่นอยู่ (ไม่สามารถเข้าใหม่)
3. **FINISHED** - จบเกมแล้ว

---

## 💡 Use Cases

### Create a private room (มีรหัสผ่าน)
```json
{
  "roomName": "Private Game",
  "maxPlayers": 6,
  "password": "mypassword",
  "hostUuid": "xxx",
  "hostName": "Alice"
}
```

### Create a public room (ไม่มีรหัสผ่าน)
```json
{
  "roomName": "Public Game",
  "maxPlayers": 12,
  "hostUuid": "xxx",
  "hostName": "Bob"
}
```

### Join a public room
```json
{
  "roomCode": "ABC123",
  "playerUuid": "yyy",
  "playerName": "Charlie"
}
```

### Join a private room
```json
{
  "roomCode": "XYZ789",
  "password": "mypassword",
  "playerUuid": "zzz",
  "playerName": "David"
}
```

