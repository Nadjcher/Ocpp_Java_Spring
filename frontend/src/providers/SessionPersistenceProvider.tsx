// frontend/src/providers/SessionPersistenceProvider.tsx
// Provider pour initialiser et gérer la persistance des sessions

import React, { useEffect, useRef } from 'react';
import { useSessionStore } from '@/store/sessionStore';
import { wsManager } from '@/services/WebSocketManager';

interface SessionPersistenceProviderProps {
    children: React.ReactNode;
    autoReconnect?: boolean;
    keepaliveIntervalMs?: number;
}

export function SessionPersistenceProvider({
    children,
    autoReconnect = true,
    keepaliveIntervalMs = 15000
}: SessionPersistenceProviderProps) {
    const store = useSessionStore();
    const keepaliveTimerRef = useRef<NodeJS.Timeout | null>(null);
    const isInitializedRef = useRef(false);

    // Initialisation au montage
    useEffect(() => {
        if (isInitializedRef.current) return;
        isInitializedRef.current = true;

        console.log('[SessionPersistenceProvider] Initializing...');

        // Charger les sessions depuis le backend
        store.loadSessions().then(() => {
            console.log('[SessionPersistenceProvider] Sessions loaded');

            // Restaurer les sessions persistées
            store.restorePersistedSessions();

            // Auto-reconnecter les sessions non volontairement déconnectées
            if (autoReconnect) {
                setTimeout(() => {
                    const reconnectable = store.getReconnectableSessions();
                    console.log('[SessionPersistenceProvider] Found', reconnectable.length, 'reconnectable sessions');

                    for (const session of reconnectable) {
                        if (session.url && !session.voluntaryStop) {
                            console.log('[SessionPersistenceProvider] Auto-reconnecting session:', session.id);
                            wsManager.connect(session.id, session.url);
                        }
                    }
                }, 1000); // Petit délai pour permettre l'hydratation complète
            }
        });

        // Cleanup au démontage
        return () => {
            console.log('[SessionPersistenceProvider] Cleaning up...');
            // Ne pas détruire le wsManager car les sessions doivent persister
        };
    }, []);

    // Keepalive loop
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
    }, [keepaliveIntervalMs]);

    // Visibility change handler
    useEffect(() => {
        const handleVisibilityChange = () => {
            const isHidden = document.hidden;
            console.log('[SessionPersistenceProvider] Visibility changed:', isHidden ? 'hidden' : 'visible');

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
    }, []);

    // Beforeunload handler - envoyer un dernier keepalive
    useEffect(() => {
        const handleBeforeUnload = () => {
            // On ne marque PAS les sessions comme voluntaryStop ici
            // Car ce n'est pas une déconnexion volontaire de l'utilisateur
            console.log('[SessionPersistenceProvider] Page unloading, sessions will persist');
        };

        window.addEventListener('beforeunload', handleBeforeUnload);

        return () => {
            window.removeEventListener('beforeunload', handleBeforeUnload);
        };
    }, []);

    return <>{children}</>;
}

export default SessionPersistenceProvider;
