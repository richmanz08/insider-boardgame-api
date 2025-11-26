# Frontend Integration Guide - Next.js + TypeScript

## 📦 Installation

```bash
npm install sockjs-client @stomp/stompjs
npm install --save-dev @types/sockjs-client
```

---

## 🎯 TypeScript Types

Create `types/room.ts`:

```typescript
// types/room.ts

export interface Player {
  uuid: string;
  playerName: string;
  isHost: boolean;
  isReady: boolean;
  joinedAt: string;
}

export interface Room {
  roomCode: string;
  roomName: string;
  maxPlayers: number;
  currentPlayers: number;
  hasPassword: boolean;
  status: 'WAITING' | 'PLAYING' | 'FINISHED';
  hostUuid: string;
  hostName: string;
  createdAt: string;
}

export interface RoomUpdateMessage {
  type: 'PLAYER_JOINED' | 'PLAYER_LEFT' | 'PLAYER_READY' | 'ROOM_UPDATE';
  roomCode: string;
  roomName: string;
  maxPlayers: number;
  currentPlayers: number;
  status: string;
  players: Player[];
  message: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  status: string;
}
```

---

## 🔧 API Service

Create `services/roomApi.ts`:

```typescript
// services/roomApi.ts

import { ApiResponse, Room, Player } from '@/types/room';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export const roomApi = {
  // Create room
  async createRoom(data: {
    roomName: string;
    maxPlayers: number;
    password?: string;
    hostUuid: string;
    hostName: string;
  }): Promise<ApiResponse<Room>> {
    const response = await fetch(`${API_BASE_URL}/api/room/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return response.json();
  },

  // Join room
  async joinRoom(data: {
    roomCode: string;
    password?: string;
    playerUuid: string;
    playerName: string;
  }): Promise<ApiResponse<Room>> {
    const response = await fetch(`${API_BASE_URL}/api/room/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return response.json();
  },

  // Leave room
  async leaveRoom(data: {
    roomCode: string;
    playerUuid: string;
  }): Promise<ApiResponse<Room>> {
    const response = await fetch(`${API_BASE_URL}/api/room/leave`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return response.json();
  },

  // Get available rooms
  async getAvailableRooms(): Promise<ApiResponse<Room[]>> {
    const response = await fetch(`${API_BASE_URL}/api/room/available`);
    return response.json();
  },

  // Get room by code
  async getRoomByCode(roomCode: string): Promise<ApiResponse<Room>> {
    const response = await fetch(`${API_BASE_URL}/api/room/${roomCode}`);
    return response.json();
  },

  // Get players in room
  async getRoomPlayers(roomCode: string): Promise<ApiResponse<Player[]>> {
    const response = await fetch(`${API_BASE_URL}/api/room/${roomCode}/players`);
    return response.json();
  },
};
```

---

## 🔌 WebSocket Hook

Create `hooks/useRoomWebSocket.ts`:

```typescript
// hooks/useRoomWebSocket.ts

import { useEffect, useRef, useState, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client, IMessage } from '@stomp/stompjs';
import { RoomUpdateMessage, Player } from '@/types/room';

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080/ws';

export function useRoomWebSocket(roomCode: string, playerUuid: string) {
  const clientRef = useRef<Client | null>(null);
  const [players, setPlayers] = useState<Player[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<RoomUpdateMessage | null>(null);

  // Connect to WebSocket
  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      debug: (str) => {
        console.log('STOMP: ' + str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
      console.log('WebSocket Connected!');
      setIsConnected(true);

      // Subscribe to room updates
      client.subscribe(`/topic/room/${roomCode}`, (message: IMessage) => {
        const update: RoomUpdateMessage = JSON.parse(message.body);
        console.log('Room update received:', update);
        
        setLastUpdate(update);
        setPlayers(update.players);
      });
    };

    client.onDisconnect = () => {
      console.log('WebSocket Disconnected');
      setIsConnected(false);
    };

    client.onStompError = (frame) => {
      console.error('WebSocket error:', frame);
    };

    client.activate();
    clientRef.current = client;

    // Cleanup on unmount
    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, [roomCode]);

  // Toggle ready status
  const toggleReady = useCallback(() => {
    if (clientRef.current && isConnected) {
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/ready`,
        body: JSON.stringify({ playerUuid }),
      });
    }
  }, [roomCode, playerUuid, isConnected]);

  return {
    players,
    isConnected,
    lastUpdate,
    toggleReady,
  };
}
```

---

## 🎮 Room Page Component

Create `app/room/[roomCode]/page.tsx`:

```typescript
// app/room/[roomCode]/page.tsx

