/**
 * PowerAnomalyDetector - Détection d'anomalies de puissance
 */

import {
  Anomaly,
  PowerDataPoint,
  DetectionThresholds,
  DEFAULT_THRESHOLDS,
  generateAnomalyId,
  getSeverityFromDeviation,
} from '@/types/anomaly.types';

export class PowerAnomalyDetector {
  private thresholds: DetectionThresholds;
  private dataBuffer: PowerDataPoint[] = [];
  private maxBufferSize: number;

  constructor(
    thresholds: DetectionThresholds = DEFAULT_THRESHOLDS,
    maxBufferSize: number = 100
  ) {
    this.thresholds = thresholds;
    this.maxBufferSize = maxBufferSize;
  }

  /**
   * Analyze a new power data point and detect anomalies
   */
  analyze(dataPoint: PowerDataPoint): Anomaly[] {
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

    // Check for various power anomalies
    const spikeAnomaly = this.detectPowerSpike(prev, current);
    if (spikeAnomaly) anomalies.push(spikeAnomaly);

    const dropAnomaly = this.detectPowerDrop(prev, current);
    if (dropAnomaly) anomalies.push(dropAnomaly);

    const oscillationAnomaly = this.detectOscillation();
    if (oscillationAnomaly) anomalies.push(oscillationAnomaly);

    const limitAnomaly = this.detectLimitExceeded(current);
    if (limitAnomaly) anomalies.push(limitAnomaly);

    const negativeAnomaly = this.detectNegativePower(current);
    if (negativeAnomaly) anomalies.push(negativeAnomaly);

    const zeroPowerAnomaly = this.detectZeroPowerCharging(current);
    if (zeroPowerAnomaly) anomalies.push(zeroPowerAnomaly);

    return anomalies;
  }

  /**
   * Detect sudden power spikes
   */
  private detectPowerSpike(
    prev: PowerDataPoint,
    current: PowerDataPoint
  ): Anomaly | null {
    if (prev.powerW <= 0) return null;

    const changePercent = ((current.powerW - prev.powerW) / prev.powerW) * 100;

    if (changePercent > this.thresholds.powerSpikePercent) {
      return {
        id: generateAnomalyId(),
        type: 'POWER_SPIKE',
        category: 'power',
        severity: getSeverityFromDeviation(changePercent, {
          low: 50,
          medium: 100,
          high: 200,
        }),
        timestamp: current.timestamp,
        message: `Pic de puissance détecté: +${changePercent.toFixed(1)}%`,
        details: {
          expectedValue: prev.powerW,
          actualValue: current.powerW,
          deviation: current.powerW - prev.powerW,
          deviationPercent: changePercent,
          threshold: this.thresholds.powerSpikePercent,
          affectedMetric: 'powerW',
        },
        context: {
          currentPower: current.powerW,
          previousValues: this.getRecentPowerValues(5),
        },
        suggestion:
          'Vérifier la stabilité de la connexion et les paramètres de charge',
      };
    }

    return null;
  }

  /**
   * Detect sudden power drops
   */
  private detectPowerDrop(
    prev: PowerDataPoint,
    current: PowerDataPoint
  ): Anomaly | null {
    if (prev.powerW <= 0) return null;

    const changePercent = ((prev.powerW - current.powerW) / prev.powerW) * 100;

    if (changePercent > this.thresholds.powerDropPercent && current.powerW > 0) {
      return {
        id: generateAnomalyId(),
        type: 'POWER_DROP',
        category: 'power',
        severity: getSeverityFromDeviation(changePercent, {
          low: 30,
          medium: 50,
          high: 80,
        }),
        timestamp: current.timestamp,
        message: `Chute de puissance détectée: -${changePercent.toFixed(1)}%`,
        details: {
          expectedValue: prev.powerW,
          actualValue: current.powerW,
          deviation: prev.powerW - current.powerW,
          deviationPercent: changePercent,
          threshold: this.thresholds.powerDropPercent,
          affectedMetric: 'powerW',
        },
        context: {
          currentPower: current.powerW,
          previousValues: this.getRecentPowerValues(5),
        },
        suggestion:
          'Vérifier le câble de charge et la connexion au véhicule',
      };
    }

    return null;
  }

