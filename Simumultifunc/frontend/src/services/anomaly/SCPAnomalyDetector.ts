/**
 * SCPAnomalyDetector - Détection d'anomalies Smart Charging Protocol
 */

import {
  Anomaly,
  SCPDataPoint,
  DetectionThresholds,
  DEFAULT_THRESHOLDS,
  generateAnomalyId,
  getSeverityFromDeviation,
} from '@/types/anomaly.types';

export class SCPAnomalyDetector {
  private thresholds: DetectionThresholds;
  private dataBuffer: SCPDataPoint[] = [];
  private maxBufferSize: number;
  private lastSetpoint: number = 0;
  private setpointChangeCount: number = 0;
  private lastSetpointChangeTime: number = 0;

  constructor(
    thresholds: DetectionThresholds = DEFAULT_THRESHOLDS,
    maxBufferSize: number = 100
  ) {
    this.thresholds = thresholds;
    this.maxBufferSize = maxBufferSize;
  }

  /**
   * Analyze a new SCP data point and detect anomalies
   */
  analyze(dataPoint: SCPDataPoint): Anomaly[] {
    const anomalies: Anomaly[] = [];

    // Add to buffer
    this.dataBuffer.push(dataPoint);
    if (this.dataBuffer.length > this.maxBufferSize) {
      this.dataBuffer.shift();
    }

    // Track setpoint changes
    if (dataPoint.setpointW !== this.lastSetpoint) {
      this.setpointChangeCount++;
      this.lastSetpointChangeTime = dataPoint.timestamp;
      this.lastSetpoint = dataPoint.setpointW;
    }

    // Check for various SCP anomalies
    const ignoredAnomaly = this.detectSetpointIgnored(dataPoint);
    if (ignoredAnomaly) anomalies.push(ignoredAnomaly);

    const violationAnomaly = this.detectProfileViolation(dataPoint);
    if (violationAnomaly) anomalies.push(violationAnomaly);

    const limitAnomaly = this.detectLimitExceeded(dataPoint);
    if (limitAnomaly) anomalies.push(limitAnomaly);

    const unexpectedAnomaly = this.detectUnexpectedChange(dataPoint);
    if (unexpectedAnomaly) anomalies.push(unexpectedAnomaly);

    return anomalies;
  }

  /**
   * Detect when setpoint is ignored (actual power doesn't follow setpoint)
   */
  private detectSetpointIgnored(current: SCPDataPoint): Anomaly | null {
    if (current.setpointW <= 0) return null;

    const deviation = Math.abs(current.actualPowerW - current.setpointW);
    const deviationPercent = (deviation / current.setpointW) * 100;

    // Check if deviation is significant
    if (deviationPercent > this.thresholds.scpDeviationPercent) {
      // Check if this has been consistent (not just a transient)
      const recentPoints = this.dataBuffer.slice(-5);
      const consistentDeviation = recentPoints.filter((p) => {
        const dev = Math.abs(p.actualPowerW - p.setpointW);
        return p.setpointW > 0 && (dev / p.setpointW) * 100 > this.thresholds.scpDeviationPercent;
      });

      if (consistentDeviation.length >= 3) {
        const isOver = current.actualPowerW > current.setpointW;

        return {
          id: generateAnomalyId(),
          type: 'SCP_SETPOINT_IGNORED',
          category: 'scp',
          severity: getSeverityFromDeviation(deviationPercent, {
            low: 10,
            medium: 20,
            high: 40,
          }),
          timestamp: current.timestamp,
          message: `Setpoint ${isOver ? 'dépassé' : 'non atteint'}: ${(current.actualPowerW / 1000).toFixed(1)} kW vs ${(current.setpointW / 1000).toFixed(1)} kW`,
          details: {
            expectedValue: current.setpointW,
            actualValue: current.actualPowerW,
            deviation,
            deviationPercent,
            threshold: this.thresholds.scpDeviationPercent,
            affectedMetric: 'setpointW',
          },
          context: {
            currentPower: current.actualPowerW,
          },
          suggestion: isOver
            ? 'Le véhicule dépasse la limite assignée. Vérifier la configuration Smart Charging.'
            : 'Le véhicule ne charge pas à la puissance autorisée. Possible limitation côté véhicule.',
        };
      }
    }

    return null;
  }

  /**
   * Detect charging profile violations
   */
  private detectProfileViolation(current: SCPDataPoint): Anomaly | null {
    if (!current.profileId) return null;

    // Check if profile changed unexpectedly
    const recentProfiles = this.dataBuffer
      .slice(-10)
      .filter((p) => p.profileId)
      .map((p) => p.profileId);

    const uniqueProfiles = new Set(recentProfiles);

    if (uniqueProfiles.size > 2) {
      return {
        id: generateAnomalyId(),
        type: 'SCP_PROFILE_VIOLATION',
        category: 'scp',
        severity: 'medium',
        timestamp: current.timestamp,
        message: `Changements fréquents de profil de charge: ${uniqueProfiles.size} profils différents`,
        details: {
          affectedMetric: 'profileId',
          rawData: {
            profiles: Array.from(uniqueProfiles),
            changeCount: uniqueProfiles.size,
          },
        },
        context: {},
        suggestion:
          'Profils de charge instables. Vérifier la configuration du CSMS.',
      };
    }

    return null;
  }

