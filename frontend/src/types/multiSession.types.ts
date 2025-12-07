// frontend/src/types/multiSession.types.ts
// DEPRECATED: Ce fichier est conservé pour compatibilité
// Utiliser @/store/multiSessionStore pour les types multi-session (Zustand)
// Utiliser @/types/session.types pour les types de session backend

import type { SessionState, SessionConfig } from './session.types';

// Re-exporter les types de session.types.ts pour compatibilité
export {
  type SessionState,
  type SessionConfig,
  type SessionMetrics,
  type ChartSeries,
  type ChartPoint,
  type LogEntry,
  type BroadcastSessionData,
  type BroadcastMessage,
  type BroadcastMessageType,
  type MeterValuesMask,
  DEFAULT_SESSION_CONFIG,
  DEFAULT_SESSION_METRICS,
  createSessionState
} from './session.types';

// Re-exporter les types du multi-session store (Zustand)
export {
  type MultiSessionStatus,
  type SessionData,
  type SessionConfig as MultiSessionConfig,
  type SessionMetrics as MultiSessionMetrics,
} from '@/store/multiSessionStore';

// Interface pour le hook useMultiSessions (basé sur useState, pas Zustand)
export interface MultiSessionStore {
  sessions: SessionState[];
  activeSessionId: string | null;
  addSession: (configOverrides?: Partial<SessionConfig>) => string;
  removeSession: (id: string) => void;
  setActiveSession: (id: string) => void;
  updateSession: (id: string, updates: Partial<SessionState>) => void;
  updateSessionConfig: (id: string, configUpdates: Partial<SessionConfig>) => void;
  getSession: (id: string) => SessionState | undefined;
  getActiveSession: () => SessionState | undefined;
}
