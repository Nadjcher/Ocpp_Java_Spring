/**
 * useAnomalyAnalysis - Hook React pour l'analyse d'anomalies
 * Intègre le système de détection avec les composants React
 */

import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  Anomaly,
  AnalysisResult,
  DetectionConfig,
  DetectionThresholds,
  DEFAULT_CONFIG,
  PowerDataPoint,
  SOCDataPoint,
  OCPPDataPoint,
  SCPDataPoint,
} from '@/types/anomaly.types';
import { AnomalyAnalyzer } from '@/services/anomaly/AnomalyAnalyzer';

interface UseAnomalyAnalysisOptions {
  enabled?: boolean;
  config?: Partial<DetectionConfig>;
  onAnomaly?: (anomaly: Anomaly) => void;
  onCritical?: (anomaly: Anomaly) => void;
  autoAnalyzeInterval?: number; // ms, 0 to disable
}

interface UseAnomalyAnalysisReturn {
  // State
  enabled: boolean;
  analysisResult: AnalysisResult | null;
  healthScore: number;
  recentAnomalies: Anomaly[];
  allAnomalies: Anomaly[];
  isAnalyzing: boolean;

  // Actions
  analyzePower: (data: PowerDataPoint) => Anomaly[];
  analyzeSOC: (data: SOCDataPoint) => Anomaly[];
  analyzeOCPP: (data: OCPPDataPoint) => Anomaly[];
  analyzeSCP: (data: SCPDataPoint) => Anomaly[];
  analyzeAll: (data: {
    power?: PowerDataPoint;
    soc?: SOCDataPoint;
    ocpp?: OCPPDataPoint;
    scp?: SCPDataPoint;
  }) => Anomaly[];
  refreshAnalysis: () => void;
  resolveAnomaly: (id: string) => void;
  clearHistory: () => void;
  reset: () => void;

  // Configuration
  setEnabled: (enabled: boolean) => void;
  updateConfig: (config: Partial<DetectionConfig>) => void;
  updateThresholds: (thresholds: Partial<DetectionThresholds>) => void;
  getConfig: () => DetectionConfig;
}

