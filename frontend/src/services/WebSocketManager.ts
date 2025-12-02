// frontend/src/services/WebSocketManager.ts
// Singleton pour gérer les connexions WebSocket persistantes par session

import { useSessionStore, type SessionWithChartData } from '@/store/sessionStore';

// Types pour les messages WebSocket
interface WebSocketMessage {
    type: string;
    sessionId?: string;
    data?: any;
}

interface ConnectionState {
    ws: WebSocket | null;
    sessionId: string;
    url: string;
    reconnectAttempts: number;
    reconnectTimer: NodeJS.Timeout | null;
    lastActivity: number;
    isIntentionallyClosed: boolean;
}

type ConnectionHandler = (sessionId: string, connected: boolean) => void;
type MessageHandler = (sessionId: string, message: WebSocketMessage) => void;

class WebSocketManager {
    private static instance: WebSocketManager | null = null;
    private connections: Map<string, ConnectionState> = new Map();
    private connectionHandlers: ConnectionHandler[] = [];
    private messageHandlers: MessageHandler[] = [];
    private keepaliveInterval: NodeJS.Timeout | null = null;
    private visibilityHandler: (() => void) | null = null;
    private isBackgrounded: boolean = false;

    // Configuration
    private readonly RECONNECT_BASE_DELAY = 2000;
    private readonly RECONNECT_MAX_DELAY = 30000;
    private readonly MAX_RECONNECT_ATTEMPTS = 10;
    private readonly KEEPALIVE_INTERVAL = 15000; // 15 secondes
    private readonly PING_INTERVAL = 30000; // 30 secondes

    private constructor() {
        this.setupVisibilityHandler();
        this.startKeepaliveLoop();
        console.log('[WebSocketManager] Initialized');
    }

    static getInstance(): WebSocketManager {
        if (!WebSocketManager.instance) {
            WebSocketManager.instance = new WebSocketManager();
        }
        return WebSocketManager.instance;
    }

    // =========================================================================
    // Connection Management
    // =========================================================================

    connect(sessionId: string, url: string): void {
        // Vérifier si déjà connecté
        const existing = this.connections.get(sessionId);
        if (existing?.ws?.readyState === WebSocket.OPEN) {
            console.log('[WebSocketManager] Session', sessionId, 'already connected');
            return;
        }

        // Nettoyer l'ancienne connexion si elle existe
        if (existing) {
            this.cleanupConnection(sessionId);
        }

        console.log('[WebSocketManager] Connecting session', sessionId, 'to', url);

        const state: ConnectionState = {
            ws: null,
            sessionId,
            url,
            reconnectAttempts: 0,
            reconnectTimer: null,
            lastActivity: Date.now(),
            isIntentionallyClosed: false
        };

        this.connections.set(sessionId, state);
        this.createWebSocket(sessionId);
    }

    private createWebSocket(sessionId: string): void {
        const state = this.connections.get(sessionId);
        if (!state || state.isIntentionallyClosed) return;

        try {
            const ws = new WebSocket(state.url);
            state.ws = ws;

            ws.onopen = () => {
                console.log('[WebSocketManager] Session', sessionId, 'connected');
                state.reconnectAttempts = 0;
                state.lastActivity = Date.now();

                // Notifier les handlers
                this.connectionHandlers.forEach(h => h(sessionId, true));

                // Mettre à jour le store
                useSessionStore.getState().updateSessionFromWebSocket({
                    sessionId,
                    data: { connected: true }
                });
            };

            ws.onmessage = (event) => {
                state.lastActivity = Date.now();
                try {
                    const message = JSON.parse(event.data);
                    this.handleMessage(sessionId, message);
                } catch (error) {
                    console.error('[WebSocketManager] Failed to parse message:', error);
                }
            };

            ws.onerror = (error) => {
                console.error('[WebSocketManager] Session', sessionId, 'error:', error);
            };

            ws.onclose = (event) => {
                console.log('[WebSocketManager] Session', sessionId, 'closed:', event.code, event.reason);

                // Notifier les handlers
                this.connectionHandlers.forEach(h => h(sessionId, false));

                // Mettre à jour le store
                useSessionStore.getState().updateSessionFromWebSocket({
                    sessionId,
                    data: { connected: false }
                });

                // Tenter une reconnexion si ce n'est pas volontaire
                if (!state.isIntentionallyClosed) {
                    this.scheduleReconnect(sessionId);
                }
            };
        } catch (error) {
            console.error('[WebSocketManager] Failed to create WebSocket:', error);
            this.scheduleReconnect(sessionId);
        }
    }

