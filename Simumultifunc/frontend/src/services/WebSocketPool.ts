// frontend/src/services/WebSocketPool.ts
// Pool de WebSocket independants - chaque session a sa propre connexion isolee

type MessageHandler = (sessionId: string, message: any) => void;
type StateHandler = (sessionId: string, state: 'open' | 'close' | 'error', data?: any) => void;

interface WebSocketConnection {
  ws: WebSocket;
  sessionId: string;
  url: string;
  reconnectAttempts: number;
  heartbeatInterval: ReturnType<typeof setInterval> | null;
  isClosingIntentionally: boolean;
  messageHandler: MessageHandler;
  stateHandler: StateHandler;
}

/**
 * Pool de WebSocket pour gerer les connexions independantes par session
 * PRINCIPE: Connecter Session B ne deconnecte PAS Session A
 */
class WebSocketPool {
  private static instance: WebSocketPool;

  private connections: Map<string, WebSocketConnection> = new Map();

  private readonly maxReconnectAttempts = 5;
  private readonly reconnectBaseDelay = 2000;
  private readonly heartbeatIntervalMs = 30000;

  private constructor() {
    console.log('[WebSocketPool] Initialise');

    // Nettoyer a la fermeture de la page
    if (typeof window !== 'undefined') {
      window.addEventListener('beforeunload', () => this.disconnectAll());
    }
  }

  static getInstance(): WebSocketPool {
    if (!WebSocketPool.instance) {
      WebSocketPool.instance = new WebSocketPool();
    }
    return WebSocketPool.instance;
  }