'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useRoomWebSocket } from '@/hooks/useRoomWebSocket';
import { roomApi } from '@/services/roomApi';
import { Room, Player } from '@/types/room';

export default function RoomPage() {
  const params = useParams();
  const router = useRouter();
  const roomCode = params.roomCode as string;
  
  const [room, setRoom] = useState<Room | null>(null);
  const [playerUuid] = useState(() => localStorage.getItem('playerUuid') || '');
  const [playerName] = useState(() => localStorage.getItem('playerName') || '');
  
  const { players, isConnected, lastUpdate, toggleReady } = useRoomWebSocket(roomCode, playerUuid);

  // Load initial room data
  useEffect(() => {
    loadRoomData();
  }, [roomCode]);

  // Update room when WebSocket message received
  useEffect(() => {
    if (lastUpdate) {
      setRoom((prev) => ({
        ...prev!,
        currentPlayers: lastUpdate.currentPlayers,
        status: lastUpdate.status as any,
      }));
    }
  }, [lastUpdate]);

  const loadRoomData = async () => {
    try {
      const response = await roomApi.getRoomByCode(roomCode);
      if (response.success) {
        setRoom(response.data);
      }
    } catch (error) {
      console.error('Failed to load room:', error);
    }
  };

  const handleLeaveRoom = async () => {
    try {
      await roomApi.leaveRoom({ roomCode, playerUuid });
      router.push('/');
    } catch (error) {
      console.error('Failed to leave room:', error);
    }
  };

  const handleToggleReady = () => {
    toggleReady();
  };

  if (!room) {
    return <div className="p-8">Loading room...</div>;
  }

  const currentPlayer = players.find((p) => p.uuid === playerUuid);

  return (
    <div className="min-h-screen bg-gray-100 p-8">
      <div className="max-w-4xl mx-auto">
        {/* Room Header */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <div className="flex justify-between items-center">
            <div>
              <h1 className="text-3xl font-bold">{room.roomName}</h1>
              <p className="text-gray-600">Room Code: {room.roomCode}</p>
            </div>
            <div className="text-right">
              <p className="text-lg">
                {room.currentPlayers} / {room.maxPlayers} Players
              </p>
              <span className={`inline-block px-3 py-1 rounded-full text-sm ${
                isConnected ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
              }`}>
                {isConnected ? '🟢 Connected' : '🔴 Disconnected'}
              </span>
            </div>
          </div>
        </div>

        {/* Players List */}
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <h2 className="text-2xl font-bold mb-4">Players</h2>
          <div className="space-y-3">
            {players.map((player) => (
              <div
                key={player.uuid}
                className={`flex items-center justify-between p-4 rounded-lg ${
                  player.uuid === playerUuid
                    ? 'bg-blue-50 border-2 border-blue-300'
                    : 'bg-gray-50'
                }`}
              >
                <div className="flex items-center gap-3">
                  <div className={`w-3 h-3 rounded-full ${
                    player.isReady ? 'bg-green-500' : 'bg-gray-300'
                  }`} />
                  <span className="font-semibold">{player.playerName}</span>
                  {player.isHost && (
                    <span className="text-yellow-600 text-xl">👑</span>
                  )}
                  {player.uuid === playerUuid && (
                    <span className="text-blue-600 text-sm">(You)</span>
                  )}
                </div>
                <div>
                  {player.isReady ? (
                    <span className="text-green-600 font-semibold">✅ Ready</span>
                  ) : (
                    <span className="text-gray-400">⏳ Not Ready</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Actions */}
        <div className="bg-white rounded-lg shadow-md p-6">
          <div className="flex gap-4">
            <button
              onClick={handleToggleReady}
              disabled={!isConnected}
              className={`flex-1 py-3 px-6 rounded-lg font-semibold transition ${
                currentPlayer?.isReady
                  ? 'bg-yellow-500 hover:bg-yellow-600 text-white'
                  : 'bg-green-500 hover:bg-green-600 text-white'
              } disabled:opacity-50 disabled:cursor-not-allowed`}
            >
              {currentPlayer?.isReady ? 'Cancel Ready' : 'Ready Up!'}
            </button>
            <button
              onClick={handleLeaveRoom}
              className="px-6 py-3 bg-red-500 hover:bg-red-600 text-white rounded-lg font-semibold transition"
            >
              Leave Room
            </button>
          </div>
        </div>

        {/* Debug Info */}
        {lastUpdate && (
          <div className="mt-6 bg-gray-800 text-white rounded-lg p-4">
            <p className="text-sm font-mono">
              Last Update: {lastUpdate.type} - {lastUpdate.message}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
```

---

## 🏠 Room List Component

Create `app/page.tsx`:

```typescript
// app/page.tsx

'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { roomApi } from '@/services/roomApi';
import { Room } from '@/types/room';

export default function HomePage() {
  const router = useRouter();
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRooms();
    const interval = setInterval(loadRooms, 5000); // Refresh every 5s
    return () => clearInterval(interval);
  }, []);

  const loadRooms = async () => {
    try {
      const response = await roomApi.getAvailableRooms();
      if (response.success) {
        setRooms(response.data);
      }
    } catch (error) {
      console.error('Failed to load rooms:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleJoinRoom = (roomCode: string) => {
    router.push(`/room/${roomCode}`);
  };

  return (
    <div className="min-h-screen bg-gray-100 p-8">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-4xl font-bold mb-8">Available Rooms</h1>
        
        {loading ? (
          <p>Loading rooms...</p>
        ) : rooms.length === 0 ? (
          <p className="text-gray-600">No rooms available. Create one!</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {rooms.map((room) => (
              <div
                key={room.roomCode}
                className="bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition"
              >
                <h3 className="text-xl font-bold mb-2">{room.roomName}</h3>
                <p className="text-gray-600 mb-2">Code: {room.roomCode}</p>
                <div className="flex items-center justify-between mb-4">
                  <span className="text-sm">
                    {room.currentPlayers} / {room.maxPlayers} players
                  </span>
                  {room.hasPassword && <span>🔒</span>}
                </div>
                <button
                  onClick={() => handleJoinRoom(room.roomCode)}
                  className="w-full py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg font-semibold transition"
                >
                  Join Room
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
```

---

## 🔧 Environment Variables

Create `.env.local`:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=http://localhost:8080/ws
```

---

## 📝 Usage Flow

```
1. User opens homepage
   └─> Calls GET /api/room/available
   └─> Shows list of rooms

2. User clicks "Join Room"
   └─> Calls POST /api/room/join
   └─> Redirects to /room/[roomCode]

3. Room page loads
   └─> Connects to WebSocket ws://localhost:8080/ws
   └─> Subscribes to /topic/room/{roomCode}
   └─> Receives real-time updates

4. User clicks "Ready"
   └─> Sends message to /app/room/{roomCode}/ready
   └─> Server broadcasts to all players
   └─> All players see updated ready status

5. User leaves room
   └─> Calls POST /api/room/leave
   └─> Disconnects WebSocket
   └─> Returns to homepage
```

---

## ✅ Features

✅ Real-time player list updates  
✅ Ready status toggle via WebSocket  
✅ Auto-reconnect on connection loss  
✅ TypeScript type safety  
✅ Responsive UI with Tailwind CSS  
✅ Auto-refresh room list every 5 seconds  

---

## 🚀 Run Frontend

```bash
npm install
npm run dev
```

Open `http://localhost:3000`

