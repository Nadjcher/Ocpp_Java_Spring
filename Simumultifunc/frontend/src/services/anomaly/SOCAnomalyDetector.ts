/**
 * SOCAnomalyDetector - Détection d'anomalies d'état de charge (SOC)
 */

import {
  Anomaly,
  SOCDataPoint,
  DetectionThresholds,
  DEFAULT_THRESHOLDS,
  generateAnomalyId,
  getSeverityFromDeviation,
} from '@/types/anomaly.types';

export class SOCAnomalyDetector {
  private thresholds: DetectionThresholds;
  private dataBuffer: SOCDataPoint[] = [];
  private maxBufferSize: number;
  private lastStagnationAlert: number = 0;

  constructor(
    thresholds: DetectionThresholds = DEFAULT_THRESHOLDS,
    maxBufferSize: number = 100
  ) {
    this.thresholds = thresholds;
    this.maxBufferSize = maxBufferSize;
  }

  /**
   * Analyze a new SOC data point and detect anomalies
   */
  analyze(dataPoint: SOCDataPoint): Anomaly[] {
    const anomalies: Anomaly[] = [];

    // Add to buffer
    this.dataBuffer.push(dataPoint);
    if (this.dataBuffer.length > this.maxBufferSize) {
      this.dataBuffer.shift();
    }

    // Need at least 2 points for comparison
    if (this.dataBuffer.length < 2) {
      return anomalies;
    }

    const prev = this.dataBuffer[this.dataBuffer.length - 2];
    const current = dataPoint;

    // Check for various SOC anomalies
    const jumpAnomaly = this.detectSOCJump(prev, current);
    if (jumpAnomaly) anomalies.push(jumpAnomaly);

    const dropAnomaly = this.detectSOCDrop(prev, current);
    if (dropAnomaly) anomalies.push(dropAnomaly);

    const stagnationAnomaly = this.detectSOCStagnation(current);
    if (stagnationAnomaly) anomalies.push(stagnationAnomaly);

    const regressionAnomaly = this.detectSOCRegression(current);
    if (regressionAnomaly) anomalies.push(regressionAnomaly);

    const overflowAnomaly = this.detectSOCOverflow(current);
    if (overflowAnomaly) anomalies.push(overflowAnomaly);

    const underflowAnomaly = this.detectSOCUnderflow(current);
    if (underflowAnomaly) anomalies.push(underflowAnomaly);

    return anomalies;
  }

  /**
   * Detect sudden SOC jumps (impossible fast charging)
   */
  private detectSOCJump(
    prev: SOCDataPoint,
    current: SOCDataPoint
  ): Anomaly | null {
    const socChange = current.soc - prev.soc;
    const timeDiffMinutes = (current.timestamp - prev.timestamp) / 60000;

    // Skip if time difference is too small
    if (timeDiffMinutes < 0.1) return null;

    // Calculate expected max SOC change based on power
    const avgPowerW = (prev.powerW + current.powerW) / 2;
    const expectedMaxChange = this.calculateExpectedSOCChange(
      avgPowerW,
      timeDiffMinutes
    );

    // If actual change is much higher than expected
    if (socChange > expectedMaxChange * 2 && socChange > this.thresholds.socJumpPercent) {
      return {
        id: generateAnomalyId(),
        type: 'SOC_JUMP',
        category: 'soc',
        severity: getSeverityFromDeviation(socChange, {
          low: 5,
          medium: 10,
          high: 20,
        }),
        timestamp: current.timestamp,
        message: `Saut de SOC anormal: +${socChange.toFixed(1)}% en ${timeDiffMinutes.toFixed(1)} min`,
        details: {
          expectedValue: prev.soc + expectedMaxChange,
          actualValue: current.soc,
          deviation: socChange - expectedMaxChange,
          deviationPercent: socChange,
          threshold: this.thresholds.socJumpPercent,
          duration: timeDiffMinutes * 60000,
          affectedMetric: 'soc',
        },
        context: {
          currentSoc: current.soc,
          currentPower: current.powerW,
          previousValues: this.getRecentSOCValues(5),
        },
        suggestion:
          'Vérifier la cohérence des données SOC du véhicule',
      };
    }

    return null;
  }

