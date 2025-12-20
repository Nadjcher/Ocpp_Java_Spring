// frontend/src/store/sessionStore.ts
// Store Zustand avec persistance pour la gestion des sessions EVSE

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { api } from '../services/api';
import type { SessionState, LogEntry, ChartPoint, OCPPMessage } from '@/types';

// Réexporter les types pour compatibilité
export type { SessionState as Session, LogEntry, ChartPoint } from '@/types';

// Type étendu avec données de graphique spécifiques au store
export interface SessionWithChartData extends SessionState {
    socData: Array<{ time: number; soc?: number; offered?: number; active?: number; setpoint?: number }>;
    powerData: Array<{ time: number; soc?: number; offered?: number; active?: number; setpoint?: number }>;
    hidden: boolean;
    meterValueCount: number;
    lastMeterValueSent?: Date;
    // Champs de persistance
    voluntaryStop: boolean;
    backgrounded: boolean;
    lastKeepalive?: string;
    reconnectAttempts: number;
    disconnectReason?: string;
}

// Type pour les données persistées (minimal)
interface PersistedSessionData {
    id: string;
    wsUrl: string | null;
    cpId: string;
    idTag: string;
    isConnected: boolean;
    voluntaryStop: boolean;
    status: string;
}

interface SessionStore {
    // State
    sessions: SessionWithChartData[];
    activeSessionId: string | null;
    persistedSessions: PersistedSessionData[];
    isRehydrated: boolean;

    // CRUD
    loadSessions: () => Promise<void>;
    createSession: (title: string) => Promise<void>;
    updateSession: (id: string, updates: Partial<SessionWithChartData>) => Promise<void>;
    deleteSession: (id: string) => Promise<void>;
    setActiveSessionId: (id: string | null) => void;

    // Logging
    addLog: (sessionId: string, log: LogEntry) => void;
    updateSessionFromWebSocket: (update: { sessionId: string; data: Partial<SessionWithChartData> }) => void;

    // Persistence & Keepalive
    sendKeepalive: (sessionId: string) => Promise<void>;
    sendKeepaliveAll: () => Promise<void>;
    markVoluntaryStop: (sessionId: string, reason?: string) => Promise<void>;
    clearVoluntaryStop: (sessionId: string) => Promise<void>;
    setBackgrounded: (sessionId: string, backgrounded: boolean) => Promise<void>;
    getReconnectableSessions: () => SessionWithChartData[];
    persistSession: (session: SessionWithChartData) => void;
    restorePersistedSessions: () => Promise<void>;

    // Helpers
    getSession: (id: string) => SessionWithChartData | undefined;
    getActiveSession: () => SessionWithChartData | undefined;
}

const STORAGE_KEY = 'evse-sessions-v2';

