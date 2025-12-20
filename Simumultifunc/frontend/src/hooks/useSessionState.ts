// frontend/src/hooks/useSessionState.ts
// Hook pour gérer l'état local isolé par session

import { useRef, useCallback, useEffect } from 'react';

/**
 * État local d'une session (non persisté sur le backend)
 */
export interface SessionLocalState {
  // État visuel
  isParked: boolean;
  isPlugged: boolean;
  mvRunning: boolean;

  // Charge
  chargeStartTime: number | null;
  energyStartKWh: number | null;
  energyNowKWh: number | null;
  energyFromPowerKWh: number;
  lastPowerMs: number | null;
  socFilt: number | null;
  pActiveFilt: number | null;
  lastRealMvMs: number;

  // Graphiques
  series: {
    soc: Array<{ t: number; y: number }>;
    pActive: Array<{ t: number; y: number }>;
    expected: Array<{ t: number; y: number }>;
  };

  // Logs
  logs: Array<{ ts: string; line: string }>;

  // Configuration locale
  socStart: number;
  socTarget: number;
  vehicleId: string;
}

const DEFAULT_LOCAL_STATE: SessionLocalState = {
  isParked: false,
  isPlugged: false,
  mvRunning: false,
  chargeStartTime: null,
  energyStartKWh: null,
  energyNowKWh: null,
  energyFromPowerKWh: 0,
  lastPowerMs: null,
  socFilt: 20,
  pActiveFilt: null,
  lastRealMvMs: 0,
  series: { soc: [], pActive: [], expected: [] },
  logs: [],
  socStart: 20,
  socTarget: 80,
  vehicleId: '1'
};

/**
 * Cache global pour les états locaux des sessions
 * Permet de conserver l'état quand on change de session
 */
const sessionStateCache = new Map<string, SessionLocalState>();

/**
 * Hook pour gérer l'état local isolé par session
 * Sauvegarde automatiquement l'état quand on change de session
 */
export function useSessionState(sessionId: string | null) {
  const previousSessionIdRef = useRef<string | null>(null);
  const currentStateRef = useRef<SessionLocalState>({ ...DEFAULT_LOCAL_STATE });

  // Sauvegarder l'état actuel dans le cache
  const saveCurrentState = useCallback(() => {
    if (previousSessionIdRef.current) {
      sessionStateCache.set(previousSessionIdRef.current, { ...currentStateRef.current });
    }
  }, []);

  // Charger l'état depuis le cache ou créer un nouveau
  const loadState = useCallback((id: string | null): SessionLocalState => {
    if (!id) return { ...DEFAULT_LOCAL_STATE };

    const cached = sessionStateCache.get(id);
    if (cached) {
      return { ...cached };
    }

    return { ...DEFAULT_LOCAL_STATE };
  }, []);

  // Quand la session change, sauvegarder l'ancienne et charger la nouvelle
  useEffect(() => {
    if (previousSessionIdRef.current !== sessionId) {
      // Sauvegarder l'état de l'ancienne session
      saveCurrentState();

      // Charger l'état de la nouvelle session
      currentStateRef.current = loadState(sessionId);

      // Mémoriser la nouvelle session
      previousSessionIdRef.current = sessionId;
    }
  }, [sessionId, saveCurrentState, loadState]);

  // Obtenir l'état actuel
  const getState = useCallback((): SessionLocalState => {
    return currentStateRef.current;
  }, []);

  // Mettre à jour une partie de l'état
  const updateState = useCallback((updates: Partial<SessionLocalState>) => {
    currentStateRef.current = { ...currentStateRef.current, ...updates };

    // Sauvegarder immédiatement dans le cache si on a une session
    if (sessionId) {
      sessionStateCache.set(sessionId, { ...currentStateRef.current });
    }
  }, [sessionId]);

  // Obtenir une valeur spécifique
  const getValue = useCallback(<K extends keyof SessionLocalState>(key: K): SessionLocalState[K] => {
    return currentStateRef.current[key];
  }, []);

  // Définir une valeur spécifique
  const setValue = useCallback(<K extends keyof SessionLocalState>(key: K, value: SessionLocalState[K]) => {
    currentStateRef.current[key] = value;

    if (sessionId) {
      sessionStateCache.set(sessionId, { ...currentStateRef.current });
    }
  }, [sessionId]);

  // Réinitialiser l'état d'une session
  const resetState = useCallback((id?: string) => {
    const targetId = id || sessionId;
    if (targetId) {
      sessionStateCache.delete(targetId);
      if (targetId === sessionId) {
        currentStateRef.current = { ...DEFAULT_LOCAL_STATE };
      }
    }
  }, [sessionId]);

  // Supprimer une session du cache
  const removeSession = useCallback((id: string) => {
    sessionStateCache.delete(id);
  }, []);

  // Nettoyer les sessions orphelines
  const cleanupOrphanedSessions = useCallback((activeSessionIds: string[]) => {
    const activeSet = new Set(activeSessionIds);
    for (const id of sessionStateCache.keys()) {
      if (!activeSet.has(id)) {
        sessionStateCache.delete(id);
      }
    }
  }, []);

  return {
    getState,
    updateState,
    getValue,
    setValue,
    resetState,
    removeSession,
    cleanupOrphanedSessions,
    currentState: currentStateRef.current
  };
}

/**
 * Obtenir l'état d'une session spécifique (lecture seule)
 */
export function getSessionState(sessionId: string): SessionLocalState | undefined {
  return sessionStateCache.get(sessionId);
}

/**
 * Vérifier si une session a un état en cache
 */
export function hasSessionState(sessionId: string): boolean {
  return sessionStateCache.has(sessionId);
}

/**
 * Obtenir toutes les sessions en cache
 */
export function getAllCachedSessionIds(): string[] {
  return Array.from(sessionStateCache.keys());
}

export default useSessionState;
