/**
 * Anomaly Detection Types
 * Système ML de détection d'anomalies pour EVSE Simulator
 */

// ─────────────────────────────────────────────────────────────────────────────
// TYPES D'ANOMALIES
// ─────────────────────────────────────────────────────────────────────────────

export type AnomalySeverity = 'low' | 'medium' | 'high' | 'critical';

export type AnomalyCategory =
  | 'power'      // Anomalies de puissance
  | 'soc'        // Anomalies de SOC
  | 'ocpp'       // Anomalies de protocole OCPP
  | 'scp'        // Anomalies Smart Charging Protocol
  | 'timing'     // Anomalies temporelles
  | 'hardware';  // Anomalies matérielles

export type AnomalyType =
  // Power anomalies
  | 'POWER_SPIKE'
  | 'POWER_DROP'
  | 'POWER_OSCILLATION'
  | 'POWER_LIMIT_EXCEEDED'
  | 'NEGATIVE_POWER'
  | 'ZERO_POWER_CHARGING'
  // SOC anomalies
  | 'SOC_JUMP'
  | 'SOC_DROP'
  | 'SOC_STAGNATION'
  | 'SOC_REGRESSION'
  | 'SOC_OVERFLOW'
  | 'SOC_UNDERFLOW'
  // OCPP anomalies
  | 'OCPP_TIMEOUT'
  | 'OCPP_ERROR_BURST'
  | 'OCPP_SEQUENCE_ERROR'
  | 'OCPP_DUPLICATE_MESSAGE'
  | 'OCPP_MISSING_RESPONSE'
  // SCP anomalies
  | 'SCP_SETPOINT_IGNORED'
  | 'SCP_PROFILE_VIOLATION'
  | 'SCP_LIMIT_EXCEEDED'
  | 'SCP_UNEXPECTED_CHANGE'
  // Timing anomalies
  | 'TIMING_DRIFT'
  | 'TIMING_GAP'
  | 'TIMING_OVERLAP'
  // Hardware anomalies
  | 'TEMPERATURE_HIGH'
  | 'VOLTAGE_ANOMALY'
  | 'CURRENT_IMBALANCE';

// ─────────────────────────────────────────────────────────────────────────────
// ANOMALY INTERFACE
// ─────────────────────────────────────────────────────────────────────────────

export interface Anomaly {
  id: string;
  type: AnomalyType;
  category: AnomalyCategory;
  severity: AnomalySeverity;
  timestamp: number;
  message: string;
  details: AnomalyDetails;
  context: AnomalyContext;
  suggestion?: string;
  resolved?: boolean;
  resolvedAt?: number;
}

export interface AnomalyDetails {
  expectedValue?: number;
  actualValue?: number;
  deviation?: number;
  deviationPercent?: number;
  threshold?: number;
  duration?: number;
  affectedMetric?: string;
  relatedMessages?: string[];
  rawData?: Record<string, unknown>;
}

export interface AnomalyContext {
  evseId?: string;
  transactionId?: string;
  connectorId?: number;
  chargeType?: string;
  sessionDuration?: number;
  currentSoc?: number;
  currentPower?: number;
  previousValues?: number[];
}

// ─────────────────────────────────────────────────────────────────────────────
// DETECTION CONFIG
// ─────────────────────────────────────────────────────────────────────────────

export interface DetectionConfig {
  enabled: boolean;
  sensitivity: 'low' | 'medium' | 'high';
  thresholds: DetectionThresholds;
  windowSize: number; // Number of data points to analyze
  minDataPoints: number; // Minimum data points before detection
}

export interface DetectionThresholds {
  // Power thresholds
  powerSpikePercent: number;      // % change for spike detection (default: 50)
  powerDropPercent: number;       // % change for drop detection (default: 30)
  powerOscillationCount: number;  // Count for oscillation (default: 3)
  maxPowerDeviationW: number;     // Max deviation from setpoint (default: 1000)

  // SOC thresholds
  socJumpPercent: number;         // % jump in single reading (default: 5)
  socDropPercent: number;         // % unexpected drop (default: 2)
  socStagnationMinutes: number;   // Minutes without change (default: 10)
  minSocChangePerHour: number;    // Min expected change/hour (default: 1)

  // OCPP thresholds
  ocppTimeoutMs: number;          // Response timeout (default: 30000)
  ocppErrorBurstCount: number;    // Errors in window (default: 5)
  ocppDuplicateWindowMs: number;  // Window for duplicates (default: 1000)

  // SCP thresholds
  scpDeviationPercent: number;    // % deviation from setpoint (default: 10)
  scpResponseTimeMs: number;      // Expected response time (default: 5000)

  // Timing thresholds
  timingDriftMs: number;          // Acceptable drift (default: 5000)
  timingGapMs: number;            // Gap detection (default: 60000)
}