export const useSessionStore = create<SessionStore>()(
    persist(
        (set, get) => ({
            sessions: [],
            activeSessionId: null,
            persistedSessions: [],
            isRehydrated: false,

            // =========================================================================
            // CRUD Operations
            // =========================================================================

            loadSessions: async () => {
                try {
                    const sessions = await api.getSessions();
                    // Enrichir avec les champs du store
                    const enrichedSessions = sessions.map((s: SessionState) => ({
                        ...s,
                        socData: [],
                        powerData: [],
                        hidden: false,
                        meterValueCount: 0,
                        voluntaryStop: (s as any).voluntaryStop ?? false,
                        backgrounded: (s as any).backgrounded ?? false,
                        lastKeepalive: (s as any).lastKeepalive,
                        reconnectAttempts: (s as any).reconnectAttempts ?? 0,
                        disconnectReason: (s as any).disconnectReason
                    }));
                    set({ sessions: enrichedSessions, isRehydrated: true });
                    console.log('[SessionStore] Loaded', enrichedSessions.length, 'sessions from backend');
                } catch (error) {
                    console.error('[SessionStore] Failed to load sessions:', error);
                    set({ isRehydrated: true });
                }
            },

            createSession: async (title: string) => {
                try {
                    const session = await api.createSession(title);
                    const enrichedSession: SessionWithChartData = {
                        ...session,
                        socData: [],
                        powerData: [],
                        hidden: false,
                        meterValueCount: 0,
                        voluntaryStop: false,
                        backgrounded: false,
                        reconnectAttempts: 0
                    };
                    set(state => ({
                        sessions: [...state.sessions, enrichedSession],
                        activeSessionId: session.id
                    }));
                    // Persister la nouvelle session
                    get().persistSession(enrichedSession);
                    console.log('[SessionStore] Created session:', session.id);
                } catch (error) {
                    console.error('[SessionStore] Failed to create session:', error);
                }
            },

            updateSession: async (id: string, updates: Partial<SessionWithChartData>) => {
                try {
                    const updated = await api.updateSession(id, updates);
                    set(state => ({
                        sessions: state.sessions.map(s => s.id === id ? { ...s, ...updated } : s)
                    }));
                    // Mettre à jour la persistance
                    const session = get().sessions.find(s => s.id === id);
                    if (session) {
                        get().persistSession(session);
                    }
                } catch (error) {
                    console.error('[SessionStore] Failed to update session:', error);
                }
            },

            deleteSession: async (id: string) => {
                try {
                    await api.deleteSession(id);
                    set(state => ({
                        sessions: state.sessions.filter(s => s.id !== id),
                        activeSessionId: state.activeSessionId === id ? null : state.activeSessionId,
                        persistedSessions: state.persistedSessions.filter(s => s.id !== id)
                    }));
                    console.log('[SessionStore] Deleted session:', id);
                } catch (error) {
                    console.error('[SessionStore] Failed to delete session:', error);
                }
            },

            setActiveSessionId: (id: string | null) => {
                set({ activeSessionId: id });
            },

            // =========================================================================
            // Logging & WebSocket Updates
            // =========================================================================

            addLog: (sessionId: string, log: LogEntry) => {
                set(state => ({
                    sessions: state.sessions.map(s => {
                        if (s.id === sessionId) {
                            const logs = [...s.logs, log].slice(-1000);
                            return { ...s, logs };
                        }
                        return s;
                    })
                }));
            },

            updateSessionFromWebSocket: (update: { sessionId: string; data: Partial<SessionWithChartData> }) => {
                set(state => ({
                    sessions: state.sessions.map(s => {
                        if (s.id === update.sessionId) {
                            const updated = { ...s, ...update.data };
                            return updated;
                        }
                        return s;
                    })
                }));
            },

            // =========================================================================
            // Persistence & Keepalive
            // =========================================================================

            sendKeepalive: async (sessionId: string) => {
                try {
                    const response = await fetch(`/api/sessions/${sessionId}/keepalive`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    if (response.ok) {
                        const data = await response.json();
                        if (data.status === 'ok') {
                            set(state => ({
                                sessions: state.sessions.map(s =>
                                    s.id === sessionId
                                        ? { ...s, lastKeepalive: new Date().toISOString(), isConnected: data.connected }
                                        : s
                                )
                            }));
                            console.debug('[Keepalive] Session', sessionId, 'updated');
                        }
                        // Silently ignore "session not found" errors - session may exist only locally
                    }
                } catch (error) {
                    // Network errors are expected when backend is not running
                    console.debug('[Keepalive] Network error for session', sessionId);
                }
            },

            sendKeepaliveAll: async () => {
                const { sessions } = get();
                const connectedSessions = sessions.filter(s => s.isConnected && !s.voluntaryStop);
                console.debug('[Keepalive] Sending keepalive for', connectedSessions.length, 'sessions');
                await Promise.all(connectedSessions.map(s => get().sendKeepalive(s.id)));
            },

            markVoluntaryStop: async (sessionId: string, reason = 'User requested disconnect') => {
                try {
                    const response = await fetch(`/api/sessions/${sessionId}/voluntary-stop?reason=${encodeURIComponent(reason)}`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    if (response.ok) {
                        set(state => ({
                            sessions: state.sessions.map(s =>
                                s.id === sessionId
                                    ? { ...s, voluntaryStop: true, disconnectReason: reason, isConnected: false }
                                    : s
                            ),
                            persistedSessions: state.persistedSessions.map(s =>
                                s.id === sessionId
                                    ? { ...s, voluntaryStop: true, isConnected: false }
                                    : s
                            )
                        }));
                        console.log('[SessionStore] Marked voluntary stop for session:', sessionId);
                    }
                } catch (error) {
                    console.error('[SessionStore] Failed to mark voluntary stop:', error);
                }
            },

            clearVoluntaryStop: async (sessionId: string) => {
                try {
                    const response = await fetch(`/api/sessions/${sessionId}/clear-voluntary-stop`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    if (response.ok) {
                        set(state => ({
                            sessions: state.sessions.map(s =>
                                s.id === sessionId
                                    ? { ...s, voluntaryStop: false, disconnectReason: undefined, reconnectAttempts: 0 }
                                    : s
                            ),
                            persistedSessions: state.persistedSessions.map(s =>
                                s.id === sessionId
                                    ? { ...s, voluntaryStop: false }
                                    : s
                            )
                        }));
                        console.log('[SessionStore] Cleared voluntary stop for session:', sessionId);
                    }
                } catch (error) {
                    console.error('[SessionStore] Failed to clear voluntary stop:', error);
                }
            },

            setBackgrounded: async (sessionId: string, backgrounded: boolean) => {
                // Update local state immediately
                set(state => ({
                    sessions: state.sessions.map(s =>
                        s.id === sessionId ? { ...s, backgrounded } : s
                    )
                }));
                // Try to sync with backend (ignore errors for local-only sessions)
                try {
                    await fetch(`/api/sessions/${sessionId}/backgrounded?backgrounded=${backgrounded}`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' }
                    });
                } catch (error) {
                    // Silently ignore - session may only exist locally
                    console.debug('[SessionStore] Could not sync backgrounded state:', sessionId);
                }
            },

            getReconnectableSessions: () => {
                const { sessions } = get();
                return sessions.filter(s => !s.isConnected && !s.voluntaryStop && s.reconnectAttempts < 10);
            },

            persistSession: (session: SessionWithChartData) => {
                const persisted: PersistedSessionData = {
                    id: session.id,
                    wsUrl: session.wsUrl,
                    cpId: session.cpId,
                    idTag: session.config.idTag,
                    isConnected: session.isConnected,
                    voluntaryStop: session.voluntaryStop,
                    status: session.status
                };
                set(state => ({
                    persistedSessions: [
                        ...state.persistedSessions.filter(s => s.id !== session.id),
                        persisted
                    ]
                }));
            },

            restorePersistedSessions: async () => {
                const { persistedSessions } = get();
                console.log('[SessionStore] Restoring', persistedSessions.length, 'persisted sessions');

                // Les sessions persistées seront réconciliées avec celles du backend lors du loadSessions
                // Cette méthode est appelée au démarrage pour identifier les sessions à reconnecter
                for (const persisted of persistedSessions) {
                    if (!persisted.voluntaryStop && persisted.isConnected) {
                        console.log('[SessionStore] Session', persisted.id, 'should be reconnected');
                    }
                }
            },

            // =========================================================================
            // Helpers
            // =========================================================================

            getSession: (id: string) => {
                return get().sessions.find(s => s.id === id);
            },

            getActiveSession: () => {
                const { sessions, activeSessionId } = get();
                return activeSessionId ? sessions.find(s => s.id === activeSessionId) : undefined;
            }
        }),
        {
            name: STORAGE_KEY,
            storage: createJSONStorage(() => localStorage),
            partialize: (state) => ({
                persistedSessions: state.persistedSessions,
                activeSessionId: state.activeSessionId
            }),
            onRehydrateStorage: () => (state) => {
                console.log('[SessionStore] Rehydrated from localStorage');
                if (state) {
                    state.isRehydrated = true;
                }
            }
        }
    )
);

// Export du hook pour compatibilité
export default useSessionStore;
