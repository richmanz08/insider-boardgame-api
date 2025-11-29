import { useEffect, useRef, useState, useCallback } from "react";
import SockJS from "sockjs-client";
import { Client, IMessage } from "@stomp/stompjs";
import {
  RoomUpdateMessage,
  // GamePrivateMessage,
  ActiveGame,
} from "./interface";
import { isUndefined } from "lodash";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "http://localhost:8080/ws";

export function useRoomWebSocket(roomCode: string, playerUuid: string) {
  const clientRef = useRef<Client | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [room, setRoom] = useState<RoomUpdateMessage | null>(null);
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
      console.log(
        "🔌 WebSocket Connected for room:",
        roomCode,
        "player:",
        playerUuid
      );
      setIsConnected(true);

      // Subscribe to room updates
      client.subscribe(`/topic/room/${roomCode}`, (message: IMessage) => {
        const update: RoomUpdateMessage = JSON.parse(message.body);
        console.log("Room update received:", update);

        setRoom(update);
        // setPlayers(update.players);

        // QUICK-ON-DEMAND FLOW:
        // Request per-user active game snapshot when:
        //  - the server announced CARD_OPENED (someone opened a card),
        //  - OR the game started (GAME_STARTED) — we need the full game to get roles/cardOpened/endsAt,
        //  - OR the broadcast included an activeGame summary (safety).
        //  - OR a vote was cast (VOTE_CAST) — to see updated votes
        if (
          update.type === "CARD_OPENED" ||
          update.type === "GAME_STARTED" ||
          update.type === "VOTE_CAST" ||
          update.type === "VOTE_STARTED" ||
          (update.activeGame !== undefined && update.activeGame !== null)
        ) {
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
        console.log("🎯 @@@ active_game message received:", message.body);

        try {
          const payload = JSON.parse(message.body);
          // server might send either { game: {...} } or direct Game object
          const game = payload && payload.game ? payload.game : payload;
          console.log("🎮 active_game (user queue) received:", game);
          if (isUndefined(game?.game)) {
            setActiveGame(game);
            // setGamePrivateInfo(game.privateMessage || null);
          }
        } catch (err) {
          console.error("❌ Failed parse active_game response:", err);
        }
      });

      // join
      client.publish({
        destination: `/app/room/${roomCode}/join`,
        body: JSON.stringify({
          playerUuid,
          active: true,
        }),
      });

      // ⭐ ALWAYS request active game data after join (especially important on refresh)
      setTimeout(() => {
        console.log("🔄 Requesting active game data after join/refresh...");
        if (clientRef.current?.connected && playerUuid) {
          clientRef.current.publish({
            destination: `/app/room/${roomCode}/active_game`,
            body: JSON.stringify({ playerUuid }),
          });
        }
      }, 1000); // Wait a bit longer for join to complete

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
      console.log("🃏 Opening card for player:", playerUuid);
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

  // 🆘 Manual function to request active game (for debugging)
  const requestActiveGame = useCallback(() => {
    if (clientRef.current && isConnected) {
      console.log("🔄 Manually requesting active game data...");
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/active_game`,
        body: JSON.stringify({ playerUuid }),
      });
    } else {
      console.warn("⚠️ Cannot request active game - WebSocket not connected");
    }
  }, [roomCode, playerUuid, isConnected]);

  const masterRoleIsSetToVoteTime = useCallback(() => {
    if (clientRef.current && isConnected) {
      console.log("⏰ Master is setting to vote time...");
      clientRef.current.publish({
        destination: `/app/room/${roomCode}/master_end`,
        body: JSON.stringify({ playerUuid }),
      });
    }
  }, [roomCode, playerUuid, isConnected]);

  const playerVote = useCallback(
    (targetPlayerUuid: string) => {
      if (clientRef.current && isConnected) {
        console.log("🗳️ Player is voting for:", targetPlayerUuid);
        clientRef.current.publish({
          destination: `/app/room/${roomCode}/vote`,
          body: JSON.stringify({ playerUuid, targetPlayerUuid }),
        });

        // Immediately request active game to see updated votes
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

  return {
    isConnected,
    room,
    activeGame, // NEW: the per-user active game snapshot (may contain cardOpened map and votes)
    // gamePrivateInfo,
    toggleReady,
    startGame,
    handleCardOpened,
    requestActiveGame, // 🆘 For debugging - manually request active game data
    masterRoleIsSetToVoteTime,
    playerVote,
  };
}

