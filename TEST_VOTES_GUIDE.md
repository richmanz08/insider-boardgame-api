# Quick Test Guide: Verify Votes Are Working

## Setup
1. Start the backend server
2. Update your frontend hook:
   ```bash
   cp hooks/useRoomWebSocket_updated.ts hooks/useRoomWebSocket.ts
   ```
3. Start your frontend app

## Test Scenario

### Step 1: Create Game
- Create a room with 3+ players
- All players ready
- Host starts game
- All players open their cards

### Step 2: Trigger Voting
- MASTER player calls: `masterRoleIsSetToVoteTime()`
- **Expected console output** (all players):
  ```
  Room update received: { type: "VOTE_STARTED" }
  🔄 Requesting active game after join/refresh...
  🎮 active_game received: { votes: {}, ... }
  ```

### Step 3: Cast Votes
- Player 1 calls: `playerVote("uuid-of-player-2")`
- **Expected console output** (Player 1):
  ```
  🗳️ Player is voting for: uuid-of-player-2
  🔄 Requesting active game after vote...
  ```
- **Expected console output** (ALL players):
  ```
  Room update received: { type: "VOTE_CAST" }
  🔄 Requesting active game data...
  🎮 active_game received: { 
    votes: { 
      "uuid-player-1": "uuid-of-player-2" 
    }
  }
  ```

### Step 4: More Votes
- Player 2 votes for Player 3
- Player 3 votes for Player 2
- **Expected** (all players see updated votes):
  ```json
  {
    "votes": {
      "uuid-player-1": "uuid-player-2",
      "uuid-player-2": "uuid-player-3",
      "uuid-player-3": "uuid-player-2"
    }
  }
  ```

### Step 5: Change Vote
- Player 1 votes again: `playerVote("uuid-of-player-3")`
- **Expected** (votes updated):
  ```json
  {
    "votes": {
      "uuid-player-1": "uuid-player-3",  // ← Changed!
      "uuid-player-2": "uuid-player-3",
      "uuid-player-3": "uuid-player-2"
    }
  }
  ```

## Debug Checklist

If `votes` is still empty `{}`:

### Backend Debug:
1. Check server logs when voting:
   ```
   Vote request from player={uuid} in room={code} for target={uuid}
   Broadcasted VOTE_CAST to room {code}
   ```

2. Verify `GameManager.recordVote()` is called:
   - Add breakpoint or log in `GameManager.recordVote()`
   - Check if `g.getVotes()` is updated

3. Verify response includes votes:
   - Check `currentGame()` log:
     ```
     Sent active_game to session={id} (room={code})
     ```
   - Add log to see payload:
     ```java
     log.info("Active game payload votes: {}", gameMap.get("votes"));
     ```

### Frontend Debug:
1. Check if `VOTE_CAST` is received:
   ```typescript
   console.log("Room update received:", update);
   // Should see: { type: "VOTE_CAST", ... }
   ```

2. Check if active_game request is sent:
   ```typescript
   console.log("🔄 Requesting active game after vote...");
   // Should appear after playerVote() and after VOTE_CAST
   ```

3. Check if active_game response is received:
   ```typescript
   console.log("🎮 active_game received:", game);
   // Should include votes: { ... }
   ```

4. Verify state update:
   ```typescript
   useEffect(() => {
     console.log("activeGame state updated:", activeGame);
   }, [activeGame]);
   ```

## Common Issues

### Issue 1: `votes` is `undefined`
**Cause**: Backend not sending votes field
**Fix**: ✅ Already fixed in `currentGame()` and `masterEnd()`

### Issue 2: `votes` is `{}` and never updates
**Cause**: Frontend not requesting updated activeGame after vote
**Fix**: Use updated hook with `VOTE_CAST` listener and auto-request

### Issue 3: Only voter sees updated votes
**Cause**: `VOTE_CAST` broadcast listener not implemented
**Fix**: Add `VOTE_CAST` check in room subscription

### Issue 4: Votes appear after delay
**Cause**: Normal - backend broadcasts → all clients request → server responds
**Expected**: ~100-200ms delay is normal

## Success Criteria ✅
- [ ] Votes appear immediately after casting (within 200ms)
- [ ] All players see the same votes
- [ ] Votes can be changed (latest vote overwrites)
- [ ] Votes persist after page refresh
- [ ] `activeGame.votes` is never `undefined` or empty after voting starts

## Next Steps
After votes work, you may want to:
1. Display vote counts to each player
2. Show who voted for whom (after voting ends)
3. Determine winner(s) based on vote tally
4. Broadcast `VOTE_FINISHED` when all players voted
5. Reveal roles at the end