export function useAnomalyAnalysis(
  options: UseAnomalyAnalysisOptions = {}
): UseAnomalyAnalysisReturn {
  const {
    enabled: initialEnabled = true,
    config: initialConfig,
    onAnomaly,
    onCritical,
    autoAnalyzeInterval = 0,
  } = options;

  // Create analyzer instance (stable reference)
  const analyzerRef = useRef<AnomalyAnalyzer | null>(null);
  if (!analyzerRef.current) {
    const config = { ...DEFAULT_CONFIG, ...initialConfig };
    analyzerRef.current = new AnomalyAnalyzer(config);
  }

  // State
  const [enabled, setEnabledState] = useState(initialEnabled);
  const [analysisResult, setAnalysisResult] = useState<AnalysisResult | null>(
    null
  );
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [updateTrigger, setUpdateTrigger] = useState(0);

  // Memoized values
  const healthScore = useMemo(
    () => analysisResult?.healthScore ?? 100,
    [analysisResult]
  );

  const recentAnomalies = useMemo(
    () => analysisResult?.anomalies ?? [],
    [analysisResult]
  );

  const allAnomalies = useMemo(
    () => analyzerRef.current?.getAllAnomalies() ?? [],
    [updateTrigger]
  );

  // Handle anomaly callbacks
  const handleNewAnomalies = useCallback(
    (anomalies: Anomaly[]) => {
      if (anomalies.length === 0) return;

      anomalies.forEach((anomaly) => {
        onAnomaly?.(anomaly);

        if (anomaly.severity === 'critical') {
          onCritical?.(anomaly);
        }
      });

      // Trigger update
      setUpdateTrigger((t) => t + 1);
    },
    [onAnomaly, onCritical]
  );

  // Analysis functions
  const analyzePower = useCallback(
    (data: PowerDataPoint): Anomaly[] => {
      if (!analyzerRef.current || !enabled) return [];
      const anomalies = analyzerRef.current.analyzePower(data);
      handleNewAnomalies(anomalies);
      return anomalies;
    },
    [enabled, handleNewAnomalies]
  );

  const analyzeSOC = useCallback(
    (data: SOCDataPoint): Anomaly[] => {
      if (!analyzerRef.current || !enabled) return [];
      const anomalies = analyzerRef.current.analyzeSOC(data);
      handleNewAnomalies(anomalies);
      return anomalies;
    },
    [enabled, handleNewAnomalies]
  );

  const analyzeOCPP = useCallback(
    (data: OCPPDataPoint): Anomaly[] => {
      if (!analyzerRef.current || !enabled) return [];
      const anomalies = analyzerRef.current.analyzeOCPP(data);
      handleNewAnomalies(anomalies);
      return anomalies;
    },
    [enabled, handleNewAnomalies]
  );

  const analyzeSCP = useCallback(
    (data: SCPDataPoint): Anomaly[] => {
      if (!analyzerRef.current || !enabled) return [];
      const anomalies = analyzerRef.current.analyzeSCP(data);
      handleNewAnomalies(anomalies);
      return anomalies;
    },
    [enabled, handleNewAnomalies]
  );

  const analyzeAll = useCallback(
    (data: {
      power?: PowerDataPoint;
      soc?: SOCDataPoint;
      ocpp?: OCPPDataPoint;
      scp?: SCPDataPoint;
    }): Anomaly[] => {
      if (!analyzerRef.current || !enabled) return [];
      const anomalies = analyzerRef.current.analyzeAll(data);
      handleNewAnomalies(anomalies);
      return anomalies;
    },
    [enabled, handleNewAnomalies]
  );

  // Refresh analysis result
  const refreshAnalysis = useCallback(() => {
    if (!analyzerRef.current) return;

    setIsAnalyzing(true);
    try {
      const result = analyzerRef.current.getAnalysisResult();
      setAnalysisResult(result);
    } finally {
      setIsAnalyzing(false);
    }
  }, []);

  // Resolve anomaly
  const resolveAnomaly = useCallback((id: string) => {
    if (!analyzerRef.current) return;
    analyzerRef.current.resolveAnomaly(id);
    setUpdateTrigger((t) => t + 1);
  }, []);

  // Clear history
  const clearHistory = useCallback(() => {
    if (!analyzerRef.current) return;
    analyzerRef.current.clearHistory();
    setAnalysisResult(null);
    setUpdateTrigger((t) => t + 1);
  }, []);

  // Reset analyzer
  const reset = useCallback(() => {
    if (!analyzerRef.current) return;
    analyzerRef.current.reset();
    setAnalysisResult(null);
    setUpdateTrigger((t) => t + 1);
  }, []);

  // Set enabled
  const setEnabled = useCallback((value: boolean) => {
    setEnabledState(value);
    analyzerRef.current?.setEnabled(value);
  }, []);

  // Update config
  const updateConfig = useCallback((config: Partial<DetectionConfig>) => {
    analyzerRef.current?.updateConfig(config);
  }, []);

  // Update thresholds
  const updateThresholds = useCallback(
    (thresholds: Partial<DetectionThresholds>) => {
      analyzerRef.current?.updateThresholds(thresholds);
    },
    []
  );

  // Get config
  const getConfig = useCallback((): DetectionConfig => {
    return analyzerRef.current?.getConfig() ?? DEFAULT_CONFIG;
  }, []);

  // Auto-analyze interval
  useEffect(() => {
    if (!enabled || autoAnalyzeInterval <= 0) return;

    const interval = setInterval(() => {
      refreshAnalysis();
    }, autoAnalyzeInterval);

    return () => clearInterval(interval);
  }, [enabled, autoAnalyzeInterval, refreshAnalysis]);

  // Initial analysis
  useEffect(() => {
    if (enabled) {
      refreshAnalysis();
    }
  }, [enabled, refreshAnalysis]);

  // Sync enabled state with analyzer
  useEffect(() => {
    analyzerRef.current?.setEnabled(enabled);
  }, [enabled]);

  return {
    // State
    enabled,
    analysisResult,
    healthScore,
    recentAnomalies,
    allAnomalies,
    isAnalyzing,

    // Actions
    analyzePower,
    analyzeSOC,
    analyzeOCPP,
    analyzeSCP,
    analyzeAll,
    refreshAnalysis,
    resolveAnomaly,
    clearHistory,
    reset,

    // Configuration
    setEnabled,
    updateConfig,
    updateThresholds,
    getConfig,
  };
}

export default useAnomalyAnalysis;
