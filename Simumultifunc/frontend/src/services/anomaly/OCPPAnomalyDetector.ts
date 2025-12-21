/**
 * OCPPAnomalyDetector - Détection d'anomalies de protocole OCPP
 */

import {
  Anomaly,
  OCPPDataPoint,
  DetectionThresholds,
  DEFAULT_THRESHOLDS,
  generateAnomalyId,
} from '@/types/anomaly.types';

interface MessageTracker {
  messageId: string;
  action: string;
  timestamp: number;
  responded: boolean;
}

export class OCPPAnomalyDetector {
  private thresholds: DetectionThresholds;
  private dataBuffer: OCPPDataPoint[] = [];
  private pendingMessages: Map<string, MessageTracker> = new Map();
  private recentErrors: OCPPDataPoint[] = [];
  private maxBufferSize: number;

  constructor(
    thresholds: DetectionThresholds = DEFAULT_THRESHOLDS,
    maxBufferSize: number = 200
  ) {
    this.thresholds = thresholds;
    this.maxBufferSize = maxBufferSize;
  }

  /**
   * Analyze a new OCPP message and detect anomalies
   */
  analyze(dataPoint: OCPPDataPoint): Anomaly[] {
    const anomalies: Anomaly[] = [];

    // Add to buffer
    this.dataBuffer.push(dataPoint);
    if (this.dataBuffer.length > this.maxBufferSize) {
      this.dataBuffer.shift();
    }

    // Track errors
    if (!dataPoint.success) {
      this.recentErrors.push(dataPoint);
      // Keep only recent errors (last minute)
      const oneMinuteAgo = Date.now() - 60000;
      this.recentErrors = this.recentErrors.filter(
        (e) => e.timestamp > oneMinuteAgo
      );
    }

    // Check for various OCPP anomalies
    const timeoutAnomaly = this.detectTimeout(dataPoint);
    if (timeoutAnomaly) anomalies.push(timeoutAnomaly);

    const errorBurstAnomaly = this.detectErrorBurst(dataPoint);
    if (errorBurstAnomaly) anomalies.push(errorBurstAnomaly);

    const sequenceAnomaly = this.detectSequenceError(dataPoint);
    if (sequenceAnomaly) anomalies.push(sequenceAnomaly);

    const duplicateAnomaly = this.detectDuplicateMessage(dataPoint);
    if (duplicateAnomaly) anomalies.push(duplicateAnomaly);

    // Check for missing responses
    const missingResponseAnomaly = this.checkPendingMessages(dataPoint);
    if (missingResponseAnomaly) anomalies.push(missingResponseAnomaly);

    return anomalies;
  }

  /**
   * Track a request that expects a response
   */
  trackRequest(messageId: string, action: string): void {
    this.pendingMessages.set(messageId, {
      messageId,
      action,
      timestamp: Date.now(),
      responded: false,
    });
  }

  /**
   * Mark a response as received
   */
  trackResponse(messageId: string): void {
    const pending = this.pendingMessages.get(messageId);
    if (pending) {
      pending.responded = true;
      this.pendingMessages.delete(messageId);
    }
  }

  /**
   * Detect message timeout
   */
  private detectTimeout(current: OCPPDataPoint): Anomaly | null {
    if (
      current.responseTimeMs &&
      current.responseTimeMs > this.thresholds.ocppTimeoutMs
    ) {
      return {
        id: generateAnomalyId(),
        type: 'OCPP_TIMEOUT',
        category: 'ocpp',
        severity: 'high',
        timestamp: current.timestamp,
        message: `Timeout OCPP: ${current.action} en ${(current.responseTimeMs / 1000).toFixed(1)}s`,
        details: {
          expectedValue: this.thresholds.ocppTimeoutMs,
          actualValue: current.responseTimeMs,
          deviation: current.responseTimeMs - this.thresholds.ocppTimeoutMs,
          threshold: this.thresholds.ocppTimeoutMs,
          affectedMetric: 'responseTimeMs',
          relatedMessages: [current.action],
        },
        context: {},
        suggestion:
          'La réponse OCPP a dépassé le délai maximal. Vérifier la connectivité réseau.',
      };
    }

    return null;
  }

  /**
   * Detect burst of errors
   */
  private detectErrorBurst(current: OCPPDataPoint): Anomaly | null {
    if (this.recentErrors.length >= this.thresholds.ocppErrorBurstCount) {
      // Group errors by type
      const errorTypes = new Map<string, number>();
      this.recentErrors.forEach((e) => {
        const key = e.errorCode || e.action;
        errorTypes.set(key, (errorTypes.get(key) || 0) + 1);
      });

      const errorSummary = Array.from(errorTypes.entries())
        .map(([type, count]) => `${type}: ${count}`)
        .join(', ');

      // Clear errors to avoid repeated alerts
      const errorCount = this.recentErrors.length;
      this.recentErrors = [];

      return {
        id: generateAnomalyId(),
        type: 'OCPP_ERROR_BURST',
        category: 'ocpp',
        severity: 'critical',
        timestamp: current.timestamp,
        message: `Rafale d'erreurs OCPP: ${errorCount} erreurs en 1 minute`,
        details: {
          threshold: this.thresholds.ocppErrorBurstCount,
          affectedMetric: 'errorCount',
          rawData: {
            errorCount,
            errorTypes: Object.fromEntries(errorTypes),
          },
          relatedMessages: Array.from(errorTypes.keys()),
        },
        context: {},
        suggestion: `Erreurs multiples détectées (${errorSummary}). Vérifier la configuration OCPP.`,
      };
    }

    return null;
  }