  /**
   * Detect unexpected SOC drops during charging
   */
  private detectSOCDrop(
    prev: SOCDataPoint,
    current: SOCDataPoint
  ): Anomaly | null {
    const socChange = current.soc - prev.soc;

    // SOC shouldn't decrease during active charging
    if (socChange < -this.thresholds.socDropPercent && current.powerW > 100) {
      return {
        id: generateAnomalyId(),
        type: 'SOC_DROP',
        category: 'soc',
        severity: getSeverityFromDeviation(Math.abs(socChange), {
          low: 2,
          medium: 5,
          high: 10,
        }),
        timestamp: current.timestamp,
        message: `Baisse de SOC pendant la charge: ${socChange.toFixed(1)}%`,
        details: {
          expectedValue: prev.soc,
          actualValue: current.soc,
          deviation: socChange,
          deviationPercent: Math.abs(socChange),
          threshold: this.thresholds.socDropPercent,
          affectedMetric: 'soc',
        },
        context: {
          currentSoc: current.soc,
          currentPower: current.powerW,
          previousValues: this.getRecentSOCValues(5),
        },
        suggestion:
          'Le SOC ne devrait pas baisser pendant la charge. Problème de mesure ou consommation auxiliaire.',
      };
    }

    return null;
  }

  /**
   * Detect SOC stagnation (no progress despite charging)
   */
  private detectSOCStagnation(current: SOCDataPoint): Anomaly | null {
    // Need enough history
    if (this.dataBuffer.length < 5) return null;

    // Avoid repeated alerts - only alert every 5 minutes
    const timeSinceLastAlert = current.timestamp - this.lastStagnationAlert;
    if (timeSinceLastAlert < 5 * 60000) return null;

    // Get data from last N minutes
    const stagnationWindowMs = this.thresholds.socStagnationMinutes * 60000;
    const windowStart = current.timestamp - stagnationWindowMs;

    const pointsInWindow = this.dataBuffer.filter(
      (p) => p.timestamp >= windowStart
    );

    if (pointsInWindow.length < 3) return null;

    // Check if SOC changed during window
    const firstSOC = pointsInWindow[0].soc;
    const lastSOC = current.soc;
    const socChange = Math.abs(lastSOC - firstSOC);

    // Check if there was power during this time
    const avgPower =
      pointsInWindow.reduce((sum, p) => sum + p.powerW, 0) /
      pointsInWindow.length;

    if (socChange < 0.5 && avgPower > 500) {
      this.lastStagnationAlert = current.timestamp;

      return {
        id: generateAnomalyId(),
        type: 'SOC_STAGNATION',
        category: 'soc',
        severity: 'medium',
        timestamp: current.timestamp,
        message: `SOC stagnant: ${socChange.toFixed(1)}% de changement en ${this.thresholds.socStagnationMinutes} min`,
        details: {
          expectedValue: firstSOC + this.thresholds.minSocChangePerHour,
          actualValue: lastSOC,
          deviation: socChange,
          duration: stagnationWindowMs,
          affectedMetric: 'soc',
          rawData: {
            avgPowerW: avgPower,
            periodMinutes: this.thresholds.socStagnationMinutes,
          },
        },
        context: {
          currentSoc: current.soc,
          currentPower: current.powerW,
        },
        suggestion:
          'Le SOC ne progresse pas malgré la puissance active. Vérifier la communication véhicule.',
      };
    }

    return null;
  }