  /**
   * Detect when actual power exceeds setpoint significantly
   */
  private detectLimitExceeded(current: SCPDataPoint): Anomaly | null {
    if (current.setpointW <= 0) return null;

    const excess = current.actualPowerW - current.setpointW;
    const excessPercent = (excess / current.setpointW) * 100;

    // Only flag significant exceedance
    if (excessPercent > 20) {
      return {
        id: generateAnomalyId(),
        type: 'SCP_LIMIT_EXCEEDED',
        category: 'scp',
        severity: getSeverityFromDeviation(excessPercent, {
          low: 20,
          medium: 40,
          high: 60,
        }),
        timestamp: current.timestamp,
        message: `Limite SCP dépassée de ${excessPercent.toFixed(1)}%`,
        details: {
          expectedValue: current.setpointW,
          actualValue: current.actualPowerW,
          deviation: excess,
          deviationPercent: excessPercent,
          affectedMetric: 'setpointW',
        },
        context: {
          currentPower: current.actualPowerW,
        },
        suggestion:
          'Violation grave de la limite de puissance. Intervention nécessaire.',
      };
    }

    return null;
  }

  /**
   * Detect unexpected setpoint changes
   */
  private detectUnexpectedChange(current: SCPDataPoint): Anomaly | null {
    if (this.dataBuffer.length < 2) return null;

    const prev = this.dataBuffer[this.dataBuffer.length - 2];

    // Check for large sudden setpoint change
    if (prev.setpointW > 0 && current.setpointW > 0) {
      const changePercent =
        Math.abs(current.setpointW - prev.setpointW) / prev.setpointW * 100;

      if (changePercent > 50) {
        // Check if this is part of a pattern (frequent changes)
        const recentChanges = this.countRecentSetpointChanges(60000); // 1 minute

        if (recentChanges > 3) {
          return {
            id: generateAnomalyId(),
            type: 'SCP_UNEXPECTED_CHANGE',
            category: 'scp',
            severity: 'medium',
            timestamp: current.timestamp,
            message: `Changements de setpoint fréquents: ${recentChanges} en 1 minute`,
            details: {
              expectedValue: prev.setpointW,
              actualValue: current.setpointW,
              deviation: Math.abs(current.setpointW - prev.setpointW),
              deviationPercent: changePercent,
              affectedMetric: 'setpointW',
              rawData: {
                changeCount: recentChanges,
              },
            },
            context: {
              currentPower: current.actualPowerW,
            },
            suggestion:
              'Setpoints instables. Possible problème de régulation GPM ou CSMS.',
          };
        }
      }
    }

    return null;
  }

  /**
   * Count setpoint changes in time window
   */
  private countRecentSetpointChanges(windowMs: number): number {
    const windowStart = Date.now() - windowMs;
    let changes = 0;
    let lastSetpoint: number | null = null;

    for (const point of this.dataBuffer) {
      if (point.timestamp >= windowStart) {
        if (lastSetpoint !== null && point.setpointW !== lastSetpoint) {
          changes++;
        }
        lastSetpoint = point.setpointW;
      }
    }

    return changes;
  }

  /**
   * Get SCP statistics
   */
  getStats(): {
    avgDeviation: number;
    maxDeviation: number;
    acceptanceRate: number;
    setpointChanges: number;
  } {
    const deviations = this.dataBuffer
      .filter((p) => p.setpointW > 0)
      .map((p) => Math.abs(p.actualPowerW - p.setpointW) / p.setpointW * 100);

    const accepted = this.dataBuffer.filter((p) => p.accepted).length;

    return {
      avgDeviation:
        deviations.length > 0
          ? deviations.reduce((a, b) => a + b, 0) / deviations.length
          : 0,
      maxDeviation: deviations.length > 0 ? Math.max(...deviations) : 0,
      acceptanceRate:
        this.dataBuffer.length > 0
          ? (accepted / this.dataBuffer.length) * 100
          : 100,
      setpointChanges: this.setpointChangeCount,
    };
  }

  /**
   * Clear the data buffer
   */
  reset(): void {
    this.dataBuffer = [];
    this.lastSetpoint = 0;
    this.setpointChangeCount = 0;
    this.lastSetpointChangeTime = 0;
  }

  /**
   * Update thresholds
   */
  updateThresholds(thresholds: Partial<DetectionThresholds>): void {
    this.thresholds = { ...this.thresholds, ...thresholds };
  }
}

export default SCPAnomalyDetector;
