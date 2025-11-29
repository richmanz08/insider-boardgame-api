# Fix: activeGame.votes Not Showing After Player Vote

## Problem
After calling `playerVote()`, the `activeGame` field `votes` was not visible in the frontend.

## Root Causes

### 1. **Backend**: Votes field was missing from active_game response
The `currentGame()` handler in `RoomWebSocketController` was not including the `votes` field when building the gameMap response.

### 2. **Frontend**: No request for updated activeGame after voting
The `playerVote()` function was sending the vote but not requesting the updated active game data to see the votes.

### 3. **Frontend**: Missing VOTE_CAST trigger
The room update subscription wasn't listening for `VOTE_CAST` events to trigger an active game refresh.

## Solution

### Backend Fix ✅
**File**: `RoomWebSocketController.java`

Added `votes` field to the active_game response payload:

```java
gameMap.put("votes", g.getVotes());
```

Now when clients request `/app/room/{roomCode}/active_game`, they receive:
```json
{
  "game": {
    "id": "...",
    "votes": {
      "player-uuid-1": "target-uuid-a",
      "player-uuid-2": "target-uuid-b"
    },
    "cardOpened": {...},
    ...
  }
}
```

### Frontend Fix ✅
**File**: `useRoomWebSocket.ts` (or `useRoomWebSocket_updated.ts`)

#### 1. Added VOTE_CAST listener
```typescript
if (
  update.type === "CARD_OPENED" ||
  update.type === "GAME_STARTED" ||
  update.type === "VOTE_CAST" ||      // ← NEW
  update.type === "VOTE_STARTED" ||   // ← NEW
  (update.activeGame !== undefined && update.activeGame !== null)
) {
  // Request updated active game
  clientRef.current.publish({
    destination: `/app/room/${roomCode}/active_game`,
    body: JSON.stringify({ playerUuid }),
  });
}
```

#### 2. Auto-request after voting
```typescript
const playerVote = useCallback(
  (targetPlayerUuid: string) => {
    if (clientRef.current && isConnected) {
      // Send vote
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/vote`,
        body: JSON.stringify({ playerUuid, targetPlayerUuid }),
      });
      
      // Immediately request active game to see updated votes
      setTimeout(() => {
        if (clientRef.current?.connected) {
          clientRef.current.publish({
            destination: `/app/room/${roomCode}/active_game`,
            body: JSON.stringify({ playerUuid }),
          });
        }
      }, 100);
    }
  },
  [roomCode, playerUuid, isConnected]
);
```

## How It Works Now

### Flow:
1. **Player clicks vote** → `playerVote(targetUuid)` called
2. **Vote sent** → Server receives at `/app/room/{roomCode}/vote`
3. **Server updates** → `GameManager.recordVote()` stores vote in `Game.votes` map
4. **Server broadcasts** → `VOTE_CAST` message to `/topic/room/{roomCode}`
5. **Frontend receives** → `VOTE_CAST` in room subscription
6. **Frontend requests** → `/app/room/{roomCode}/active_game`
7. **Server responds** → `/user/queue/active_game` with updated votes field
8. **Frontend updates** → `setActiveGame(game)` with votes visible

### Result:
```typescript
activeGame.votes = {
  "uuid-player-1": "uuid-target-a",
  "uuid-player-2": "uuid-target-b",
  "uuid-player-3": "uuid-target-a"
}
```

## Testing
1. Start a game with multiple players
2. Trigger voting phase (master_end)
3. Each player calls `playerVote(targetUuid)`
4. Check browser console for:
   - `🗳️ Player is voting for: [uuid]`
   - `Room update received: { type: "VOTE_CAST" }`
   - `🔄 Requesting active game after vote...`
   - `🎮 active_game (user queue) received: { votes: {...} }`
5. Verify `activeGame.votes` object in React state contains all votes

## Additional Notes
- Votes can be changed (new vote overwrites previous vote from same voter)
- Only participants (players with roles) can vote
- The `votes` map is initialized as empty `{}` when game is created
- Votes persist across reconnects (stored in server memory via GameManager)

## Files Modified
1. `/src/main/java/com/insidergame/insider_api/websocket/RoomWebSocketController.java`
2. `/hooks/useRoomWebSocket.ts` (create updated version as `useRoomWebSocket_updated.ts`)

