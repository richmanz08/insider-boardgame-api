# WebSocket Real-Time Room Updates

## 🎮 Features

✅ **Real-time player list updates**  
✅ **See when players join/leave**  
✅ **See when players toggle ready status**  
✅ **Automatic updates via WebSocket**  

---

## 🔌 WebSocket Connection

### Endpoint
```
ws://localhost:8080/ws
```

### Client Connection (JavaScript)
```javascript
// Using SockJS + STOMP
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);
    
    // Subscribe to room updates
    stompClient.subscribe('/topic/room/' + roomCode, function(message) {
        const update = JSON.parse(message.body);
        console.log('Room update:', update);
        updatePlayerList(update.players);
    });
});
```

---

## 📡 WebSocket Topics

### Subscribe to Room Updates
```
/topic/room/{roomCode}
```

**Message Format:**
```json
{
  "type": "PLAYER_JOINED | PLAYER_LEFT | PLAYER_READY | ROOM_UPDATE",
  "roomCode": "ABC123",
  "roomName": "My Room",
  "maxPlayers": 8,
  "currentPlayers": 3,
  "status": "WAITING",
  "players": [
    {
      "uuid": "host-123",
      "playerName": "Alice",
      "isHost": true,
      "isReady": false,
      "joinedAt": "2025-11-26T10:30:00"
    },
    {
      "uuid": "player-456",
      "playerName": "Bob",
      "isHost": false,
      "isReady": true,
      "joinedAt": "2025-11-26T10:31:00"
    }
  ],
  "message": "A player joined the room"
}
```

---

## 🎯 WebSocket Events

### 1. Player Joins Room
**Trigger:** When `POST /api/room/join` is called

**WebSocket Message:**
```json
{
  "type": "PLAYER_JOINED",
  "roomCode": "ABC123",
  "currentPlayers": 3,
  "players": [...]
}
```

---

### 2. Player Leaves Room
**Trigger:** When `POST /api/room/leave` is called

**WebSocket Message:**
```json
{
  "type": "PLAYER_LEFT",
  "roomCode": "ABC123",
  "currentPlayers": 2,
  "players": [...]
}
```

---

### 3. Player Toggles Ready
**Send to Server:**
```javascript
stompClient.send('/app/room/' + roomCode + '/ready', {}, JSON.stringify({
    playerUuid: 'player-456'
}));
```

**WebSocket Broadcast:**
```json
{
  "type": "PLAYER_READY",
  "roomCode": "ABC123",
  "players": [
    {
      "uuid": "player-456",
      "playerName": "Bob",
      "isReady": true  ← Updated
    }
  ]
}
```

---

## 🧪 REST API Endpoints

### Get Players in Room
```bash
GET /api/room/{roomCode}/players
```

**Response:**
```json
{
  "success": true,
  "message": "Players retrieved successfully",
  "data": [
    {
      "uuid": "host-123",
      "playerName": "Alice",
      "isHost": true,
      "isReady": false,
      "joinedAt": "2025-11-26T10:30:00"
    },
    {
      "uuid": "player-456",
      "playerName": "Bob",
      "isHost": false,
      "isReady": true,
      "joinedAt": "2025-11-26T10:31:00"
    }
  ]
}
```

---

## 💻 Complete Client Example

```html
<!DOCTYPE html>
<html>
<head>
    <title>Room Players</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
</head>
<body>
    <h1>Room: <span id="roomCode"></span></h1>
    <h2>Players:</h2>
    <ul id="playerList"></ul>
    <button onclick="toggleReady()">Toggle Ready</button>

    <script>
        const roomCode = 'ABC123';
        const playerUuid = 'player-456';
        let stompClient = null;

        // Connect to WebSocket
        function connect() {
            const socket = new SockJS('http://localhost:8080/ws');
            stompClient = Stomp.over(socket);

            stompClient.connect({}, function(frame) {
                console.log('Connected: ' + frame);

                // Subscribe to room updates
                stompClient.subscribe('/topic/room/' + roomCode, function(message) {
                    const update = JSON.parse(message.body);
                    console.log('Update:', update);
                    updatePlayerList(update.players);
                });
            });
        }

        // Update player list UI
        function updatePlayerList(players) {
            const list = document.getElementById('playerList');
            list.innerHTML = '';

            players.forEach(player => {
                const li = document.createElement('li');
                li.innerHTML = `
                    ${player.playerName} 
                    ${player.isHost ? '👑' : ''} 
                    ${player.isReady ? '✅ Ready' : '⏳ Not Ready'}
                `;
                list.appendChild(li);
            });
        }

        // Toggle ready status
        function toggleReady() {
            if (stompClient && stompClient.connected) {
                stompClient.send('/app/room/' + roomCode + '/ready', {}, JSON.stringify({
                    playerUuid: playerUuid
                }));
            }
        }

        // Load initial player list
        async function loadPlayers() {
            const response = await fetch(`http://localhost:8080/api/room/${roomCode}/players`);
            const data = await response.json();
            if (data.success) {
                updatePlayerList(data.data);
            }
        }

        // Initialize
        document.getElementById('roomCode').textContent = roomCode;
        connect();
        loadPlayers();
    </script>
</body>
</html>
```

---

## 📊 Flow Diagram

```
Client A                    Server                      Client B
   |                          |                            |
   |-- Join Room (REST) ----->|                            |
   |                          |-- Broadcast PLAYER_JOINED ->|
   |                          |                            |
   |<---- Subscribe WS -------|                            |
   |                          |                            |
   |                          |<-- Toggle Ready (WS) ------|
   |<---- Broadcast PLAYER_READY ----------------------->|
   |                          |                            |
   |                          |<-- Leave Room (REST) ------|
   |<---- Broadcast PLAYER_LEFT -------------------------|
```

---

## 🔧 Dependencies Added

```xml
<!-- Already included in spring-boot-starter-websocket -->
- spring-websocket
- spring-messaging
- SockJS
- STOMP
```

---

## ✅ Testing

### 1. Test WebSocket Connection
```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function() {
    console.log('Connected!');
});
```

### 2. Test Room Subscription
```javascript
stompClient.subscribe('/topic/room/ABC123', function(message) {
    console.log('Received:', JSON.parse(message.body));
});
```

### 3. Test Ready Toggle
```bash
# Player joins first
POST /api/room/join
{
  "roomCode": "ABC123",
  "playerUuid": "player-456",
  "playerName": "Bob"
}

# Then toggle ready via WebSocket
stompClient.send('/app/room/ABC123/ready', {}, JSON.stringify({
    playerUuid: 'player-456'
}));
```

---

## 🎯 Message Types

| Type | Trigger | Description |
|------|---------|-------------|
| `PLAYER_JOINED` | REST: Join room | New player entered |
| `PLAYER_LEFT` | REST: Leave room | Player exited |
| `PLAYER_READY` | WS: Toggle ready | Ready status changed |
| `ROOM_UPDATE` | Any room change | General room update |

---

## 🚀 Next Steps

1. ✅ WebSocket configured
2. ✅ Real-time player list
3. ✅ Ready status toggle
4. 🔜 Game start logic (when all ready)
5. 🔜 In-game events
6. 🔜 Chat system