    private scheduleReconnect(sessionId: string): void {
        const state = this.connections.get(sessionId);
        if (!state || state.isIntentionallyClosed) return;

        // Vérifier si le voluntaryStop est actif
        const session = useSessionStore.getState().getSession(sessionId);
        if (session?.voluntaryStop) {
            console.log('[WebSocketManager] Session', sessionId, 'has voluntaryStop, not reconnecting');
            return;
        }

        if (state.reconnectAttempts >= this.MAX_RECONNECT_ATTEMPTS) {
            console.log('[WebSocketManager] Max reconnect attempts reached for', sessionId);
            return;
        }

        // Exponential backoff
        const delay = Math.min(
            this.RECONNECT_BASE_DELAY * Math.pow(2, state.reconnectAttempts),
            this.RECONNECT_MAX_DELAY
        );

        console.log('[WebSocketManager] Scheduling reconnect for', sessionId, 'in', delay, 'ms');

        state.reconnectTimer = setTimeout(() => {
            state.reconnectAttempts++;
            state.reconnectTimer = null;
            this.createWebSocket(sessionId);
        }, delay);
    }

    disconnect(sessionId: string, voluntary: boolean = true): void {
        const state = this.connections.get(sessionId);
        if (!state) return;

        console.log('[WebSocketManager] Disconnecting session', sessionId, 'voluntary:', voluntary);

        state.isIntentionallyClosed = voluntary;

        // Si volontaire, marquer la session
        if (voluntary) {
            useSessionStore.getState().markVoluntaryStop(sessionId);
        }

        this.cleanupConnection(sessionId);
    }

    private cleanupConnection(sessionId: string): void {
        const state = this.connections.get(sessionId);
        if (!state) return;

        if (state.reconnectTimer) {
            clearTimeout(state.reconnectTimer);
            state.reconnectTimer = null;
        }

        if (state.ws) {
            state.ws.onopen = null;
            state.ws.onmessage = null;
            state.ws.onerror = null;
            state.ws.onclose = null;
            if (state.ws.readyState === WebSocket.OPEN || state.ws.readyState === WebSocket.CONNECTING) {
                state.ws.close();
            }
            state.ws = null;
        }

        this.connections.delete(sessionId);
    }

    disconnectAll(voluntary: boolean = false): void {
        console.log('[WebSocketManager] Disconnecting all sessions, voluntary:', voluntary);
        for (const sessionId of this.connections.keys()) {
            this.disconnect(sessionId, voluntary);
        }
    }

    // =========================================================================
    // Message Handling
    // =========================================================================

    private handleMessage(sessionId: string, message: WebSocketMessage): void {
        // Notifier les handlers personnalisés
        this.messageHandlers.forEach(h => h(sessionId, message));

        // Traitement par défaut selon le type
        const store = useSessionStore.getState();

        switch (message.type) {
            case 'SESSION_UPDATE':
                if (message.data) {
                    store.updateSessionFromWebSocket({
                        sessionId,
                        data: message.data
                    });
                }
                break;

            case 'OCPP_MESSAGE':
                if (message.data) {
                    store.addLog(sessionId, {
                        timestamp: new Date().toISOString(),
                        level: 'info',
                        category: message.data.direction === 'TX' ? 'OCPP_TX' : 'OCPP_RX',
                        message: `${message.data.action}: ${JSON.stringify(message.data.payload).substring(0, 100)}...`
                    });
                }
                break;

            case 'CHART_UPDATE':
                // Géré par le store si nécessaire
                break;

            case 'LOG_ENTRY':
                if (message.data) {
                    store.addLog(sessionId, message.data);
                }
                break;

            case 'pong':
                // Réponse au ping, session active
                break;

            default:
                console.debug('[WebSocketManager] Unhandled message type:', message.type);
        }
    }

