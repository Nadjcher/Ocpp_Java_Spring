/**
 * Types pour le simulateur GPM (Grid Power Management)
 */

// ══════════════════════════════════════════════════════════════
// ENUMS ET TYPES DE BASE
// ══════════════════════════════════════════════════════════════

export type GPMChargeType = 'MONO' | 'TRI' | 'DC';
export type GPMSimulationStatus = 'CREATED' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type GPMSimulationMode = 'LOCAL' | 'DRY_RUN';

export interface ChargeTypeConfig {
  type: GPMChargeType;
  label: string;
  phases: number;
  voltageV: number;
  maxCurrentA: number;
  maxPowerW: number;
}

// Configuration des types de charge
export const CHARGE_TYPES: Record<GPMChargeType, ChargeTypeConfig> = {
  MONO: {
    type: 'MONO',
    label: 'Monophasé',
    phases: 1,
    voltageV: 230,
    maxCurrentA: 32,
    maxPowerW: 7360,
  },
  TRI: {
    type: 'TRI',
    label: 'Triphasé',
    phases: 3,
    voltageV: 400,
    maxCurrentA: 32,
    maxPowerW: 22000,
  },
  DC: {
    type: 'DC',
    label: 'Courant Continu',
    phases: 0,
    voltageV: 800,
    maxCurrentA: 500,
    maxPowerW: 350000,
  },
};

// ══════════════════════════════════════════════════════════════
// CONFIGURATION VÉHICULE
// ══════════════════════════════════════════════════════════════

export interface PowerBySocPoint {
  soc: number;
  powerW: number;
}

export interface EVTypeConfig {
  id: string;
  name: string;
  chargeType: GPMChargeType;
  capacityWh: number;
  maxPowerW: number;
  powerBySoc?: Record<number, number>;
}

// ══════════════════════════════════════════════════════════════
// ÉTAT VÉHICULE
// ══════════════════════════════════════════════════════════════

export interface VehicleHistoryEntry {
  tick: number;
  soc: number;
  powerW: number;
  energyWh: number;
  setpointW?: number;
  timestamp: string;
}

export interface GPMVehicleState {
  evseId: string;
  evTypeId: string;
  chargeType: GPMChargeType;
  transactionId: string;
  initialSoc: number;
  currentSoc: number;
  targetSoc: number;
  capacityWh: number;
  maxPowerW: number;
  currentPowerW: number;
  energyRegisterWh: number;
  lastSetpointW?: number;
  charging: boolean;
  history: VehicleHistoryEntry[];
}

// ══════════════════════════════════════════════════════════════
// RÉSULTATS TICK
// ══════════════════════════════════════════════════════════════

export interface GPMVehicleTickResult {
  evseId: string;
  transactionId: string;
  requestedPowerW: number;
  actualPowerW: number;
  setpointAppliedW?: number;
  energyChargedWh: number;
  socBefore: number;
  socAfter: number;
  currentL1A?: number;
  currentL2A?: number;
  currentL3A?: number;
}

export interface GPMTickResult {
  tick: number;
  tickId: string;
  timestamp: string;
  simulatedTime: string;
  vehicleResults: GPMVehicleTickResult[];
  totalPowerW: number;
  totalEnergyWh: number;
}

// ══════════════════════════════════════════════════════════════
// ERREURS API
// ══════════════════════════════════════════════════════════════

export interface GPMApiError {
  tick: number;
  type: string;
  evseId?: string;
  message: string;
  timestamp: string;
}

// ══════════════════════════════════════════════════════════════
// CONFIGURATION SIMULATION
// ══════════════════════════════════════════════════════════════

export interface GPMSimulationConfig {
  name: string;
  rootNodeId: string;
  tickIntervalMinutes: number;
  numberOfTicks: number;
  timeScale: number;
  mode: GPMSimulationMode;
}

// ══════════════════════════════════════════════════════════════
// SIMULATION
// ══════════════════════════════════════════════════════════════

export interface GPMSimulation {
  id: string;
  name: string;
  dryRunId?: string;
  status: GPMSimulationStatus;
  rootNodeId: string;
  tickIntervalMinutes: number;
  numberOfTicks: number;
  timeScale: number;
  currentTick: number;
  totalTicks: number;
  progressPercent: number;
  startedAt?: string;
  completedAt?: string;
  currentSimulatedTime?: string;
  vehicles: GPMVehicleState[];
  tickResults?: GPMTickResult[];
  apiErrors: GPMApiError[];
  errorCount: number;
  totalEnergyWh: number;
  averagePowerW: number;
  peakPowerW: number;
}

// ══════════════════════════════════════════════════════════════
// REQUESTS
// ══════════════════════════════════════════════════════════════

export interface CreateSimulationRequest {
  name: string;
  rootNodeId: string;
  tickIntervalMinutes?: number;
  numberOfTicks?: number;
  timeScale?: number;
  mode?: GPMSimulationMode;
}

export interface AddVehicleRequest {
  evseId: string;
  evTypeId: string;
  initialSoc?: number;
  targetSoc?: number;
  transactionId?: string;
}

// ══════════════════════════════════════════════════════════════
// CONFIG STATUS
// ══════════════════════════════════════════════════════════════

export interface GPMConfigStatus {
  dryRunEnabled: boolean;
  connectionOk: boolean;
  vehicleTypesCount: number;
  vehiclesByType: Record<GPMChargeType, number>;
}

// ══════════════════════════════════════════════════════════════
// UI STATE
// ══════════════════════════════════════════════════════════════

export interface GPMFormState {
  simulationName: string;
  rootNodeId: string;
  tickIntervalMinutes: number;
  numberOfTicks: number;
  timeScale: number;
  mode: GPMSimulationMode;
}

export interface GPMVehicleFormState {
  evseId: string;
  evTypeId: string;
  initialSoc: number;
  targetSoc: number;
}

export const DEFAULT_FORM_STATE: GPMFormState = {
  simulationName: 'Simulation GPM',
  rootNodeId: '',
  tickIntervalMinutes: 15,
  numberOfTicks: 96,
  timeScale: 60, // 60x = 1 tick/seconde
  mode: 'DRY_RUN',
};

export const DEFAULT_VEHICLE_FORM: GPMVehicleFormState = {
  evseId: '',
  evTypeId: '',
  initialSoc: 20,
  targetSoc: 80,
};