  /**
   * Connecte une session - INDEPENDANT des autres sessions
   * Ne deconnecte jamais une autre session
   */
  connect(
    sessionId: string,
    url: string,
    chargePointId: string,
    onMessage: MessageHandler,
    onStateChange: StateHandler
  ): boolean {
    // Verifier si deja connecte
    const existing = this.connections.get(sessionId);
    if (existing && existing.ws.readyState === WebSocket.OPEN) {
      console.log(`[WebSocketPool] ${sessionId} deja connecte`);
      return true;
    }

    // Fermer l'ancienne connexion de CETTE session uniquement
    if (existing) {
      this.disconnectSession(sessionId, true);
    }

    // Construire l'URL complete
    const fullUrl = url.endsWith('/')
      ? `${url}${chargePointId}`
      : `${url}/${chargePointId}`;

    console.log(`[WebSocketPool] Connexion ${sessionId} -> ${fullUrl}`);

    try {
      const ws = new WebSocket(fullUrl, ['ocpp1.6']);

      const connection: WebSocketConnection = {
        ws,
        sessionId,
        url: fullUrl,
        reconnectAttempts: 0,
        heartbeatInterval: null,
        isClosingIntentionally: false,
        messageHandler: onMessage,
        stateHandler: onStateChange,
      };

      // === EVENEMENTS WEBSOCKET ===

      ws.onopen = () => {
        console.log(`[WebSocketPool] ${sessionId} OUVERT`);
        connection.reconnectAttempts = 0;
        this.startHeartbeat(sessionId);
        onStateChange(sessionId, 'open');
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          onMessage(sessionId, data);
        } catch (e) {
          console.error(`[WebSocketPool] ${sessionId} Erreur parsing:`, e);
        }
      };

      ws.onerror = (error) => {
        console.error(`[WebSocketPool] ${sessionId} ERREUR:`, error);
        onStateChange(sessionId, 'error', error);
      };

      ws.onclose = (event) => {
        console.log(`[WebSocketPool] ${sessionId} FERME (code: ${event.code})`);
        this.stopHeartbeat(sessionId);

        // Reconnexion automatique si fermeture non intentionnelle
        if (!connection.isClosingIntentionally &&
            connection.reconnectAttempts < this.maxReconnectAttempts) {
          connection.reconnectAttempts++;
          const delay = this.reconnectBaseDelay * connection.reconnectAttempts;
          console.log(`[WebSocketPool] ${sessionId} Reconnexion dans ${delay}ms (tentative ${connection.reconnectAttempts})`);

          setTimeout(() => {
            const conn = this.connections.get(sessionId);
            if (conn && !conn.isClosingIntentionally) {
              const urlParts = fullUrl.split('/');
              const cpId = urlParts.pop() || '';
              const baseUrl = urlParts.join('/');
              this.connect(sessionId, baseUrl, cpId, conn.messageHandler, conn.stateHandler);
            }
          }, delay);
        } else {
          onStateChange(sessionId, 'close', { code: event.code, reason: event.reason });
          this.connections.delete(sessionId);
        }
      };

      this.connections.set(sessionId, connection);
      return true;

    } catch (error) {
      console.error(`[WebSocketPool] ${sessionId} Erreur creation:`, error);
      onStateChange(sessionId, 'error', error);
      return false;
    }
  }

  /**
   * Envoie un message sur une session specifique
   * N'affecte pas les autres sessions
   */
  send(sessionId: string, message: any): boolean {
    const connection = this.connections.get(sessionId);
    if (!connection || connection.ws.readyState !== WebSocket.OPEN) {
      console.warn(`[WebSocketPool] ${sessionId} pas connecte, impossible d'envoyer`);
      return false;
    }

    try {
      const messageStr = typeof message === 'string' ? message : JSON.stringify(message);
      connection.ws.send(messageStr);
      return true;
    } catch (error) {
      console.error(`[WebSocketPool] ${sessionId} Erreur envoi:`, error);
      return false;
    }
  }

  /**
   * Deconnecte UNE session sans affecter les autres
   * C'est la cle de l'isolation multi-session
   */
  disconnectSession(sessionId: string, intentional: boolean = true): void {
    const connection = this.connections.get(sessionId);
    if (!connection) return;

    console.log(`[WebSocketPool] Deconnexion ${sessionId} (intentionnelle: ${intentional})`);

    connection.isClosingIntentionally = intentional;
    this.stopHeartbeat(sessionId);

    if (connection.ws.readyState === WebSocket.OPEN ||
        connection.ws.readyState === WebSocket.CONNECTING) {
      connection.ws.close(1000, 'Deconnexion utilisateur');
    }

    this.connections.delete(sessionId);
  }

  /**
   * Deconnecte TOUTES les sessions (ex: fermeture de page)
   */
  disconnectAll(): void {
    console.log(`[WebSocketPool] Deconnexion de ${this.connections.size} sessions`);
    const sessionIds = Array.from(this.connections.keys());
    sessionIds.forEach(id => this.disconnectSession(id, true));
  }

  /**
   * Verifie si une session est connectee
   */
  isConnected(sessionId: string): boolean {
    const connection = this.connections.get(sessionId);
    return connection?.ws.readyState === WebSocket.OPEN;
  }

  /**
   * Retourne le nombre de connexions actives
   */
  getActiveCount(): number {
    return Array.from(this.connections.values())
      .filter(c => c.ws.readyState === WebSocket.OPEN)
      .length;
  }

  /**
   * Retourne les IDs des sessions connectees
   */
  getConnectedSessionIds(): string[] {
    return Array.from(this.connections.entries())
      .filter(([_, c]) => c.ws.readyState === WebSocket.OPEN)
      .map(([id]) => id);
  }

  // === HEARTBEAT ===

  private startHeartbeat(sessionId: string): void {
    const connection = this.connections.get(sessionId);
    if (!connection) return;

    connection.heartbeatInterval = setInterval(() => {
      if (connection.ws.readyState === WebSocket.OPEN) {
        // Envoyer Heartbeat OCPP
        const heartbeat = [2, `hb-${Date.now()}`, 'Heartbeat', {}];
        connection.ws.send(JSON.stringify(heartbeat));
      }
    }, this.heartbeatIntervalMs);
  }

  private stopHeartbeat(sessionId: string): void {
    const connection = this.connections.get(sessionId);
    if (connection?.heartbeatInterval) {
      clearInterval(connection.heartbeatInterval);
      connection.heartbeatInterval = null;
    }
  }

  /**
   * Debug: retourne l'etat de toutes les connexions
   */
  getDebugInfo(): { sessionId: string; url: string; state: string; reconnectAttempts: number }[] {
    return Array.from(this.connections.entries()).map(([id, conn]) => ({
      sessionId: id,
      url: conn.url,
      state: ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'][conn.ws.readyState],
      reconnectAttempts: conn.reconnectAttempts
    }));
  }
}

// Export singleton
export const wsPool = WebSocketPool.getInstance();
export default wsPool;
