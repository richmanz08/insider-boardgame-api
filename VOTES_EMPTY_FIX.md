# Fix: Votes Field Empty After Player Votes

## Problem
The `votes` field in `activeGame` shows as empty object `{}` even after players vote, and `activeGame` is not updating after votes are cast.

## Root Causes

### 1. Missing `votes` in `masterEnd` response ❌
When MASTER triggers voting (`master_end`), the active_game snapshot sent to each player was missing the `votes` field.

### 2. Frontend not requesting updated activeGame after vote ❌
After calling `playerVote()`, the frontend wasn't automatically requesting the updated `activeGame` to see the new votes.

## Complete Solution

### Backend Fixes ✅

#### 1. Added `votes` to `currentGame` handler (ALREADY DONE)
**File**: `RoomWebSocketController.java` → `currentGame()` method
```java
gameMap.put("votes", g.getVotes());
```

#### 2. Added `votes` to `masterEnd` handler (FIXED NOW)
**File**: `RoomWebSocketController.java` → `masterEnd()` method
```java
gameMap.put("votes", g.getVotes());
```

Now both handlers include the votes field in their responses.

### Frontend Fix Required ✅

**File**: `useRoomWebSocket.ts` (use the updated version)

You need to update your React hook to:

1. **Listen for VOTE_CAST events** and request active game:
```typescript
if (
  update.type === "CARD_OPENED" ||
  update.type === "GAME_STARTED" ||
  update.type === "VOTE_CAST" ||      // ← ADD THIS
  update.type === "VOTE_STARTED" ||   // ← ADD THIS
  (update.activeGame !== undefined && update.activeGame !== null)
) {
  clientRef.current.publish({
    destination: `/app/room/${roomCode}/active_game`,
    body: JSON.stringify({ playerUuid }),
  });
}
```

2. **Auto-request after voting**:
```typescript
const playerVote = useCallback(
  (targetPlayerUuid: string) => {
    if (clientRef.current && isConnected) {
      console.log("🗳️ Player is voting for:", targetPlayerUuid);
      
      // Send vote
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/vote`,
        body: JSON.stringify({ playerUuid, targetPlayerUuid }),
      });
      
      // Request updated active game after 100ms
      setTimeout(() => {
        if (clientRef.current?.connected) {
          console.log("🔄 Requesting active game after vote...");
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

### Complete Vote Flow:
1. **Player votes** → `playerVote(targetUuid)` called
2. **Vote sent** → Server `/app/room/{roomCode}/vote`
3. **Server stores** → `GameManager.recordVote()` updates `Game.votes` map
4. **Server broadcasts** → `VOTE_CAST` to `/topic/room/{roomCode}`
5. **All clients hear** → Room subscription receives `VOTE_CAST`
6. **Clients request** → `/app/room/{roomCode}/active_game` for each player
7. **Server responds** → `/user/queue/active_game` with updated `votes`
8. **UI updates** → `activeGame.votes` now shows all votes

### Example Result:
```json
{
  "votes": {
    "player-uuid-1": "target-uuid-a",
    "player-uuid-2": "target-uuid-b",
    "player-uuid-3": "target-uuid-a"
  }
}
```

## Testing Steps

1. **Start a game** with multiple players
2. **Master triggers voting** → `masterRoleIsSetToVoteTime()`
3. **Check console** for:
   ```
   🎮 active_game received: { votes: {} }
   ```
4. **Player 1 votes** → `playerVote("target-uuid")`
5. **Check console** for:
   ```
   🗳️ Player is voting for: target-uuid
   Room update received: { type: "VOTE_CAST" }
   🔄 Requesting active game after vote...
   🎮 active_game received: { votes: { "voter-uuid": "target-uuid" } }
   ```
6. **Verify** `activeGame.votes` in React state
7. **Other players vote** and watch votes accumulate

## Why Was It Empty?

### Before Fix:
- `masterEnd` sent activeGame snapshot WITHOUT `votes` field
- When voting started, clients got `votes: undefined` → became `{}`
- After voting, no trigger to refresh activeGame
- Result: `votes` stayed empty `{}`

### After Fix:
- `masterEnd` sends activeGame WITH `votes: {}`
- Each vote triggers `VOTE_CAST` broadcast
- Clients auto-request updated activeGame
- Result: `votes` updates in real-time with each vote

## Quick Checklist

Backend ✅:
- [x] `currentGame()` includes `votes`
- [x] `masterEnd()` includes `votes`
- [x] `GameManager.recordVote()` stores votes
- [x] `votePlayer()` broadcasts `VOTE_CAST`

Frontend 🔄 (Update your hook):
- [ ] Subscribe to `VOTE_CAST` events
- [ ] Request activeGame on `VOTE_CAST`
- [ ] Auto-request after `playerVote()`
- [ ] Parse and set `activeGame.votes` in state

## Files Changed
- ✅ `RoomWebSocketController.java` (Backend - DONE)
- 🔄 `useRoomWebSocket.ts` (Frontend - USE UPDATED VERSION)

Copy `useRoomWebSocket_updated.ts` to replace your current hook!

