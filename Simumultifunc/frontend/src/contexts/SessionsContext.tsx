// frontend/src/contexts/SessionsContext.tsx
// Context React global pour la gestion des sessions - REMPLACE localStorage/BroadcastChannel

import React, { createContext, useContext, useState, useCallback, useRef, ReactNode } from 'react';
import type {
  SessionState,
  SessionConfig,
  SessionMetrics,
  ChartPoint,
  LogEntry,
  DEFAULT_SESSION_CONFIG,
  DEFAULT_SESSION_METRICS,
  createSessionState
} from '@/types';

// Réexporter les types pour compatibilité
export type { SessionState as SessionData, SessionConfig } from '@/types';

interface SessionsContextType {
  // État
  sessions: SessionState[];
  activeSessionId: string | null;

  // Actions de base
  addSession: (config?: Partial<SessionConfig>) => string;
  removeSession: (id: string) => void;
  setActiveSession: (id: string | null) => void;

  // Mise à jour session
  updateSession: (id: string, updates: Partial<SessionState>) => void;
  updateSessionConfig: (id: string, configUpdates: Partial<SessionConfig>) => void;
  updateSessionMetrics: (id: string, metrics: Partial<Pick<SessionState, 'soc' | 'activePower' | 'offeredPower' | 'energy' | 'voltage' | 'current'>>) => void;
  addSessionLog: (id: string, line: string) => void;
  addChartPoint: (id: string, soc: number, power: number) => void;

  // Getters
  getSession: (id: string) => SessionState | undefined;
  getActiveSession: () => SessionState | undefined;
  getChargingSessions: () => SessionState[];
  getConnectedSessions: () => SessionState[];

  // Stats agrégées (pour GPM)
  getStats: () => {
    totalSessions: number;
    connectedCount: number;
    chargingCount: number;
    totalPowerKW: number;
    totalEnergyKWh: number;
    avgSoc: number;
  };
}

const SessionsContext = createContext<SessionsContextType | null>(null);

