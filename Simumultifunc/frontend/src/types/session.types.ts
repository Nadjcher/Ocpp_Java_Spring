// frontend/src/types/session.types.ts
// Types unifiés pour les sessions EVSE - Source unique de vérité

import type { FrontendStatus } from '@/utils/statusMapping';

// =============================================================================
// SESSION CONFIGURATION
// =============================================================================

/**
 * Type de borne EVSE
 */
export type EvseType = 'ac-mono' | 'ac-bi' | 'ac-tri' | 'dc';

/**
 * Environnement de test
 */
export type Environment = 'test' | 'pp';

/**
 * Masque pour la configuration des MeterValues
 */
export interface MeterValuesMask {
  powerActive: boolean;
  energy: boolean;
  soc: boolean;
  powerOffered: boolean;
}

/**
 * Configuration d'une session EVSE
 */
export interface SessionConfig {
  cpId: string;
  environment: Environment;
  evseType: EvseType;
  maxA: number;
  idTag: string;
  vehicleId: string;
  socStart: number;
  socTarget: number;
  mvEvery: number;
  mvMask: MeterValuesMask;
}

/**
 * Configuration par défaut pour une nouvelle session
 */
export const DEFAULT_SESSION_CONFIG: SessionConfig = {
  cpId: '',
  environment: 'test',
  evseType: 'ac-mono',
  maxA: 32,
  idTag: '',
  vehicleId: '1',
  socStart: 20,
  socTarget: 80,
  mvEvery: 10,
  mvMask: { powerActive: true, energy: true, soc: true, powerOffered: true }
};

// =============================================================================
// SESSION METRICS
// =============================================================================

/**
 * Métriques temps réel d'une session de charge
 */
export interface SessionMetrics {
  stationKwMax: number;     // Limite physique station (kW)
  backendKwMax: number;     // Limite imposée par le backend (kW)
  txpKw: number;            // TxProfile limit (kW)
  txdpKw: number;           // TxDefaultProfile limit (kW)
  voltage: number;          // Tension (V)
  phases: number;           // Nombre de phases
  energyKWh: number;        // Énergie délivrée (kWh)
}

/**
 * Métriques par défaut pour une nouvelle session
 */
export const DEFAULT_SESSION_METRICS: SessionMetrics = {
  stationKwMax: 0,
  backendKwMax: 0,
  txpKw: 0,
  txdpKw: 0,
  voltage: 230,
  phases: 1,
  energyKWh: 0
};

// =============================================================================
// CHART DATA
// =============================================================================

/**
 * Point de données pour les graphiques
 */
export interface ChartPoint {
  t: number;  // timestamp
  y: number;  // valeur
}

/**
 * Séries de données pour les graphiques d'une session
 */
export interface ChartSeries {
  soc: ChartPoint[];
  power: ChartPoint[];
  expected?: ChartPoint[];
}

// =============================================================================
// LOG ENTRY
// =============================================================================

/**
 * Entrée de log d'une session
 */
export interface LogEntry {
  ts: string;   // timestamp ISO
  line: string; // message
}

// =============================================================================
// SESSION STATE
// =============================================================================

/**
 * État complet d'une session EVSE
 * C'est le type principal unifié pour toutes les sessions
 */
export interface SessionState {
  // Identifiants
  id: string;
  cpId: string;
  isTemporary: boolean;  // Session non encore connectée au backend

  // Connexion
  status: FrontendStatus;
  wsUrl: string | null;

  // Transaction
  txId: number | null;
  transactionStartTime: number | null;

  // État physique
  isParked: boolean;
  isPlugged: boolean;
  isConnected: boolean;
  isCharging: boolean;

  // Charge - valeurs en unités SI (W, Wh)
  soc: number;              // % (0-100)
  activePower: number;      // W
  offeredPower: number;     // W
  energy: number;           // Wh
  voltage: number;          // V
  current: number;          // A

  // Configuration
  config: SessionConfig;

  // Métriques temps réel
  metrics: SessionMetrics;

  // Logs
  logs: LogEntry[];

  // Données graphique
  chartData: ChartSeries;
}

/**
 * Créer un nouvel état de session
 */
export function createSessionState(
  id: string,
  cpId: string,
  configOverrides?: Partial<SessionConfig>
): SessionState {
  return {
    id,
    cpId,
    isTemporary: true,
    status: 'disconnected',
    wsUrl: null,
    txId: null,
    transactionStartTime: null,
    isParked: false,
    isPlugged: false,
    isConnected: false,
    isCharging: false,
    soc: configOverrides?.socStart ?? DEFAULT_SESSION_CONFIG.socStart,
    activePower: 0,
    offeredPower: 0,
    energy: 0,
    voltage: 230,
    current: 0,
    config: {
      ...DEFAULT_SESSION_CONFIG,
      cpId,
      ...configOverrides
    },
    metrics: { ...DEFAULT_SESSION_METRICS },
    logs: [],
    chartData: { soc: [], power: [], expected: [] }
  };
}

