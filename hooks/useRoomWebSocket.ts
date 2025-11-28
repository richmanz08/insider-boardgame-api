import { useEffect, useRef, useState, useCallback } from "react";
import SockJS from "sockjs-client";
import { Client, IMessage } from "@stomp/stompjs";
import {
  PlayerData,
  RoomUpdateMessage,
  GamePrivateMessage,
  ActiveGame,
} from "./interface";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080/ws";

export function useRoomWebSocket(roomCode: string, playerUuid: string) {
  const clientRef = useRef<Client | null>(null);
  const [players, setPlayers] = useState<PlayerData[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<RoomUpdateMessage | null>(null);
  const [gamePrivateInfo, setGamePrivateInfo] =
    useState<GamePrivateMessage | null>(null);

  // NEW: activeGame snapshot (may contain cardOpened map)
  const [activeGame, setActiveGame] = useState<ActiveGame | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
      setIsConnected(true);

      // Subscribe to room updates
      client.subscribe(`/topic/room/${roomCode}`, (message: IMessage) => {
        const update: RoomUpdateMessage = JSON.parse(message.body);
        console.log("Room update received:", update);

        setLastUpdate(update);
        setPlayers(update.players);

        // If server included activeGame in the broadcast, use it directly (contains cardOpened)
        if (update.activeGame) {
          try {
            setActiveGame(update.activeGame as ActiveGame);
          } catch (e) {
            // fallback: request per-session snapshot
            if (clientRef.current?.connected && playerUuid) {
              clientRef.current.publish({
                destination: `/app/room/${roomCode}/active_game`,
                body: JSON.stringify({ playerUuid }),
              });
            }
          }
        } else if (update.type === "CARD_OPENED" || update.type === "GAME_STARTED") {
          // broadcast didn't include activeGame; request per-session snapshot
          if (clientRef.current?.connected && playerUuid) {
            clientRef.current.publish({
              destination: `/app/room/${roomCode}/active_game`,
              body: JSON.stringify({ playerUuid }),
            });
          }
        }
      });

      // Subscribe to per-user active_game response (server sends to /user/queue/active_game)
      client.subscribe("/user/queue/active_game", (message: IMessage) => {
        try {
          const payload = JSON.parse(message.body);
          // server might send either { game: {...} } or direct Game object
          const game = payload && payload.game ? payload.game : payload;
          console.log("active_game (user queue) received:", game);
          setActiveGame(game);
        } catch (err) {
          console.error("Failed parse active_game response:", err);
        }
      });

      // Subscribe to game private messages (role & word for MASTER/INSIDER)
      client.subscribe(`/user/queue/game_private`, (message: IMessage) => {
        const privateInfo: GamePrivateMessage = JSON.parse(message.body);
        console.log("Game private info received:", privateInfo);
        setGamePrivateInfo(privateInfo);
      });

      // join
      client.publish({
        destination: `/app/room/${roomCode}/join`,
        body: JSON.stringify({
          playerUuid,
          active: true,
        }),
      });

      // sync visibility after join
      setTimeout(() => {
        const isVisible = document.visibilityState === "visible";
        if (!isVisible && clientRef.current?.connected) {
          clientRef.current.publish({
            destination: `/app/room/${roomCode}/status`,
            body: JSON.stringify({
              playerUuid,
              active: false,
            }),
          });
        }
      }, 500);
    };

    client.onDisconnect = () => {
      setIsConnected(false);
    };

    client.onStompError = (frame) => {
      console.error("WebSocket error:", frame);
    };

    client.activate();
    clientRef.current = client;

    const handleVisibilityChange = () => {
      const isVisible = document.visibilityState === "visible";
      if (clientRef.current?.connected && playerUuid) {
        clientRef.current.publish({
          destination: `/app/room/${roomCode}/status`,
          body: JSON.stringify({
            playerUuid,
            active: isVisible,
          }),
        });
      }
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      if (clientRef.current?.connected && playerUuid) {
        try {
          clientRef.current.publish({
            destination: `/app/room/${roomCode}/leave`,
            body: JSON.stringify({ playerUuid }),
          });
        } catch (error) {
          console.error("Error sending leave message:", error);
        }
      }
      clientRef.current?.deactivate();
    };
  }, [roomCode, playerUuid]);

  // Toggle ready
  const toggleReady = useCallback(() => {
    if (clientRef.current && isConnected) {
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/ready`,
        body: JSON.stringify({ playerUuid }),
      });
    }
  }, [roomCode, playerUuid, isConnected]);

  // Start game (host)
  const startGame = useCallback(() => {
    if (clientRef.current && isConnected) {
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/start`,
        body: JSON.stringify({ triggerByUuid: playerUuid }),
      });
    }
  }, [roomCode, playerUuid, isConnected]);

  // Handle card opened (user action)
  const handleCardOpened = useCallback(() => {
    if (clientRef.current && isConnected) {
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/open_card`,
        body: JSON.stringify({ playerUuid }),
      });

      // Optionally also request snapshot immediately (redundant if server broadcasts CARD_OPENED)
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/active_game`,
        body: JSON.stringify({ playerUuid }),
      });
    }
  }, [roomCode, playerUuid, isConnected]);

  return {
    players,
    isConnected,
    lastUpdate,
    activeGame, // NEW: the per-user active game snapshot (may contain cardOpened map)
    gamePrivateInfo,
    toggleReady,
    startGame,
    handleCardOpened,
  };
}
