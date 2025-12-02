// frontend/src/store/sessionStore.ts
import { create } from 'zustand';
import { api } from '../services/api';
import type { SessionState, LogEntry, ChartPoint, OCPPMessage } from '@/types';

// Réexporter les types pour compatibilité
export type { SessionState as Session, LogEntry, ChartPoint } from '@/types';

// Type étendu avec données de graphique spécifiques au store
interface SessionWithChartData extends SessionState {
    socData: Array<{ time: number; soc?: number; offered?: number; active?: number; setpoint?: number }>;
    powerData: Array<{ time: number; soc?: number; offered?: number; active?: number; setpoint?: number }>;
    hidden: boolean;
    meterValueCount: number;
    lastMeterValueSent?: Date;
}

interface SessionStore {
    sessions: SessionWithChartData[];
    activeSessionId: string | null;

    loadSessions: () => Promise<void>;
    createSession: (title: string) => Promise<void>;
    updateSession: (id: string, updates: Partial<SessionWithChartData>) => Promise<void>;
    deleteSession: (id: string) => Promise<void>;
    setActiveSessionId: (id: string | null) => void;
    addLog: (sessionId: string, log: LogEntry) => void;
    updateSessionFromWebSocket: (update: { sessionId: string; data: Partial<SessionWithChartData> }) => void;
}

export const useSessionStore = create<SessionStore>((set, get) => ({
    sessions: [],
    activeSessionId: null,

    loadSessions: async () => {
        try {
            const sessions = await api.getSessions();
            // Enrichir avec les champs du store
            const enrichedSessions = sessions.map((s: SessionState) => ({
                ...s,
                socData: [],
                powerData: [],
                hidden: false,
                meterValueCount: 0
            }));
            set({ sessions: enrichedSessions });
        } catch (error) {
            console.error('Failed to load sessions:', error);
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
                meterValueCount: 0
            };
            set(state => ({
                sessions: [...state.sessions, enrichedSession],
                activeSessionId: session.id
            }));
        } catch (error) {
            console.error('Failed to create session:', error);
        }
    },

    updateSession: async (id: string, updates: Partial<SessionWithChartData>) => {
        try {
            const updated = await api.updateSession(id, updates);
            set(state => ({
                sessions: state.sessions.map(s => s.id === id ? { ...s, ...updated } : s)
            }));
        } catch (error) {
            console.error('Failed to update session:', error);
        }
    },

    deleteSession: async (id: string) => {
        try {
            await api.deleteSession(id);
            set(state => ({
                sessions: state.sessions.filter(s => s.id !== id),
                activeSessionId: state.activeSessionId === id ? null : state.activeSessionId
            }));
        } catch (error) {
            console.error('Failed to delete session:', error);
        }
    },

    setActiveSessionId: (id: string | null) => {
        set({ activeSessionId: id });
    },

    addLog: (sessionId: string, log: LogEntry) => {
        set(state => ({
            sessions: state.sessions.map(s => {
                if (s.id === sessionId) {
                    const logs = [...s.logs, log].slice(-1000); // Keep last 1000 logs
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
                    return { ...s, ...update.data };
                }
                return s;
            })
        }));
    }
}));
