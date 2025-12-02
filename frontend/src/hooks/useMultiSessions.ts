// frontend/src/hooks/useMultiSessions.ts
// Hook pour la gestion multi-sessions

import { useState, useCallback, useRef } from 'react';
import type {
  SessionState,
  SessionConfig,
  MultiSessionStore,
  DEFAULT_SESSION_CONFIG
} from '@/types/multiSession.types';
import { createSessionState } from '@/types/multiSession.types';

/**
 * Hook pour gérer plusieurs sessions EVSE simultanément
 */
export function useMultiSessions(): MultiSessionStore {
  const [sessions, setSessions] = useState<SessionState[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const sessionCounter = useRef(0);

  /**
   * Génère un nouveau CP-ID unique
   */
  const generateCpId = useCallback(() => {
    sessionCounter.current += 1;
    const timestamp = Date.now().toString(36).toUpperCase();
    return `SIM-${timestamp}-${sessionCounter.current.toString().padStart(3, '0')}`;
  }, []);

  /**
   * Génère un ID tag unique
   */
  const generateIdTag = useCallback(() => {
    return `TAG-${sessionCounter.current.toString().padStart(3, '0')}`;
  }, []);

  /**
   * Ajoute une nouvelle session
   */
  const addSession = useCallback((configOverrides?: Partial<SessionConfig>): string => {
    const newId = `session-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const cpId = configOverrides?.cpId || generateCpId();
    const idTag = configOverrides?.idTag || generateIdTag();

    const newSession = createSessionState(newId, cpId, {
      ...configOverrides,
      cpId,
      idTag
    });

    setSessions(prev => [...prev, newSession]);
    setActiveSessionId(newId);

    return newId;
  }, [generateCpId, generateIdTag]);

  /**
   * Supprime une session
   */
  const removeSession = useCallback((id: string) => {
    setSessions(prev => {
      const filtered = prev.filter(s => s.id !== id);

      // Si on supprime la session active, sélectionner une autre
      if (activeSessionId === id && filtered.length > 0) {
        setActiveSessionId(filtered[0].id);
      } else if (filtered.length === 0) {
        setActiveSessionId(null);
      }

      return filtered;
    });
  }, [activeSessionId]);

  /**
   * Met à jour une session
   */
  const updateSession = useCallback((id: string, updates: Partial<SessionState>) => {
    setSessions(prev => prev.map(s =>
      s.id === id ? { ...s, ...updates } : s
    ));
  }, []);

  /**
   * Met à jour la configuration d'une session
   */
  const updateSessionConfig = useCallback((id: string, configUpdates: Partial<SessionConfig>) => {
    setSessions(prev => prev.map(s =>
      s.id === id ? {
        ...s,
        config: { ...s.config, ...configUpdates }
      } : s
    ));
  }, []);

  /**
   * Obtient une session par ID
   */
  const getSession = useCallback((id: string): SessionState | undefined => {
    return sessions.find(s => s.id === id);
  }, [sessions]);

  /**
   * Obtient la session active
   */
  const getActiveSession = useCallback((): SessionState | undefined => {
    return activeSessionId ? sessions.find(s => s.id === activeSessionId) : undefined;
  }, [sessions, activeSessionId]);

  /**
   * Change la session active
   */
  const setActiveSession = useCallback((id: string) => {
    if (sessions.some(s => s.id === id)) {
      setActiveSessionId(id);
    }
  }, [sessions]);

  return {
    sessions,
    activeSessionId,
    addSession,
    removeSession,
    setActiveSession,
    updateSession,
    updateSessionConfig,
    getSession,
    getActiveSession
  };
}

export default useMultiSessions;
