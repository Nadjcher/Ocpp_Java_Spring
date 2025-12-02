// frontend/src/store/useAppStore.ts
import { create } from 'zustand';
import { api } from '@/services/api';
import type { SessionState, OCPPMessage, PerformanceMetrics } from '@/types';

// Réexporter les types pour compatibilité
export type { SessionState as Session, OCPPMessage, PerformanceMetrics } from '@/types';

interface AppState {
    sessions: SessionState[];
    ocppMessages: OCPPMessage[];
    performanceMetrics: PerformanceMetrics | null;
    selectedSessionId: string | null;
}

interface AppActions {
    loadSessions: () => Promise<void>;
    createSession: (title: string) => Promise<void>;
    updateSession: (id: string, updates: Partial<SessionState>) => Promise<void>;
    deleteSession: (id: string) => Promise<void>;
    selectSession: (id: string | null) => void;
    addOCPPMessage: (message: OCPPMessage) => void;
    setPerformanceMetrics: (metrics: PerformanceMetrics) => void;
}

export const useAppStore = create<AppState & AppActions>((set) => ({
    sessions: [],
    ocppMessages: [],
    performanceMetrics: null,
    selectedSessionId: null,

    loadSessions: async () => {
        try {
            const sessions = await api.getSessions();
            set({ sessions });
        } catch (error) {
            console.error('Failed to load sessions:', error);
        }
    },

    createSession: async (title: string) => {
        try {
            const session = await api.createSession(title);
            set((state) => ({ sessions: [...state.sessions, session] }));
        } catch (error) {
            console.error('Failed to create session:', error);
        }
    },

    updateSession: async (id: string, updates: Partial<SessionState>) => {
        try {
            const updated = await api.updateSession(id, updates);
            set((state) => ({
                sessions: state.sessions.map((s) => (s.id === id ? updated : s)),
            }));
        } catch (error) {
            console.error('Failed to update session:', error);
        }
    },

    deleteSession: async (id: string) => {
        try {
            await api.deleteSession(id);
            set((state) => ({
                sessions: state.sessions.filter((s) => s.id !== id),
            }));
        } catch (error) {
            console.error('Failed to delete session:', error);
        }
    },

    selectSession: (id: string | null) => {
        set({ selectedSessionId: id });
    },

    addOCPPMessage: (message: OCPPMessage) => {
        set((state) => ({
            ocppMessages: [...state.ocppMessages.slice(-99), message],
        }));
    },

    setPerformanceMetrics: (metrics: PerformanceMetrics) => {
        set({ performanceMetrics: metrics });
    },
}));
