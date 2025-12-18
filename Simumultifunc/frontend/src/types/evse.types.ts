// frontend/src/types/evse.types.ts
// DEPRECATED: Ce fichier est conservé pour compatibilité
// Utiliser @/types ou @/types/session.types à la place

export {
  type EvseType,
  type Environment,
  type MeterValuesMask,
  type SessionMetrics,
  type ChartPoint,
  type ChartSeries,
  type LogEntry,
  type VehicleProfile,
  type Toast,
  type AckStats,
  type PriceData,
  type SessionConfig,
  type SessionState
} from './session.types';

// Alias pour compatibilité avec l'ancien nom
export type { SessionState as SessionItem } from './session.types';
export type { VehicleProfile as Vehicle } from './session.types';

// Type de statut (utilise FrontendStatus de statusMapping)
export type { FrontendStatus as SessionStatus } from '@/utils/statusMapping';

// Type pour les séries de graphique (alias)
export type Serie = {
  label: string;
  color: string;
  points: Array<{ t: number; y: number }>;
  dash?: string;
  width?: number;
  opacity?: number;
};

// Type pour les sessions vides (non connectées)
export interface EmptySession {
  tempId: string;
  cpId: string;
  idTag: string;
  environment: 'test' | 'pp';
  evseType: 'ac-mono' | 'ac-bi' | 'ac-tri' | 'dc';
  maxA: number;
  created: Date;
}
