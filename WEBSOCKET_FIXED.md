# ✅ แก้ไขปัญหา WebSocket Connection เรียบร้อยแล้ว!

## 🔴 ปัญหาที่พบ

ใน `.env` มีการตั้งค่าผิด:
```env
NEXT_PUBLIC_WS_URL=https://localhost:8080/ws  # ❌ ใช้ https:// ผิด!
```

**ผลลัพธ์**: SecurityError เพราะ backend รัน HTTP แต่พยายามเชื่อมต่อด้วย HTTPS

---

## ✅ วิธีแก้ที่ทำแล้ว

### 1. แก้ไข `.env`:
```env
# สำหรับ Local Development
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
NEXT_PUBLIC_WS_URL=http://localhost:8080/ws
```

### 2. แก้ไข `useRoomWebSocket.ts`:
```typescript
// เปลี่ยน default fallback จาก https:// เป็น http://
const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080/ws";
```

---

## 🎯 การตั้งค่าที่ถูกต้อง

### Scenario 1: Local Development (ไม่ใช้ ngrok)
**Backend**: `http://localhost:8080`

**Frontend `.env`**:
```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
NEXT_PUBLIC_WS_URL=http://localhost:8080/ws
```

✅ **ใช้ http://** เพราะทั้ง frontend และ backend รันบน localhost

---

### Scenario 2: ngrok (HTTPS)
**Backend**: `http://localhost:8080` (ยังคงเป็น HTTP)

**ngrok**:
```bash
ngrok http 8080
# ได้ URL: https://abc123.ngrok.io
```

**Frontend `.env`**:
```env
NEXT_PUBLIC_API_BASE_URL=https://abc123.ngrok.io/api
NEXT_PUBLIC_WS_URL=https://abc123.ngrok.io/ws
```

✅ **ใช้ https://** เพราะ ngrok ทำ TLS termination ให้

---

## 📝 วิธีเปลี่ยนระหว่าง Local ↔ ngrok

### วิธีที่ 1: แก้ไข `.env` (แนะนำ)

**Local Development**:
```env
NEXT_PUBLIC_WS_URL=http://localhost:8080/ws
```

**ngrok**:
```env
NEXT_PUBLIC_WS_URL=https://abc123.ngrok.io/ws
```

จากนั้น **restart frontend**:
```bash
npm run dev
```

---

### วิธีที่ 2: Dynamic Detection (Advanced)

แก้ไข `useRoomWebSocket.ts`:
```typescript
// Auto-detect ngrok or localhost
const getWsUrl = () => {
  // If env variable exists, use it
  if (process.env.NEXT_PUBLIC_WS_URL) {
    return process.env.NEXT_PUBLIC_WS_URL;
  }
  
  // Auto-detect based on window location
  if (typeof window !== 'undefined') {
    const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
    const host = window.location.host; // includes port
    
    // If running on ngrok domain
    if (host.includes('ngrok.io') || host.includes('ngrok-free.app')) {
      return `https://${host}/ws`;
    }
    
    // Default: localhost
    return 'http://localhost:8080/ws';
  }
  
  return 'http://localhost:8080/ws';
};

const WS_URL = getWsUrl();
```

---

## 🔍 ตรวจสอบว่าทำงานถูกต้อง

### 1. เปิด Browser Console
ควรเห็น:
```
🔌 WebSocket Connected for room: XXX player: YYY
```

ไม่ควรเห็น:
```
❌ SecurityError: An insecure SockJS connection...
```

### 2. Network Tab
- Filter: WS
- ควรเห็น connection ไปที่ URL ที่ถูกต้อง
- Status: 101 Switching Protocols (สำเร็จ)

### 3. เช็ค URL ที่ใช้งาน
เพิ่ม log:
```typescript
console.log("🔗 WS_URL:", WS_URL);
```

Expected:
- **Local**: `http://localhost:8080/ws`
- **ngrok**: `https://abc123.ngrok.io/ws`

---

## 🎉 สรุป

### ✅ แก้แล้ว:
1. เปลี่ยน `.env` จาก `https://` → `http://` สำหรับ localhost
2. แก้ default fallback ใน code
3. สร้าง `.env.example` สำหรับอ้างอิง

### 📋 Checklist:
- ✅ Backend รันที่ `http://localhost:8080`
- ✅ `.env` ใช้ `http://localhost:8080/ws`
- ✅ Frontend code fallback เป็น `http://`
- ✅ Restart frontend

### 🚀 พร้อมใช้งาน!

**Local Development**: ทำงานด้วย HTTP ✅

**ngrok**: เปลี่ยน `.env` เป็น `https://abc123.ngrok.io/ws` แล้ว restart ✅

---

## 📚 ไฟล์ที่เกี่ยวข้อง

- ✅ `useRoomWebSocket.ts` - แก้ default URL
- ✅ `.env` - ต้องแก้เป็น `http://localhost:8080/ws`
- ✅ `.env.example` - สร้างใหม่เป็น template
- 📖 `FIX_NGROK_WEBSOCKET_HTTPS.md` - คู่มือสำหรับ ngrok
- 📖 `NGROK_WEBSOCKET_GUIDE.md` - วิธีใช้งาน ngrok

---

## ⚡ Quick Fix (ถ้ายังไม่ทำ)

```bash
# 1. แก้ .env
echo "NEXT_PUBLIC_WS_URL=http://localhost:8080/ws" > .env.local

# 2. Restart frontend
npm run dev

# 3. Done! ✅
```