// Config par défaut importée depuis types
const DEFAULT_CONFIG: SessionConfig = {
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

// Provider
export function SessionsProvider({ children }: { children: ReactNode }) {
  const [sessions, setSessions] = useState<SessionState[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const sessionCounter = useRef(0);

  // Générer un ID unique
  const generateId = useCallback(() => {
    sessionCounter.current += 1;
    return `session-${Date.now()}-${sessionCounter.current}`;
  }, []);

  // Générer un CP-ID unique
  const generateCpId = useCallback(() => {
    const timestamp = Date.now().toString(36).toUpperCase().slice(-4);
    return `SIM-${timestamp}-${(sessionCounter.current).toString().padStart(2, '0')}`;
  }, []);

  // Ajouter une session
  const addSession = useCallback((configOverrides?: Partial<SessionConfig>): string => {
    const id = generateId();
    const cpId = configOverrides?.cpId || generateCpId();
    const idTag = configOverrides?.idTag || `TAG-${sessionCounter.current.toString().padStart(3, '0')}`;

    const newSession: SessionState = {
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
      soc: configOverrides?.socStart ?? DEFAULT_CONFIG.socStart,
      activePower: 0,
      offeredPower: 0,
      energy: 0,
      voltage: 230,
      current: 0,
      config: {
        ...DEFAULT_CONFIG,
        cpId,
        idTag,
        ...configOverrides
      },
      metrics: {
        stationKwMax: 0,
        backendKwMax: 0,
        txpKw: 0,
        txdpKw: 0,
        voltage: 230,
        phases: 1,
        energyKWh: 0
      },
      logs: [],
      chartData: { soc: [], power: [], expected: [] }
    };

    setSessions(prev => [...prev, newSession]);
    setActiveSessionId(id);

    return id;
  }, [generateId, generateCpId]);

  // Supprimer une session
  const removeSession = useCallback((id: string) => {
    setSessions(prev => {
      const filtered = prev.filter(s => s.id !== id);

      // Si on supprime la session active, en sélectionner une autre
      if (activeSessionId === id) {
        if (filtered.length > 0) {
          setActiveSessionId(filtered[filtered.length - 1].id);
        } else {
          setActiveSessionId(null);
        }
      }

      return filtered;
    });
  }, [activeSessionId]);

  // Mettre à jour une session
  const updateSession = useCallback((id: string, updates: Partial<SessionState>) => {
    setSessions(prev => prev.map(s =>
      s.id === id ? { ...s, ...updates } : s
    ));
  }, []);

  // Mettre à jour la config d'une session
  const updateSessionConfig = useCallback((id: string, configUpdates: Partial<SessionConfig>) => {
    setSessions(prev => prev.map(s =>
      s.id === id ? { ...s, config: { ...s.config, ...configUpdates } } : s
    ));
  }, []);

  // Mettre à jour les métriques (optimisé pour updates fréquentes)
  const updateSessionMetrics = useCallback((id: string, metrics: Partial<Pick<SessionState, 'soc' | 'activePower' | 'offeredPower' | 'energy' | 'voltage' | 'current'>>) => {
    setSessions(prev => prev.map(s =>
      s.id === id ? { ...s, ...metrics } : s
    ));
  }, []);

  // Ajouter un log
  const addSessionLog = useCallback((id: string, line: string) => {
    const ts = new Date().toISOString();
    setSessions(prev => prev.map(s =>
      s.id === id ? {
        ...s,
        logs: [...s.logs.slice(-500), { ts, line }] // Garder max 500 logs
      } : s
    ));
  }, []);

  // Ajouter un point au graphique
  const addChartPoint = useCallback((id: string, soc: number, power: number) => {
    const t = Date.now();
    setSessions(prev => prev.map(s =>
      s.id === id ? {
        ...s,
        chartData: {
          ...s.chartData,
          soc: [...s.chartData.soc.slice(-300), { t, y: soc }],
          power: [...s.chartData.power.slice(-300), { t, y: power }]
        }
      } : s
    ));
  }, []);

  // Getters
  const getSession = useCallback((id: string) => {
    return sessions.find(s => s.id === id);
  }, [sessions]);

  const getActiveSession = useCallback(() => {
    return activeSessionId ? sessions.find(s => s.id === activeSessionId) : undefined;
  }, [sessions, activeSessionId]);

  const getChargingSessions = useCallback(() => {
    return sessions.filter(s => s.isCharging);
  }, [sessions]);

  const getConnectedSessions = useCallback(() => {
    return sessions.filter(s => s.isConnected);
  }, [sessions]);

  // Stats agrégées pour GPM
  const getStats = useCallback(() => {
    const charging = sessions.filter(s => s.isCharging);
    const connected = sessions.filter(s => s.isConnected);

    return {
      totalSessions: sessions.length,
      connectedCount: connected.length,
      chargingCount: charging.length,
      totalPowerKW: charging.reduce((sum, s) => sum + s.activePower, 0) / 1000,
      totalEnergyKWh: sessions.reduce((sum, s) => sum + s.energy, 0) / 1000,
      avgSoc: charging.length > 0
        ? charging.reduce((sum, s) => sum + s.soc, 0) / charging.length
        : 0
    };
  }, [sessions]);

  const value: SessionsContextType = {
    sessions,
    activeSessionId,
    addSession,
    removeSession,
    setActiveSession: setActiveSessionId,
    updateSession,
    updateSessionConfig,
    updateSessionMetrics,
    addSessionLog,
    addChartPoint,
    getSession,
    getActiveSession,
    getChargingSessions,
    getConnectedSessions,
    getStats
  };

  return (
    <SessionsContext.Provider value={value}>
      {children}
    </SessionsContext.Provider>
  );
}

// Hook pour utiliser le context
export function useSessions() {
  const context = useContext(SessionsContext);
  if (!context) {
    throw new Error('useSessions must be used within a SessionsProvider');
  }
  return context;
}

// Hook spécifique pour GPM (lecture seule)
export function useSessionsReadOnly() {
  const { sessions, getStats, getChargingSessions, getConnectedSessions } = useSessions();
  return { sessions, getStats, getChargingSessions, getConnectedSessions };
}

export default SessionsContext;