  /**
   * Detect OCPP sequence errors (wrong message order)
   */
  private detectSequenceError(current: OCPPDataPoint): Anomaly | null {
    // Define expected sequences
    const expectedSequences: Record<string, string[]> = {
      StartTransaction: ['Authorize', 'StartTransaction'],
      StopTransaction: ['StartTransaction', 'StopTransaction'],
      MeterValues: ['StartTransaction'],
    };

    const requiredPrevious = expectedSequences[current.action];
    if (!requiredPrevious) return null;

    // Check if required previous messages exist
    const recentActions = this.dataBuffer
      .slice(-20)
      .map((p) => p.action)
      .filter((a) => a !== current.action);

    const hasRequired = requiredPrevious.some((req) =>
      recentActions.includes(req)
    );

    if (!hasRequired && this.dataBuffer.length > 5) {
      return {
        id: generateAnomalyId(),
        type: 'OCPP_SEQUENCE_ERROR',
        category: 'ocpp',
        severity: 'medium',
        timestamp: current.timestamp,
        message: `Séquence OCPP incorrecte: ${current.action} sans ${requiredPrevious.join(' ou ')}`,
        details: {
          affectedMetric: 'sequence',
          relatedMessages: [...requiredPrevious, current.action],
          rawData: {
            expectedPrevious: requiredPrevious,
            actualRecent: recentActions.slice(-5),
          },
        },
        context: {},
        suggestion:
          'Message reçu hors séquence. Possible redémarrage ou erreur de synchronisation.',
      };
    }

    return null;
  }

  /**
   * Detect duplicate messages
   */
  private detectDuplicateMessage(current: OCPPDataPoint): Anomaly | null {
    const duplicateWindowMs = this.thresholds.ocppDuplicateWindowMs;
    const windowStart = current.timestamp - duplicateWindowMs;

    // Find same messages in window
    const duplicates = this.dataBuffer.filter(
      (p) =>
        p !== current &&
        p.timestamp >= windowStart &&
        p.action === current.action &&
        p.messageType === current.messageType
    );

    if (duplicates.length > 0) {
      return {
        id: generateAnomalyId(),
        type: 'OCPP_DUPLICATE_MESSAGE',
        category: 'ocpp',
        severity: 'low',
        timestamp: current.timestamp,
        message: `Message OCPP dupliqué: ${current.action} (${duplicates.length + 1} fois en ${duplicateWindowMs}ms)`,
        details: {
          threshold: duplicateWindowMs,
          affectedMetric: 'messageCount',
          relatedMessages: [current.action],
          rawData: {
            duplicateCount: duplicates.length + 1,
            windowMs: duplicateWindowMs,
          },
        },
        context: {},
        suggestion:
          'Messages dupliqués détectés. Possible problème de retransmission.',
      };
    }

    return null;
  }

  /**
   * Check for missing responses to pending messages
   */
  private checkPendingMessages(current: OCPPDataPoint): Anomaly | null {
    const now = current.timestamp;
    const timeoutMs = this.thresholds.ocppTimeoutMs;

    // Find timed out pending messages
    const timedOut: MessageTracker[] = [];
    this.pendingMessages.forEach((tracker) => {
      if (!tracker.responded && now - tracker.timestamp > timeoutMs) {
        timedOut.push(tracker);
      }
    });

    // Clean up timed out messages
    timedOut.forEach((t) => this.pendingMessages.delete(t.messageId));

    if (timedOut.length > 0) {
      return {
        id: generateAnomalyId(),
        type: 'OCPP_MISSING_RESPONSE',
        category: 'ocpp',
        severity: 'high',
        timestamp: current.timestamp,
        message: `${timedOut.length} réponse(s) OCPP manquante(s)`,
        details: {
          threshold: timeoutMs,
          affectedMetric: 'responseCount',
          relatedMessages: timedOut.map((t) => t.action),
          rawData: {
            missingResponses: timedOut.map((t) => ({
              action: t.action,
              waitingMs: now - t.timestamp,
            })),
          },
        },
        context: {},
        suggestion:
          'Certains messages OCPP n\'ont pas reçu de réponse. Vérifier la connexion au serveur central.',
      };
    }

    return null;
  }

  /**
   * Get OCPP statistics
   */
  getStats(): {
    totalMessages: number;
    errorRate: number;
    avgResponseTime: number;
    pendingCount: number;
  } {
    const total = this.dataBuffer.length;
    const errors = this.dataBuffer.filter((p) => !p.success).length;
    const responseTimes = this.dataBuffer
      .filter((p) => p.responseTimeMs)
      .map((p) => p.responseTimeMs!);

    return {
      totalMessages: total,
      errorRate: total > 0 ? (errors / total) * 100 : 0,
      avgResponseTime:
        responseTimes.length > 0
          ? responseTimes.reduce((a, b) => a + b, 0) / responseTimes.length
          : 0,
      pendingCount: this.pendingMessages.size,
    };
  }

  /**
   * Clear the data buffer
   */
  reset(): void {
    this.dataBuffer = [];
    this.pendingMessages.clear();
    this.recentErrors = [];
  }

  /**
   * Update thresholds
   */
  updateThresholds(thresholds: Partial<DetectionThresholds>): void {
    this.thresholds = { ...this.thresholds, ...thresholds };
  }
}

export default OCPPAnomalyDetector;