// =============================================================================
// VEHICLE PROFILE
// =============================================================================

/**
 * Profil de véhicule électrique
 */
export interface VehicleProfile {
  id: string;
  name: string;
  manufacturer?: string;
  model?: string;
  variant?: string;
  capacityKWh: number;
  maxACPower?: number;
  maxDCPower?: number;
  maxPowerKW?: number;  // Puissance DC max (alias pour compatibilité)
  acMaxKW?: number;     // Puissance AC max (chargeur embarqué)
  acPhases?: number;    // Nombre de phases AC supportées (1, 2 ou 3)
  acMaxA?: number;      // Courant max par phase en AC
  efficiency?: number;
  imageUrl?: string;
  chargingCurve?: Record<number, number>;
}

// =============================================================================
// OCPP MESSAGES
// =============================================================================

/**
 * Direction d'un message OCPP
 */
export type MessageDirection = 'SENT' | 'RECEIVED';

/**
 * Message OCPP
 */
export interface OCPPMessage {
  sessionId: string;
  direction: MessageDirection;
  action: string;
  payload: unknown;
  timestamp: string;
  messageId?: string;
}

// =============================================================================
// PERFORMANCE METRICS
// =============================================================================

/**
 * Métriques de performance du système
 */
export interface PerformanceMetrics {
  activeSessions: number;
  totalSessions: number;
  cpuUsage: number;
  memoryUsage: number;
  messagesPerSecond: number;
  totalMessages: number;
  errors: number;
  errorCount: number;
  averageLatency: number;
  avgLatency: number;
  maxLatency: number;
  successRate: number;
  throughput: number;
  timestamp: string;
}

// =============================================================================
// TNR (TEST NON-RÉGRESSION)
// =============================================================================

/**
 * Session TNR
 */
export interface TNRSession {
  cpId: string;
  vehicle: string;
  startTime: string;
  duration: number;
  powerExpected: number;
  scpExpected: string[];
  messagesExpected: string[];
}

/**
 * Étape TNR
 */
export interface TNRStep {
  id: string;
  action: string;
  timestamp: string;
  status: 'pending' | 'running' | 'success' | 'failed';
  data?: unknown;
}

/**
 * Scénario TNR
 */
export interface TNRScenario {
  id: string;
  name: string;
  description: string;
  sessions: TNRSession[];
  steps: TNRStep[];
  createdAt: string;
  lastRun?: string;
  status: 'success' | 'failed' | 'running' | 'pending';
}

// =============================================================================
// BROADCAST (COMMUNICATION INTER-ONGLETS)
// =============================================================================

/**
 * Données de session pour le broadcast vers SimuGPM
 */
export interface BroadcastSessionData {
  sessionId: string;
  cpId: string;
  status: string;
  isConnected: boolean;
  isCharging: boolean;
  soc: number;
  offeredPower: number;
  activePower: number;
  setPoint: number;
  energy: number;
  voltage: number;
  current: number;
  transactionId?: number | null;
  config: SessionConfig;
}

/**
 * Types de message de broadcast
 */
export type BroadcastMessageType = 'SESSION_UPDATE' | 'SESSION_ADD' | 'SESSION_REMOVE' | 'FULL_SYNC';

/**
 * Message de broadcast entre onglets
 */
export interface BroadcastMessage {
  type: BroadcastMessageType;
  payload: BroadcastSessionData[] | BroadcastSessionData;
  timestamp: number;
}

// =============================================================================
// CHARGING PROFILES
// =============================================================================

/**
 * Type d'optimisation de charge
 */
export type OptimizationType = 'STANDARD' | 'COST' | 'GREEN_ENERGY' | 'FAST';

/**
 * Statut d'un profil de charge
 */
export type ProfileStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';

/**
 * Point de schedule de charge
 */
export interface ChargingSchedulePoint {
  startOffset: number;
  limit: number;
}

/**
 * Profil de charge
 */
export interface ChargingProfile {
  id: string;
  sessionId: string;
  startTime: string;
  endTime: string;
  targetSoc: number;
  optimizationType: OptimizationType;
  schedulePoints: ChargingSchedulePoint[];
  status: ProfileStatus;
}

// =============================================================================
// NOTIFICATION
// =============================================================================

/**
 * Notification toast
 */
export interface Toast {
  id: number;
  text: string;
  type?: 'info' | 'success' | 'warning' | 'error';
}

// =============================================================================
// ACK STATS (FIABILITÉ OCPP)
// =============================================================================

/**
 * Statistiques ACK pour la fiabilité OCPP
 */
export interface AckStats {
  sent: number;
  ack: number;
  rate: number;
}

// =============================================================================
// PRICE DATA
// =============================================================================

/**
 * Données de prix
 */
export interface PriceData {
  price: number;
  currency: string;
  unit: string;
  timestamp?: string;
}