  /**
   * Detect power oscillations (rapid changes back and forth)
   */
  private detectOscillation(): Anomaly | null {
    if (this.dataBuffer.length < this.thresholds.powerOscillationCount + 1) {
      return null;
    }

    const recentPoints = this.dataBuffer.slice(
      -this.thresholds.powerOscillationCount - 1
    );
    let directionChanges = 0;
    let lastDirection = 0;

    for (let i = 1; i < recentPoints.length; i++) {
      const diff = recentPoints[i].powerW - recentPoints[i - 1].powerW;
      const significantChange = Math.abs(diff) > 100; // 100W threshold

      if (significantChange) {
        const currentDirection = diff > 0 ? 1 : -1;
        if (lastDirection !== 0 && currentDirection !== lastDirection) {
          directionChanges++;
        }
        lastDirection = currentDirection;
      }
    }

    if (directionChanges >= this.thresholds.powerOscillationCount - 1) {
      const current = this.dataBuffer[this.dataBuffer.length - 1];
      return {
        id: generateAnomalyId(),
        type: 'POWER_OSCILLATION',
        category: 'power',
        severity: 'medium',
        timestamp: current.timestamp,
        message: `Oscillation de puissance détectée: ${directionChanges + 1} changements de direction`,
        details: {
          affectedMetric: 'powerW',
          rawData: {
            directionChanges,
            recentValues: recentPoints.map((p) => p.powerW),
          },
        },
        context: {
          currentPower: current.powerW,
          previousValues: this.getRecentPowerValues(5),
        },
        suggestion:
          'Possible instabilité du réseau ou problème de régulation',
      };
    }

    return null;
  }

  /**
   * Detect when power exceeds setpoint
   */
  private detectLimitExceeded(current: PowerDataPoint): Anomaly | null {
    if (!current.setpointW || current.setpointW <= 0) return null;

    const deviation = current.powerW - current.setpointW;
    const deviationPercent = (deviation / current.setpointW) * 100;

    if (deviation > this.thresholds.maxPowerDeviationW) {
      return {
        id: generateAnomalyId(),
        type: 'POWER_LIMIT_EXCEEDED',
        category: 'power',
        severity: getSeverityFromDeviation(deviationPercent, {
          low: 5,
          medium: 10,
          high: 20,
        }),
        timestamp: current.timestamp,
        message: `Puissance dépasse le setpoint: ${(deviation / 1000).toFixed(1)} kW au-dessus`,
        details: {
          expectedValue: current.setpointW,
          actualValue: current.powerW,
          deviation,
          deviationPercent,
          threshold: this.thresholds.maxPowerDeviationW,
          affectedMetric: 'powerW',
        },
        context: {
          currentPower: current.powerW,
        },
        suggestion:
          'Le véhicule ne respecte pas la limite de puissance imposée',
      };
    }

    return null;
  }

  /**
   * Detect negative power values
   */
  private detectNegativePower(current: PowerDataPoint): Anomaly | null {
    if (current.powerW < 0) {
      return {
        id: generateAnomalyId(),
        type: 'NEGATIVE_POWER',
        category: 'power',
        severity: 'high',
        timestamp: current.timestamp,
        message: `Puissance négative détectée: ${current.powerW}W`,
        details: {
          expectedValue: 0,
          actualValue: current.powerW,
          deviation: Math.abs(current.powerW),
          affectedMetric: 'powerW',
        },
        context: {
          currentPower: current.powerW,
        },
        suggestion:
          'Vérifier le capteur de puissance ou le sens du courant',
      };
    }

    return null;
  }

  /**
   * Detect zero power during active charging
   */
  private detectZeroPowerCharging(current: PowerDataPoint): Anomaly | null {
    // Check if we had power before and now it's zero
    if (this.dataBuffer.length < 3) return null;

    const recentPoints = this.dataBuffer.slice(-3);
    const hadPower = recentPoints.slice(0, -1).some((p) => p.powerW > 100);
    const noCurrentPower = current.powerW === 0;

    // Only flag if we had power and now we don't
    if (hadPower && noCurrentPower) {
      return {
        id: generateAnomalyId(),
        type: 'ZERO_POWER_CHARGING',
        category: 'power',
        severity: 'medium',
        timestamp: current.timestamp,
        message: 'Puissance nulle pendant la charge',
        details: {
          expectedValue: recentPoints[0].powerW,
          actualValue: 0,
          affectedMetric: 'powerW',
        },
        context: {
          currentPower: 0,
          previousValues: this.getRecentPowerValues(5),
        },
        suggestion:
          'La charge semble interrompue. Vérifier la connexion véhicule.',
      };
    }

    return null;
  }

  /**
   * Get recent power values
   */
  private getRecentPowerValues(count: number): number[] {
    return this.dataBuffer.slice(-count).map((p) => p.powerW);
  }

  /**
   * Clear the data buffer
   */
  reset(): void {
    this.dataBuffer = [];
  }

  /**
   * Update thresholds
   */
  updateThresholds(thresholds: Partial<DetectionThresholds>): void {
    this.thresholds = { ...this.thresholds, ...thresholds };
  }
}

export default PowerAnomalyDetector;
