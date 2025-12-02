// frontend/src/types/multiSession.types.ts
// DEPRECATED: Ce fichier est conservé pour compatibilité
// Utiliser @/types ou @/types/session.types à la place

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

// Alias pour compatibilité
export type { SessionState as MultiSessionState } from './session.types';
