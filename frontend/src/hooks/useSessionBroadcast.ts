// frontend/src/hooks/useSessionBroadcast.ts
// Hook pour le broadcast des sessions vers SimuGPM

import { useEffect, useCallback, useRef } from 'react';
import type { SessionState, BroadcastSessionData, BroadcastMessage } from '@/types/multiSession.types';

const STORAGE_KEY = 'evse-sessions-broadcast';
const BROADCAST_CHANNEL = 'evse-sessions-channel';

/**
 * Hook pour broadcaster les sessions vers d'autres onglets (SimuGPM)
 */
export function useSessionBroadcast(
  sessions: SessionState[],
  onExternalUpdate?: (sessions: BroadcastSessionData[]) => void
) {
  const channelRef = useRef<BroadcastChannel | null>(null);

  /**
   * Convertit les sessions en format broadcast
   */
  const sessionsTobroadcast = useCallback((sessions: SessionState[]): BroadcastSessionData[] => {
    return sessions.map(s => ({
      sessionId: s.id,
      cpId: s.cpId,
      status: s.status,
      isConnected: s.isConnected,
      isCharging: s.isCharging,
      soc: s.soc ?? 0,
      offeredPower: s.offeredPower ?? 0,
      activePower: s.activePower ?? 0,
      setPoint: s.metrics?.backendKwMax ?? 0,
      energy: (s.energy ?? 0) * 1000,
      voltage: 230,
      current: ((s.activePower ?? 0) * 1000) / 230,
      transactionId: s.txId,
      config: s.config
    }));
  }, []);

  /**
   * Broadcast les sessions vers localStorage et BroadcastChannel
   */
  const broadcastSessions = useCallback(() => {
    const data = sessionsTobroadcast(sessions);

    // Sauvegarder dans localStorage
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        sessions: data,
        timestamp: Date.now()
      }));
    } catch (e) {
      console.error('Failed to save sessions to localStorage:', e);
    }

    // Émettre via BroadcastChannel si disponible
    try {
      if (channelRef.current) {
        channelRef.current.postMessage({
          type: 'FULL_SYNC',
          payload: data,
          timestamp: Date.now()
        } as BroadcastMessage);
      }
    } catch (e) {
      // BroadcastChannel non supporté ou erreur
    }
  }, [sessions, sessionsTobroadcast]);

  // Initialiser BroadcastChannel
  useEffect(() => {
    try {
      channelRef.current = new BroadcastChannel(BROADCAST_CHANNEL);

      // Écouter les messages externes
      if (onExternalUpdate) {
        channelRef.current.onmessage = (event: MessageEvent<BroadcastMessage>) => {
          if (event.data.type === 'FULL_SYNC') {
            onExternalUpdate(event.data.payload as BroadcastSessionData[]);
          }
        };
      }
    } catch (e) {
      // BroadcastChannel non supporté
      console.warn('BroadcastChannel not supported, using localStorage only');
    }

    return () => {
      channelRef.current?.close();
      channelRef.current = null;
    };
  }, [onExternalUpdate]);

  // Broadcast à chaque changement de sessions
  useEffect(() => {
    broadcastSessions();
  }, [sessions, broadcastSessions]);

  // Écouter les mises à jour externes via localStorage
  useEffect(() => {
    if (!onExternalUpdate) return;

    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === STORAGE_KEY && e.newValue) {
        try {
          const data = JSON.parse(e.newValue);
          onExternalUpdate(data.sessions);
        } catch {
          // Ignore parsing errors
        }
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, [onExternalUpdate]);

  return { broadcastSessions };
}

/**
 * Hook pour recevoir les sessions broadcastées (utilisé par SimuGPM)
 */
export function useSessionReceiver() {
  const loadSessions = useCallback((): BroadcastSessionData[] => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const data = JSON.parse(stored);
        return data.sessions || [];
      }
    } catch (e) {
      console.error('Error loading sessions:', e);
    }
    return [];
  }, []);

  return { loadSessions, STORAGE_KEY };
}

export default useSessionBroadcast;
