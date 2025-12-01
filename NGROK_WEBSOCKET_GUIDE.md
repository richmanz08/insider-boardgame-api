# การใช้งาน WebSocket ผ่าน ngrok

## ✅ Configuration ที่ทำไว้แล้ว

### 1. **application.properties**
```properties
# CORS - Allow all origins (including ngrok)
spring.web.cors.allowed-origins=*
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.web.cors.allowed-headers=*
spring.web.cors.allow-credentials=false

# WebSocket - Allow all origins
spring.websocket.allowed-origins=*
```

### 2. **WebSocketConfig.java**
```java
registry.addEndpoint("/ws")
    .setAllowedOriginPatterns("*") // Allow all origins including ngrok
    .withSockJS();
```

---

## 🚀 วิธีใช้งานกับ ngrok

### ขั้นที่ 1: Start Backend
```bash
# Run Spring Boot application
./mvnw spring-boot:run

# หรือ
java -jar target/insider-api.jar
```

Backend จะรันที่: `http://localhost:8080`

### ขั้นที่ 2: Start ngrok
```bash
# Expose port 8080 ผ่าน ngrok
ngrok http 8080
```

คุณจะได้ URL เช่น:
```
Forwarding: https://abc123.ngrok.io -> http://localhost:8080
```

### ขั้นที่ 3: เชื่อมต่อจาก Client

#### Frontend (.env)
```env
NEXT_PUBLIC_API_URL=https://abc123.ngrok.io
NEXT_PUBLIC_WS_URL=https://abc123.ngrok.io/ws
```

#### JavaScript/TypeScript
```typescript
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

// Use ngrok URL
const WS_URL = "https://abc123.ngrok.io/ws";

const client = new Client({
  webSocketFactory: () => new SockJS(WS_URL),
  reconnectDelay: 5000,
  heartbeatIncoming: 4000,
  heartbeatOutgoing: 4000,
});

client.onConnect = () => {
  console.log("Connected via ngrok!");
  
  // Subscribe to room updates
  client.subscribe("/topic/room/ROOMCODE", (message) => {
    console.log("Received:", JSON.parse(message.body));
  });
  
  // Send message
  client.publish({
    destination: "/app/room/ROOMCODE/join",
    body: JSON.stringify({ playerUuid: "xxx" })
  });
};

client.activate();
```

---

## 🔍 ทดสอบการเชื่อมต่อ

### Test WebSocket Endpoint
```bash
curl https://abc123.ngrok.io/ws
```

Expected: HTML response from SockJS

### Test REST API
```bash
curl https://abc123.ngrok.io/api/rooms
```

---

## 📱 ทดสอบจาก Mobile/External Device

### 1. เปิด ngrok URL บน Mobile Browser
```
https://abc123.ngrok.io
```

### 2. Test WebSocket Connection
- Frontend app จะเชื่อมต่อผ่าน ngrok URL
- WebSocket จะทำงานแบบ real-time เหมือนปกติ

---

## ⚠️ สิ่งที่ต้องระวัง

### 1. **HTTPS vs HTTP**
ngrok ให้ HTTPS ฟรี แต่ถ้าใช้ free plan:
- URL จะเปลี่ยนทุกครั้งที่ restart ngrok
- ต้องอัพเดต frontend `.env` ทุกครั้ง

### 2. **Session Persistence**
ngrok free plan อาจมี session timeout:
- WebSocket อาจ disconnect หลัง idle นาน
- ใช้ `reconnectDelay` ใน STOMP client

### 3. **Rate Limits**
ngrok free plan มี rate limit:
- 40 connections/minute
- 20 req/second
- เพียงพอสำหรับ development

---

## 🔒 Production Configuration

⚠️ **สำหรับ Production ห้ามใช้ `*` (wildcard)**

แก้ไข `application.properties`:
```properties
# Production - Specify exact origins
spring.web.cors.allowed-origins=https://yourdomain.com,https://app.yourdomain.com
spring.websocket.allowed-origins=https://yourdomain.com,https://app.yourdomain.com
```

แก้ไข `WebSocketConfig.java`:
```java
registry.addEndpoint("/ws")
    .setAllowedOrigins(
        "https://yourdomain.com",
        "https://app.yourdomain.com"
    )
    .withSockJS();
```

---

## 🐛 Troubleshooting

### Problem 1: WebSocket ไม่เชื่อมต่อ
```
Solution:
1. Check ngrok running: ngrok http 8080
2. Check backend running: curl http://localhost:8080/ws
3. Check frontend WS_URL matches ngrok URL
4. Check browser console for CORS errors
```

### Problem 2: 403 Forbidden
```
Solution:
1. Verify CORS config in application.properties
2. Restart backend after config change
3. Clear browser cache
```

### Problem 3: Connection keeps dropping
```
Solution:
1. Increase heartbeat intervals
2. Add reconnect logic in client
3. Check ngrok session timeout
4. Use ngrok paid plan for stable sessions
```

### Problem 4: "WebSocket connection failed"
```
Solution:
1. Try HTTP endpoint first: http://xxx.ngrok.io/ws
2. Check if SockJS fallback works
3. Verify no firewall blocking WebSocket
4. Test on different network
```

---

## 📊 Monitor ngrok Traffic

### View ngrok Dashboard
```
http://localhost:4040
```

จะเห็น:
- All HTTP requests
- WebSocket upgrade requests
- Response times
- Status codes

---

## 💡 Tips

### 1. **ใช้ ngrok config file**
สร้าง `ngrok.yml`:
```yaml
authtoken: YOUR_AUTH_TOKEN
tunnels:
  insider-api:
    proto: http
    addr: 8080
    subdomain: insider-game  # Requires paid plan
```

Run:
```bash
ngrok start insider-api
```

### 2. **Fixed URL (Paid Plan)**
```bash
ngrok http 8080 --subdomain=insider-game
# URL จะเป็น: https://insider-game.ngrok.io (ไม่เปลี่ยน)
```

### 3. **Multiple Clients**
```bash
# Start multiple ngrok tunnels
ngrok http 8080 --region=us
ngrok http 8080 --region=eu
```

---

## ✅ สรุป

**ตอนนี้ระบบพร้อมใช้งานกับ ngrok แล้ว!**

1. ✅ CORS config: allow all origins
2. ✅ WebSocket config: allow all origins  
3. ✅ SockJS fallback: enabled
4. ✅ HTTP (not HTTPS): simpler for ngrok

**วิธีใช้**:
```bash
# 1. Start backend
./mvnw spring-boot:run

# 2. Start ngrok
ngrok http 8080

# 3. Update frontend .env with ngrok URL
NEXT_PUBLIC_WS_URL=https://xxx.ngrok.io/ws

# 4. Done! 🎉
```

