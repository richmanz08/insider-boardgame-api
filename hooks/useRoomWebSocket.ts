

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
