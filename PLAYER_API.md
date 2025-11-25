# Player Session API (Stateless)

## 🎮 API Endpoints

### 1. Create Player Session
สร้าง session สำหรับผู้เล่นใหม่ โดยจะ generate UUID และ JWT token ให้อัตโนมัติ (ไม่เก็บลง database)

**Endpoint:** `POST /api/player/register`

**Request Body:**
```json
{
  "playerName": "John Doe"
}
```

**Success Response (201 Created):**
```json
{
  "success": true,
  "message": "Player session created successfully",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "playerName": "John Doe",
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1dWlkIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIiwicGxheWVyTmFtZSI6IkpvaG4gRG9lIiwic3ViIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIiwiaWF0IjoxNzAwMTIzNDU2LCJleHAiOjE3MDAyMDk4NTZ9.abcdef123456",
    "message": "Player session created successfully"
  },
  "status": "CREATED"
}
```

**Error Response (400 Bad Request) - ชื่อว่าง:**
```json
{
  "success": false,
  "message": "Player name is required",
  "data": null,
  "status": "BAD_REQUEST"
}
```

---

### 2. Validate Token
ตรวจสอบความถูกต้องของ JWT token และดึงข้อมูล player จาก token

**Endpoint:** `GET /api/player/validate`

**Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Token is valid",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "playerName": "John Doe",
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "message": "Token is valid"
  },
  "status": "OK"
}
```

**Error Response (401 Unauthorized):**
```json
{
  "success": false,
  "message": "Invalid or expired token",
  "data": null,
  "status": "UNAUTHORIZED"
}
```

---

## 💡 Stateless Design

**ไม่มีการเก็บข้อมูล player ใน database!**

- ข้อมูล player ทั้งหมดถูกเก็บไว้ใน JWT token
- Server ไม่ต้อง query database เพื่อ validate player
- เหมาะสำหรับ session-based games
- ลด database load และเพิ่มความเร็ว

---

## 🧪 ทดสอบด้วย curl

### 1. Create Player Session
```bash
curl -X POST http://localhost:8080/api/player/register \
  -H "Content-Type: application/json" \
  -d '{"playerName": "Alice"}'
```

### 2. Validate Token
```bash
# แทนที่ YOUR_TOKEN_HERE ด้วย token ที่ได้จากการ register
curl -X GET http://localhost:8080/api/player/validate \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

---

## 🔑 JWT Token

Token ที่ generate มีอายุ **24 ชั่วโมง** และมีข้อมูลดังนี้:

**Payload:**
```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "playerName": "John Doe",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "iat": 1700123456,
  "exp": 1700209856
}
```

---

## 🚀 วิธีรัน

1. เริ่ม PostgreSQL:
```bash
docker-compose up -d
```

2. รัน Spring Boot:
```bash
./mvnw spring-boot:run
```

3. API จะทำงานที่: `http://localhost:8080`

---

## 📦 Dependencies ที่เพิ่มเข้ามา

- `io.jsonwebtoken:jjwt-api:0.12.6` - JWT API
- `io.jsonwebtoken:jjwt-impl:0.12.6` - JWT Implementation
- `io.jsonwebtoken:jjwt-jackson:0.12.6` - JWT JSON serialization

---

## 🎯 Features

✅ **Stateless Design** - ไม่เก็บข้อมูล player ใน database  
✅ **Auto-generate UUID** สำหรับแต่ละผู้เล่น  
✅ **Generate JWT token** พร้อม payload (uuid, playerName)  
✅ **Token expires in 24 hours**  
✅ **Validate input** ชื่อว่างเปล่า  
✅ **Validate token** ตรวจสอบความถูกต้องของ token  
✅ **Extract player info from token** - ไม่ต้อง query database  