export const DEFAULT_THRESHOLDS: DetectionThresholds = {
  powerSpikePercent: 50,
  powerDropPercent: 30,
  powerOscillationCount: 3,
  maxPowerDeviationW: 1000,
  socJumpPercent: 5,
  socDropPercent: 2,
  socStagnationMinutes: 10,
  minSocChangePerHour: 1,
  ocppTimeoutMs: 30000,
  ocppErrorBurstCount: 5,
  ocppDuplicateWindowMs: 1000,
  scpDeviationPercent: 10,
  scpResponseTimeMs: 5000,
  timingDriftMs: 5000,
  timingGapMs: 60000,
};

export const DEFAULT_CONFIG: DetectionConfig = {
  enabled: true,
  sensitivity: 'medium',
  thresholds: DEFAULT_THRESHOLDS,
  windowSize: 60,
  minDataPoints: 5,
};

// ─────────────────────────────────────────────────────────────────────────────
// ANALYSIS RESULTS
// ─────────────────────────────────────────────────────────────────────────────

export interface AnalysisResult {
  timestamp: number;
  healthScore: number; // 0-100
  anomalies: Anomaly[];
  stats: AnalysisStats;
  insights: Insight[];
}

export interface AnalysisStats {
  totalAnomalies: number;
  byCategory: Record<AnomalyCategory, number>;
  bySeverity: Record<AnomalySeverity, number>;
  averageHealthScore: number;
  trendDirection: 'improving' | 'stable' | 'degrading';
  analysisWindowMinutes: number;
}

export interface Insight {
  id: string;
  type: 'info' | 'warning' | 'recommendation' | 'alert';
  title: string;
  description: string;
  relatedAnomalies?: string[];
  actionable?: boolean;
  action?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// DATA POINT TYPES (for detection)
// ─────────────────────────────────────────────────────────────────────────────

export interface PowerDataPoint {
  timestamp: number;
  powerW: number;
  voltageV?: number;
  currentA?: number;
  phases?: number;
  setpointW?: number;
}

export interface SOCDataPoint {
  timestamp: number;
  soc: number;
  energyWh: number;
  powerW: number;
  estimatedTimeMinutes?: number;
}

export interface OCPPDataPoint {
  timestamp: number;
  messageType: string;
  action: string;
  success: boolean;
  responseTimeMs?: number;
  errorCode?: string;
  errorMessage?: string;
}

export interface SCPDataPoint {
  timestamp: number;
  setpointW: number;
  actualPowerW: number;
  profileId?: string;
  accepted: boolean;
  responseTimeMs?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// SEVERITY HELPERS
// ─────────────────────────────────────────────────────────────────────────────

export const SEVERITY_WEIGHTS: Record<AnomalySeverity, number> = {
  low: 1,
  medium: 3,
  high: 7,
  critical: 15,
};

export const SEVERITY_COLORS: Record<AnomalySeverity, string> = {
  low: 'text-blue-600 bg-blue-50 border-blue-200',
  medium: 'text-yellow-600 bg-yellow-50 border-yellow-200',
  high: 'text-orange-600 bg-orange-50 border-orange-200',
  critical: 'text-red-600 bg-red-50 border-red-200',
};

export const CATEGORY_ICONS: Record<AnomalyCategory, string> = {
  power: '⚡',
  soc: '🔋',
  ocpp: '📡',
  scp: '🎛️',
  timing: '⏱️',
  hardware: '🔧',
};

export const CATEGORY_LABELS: Record<AnomalyCategory, string> = {
  power: 'Puissance',
  soc: 'État de charge',
  ocpp: 'Protocole OCPP',
  scp: 'Smart Charging',
  timing: 'Timing',
  hardware: 'Matériel',
};

// ─────────────────────────────────────────────────────────────────────────────
// UTILITY FUNCTIONS
// ─────────────────────────────────────────────────────────────────────────────

export function generateAnomalyId(): string {
  return `anomaly-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

export function calculateHealthScore(anomalies: Anomaly[]): number {
  if (anomalies.length === 0) return 100;

  const totalWeight = anomalies.reduce(
    (sum, a) => sum + SEVERITY_WEIGHTS[a.severity],
    0
  );

  // Score decreases with weighted anomaly count
  // Max penalty is 100 points
  const penalty = Math.min(totalWeight * 5, 100);
  return Math.max(0, 100 - penalty);
}

export function getSeverityFromDeviation(
  deviationPercent: number,
  thresholds: { low: number; medium: number; high: number }
): AnomalySeverity {
  if (deviationPercent >= thresholds.high) return 'critical';
  if (deviationPercent >= thresholds.medium) return 'high';
  if (deviationPercent >= thresholds.low) return 'medium';
  return 'low';
}
