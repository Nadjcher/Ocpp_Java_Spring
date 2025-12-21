/**
 * AnomalyAnalyzer - Service orchestrateur de détection d'anomalies
 * Combine tous les détecteurs et génère des insights
 */

import {
  Anomaly,
  AnomalyCategory,
  AnomalySeverity,
  AnalysisResult,
  AnalysisStats,
  Insight,
  DetectionConfig,
  DetectionThresholds,
  DEFAULT_CONFIG,
  PowerDataPoint,
  SOCDataPoint,
  OCPPDataPoint,
  SCPDataPoint,
  calculateHealthScore,
  SEVERITY_WEIGHTS,
} from '@/types/anomaly.types';

import { PowerAnomalyDetector } from './PowerAnomalyDetector';
import { SOCAnomalyDetector } from './SOCAnomalyDetector';
import { OCPPAnomalyDetector } from './OCPPAnomalyDetector';
import { SCPAnomalyDetector } from './SCPAnomalyDetector';

export class AnomalyAnalyzer {
  private config: DetectionConfig;
  private powerDetector: PowerAnomalyDetector;
  private socDetector: SOCAnomalyDetector;
  private ocppDetector: OCPPAnomalyDetector;
  private scpDetector: SCPAnomalyDetector;

  private anomalyHistory: Anomaly[] = [];
  private healthScoreHistory: { timestamp: number; score: number }[] = [];
  private maxHistorySize: number;
  private analysisStartTime: number;