    send(sessionId: string, message: any): boolean {
        const state = this.connections.get(sessionId);
        if (state?.ws?.readyState === WebSocket.OPEN) {
            state.ws.send(JSON.stringify(message));
            state.lastActivity = Date.now();
            return true;
        }
        console.warn('[WebSocketManager] Cannot send to session', sessionId, '- not connected');
        return false;
    }

    // =========================================================================
    // Visibility & Keepalive
    // =========================================================================

    private setupVisibilityHandler(): void {
        this.visibilityHandler = () => {
            const isHidden = document.hidden;
            console.log('[WebSocketManager] Visibility changed:', isHidden ? 'hidden' : 'visible');

            this.isBackgrounded = isHidden;

            // Notifier le backend du changement de visibilité
            const store = useSessionStore.getState();
            for (const [sessionId, state] of this.connections) {
                if (state.ws?.readyState === WebSocket.OPEN) {
                    store.setBackgrounded(sessionId, isHidden);
                }
            }

            // Si on revient visible, envoyer un keepalive immédiat
            if (!isHidden) {
                store.sendKeepaliveAll();
            }
        };

        document.addEventListener('visibilitychange', this.visibilityHandler);
    }

    private startKeepaliveLoop(): void {
        this.keepaliveInterval = setInterval(() => {
            this.sendKeepaliveAll();
        }, this.KEEPALIVE_INTERVAL);
    }

    private sendKeepaliveAll(): void {
        // Envoyer un keepalive HTTP à toutes les sessions actives
        useSessionStore.getState().sendKeepaliveAll();

        // Envoyer un ping WebSocket
        for (const [sessionId, state] of this.connections) {
            if (state.ws?.readyState === WebSocket.OPEN && !state.isIntentionallyClosed) {
                this.send(sessionId, { type: 'ping', timestamp: Date.now() });
            }
        }
    }

    // =========================================================================
    // Event Handlers
    // =========================================================================

    onConnection(handler: ConnectionHandler): () => void {
        this.connectionHandlers.push(handler);
        return () => {
            const index = this.connectionHandlers.indexOf(handler);
            if (index > -1) {
                this.connectionHandlers.splice(index, 1);
            }
        };
    }

    onMessage(handler: MessageHandler): () => void {
        this.messageHandlers.push(handler);
        return () => {
            const index = this.messageHandlers.indexOf(handler);
            if (index > -1) {
                this.messageHandlers.splice(index, 1);
            }
        };
    }

    // =========================================================================
    // Status & Cleanup
    // =========================================================================

    isConnected(sessionId: string): boolean {
        const state = this.connections.get(sessionId);
        return state?.ws?.readyState === WebSocket.OPEN;
    }

    getConnectionCount(): number {
        let count = 0;
        for (const state of this.connections.values()) {
            if (state.ws?.readyState === WebSocket.OPEN) {
                count++;
            }
        }
        return count;
    }

    getSessionIds(): string[] {
        return Array.from(this.connections.keys());
    }

    destroy(): void {
        console.log('[WebSocketManager] Destroying...');

        if (this.keepaliveInterval) {
            clearInterval(this.keepaliveInterval);
            this.keepaliveInterval = null;
        }

        if (this.visibilityHandler) {
            document.removeEventListener('visibilitychange', this.visibilityHandler);
            this.visibilityHandler = null;
        }

        // Ne pas utiliser voluntary=true pour éviter de marquer les sessions comme arrêtées
        this.disconnectAll(false);

        this.connectionHandlers = [];
        this.messageHandlers = [];

        WebSocketManager.instance = null;
    }
}

// Export singleton
export const wsManager = WebSocketManager.getInstance();

// Hook pour utiliser le manager dans React
export function useWebSocketManager() {
    return wsManager;
}

export default WebSocketManager;
