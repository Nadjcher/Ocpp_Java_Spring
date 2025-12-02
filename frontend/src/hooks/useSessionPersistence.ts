// frontend/src/hooks/useSessionPersistence.ts
// Hook pour gérer la persistance des sessions WebSocket

import { useEffect, useRef, useCallback } from 'react';
import { useSessionStore } from '@/store/sessionStore';
import { wsManager } from '@/services/WebSocketManager';

interface UseSessionPersistenceOptions {
    autoReconnect?: boolean;
    keepaliveIntervalMs?: number;
    onConnectionChange?: (sessionId: string, connected: boolean) => void;
}

export function useSessionPersistence(options: UseSessionPersistenceOptions = {}) {
    const {
        autoReconnect = true,
        keepaliveIntervalMs = 15000,
        onConnectionChange
    } = options;

    const store = useSessionStore();
    const keepaliveTimerRef = useRef<NodeJS.Timeout | null>(null);
    const reconnectTimerRef = useRef<NodeJS.Timeout | null>(null);
    const isInitializedRef = useRef(false);

    // =========================================================================
    // Initialisation
    // =========================================================================

    useEffect(() => {
        if (isInitializedRef.current) return;
        isInitializedRef.current = true;

        console.log('[useSessionPersistence] Initializing...');

        // Charger les sessions depuis le backend
        store.loadSessions().then(() => {
            // Restaurer les sessions persistées
            store.restorePersistedSessions();

            // Auto-reconnecter les sessions non volontairement déconnectées
            if (autoReconnect) {
                const reconnectable = store.getReconnectableSessions();
                console.log('[useSessionPersistence] Found', reconnectable.length, 'reconnectable sessions');

                for (const session of reconnectable) {
                    if (session.url && !session.voluntaryStop) {
                        console.log('[useSessionPersistence] Auto-reconnecting session:', session.id);
                        wsManager.connect(session.id, session.url);
                    }
                }
            }
        });

        // Écouter les changements de connexion
        const unsubscribeConnection = wsManager.onConnection((sessionId, connected) => {
            console.log('[useSessionPersistence] Connection change:', sessionId, connected);
            onConnectionChange?.(sessionId, connected);
        });

        // Cleanup
        return () => {
            unsubscribeConnection();
        };
    }, [autoReconnect, onConnectionChange]);

    // =========================================================================
    // Keepalive Loop
    // =========================================================================

    useEffect(() => {
        const startKeepalive = () => {
            keepaliveTimerRef.current = setInterval(() => {
                store.sendKeepaliveAll();
            }, keepaliveIntervalMs);
        };

        startKeepalive();

        return () => {
            if (keepaliveTimerRef.current) {
                clearInterval(keepaliveTimerRef.current);
                keepaliveTimerRef.current = null;
            }
        };
    }, [keepaliveIntervalMs, store]);

    // =========================================================================
    // Visibility Change Handler
    // =========================================================================

    useEffect(() => {
        const handleVisibilityChange = () => {
            const isHidden = document.hidden;
            console.log('[useSessionPersistence] Visibility changed:', isHidden ? 'hidden' : 'visible');

            // Notifier le backend pour chaque session connectée
            const sessions = store.sessions.filter(s => s.connected);
            for (const session of sessions) {
                store.setBackgrounded(session.id, isHidden);
            }

            // Si on revient visible, envoyer un keepalive immédiat
            if (!isHidden) {
                store.sendKeepaliveAll();
            }
        };

        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [store]);

    // =========================================================================
    // Actions
    // =========================================================================

    const connectSession = useCallback((sessionId: string, url: string) => {
        // Annuler le voluntaryStop si présent
        store.clearVoluntaryStop(sessionId);

        // Connecter via WebSocketManager
        wsManager.connect(sessionId, url);
    }, [store]);

    const disconnectSession = useCallback((sessionId: string) => {
        // Marquer comme déconnexion volontaire et déconnecter
        wsManager.disconnect(sessionId, true);
    }, []);

    const disconnectAllSessions = useCallback(() => {
        const sessions = store.sessions;
        for (const session of sessions) {
            wsManager.disconnect(session.id, true);
        }
    }, [store]);

    const reconnectSession = useCallback((sessionId: string) => {
        const session = store.getSession(sessionId);
        if (!session) {
            console.error('[useSessionPersistence] Session not found:', sessionId);
            return;
        }

        // Annuler le voluntaryStop
        store.clearVoluntaryStop(sessionId);

        // Reconnecter
        if (session.url) {
            wsManager.connect(sessionId, session.url);
        }
    }, [store]);

    const reconnectAllSessions = useCallback(() => {
        const reconnectable = store.getReconnectableSessions();
        for (const session of reconnectable) {
            if (session.url) {
                store.clearVoluntaryStop(session.id);
                wsManager.connect(session.id, session.url);
            }
        }
    }, [store]);

    const isSessionConnected = useCallback((sessionId: string) => {
        return wsManager.isConnected(sessionId);
    }, []);

    const getConnectionStatus = useCallback(() => {
        return {
            connectedCount: wsManager.getConnectionCount(),
            sessionIds: wsManager.getSessionIds(),
            totalSessions: store.sessions.length
        };
    }, [store]);

    // =========================================================================
    // Return
    // =========================================================================

    return {
        // Store state
        sessions: store.sessions,
        activeSessionId: store.activeSessionId,
        isRehydrated: store.isRehydrated,

        // Connection actions
        connectSession,
        disconnectSession,
        disconnectAllSessions,
        reconnectSession,
        reconnectAllSessions,

        // Status
        isSessionConnected,
        getConnectionStatus,

        // Store actions (passthrough)
        setActiveSessionId: store.setActiveSessionId,
        getSession: store.getSession,
        getActiveSession: store.getActiveSession,
        getReconnectableSessions: store.getReconnectableSessions,

        // Keepalive
        sendKeepalive: store.sendKeepalive,
        sendKeepaliveAll: store.sendKeepaliveAll
    };
}

export default useSessionPersistence;
