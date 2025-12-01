# แก้ปัญหา "SecurityError: An insecure SockJS connection may not be initiated from a page loaded over HTTPS"

## 🔴 ปัญหา
- ngrok ให้ HTTPS (`https://abc123.ngrok.io`)
- Frontend โหลดผ่าน HTTPS
- แต่ WebSocket พยายามเชื่อมต่อแบบ HTTP (`http://`)
- Browser บล็อก mixed content (HTTPS → HTTP)

## ✅ วิธีแก้

### สาเหตุ: **SockJS URL ผิด**

Frontend ของคุณใช้:
```typescript
const WS_URL = "http://abc123.ngrok.io/ws"; // ❌ Wrong!
```

ต้องเปลี่ยนเป็น:
```typescript
const WS_URL = "https://abc123.ngrok.io/ws"; // ✅ Correct!
```

---

## 🔧 การแก้ไขใน Frontend

### ไฟล์: `.env` หรือ `.env.local`
```env
# ❌ ผิด
NEXT_PUBLIC_WS_URL=http://abc123.ngrok.io/ws

# ✅ ถูกต้อง
NEXT_PUBLIC_WS_URL=https://abc123.ngrok.io/ws
```

### หรือใน Code:
```typescript
// useRoomWebSocket.ts
const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "https://abc123.ngrok.io/ws";
//                                                 ↑ ต้องเป็น https://

export function useRoomWebSocket(roomCode: string, playerUuid: string) {
  const client = new Client({
    webSocketFactory: () => new SockJS(WS_URL), // ✅ จะใช้ https:// อัตโนมัติ
    // ...
  });
}
```

---

## 🎯 วิธีทำงาน

### Backend (Spring Boot)
```
HTTP on port 8080
http://localhost:8080/ws
```

### ngrok (TLS Termination)
```
ngrok http 8080
↓
HTTPS endpoint: https://abc123.ngrok.io/ws
↓
ngrok ทำ SSL/TLS ให้อัตโนมัติ
↓
Forward ไปที่ http://localhost:8080/ws
```

### Frontend (Browser)
```
Browser โหลดหน้า: https://abc123.ngrok.io
↓
SockJS connect to: https://abc123.ngrok.io/ws ✅
↓
Browser อนุญาต (HTTPS → HTTPS)
```

---

## 📝 Checklist

### 1. ✅ Backend Configuration (เสร็จแล้ว)
```properties
# application.properties
server.port=8080  # HTTP is OK - ngrok handles HTTPS
```

### 2. ✅ Start Backend
```bash
./mvnw spring-boot:run
# Backend รันที่: http://localhost:8080
```

### 3. ✅ Start ngrok
```bash
ngrok http 8080
# ngrok ให้: https://abc123.ngrok.io (HTTPS อัตโนมัติ)
```

### 4. 🔧 แก้ Frontend (ต้องทำ!)
```env
# .env
NEXT_PUBLIC_WS_URL=https://abc123.ngrok.io/ws
#                   ↑↑↑↑↑ ต้องเป็น https://
```

### 5. 🔄 Restart Frontend
```bash
npm run dev
# หรือ
yarn dev
```

---

## 🧪 ทดสอบ

### Test 1: Check Backend
```bash
curl http://localhost:8080/ws
# Expected: HTML response from SockJS
```

### Test 2: Check ngrok
```bash
curl https://abc123.ngrok.io/ws
# Expected: Same HTML response (ngrok forwards to backend)
```

### Test 3: Check Frontend Console
เปิด Browser Console ควรเห็น:
```
🔌 WebSocket Connected for room: XXX
```

ไม่ควรเห็น error:
```
❌ SecurityError: An insecure SockJS connection...
```

---

## 🔍 Debug Tips

### 1. ตรวจสอบ URL ที่ Frontend ใช้
```typescript
// เพิ่ม console.log ใน useRoomWebSocket
console.log("🔗 Connecting to:", WS_URL);
```

Expected output:
```
🔗 Connecting to: https://abc123.ngrok.io/ws
```

### 2. ตรวจสอบ Network Tab
- เปิด Chrome DevTools → Network Tab
- Filter: WS (WebSocket)
- ควรเห็น request ไปที่ `wss://abc123.ngrok.io/ws` (wss = secure WebSocket)

### 3. ตรวจสอบ ngrok Dashboard
```
http://localhost:4040
```
ควรเห็น:
- Request: `GET https://abc123.ngrok.io/ws`
- Status: 101 Switching Protocols (WebSocket upgrade)

---

## ⚠️ Common Mistakes

### ❌ ผิด #1: ใช้ http:// กับ ngrok
```typescript
const WS_URL = "http://abc123.ngrok.io/ws"; // ❌
```

### ❌ ผิด #2: ลืม restart frontend
```bash
# แก้ .env แล้วต้อง restart!
npm run dev
```

### ❌ ผิด #3: ใช้ localhost แทน ngrok URL
```typescript
const WS_URL = "http://localhost:8080/ws"; // ❌ ไม่ทำงานกับ ngrok
```

### ✅ ถูกต้อง
```typescript
const WS_URL = "https://abc123.ngrok.io/ws"; // ✅
```

---

## 🎉 สรุป

**ปัญหา**: Browser บล็อก HTTP WebSocket จาก HTTPS page

**วิธีแก้**: ใช้ HTTPS WebSocket URL

**Backend**: HTTP (port 8080) ← ngrok จัดการ SSL ให้

**Frontend**: HTTPS (`https://abc123.ngrok.io/ws`) ← เชื่อมต่อด้วย https://

**ผลลัพธ์**: WebSocket ทำงานผ่าน ngrok ได้! 🚀

---

## 📚 อ่านเพิ่มเติม

- [SockJS Mixed Content](https://github.com/sockjs/sockjs-client#mixed-content)
- [ngrok TLS Termination](https://ngrok.com/docs/secure-tunnels/tls-termination)
- [Browser Mixed Content Blocking](https://developer.mozilla.org/en-US/docs/Web/Security/Mixed_content)