  constructor(config: DetectionConfig = DEFAULT_CONFIG, maxHistorySize = 1000) {
    this.config = config;
    this.maxHistorySize = maxHistorySize;
    this.analysisStartTime = Date.now();

    // Initialize detectors
    this.powerDetector = new PowerAnomalyDetector(
      config.thresholds,
      config.windowSize
    );
    this.socDetector = new SOCAnomalyDetector(
      config.thresholds,
      config.windowSize
    );
    this.ocppDetector = new OCPPAnomalyDetector(
      config.thresholds,
      config.windowSize * 2
    );
    this.scpDetector = new SCPAnomalyDetector(
      config.thresholds,
      config.windowSize
    );
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // DATA INGESTION
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Analyze power data
   */
  analyzePower(data: PowerDataPoint): Anomaly[] {
    if (!this.config.enabled) return [];
    const anomalies = this.powerDetector.analyze(data);
    this.recordAnomalies(anomalies);
    return anomalies;
  }

  /**
   * Analyze SOC data
   */
  analyzeSOC(data: SOCDataPoint): Anomaly[] {
    if (!this.config.enabled) return [];
    const anomalies = this.socDetector.analyze(data);
    this.recordAnomalies(anomalies);
    return anomalies;
  }

  /**
   * Analyze OCPP message
   */
  analyzeOCPP(data: OCPPDataPoint): Anomaly[] {
    if (!this.config.enabled) return [];
    const anomalies = this.ocppDetector.analyze(data);
    this.recordAnomalies(anomalies);
    return anomalies;
  }

  /**
   * Analyze SCP/Smart Charging data
   */
  analyzeSCP(data: SCPDataPoint): Anomaly[] {
    if (!this.config.enabled) return [];
    const anomalies = this.scpDetector.analyze(data);
    this.recordAnomalies(anomalies);
    return anomalies;
  }

  /**
   * Analyze all data types at once
   */
  analyzeAll(data: {
    power?: PowerDataPoint;
    soc?: SOCDataPoint;
    ocpp?: OCPPDataPoint;
    scp?: SCPDataPoint;
  }): Anomaly[] {
    const allAnomalies: Anomaly[] = [];

    if (data.power) {
      allAnomalies.push(...this.analyzePower(data.power));
    }
    if (data.soc) {
      allAnomalies.push(...this.analyzeSOC(data.soc));
    }
    if (data.ocpp) {
      allAnomalies.push(...this.analyzeOCPP(data.ocpp));
    }
    if (data.scp) {
      allAnomalies.push(...this.analyzeSCP(data.scp));
    }

    return allAnomalies;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ANALYSIS RESULTS
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Get current analysis result
   */
  getAnalysisResult(): AnalysisResult {
    const now = Date.now();
    const recentAnomalies = this.getRecentAnomalies(5 * 60000); // Last 5 minutes
    const healthScore = calculateHealthScore(recentAnomalies);

    // Record health score
    this.healthScoreHistory.push({ timestamp: now, score: healthScore });
    if (this.healthScoreHistory.length > 100) {
      this.healthScoreHistory.shift();
    }

    return {
      timestamp: now,
      healthScore,
      anomalies: recentAnomalies,
      stats: this.calculateStats(recentAnomalies),
      insights: this.generateInsights(recentAnomalies, healthScore),
    };
  }

  /**
   * Get health score
   */
  getHealthScore(): number {
    const recentAnomalies = this.getRecentAnomalies(5 * 60000);
    return calculateHealthScore(recentAnomalies);
  }

  /**
   * Get all anomalies
   */
  getAllAnomalies(): Anomaly[] {
    return [...this.anomalyHistory];
  }

  /**
   * Get recent anomalies within time window
   */
  getRecentAnomalies(windowMs: number): Anomaly[] {
    const cutoff = Date.now() - windowMs;
    return this.anomalyHistory.filter((a) => a.timestamp >= cutoff);
  }

  /**
   * Get anomalies by category
   */
  getAnomaliesByCategory(category: AnomalyCategory): Anomaly[] {
    return this.anomalyHistory.filter((a) => a.category === category);
  }

  /**
   * Get anomalies by severity
   */
  getAnomaliesBySeverity(severity: AnomalySeverity): Anomaly[] {
    return this.anomalyHistory.filter((a) => a.severity === severity);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // STATISTICS
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Calculate analysis statistics
   */
  private calculateStats(anomalies: Anomaly[]): AnalysisStats {
    const byCategory: Record<AnomalyCategory, number> = {
      power: 0,
      soc: 0,
      ocpp: 0,
      scp: 0,
      timing: 0,
      hardware: 0,
    };

    const bySeverity: Record<AnomalySeverity, number> = {
      low: 0,
      medium: 0,
      high: 0,
      critical: 0,
    };

    anomalies.forEach((a) => {
      byCategory[a.category]++;
      bySeverity[a.severity]++;
    });

    // Calculate trend
    const trendDirection = this.calculateTrend();

    // Calculate average health score
    const avgScore =
      this.healthScoreHistory.length > 0
        ? this.healthScoreHistory.reduce((sum, h) => sum + h.score, 0) /
          this.healthScoreHistory.length
        : 100;

    return {
      totalAnomalies: anomalies.length,
      byCategory,
      bySeverity,
      averageHealthScore: avgScore,
      trendDirection,
      analysisWindowMinutes: (Date.now() - this.analysisStartTime) / 60000,
    };
  }

  /**
   * Calculate health trend
   */
  private calculateTrend(): 'improving' | 'stable' | 'degrading' {
    if (this.healthScoreHistory.length < 5) return 'stable';

    const recent = this.healthScoreHistory.slice(-5);
    const firstHalf = recent.slice(0, 2);
    const secondHalf = recent.slice(-2);

    const firstAvg =
      firstHalf.reduce((sum, h) => sum + h.score, 0) / firstHalf.length;
    const secondAvg =
      secondHalf.reduce((sum, h) => sum + h.score, 0) / secondHalf.length;

    const diff = secondAvg - firstAvg;

    if (diff > 5) return 'improving';
    if (diff < -5) return 'degrading';
    return 'stable';
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // INSIGHTS
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Generate insights from anomalies
   */
  private generateInsights(
    anomalies: Anomaly[],
    healthScore: number
  ): Insight[] {
    const insights: Insight[] = [];

    // Health score insight
    if (healthScore >= 90) {
      insights.push({
        id: 'health-excellent',
        type: 'info',
        title: 'Excellent score de santé',
        description: `Le système fonctionne de manière optimale (${healthScore}%)`,
      });
    } else if (healthScore >= 70) {
      insights.push({
        id: 'health-good',
        type: 'info',
        title: 'Bon score de santé',
        description: `Performance acceptable avec quelques anomalies mineures (${healthScore}%)`,
      });
    } else if (healthScore >= 50) {
      insights.push({
        id: 'health-warning',
        type: 'warning',
        title: 'Score de santé dégradé',
        description: `Plusieurs anomalies détectées, surveillance recommandée (${healthScore}%)`,
        actionable: true,
        action: 'Examiner les anomalies critiques',
      });
    } else {
      insights.push({
        id: 'health-critical',
        type: 'alert',
        title: 'Score de santé critique',
        description: `Anomalies importantes détectées, intervention recommandée (${healthScore}%)`,
        actionable: true,
        action: 'Intervention immédiate requise',
      });
    }

    // Category-specific insights
    const categoryStats = this.getCategoryStats(anomalies);

    if (categoryStats.power > 3) {
      insights.push({
        id: 'power-issues',
        type: 'warning',
        title: 'Problèmes de puissance récurrents',
        description: `${categoryStats.power} anomalies de puissance détectées`,
        relatedAnomalies: anomalies
          .filter((a) => a.category === 'power')
          .map((a) => a.id),
        actionable: true,
        action: 'Vérifier la connexion électrique et le câble de charge',
      });
    }

    if (categoryStats.soc > 3) {
      insights.push({
        id: 'soc-issues',
        type: 'warning',
        title: 'Anomalies SOC détectées',
        description: `${categoryStats.soc} problèmes de suivi SOC`,
        relatedAnomalies: anomalies
          .filter((a) => a.category === 'soc')
          .map((a) => a.id),
        actionable: true,
        action: 'Vérifier la communication véhicule',
      });
    }

    if (categoryStats.ocpp > 5) {
      insights.push({
        id: 'ocpp-issues',
        type: 'alert',
        title: 'Communication OCPP instable',
        description: `${categoryStats.ocpp} erreurs de protocole OCPP`,
        relatedAnomalies: anomalies
          .filter((a) => a.category === 'ocpp')
          .map((a) => a.id),
        actionable: true,
        action: 'Vérifier la connexion au serveur central',
      });
    }

    if (categoryStats.scp > 2) {
      insights.push({
        id: 'scp-issues',
        type: 'warning',
        title: 'Régulation Smart Charging dégradée',
        description: `${categoryStats.scp} écarts par rapport aux setpoints`,
        relatedAnomalies: anomalies
          .filter((a) => a.category === 'scp')
          .map((a) => a.id),
        actionable: true,
        action: 'Vérifier la configuration GPM/CSMS',
      });
    }

    // Critical anomalies insight
    const criticalCount = anomalies.filter(
      (a) => a.severity === 'critical'
    ).length;
    if (criticalCount > 0) {
      insights.push({
        id: 'critical-anomalies',
        type: 'alert',
        title: 'Anomalies critiques',
        description: `${criticalCount} anomalie(s) critique(s) nécessitant une attention immédiate`,
        relatedAnomalies: anomalies
          .filter((a) => a.severity === 'critical')
          .map((a) => a.id),
        actionable: true,
        action: 'Examiner et résoudre les problèmes critiques',
      });
    }

    // Trend insight
    const trend = this.calculateTrend();
    if (trend === 'degrading') {
      insights.push({
        id: 'trend-degrading',
        type: 'warning',
        title: 'Tendance à la baisse',
        description:
          'Le score de santé diminue progressivement. Surveillance accrue recommandée.',
      });
    } else if (trend === 'improving') {
      insights.push({
        id: 'trend-improving',
        type: 'info',
        title: 'Amélioration en cours',
        description:
          'Le score de santé s\'améliore. Les mesures correctives semblent efficaces.',
      });
    }

    return insights;
  }

  /**
   * Get category statistics
   */
  private getCategoryStats(
    anomalies: Anomaly[]
  ): Record<AnomalyCategory, number> {
    const stats: Record<AnomalyCategory, number> = {
      power: 0,
      soc: 0,
      ocpp: 0,
      scp: 0,
      timing: 0,
      hardware: 0,
    };

    anomalies.forEach((a) => stats[a.category]++);
    return stats;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // MANAGEMENT
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Record new anomalies
   */
  private recordAnomalies(anomalies: Anomaly[]): void {
    this.anomalyHistory.push(...anomalies);

    // Trim history
    while (this.anomalyHistory.length > this.maxHistorySize) {
      this.anomalyHistory.shift();
    }
  }

  /**
   * Mark anomaly as resolved
   */
  resolveAnomaly(anomalyId: string): boolean {
    const anomaly = this.anomalyHistory.find((a) => a.id === anomalyId);
    if (anomaly) {
      anomaly.resolved = true;
      anomaly.resolvedAt = Date.now();
      return true;
    }
    return false;
  }

  /**
   * Clear all history
   */
  clearHistory(): void {
    this.anomalyHistory = [];
    this.healthScoreHistory = [];
    this.analysisStartTime = Date.now();
  }

  /**
   * Reset all detectors
   */
  reset(): void {
    this.powerDetector.reset();
    this.socDetector.reset();
    this.ocppDetector.reset();
    this.scpDetector.reset();
    this.clearHistory();
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<DetectionConfig>): void {
    this.config = { ...this.config, ...config };

    if (config.thresholds) {
      this.updateThresholds(config.thresholds);
    }
  }

  /**
   * Update thresholds for all detectors
   */
  updateThresholds(thresholds: Partial<DetectionThresholds>): void {
    this.config.thresholds = { ...this.config.thresholds, ...thresholds };
    this.powerDetector.updateThresholds(thresholds);
    this.socDetector.updateThresholds(thresholds);
    this.ocppDetector.updateThresholds(thresholds);
    this.scpDetector.updateThresholds(thresholds);
  }

  /**
   * Enable/disable detection
   */
  setEnabled(enabled: boolean): void {
    this.config.enabled = enabled;
  }

  /**
   * Check if detection is enabled
   */
  isEnabled(): boolean {
    return this.config.enabled;
  }

  /**
   * Get current configuration
   */
  getConfig(): DetectionConfig {
    return { ...this.config };
  }
}

export default AnomalyAnalyzer;