  /**
   * Detect long-term SOC regression (overall declining trend)
   */
  private detectSOCRegression(current: SOCDataPoint): Anomaly | null {
    if (this.dataBuffer.length < 10) return null;

    // Calculate trend over last 10 points
    const recentPoints = this.dataBuffer.slice(-10);
    const trend = this.calculateTrend(recentPoints.map((p) => p.soc));

    // Negative trend with active charging
    const avgPower =
      recentPoints.reduce((sum, p) => sum + p.powerW, 0) / recentPoints.length;

    if (trend < -0.5 && avgPower > 500) {
      return {
        id: generateAnomalyId(),
        type: 'SOC_REGRESSION',
        category: 'soc',
        severity: 'high',
        timestamp: current.timestamp,
        message: `Tendance SOC négative malgré la charge active`,
        details: {
          affectedMetric: 'soc',
          rawData: {
            trend,
            avgPowerW: avgPower,
            dataPoints: recentPoints.length,
          },
        },
        context: {
          currentSoc: current.soc,
          currentPower: current.powerW,
          previousValues: this.getRecentSOCValues(10),
        },
        suggestion:
          'Anomalie grave: le SOC décroît pendant la charge. Vérifier le système de mesure.',
      };
    }

    return null;
  }

  /**
   * Detect SOC overflow (above 100%)
   */
  private detectSOCOverflow(current: SOCDataPoint): Anomaly | null {
    if (current.soc > 100) {
      return {
        id: generateAnomalyId(),
        type: 'SOC_OVERFLOW',
        category: 'soc',
        severity: 'high',
        timestamp: current.timestamp,
        message: `SOC invalide: ${current.soc.toFixed(1)}% (> 100%)`,
        details: {
          expectedValue: 100,
          actualValue: current.soc,
          deviation: current.soc - 100,
          affectedMetric: 'soc',
        },
        context: {
          currentSoc: current.soc,
        },
        suggestion:
          'Valeur SOC impossible. Erreur de calibration ou de communication.',
      };
    }

    return null;
  }

  /**
   * Detect SOC underflow (below 0%)
   */
  private detectSOCUnderflow(current: SOCDataPoint): Anomaly | null {
    if (current.soc < 0) {
      return {
        id: generateAnomalyId(),
        type: 'SOC_UNDERFLOW',
        category: 'soc',
        severity: 'high',
        timestamp: current.timestamp,
        message: `SOC invalide: ${current.soc.toFixed(1)}% (< 0%)`,
        details: {
          expectedValue: 0,
          actualValue: current.soc,
          deviation: Math.abs(current.soc),
          affectedMetric: 'soc',
        },
        context: {
          currentSoc: current.soc,
        },
        suggestion:
          'Valeur SOC impossible. Erreur de calibration ou de communication.',
      };
    }

    return null;
  }

  /**
   * Calculate expected SOC change based on power and time
   * Assumes ~60kWh battery for estimation
   */
  private calculateExpectedSOCChange(
    powerW: number,
    durationMinutes: number
  ): number {
    const assumedBatteryWh = 60000; // 60 kWh assumption
    const energyWh = (powerW * durationMinutes) / 60;
    return (energyWh / assumedBatteryWh) * 100;
  }

  /**
   * Calculate linear trend of values
   * Returns slope (positive = increasing, negative = decreasing)
   */
  private calculateTrend(values: number[]): number {
    if (values.length < 2) return 0;

    const n = values.length;
    let sumX = 0,
      sumY = 0,
      sumXY = 0,
      sumX2 = 0;

    for (let i = 0; i < n; i++) {
      sumX += i;
      sumY += values[i];
      sumXY += i * values[i];
      sumX2 += i * i;
    }

    const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    return slope;
  }

  /**
   * Get recent SOC values
   */
  private getRecentSOCValues(count: number): number[] {
    return this.dataBuffer.slice(-count).map((p) => p.soc);
  }

  /**
   * Clear the data buffer
   */
  reset(): void {
    this.dataBuffer = [];
    this.lastStagnationAlert = 0;
  }

  /**
   * Update thresholds
   */
  updateThresholds(thresholds: Partial<DetectionThresholds>): void {
    this.thresholds = { ...this.thresholds, ...thresholds };
  }
}

export default SOCAnomalyDetector;
