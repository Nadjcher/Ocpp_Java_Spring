// frontend/src/store/multiSessionStore.ts
// Store Zustand pour la gestion multi-session avec isolation complete
// VERSION 3.0 - Correction serialisation Map + isolation contexte

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { api } from '../services/api';

// === TYPES ===

/**
 * Etat de connexion d'une session multi-session
 * Distinct de SessionState (session.types.ts) qui est une interface complete
 */
export type MultiSessionStatus =
  | 'NOT_CREATED'      // Slot vide
  | 'CONFIGURED'       // URL + CP-ID definis
  | 'CONNECTING'       // WebSocket en cours
  | 'CONNECTED'        // WS ouvert, Boot envoye
  | 'PREPARING'        // Authorize en cours
  | 'CHARGING'         // Transaction active
  | 'SUSPENDED'        // Charge suspendue
  | 'FINISHING'        // Stop en cours
  | 'DISCONNECTED'     // WS ferme proprement
  | 'ERROR';           // Erreur

// Alias pour compatibilite avec le code existant
export type SessionState = MultiSessionStatus;

export interface SessionConfig {
  url: string;
  chargePointId: string;
  connectorId: number;
  evseType: 'AC_MONO' | 'AC_BI' | 'AC_TRI' | 'DC';
  maxPowerKw: number;
  vehicleId: string;
  idTag: string;
}

export interface SessionMetrics {
  soc: number;
  socTarget: number;
  activePowerKw: number;
  offeredPowerKw: number;
  energyKwh: number;
  voltage: number;
  current: number;
  temperature: number;
}

export interface SessionData {
  id: string;
  index: number;                    // Position dans la grille
  state: MultiSessionStatus;
  config: SessionConfig;
  metrics: SessionMetrics;

  // OCPP
  transactionId: string | null;
  authorized: boolean;
  bootAccepted: boolean;

  // Etat physique
  isParked: boolean;
  isPlugged: boolean;

  // Timing
  createdAt: number;
  connectedAt: number | null;
  chargingStartedAt: number | null;

  // Logs (derniers 100)
  logs: Array<{
    timestamp: number;
    level: 'info' | 'warn' | 'error' | 'debug';
    message: string;
  }>;

  // Chart data (derniers 60 points)
  chartData: Array<{
    timestamp: number;
    soc: number;
    power: number;
    energy: number;
  }>;

  // Erreur
  lastError: string | null;
}

interface MultiSessionState {
  // === STATE ===
  sessions: Map<string, SessionData>;
  activeSessionId: string | null;      // Session selectionnee pour details
  maxSessions: number;                 // Limite (ex: 10)
  isLoaded: boolean;                   // Flag pour savoir si les sessions sont chargees

  // === ACTIONS - Gestion des Sessions ===
  createSession: (config?: Partial<SessionConfig>) => string;
  createSessionAsync: (config?: Partial<SessionConfig>) => Promise<string>;
  removeSession: (id: string) => void;
  removeSessionAsync: (id: string) => Promise<void>;
  updateSession: (id: string, updates: Partial<SessionData>) => void;
  updateSessionConfig: (id: string, config: Partial<SessionConfig>) => void;
  updateSessionMetrics: (id: string, metrics: Partial<SessionMetrics>) => void;
  updateSessionState: (id: string, state: MultiSessionStatus) => void;

  // === ACTIONS - Selection ===
  selectSession: (id: string | null) => void;
  getActiveSession: () => SessionData | null;

  // === ACTIONS - Logs ===
  addLog: (id: string, level: string, message: string) => void;
  addChartPoint: (id: string, point: { soc: number; power: number; energy: number }) => void;

  // === ACTIONS - Bulk ===
  disconnectAll: () => void;
  clearInactiveSessions: () => void;
  loadFromBackend: () => Promise<void>;
  syncToBackend: (session: SessionData) => Promise<void>;

  // === GETTERS ===
  getSession: (id: string) => SessionData | undefined;
  getActiveSessions: () => SessionData[];
  getConnectedSessions: () => SessionData[];
  getSessionCount: () => number;
  getAllSessions: () => SessionData[];
}

// === VALEURS PAR DEFAUT ===

const defaultConfig: SessionConfig = {
  url: 'wss://evplatform.evcharge-pp.totalenergies.com/ocpp',
  chargePointId: 'CP-001',
  connectorId: 1,
  evseType: 'AC_TRI',
  maxPowerKw: 22,
  vehicleId: 'tesla-model-3',
  idTag: 'TAG-001',
};

const defaultMetrics: SessionMetrics = {
  soc: 20,
  socTarget: 80,
  activePowerKw: 0,
  offeredPowerKw: 0,
  energyKwh: 0,
  voltage: 400,
  current: 0,
  temperature: 25,
};

const createDefaultSession = (index: number): SessionData => ({
  id: `session-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
  index,
  state: 'NOT_CREATED',
  config: { ...defaultConfig, chargePointId: `CP-${String(index + 1).padStart(3, '0')}` },
  metrics: { ...defaultMetrics },
  transactionId: null,
  authorized: false,
  bootAccepted: false,
  isParked: false,
  isPlugged: false,
  createdAt: Date.now(),
  connectedAt: null,
  chargingStartedAt: null,
  logs: [],
  chartData: [],
  lastError: null,
});

// === STORE ===

export const useMultiSessionStore = create<MultiSessionState>()(
  persist(
    (set, get) => ({
      // State initial
      sessions: new Map(),
      activeSessionId: null,
      maxSessions: 10,
      isLoaded: false,

      // === CREATION DE SESSION (sync - local only) ===
      createSession: (configOverrides) => {
        const state = get();

        // Verifier limite
        if (state.sessions.size >= state.maxSessions) {
          console.warn(`[MultiSession] Limite de ${state.maxSessions} sessions atteinte`);
          return '';
        }

        // Trouver le prochain index disponible
        const usedIndexes = new Set(
          Array.from(state.sessions.values()).map(s => s.index)
        );
        let nextIndex = 0;
        while (usedIndexes.has(nextIndex)) nextIndex++;

        // Creer la session avec ID unique base sur timestamp + random
        const newSession = createDefaultSession(nextIndex);
        // S'assurer que l'ID est vraiment unique
        newSession.id = `ms-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;

        if (configOverrides) {
          newSession.config = { ...newSession.config, ...configOverrides };
          newSession.state = 'CONFIGURED';
        }

        // IMPORTANT: Isolation - chaque session a son propre contexte
        // Ne jamais partager l'etat entre sessions
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          newSessions.set(newSession.id, newSession);

          return {
            sessions: newSessions,
            // Selectionner automatiquement seulement si c'est la premiere
            activeSessionId: draft.sessions.size === 0 ? newSession.id : draft.activeSessionId,
          };
        });

        console.log(`[MultiSession] Session creee: ${newSession.id} (index: ${nextIndex})`);
        return newSession.id;
      },

      // === CREATION DE SESSION (async - avec backend) ===
      createSessionAsync: async (configOverrides) => {
        const state = get();

        if (state.sessions.size >= state.maxSessions) {
          console.warn(`[MultiSession] Limite de ${state.maxSessions} sessions atteinte`);
          return '';
        }

        // Trouver le prochain index disponible
        const usedIndexes = new Set(
          Array.from(state.sessions.values()).map(s => s.index)
        );
        let nextIndex = 0;
        while (usedIndexes.has(nextIndex)) nextIndex++;

        // Creer la session localement
        const newSession = createDefaultSession(nextIndex);
        newSession.id = `ms-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;

        if (configOverrides) {
          newSession.config = { ...newSession.config, ...configOverrides };
          newSession.state = 'CONFIGURED';
        }

        // Sauvegarder dans le store local
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          newSessions.set(newSession.id, newSession);
          return {
            sessions: newSessions,
            activeSessionId: draft.sessions.size === 0 ? newSession.id : draft.activeSessionId,
          };
        });

        // Synchro backend (fire and forget)
        try {
          await api.createSession(newSession.config.chargePointId);
          console.log(`[MultiSession] Session synced to backend: ${newSession.id}`);
        } catch (error) {
          console.warn(`[MultiSession] Backend sync failed for ${newSession.id}:`, error);
        }

        return newSession.id;
      },

      // === SUPPRESSION DE SESSION (sync) ===
      removeSession: (id) => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          const session = newSessions.get(id);
          if (!session) return draft;

          newSessions.delete(id);

          // Si c'est la session active, en selectionner une autre
          let newActiveId = draft.activeSessionId;
          if (draft.activeSessionId === id) {
            const remaining = Array.from(newSessions.keys());
            newActiveId = remaining.length > 0 ? remaining[0] : null;
          }

          console.log(`[MultiSession] Session supprimee: ${id}`);
          return {
            sessions: newSessions,
            activeSessionId: newActiveId,
          };
        });
      },

      // === SUPPRESSION DE SESSION (async - avec backend) ===
      removeSessionAsync: async (id) => {
        const session = get().sessions.get(id);
        if (!session) return;

        // Supprimer du store local
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          newSessions.delete(id);

          let newActiveId = draft.activeSessionId;
          if (draft.activeSessionId === id) {
            const remaining = Array.from(newSessions.keys());
            newActiveId = remaining.length > 0 ? remaining[0] : null;
          }

          return {
            sessions: newSessions,
            activeSessionId: newActiveId,
          };
        });

        // Synchro backend
        try {
          await api.deleteSession(id);
          console.log(`[MultiSession] Session deleted from backend: ${id}`);
        } catch (error) {
          console.warn(`[MultiSession] Backend delete failed for ${id}:`, error);
        }
      },

      // === MISE A JOUR DE SESSION ===
      updateSession: (id, updates) => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          const session = newSessions.get(id);
          if (session) {
            newSessions.set(id, { ...session, ...updates });
          }
          return { sessions: newSessions };
        });
      },

      updateSessionConfig: (id, config) => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          const session = newSessions.get(id);
          if (session) {
            const updatedSession = {
              ...session,
              config: { ...session.config, ...config },
            };
            // Marquer comme configure si URL et CP-ID definis
            if (updatedSession.config.url && updatedSession.config.chargePointId) {
              if (session.state === 'NOT_CREATED') {
                updatedSession.state = 'CONFIGURED';
              }
            }
            newSessions.set(id, updatedSession);
          }
          return { sessions: newSessions };
        });
      },

      updateSessionMetrics: (id, metrics) => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          const session = newSessions.get(id);
          if (session) {
            newSessions.set(id, {
              ...session,
              metrics: { ...session.metrics, ...metrics },
            });
          }
          return { sessions: newSessions };
        });
      },

      updateSessionState: (id, state) => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          const session = newSessions.get(id);
          if (session) {
            const oldState = session.state;
            const updatedSession = { ...session, state };

            // Mettre a jour les timestamps
            if (state === 'CONNECTED' && oldState !== 'CONNECTED') {
              updatedSession.connectedAt = Date.now();
            }
            if (state === 'CHARGING' && oldState !== 'CHARGING') {
              updatedSession.chargingStartedAt = Date.now();
            }
            if (state === 'DISCONNECTED' || state === 'ERROR') {
              updatedSession.transactionId = null;
              updatedSession.authorized = false;
            }

            newSessions.set(id, updatedSession);
            console.log(`[MultiSession] ${id}: ${oldState} -> ${state}`);
          }
          return { sessions: newSessions };
        });
      },

      // === SELECTION ===
      selectSession: (id) => {
        set((draft) => {
          // Verifier que la session existe si id non null
          if (id === null || draft.sessions.has(id)) {
            return { activeSessionId: id };
          }
          return draft;
        });
      },

      getActiveSession: () => {
        const state = get();
        if (!state.activeSessionId) return null;
        return state.sessions.get(state.activeSessionId) || null;
      },

      // === LOGS ===
      addLog: (id, level, message) => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          const session = newSessions.get(id);
          if (session) {
            const logs = [...session.logs, {
              timestamp: Date.now(),
              level: level as 'info' | 'warn' | 'error' | 'debug',
              message,
            }];
            // Garder les 100 derniers
            if (logs.length > 100) {
              logs.splice(0, logs.length - 100);
            }
            newSessions.set(id, { ...session, logs });
          }
          return { sessions: newSessions };
        });
      },

      addChartPoint: (id, point) => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          const session = newSessions.get(id);
          if (session) {
            const chartData = [...session.chartData, {
              timestamp: Date.now(),
              ...point,
            }];
            // Garder les 60 derniers points
            if (chartData.length > 60) {
              chartData.splice(0, chartData.length - 60);
            }
            newSessions.set(id, { ...session, chartData });
          }
          return { sessions: newSessions };
        });
      },

      // === BULK ACTIONS ===
      disconnectAll: () => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          newSessions.forEach((session, id) => {
            if (['CONNECTING', 'CONNECTED', 'PREPARING', 'CHARGING', 'SUSPENDED'].includes(session.state)) {
              newSessions.set(id, {
                ...session,
                state: 'DISCONNECTED',
                transactionId: null,
                authorized: false,
              });
            }
          });
          return { sessions: newSessions };
        });
      },

      clearInactiveSessions: () => {
        set((draft) => {
          const newSessions = new Map(draft.sessions);
          const toRemove: string[] = [];
          newSessions.forEach((session, id) => {
            if (['NOT_CREATED', 'CONFIGURED', 'DISCONNECTED', 'ERROR'].includes(session.state)) {
              toRemove.push(id);
            }
          });
          toRemove.forEach(id => newSessions.delete(id));
          return { sessions: newSessions };
        });
      },

      // === SYNCHRONISATION BACKEND ===
      loadFromBackend: async () => {
        try {
          const backendSessions = await api.getSessions();
          console.log(`[MultiSession] Loaded ${backendSessions.length} sessions from backend`);

          // Convertir les sessions backend en SessionData
          set((draft) => {
            const newSessions = new Map(draft.sessions);

            backendSessions.forEach((bs: any, index: number) => {
              // Verifier si la session existe deja
              if (!newSessions.has(bs.id)) {
                const sessionData: SessionData = {
                  id: bs.id,
                  index: newSessions.size + index,
                  state: bs.isConnected ? 'CONNECTED' : 'CONFIGURED',
                  config: {
                    url: bs.wsUrl || defaultConfig.url,
                    chargePointId: bs.cpId || 'CP-001',
                    connectorId: bs.config?.connectorId || 1,
                    evseType: bs.config?.evseType || 'AC_TRI',
                    maxPowerKw: bs.config?.maxPowerKw || 22,
                    vehicleId: bs.config?.vehicleId || 'tesla-model-3',
                    idTag: bs.config?.idTag || 'TAG-001',
                  },
                  metrics: { ...defaultMetrics },
                  transactionId: bs.transactionId || null,
                  authorized: bs.authorized || false,
                  bootAccepted: bs.bootAccepted || false,
                  isParked: false,
                  isPlugged: false,
                  createdAt: Date.now(),
                  connectedAt: bs.isConnected ? Date.now() : null,
                  chargingStartedAt: null,
                  logs: [],
                  chartData: [],
                  lastError: null,
                };
                newSessions.set(bs.id, sessionData);
              }
            });

            return { sessions: newSessions, isLoaded: true };
          });
        } catch (error) {
          console.error('[MultiSession] Failed to load from backend:', error);
          set({ isLoaded: true });
        }
      },

      syncToBackend: async (session) => {
        try {
          // Convertir la config multi-session vers le format backend
          await api.updateSession(session.id, {
            cpId: session.config.chargePointId,
            wsUrl: session.config.url,
            // Passer uniquement les champs compatibles avec l'API
          } as any);
          console.log(`[MultiSession] Synced session ${session.id} to backend`);
        } catch (error) {
          console.warn(`[MultiSession] Failed to sync session ${session.id}:`, error);
        }
      },

      // === GETTERS ===
      getSession: (id) => get().sessions.get(id),

      getActiveSessions: () => {
        return Array.from(get().sessions.values())
          .filter(s => !['NOT_CREATED', 'DISCONNECTED', 'ERROR'].includes(s.state));
      },

      getConnectedSessions: () => {
        return Array.from(get().sessions.values())
          .filter(s => ['CONNECTED', 'PREPARING', 'CHARGING', 'SUSPENDED', 'FINISHING'].includes(s.state));
      },

      getSessionCount: () => get().sessions.size,

      getAllSessions: () => {
        return Array.from(get().sessions.values()).sort((a, b) => a.index - b.index);
      },
    }),
    {
      name: 'evse-multi-session-v3', // v3 pour forcer reset du localStorage corrompu
      storage: {
        getItem: (name) => {
          const str = localStorage.getItem(name);
          if (!str) return null;

          try {
            const parsed = JSON.parse(str);
            // Reconvertir le tableau en Map
            if (parsed.state && Array.isArray(parsed.state.sessions)) {
              parsed.state.sessions = new Map(parsed.state.sessions);
            }
            return parsed;
          } catch (e) {
            console.error('[MultiSession] Failed to parse storage:', e);
            return null;
          }
        },
        setItem: (name, value) => {
          try {
            // Convertir la Map en tableau pour la serialisation
            const toStore = {
              ...value,
              state: {
                ...value.state,
                sessions: Array.from(value.state.sessions.entries()),
              },
            };
            localStorage.setItem(name, JSON.stringify(toStore));
          } catch (e) {
            console.error('[MultiSession] Failed to save storage:', e);
          }
        },
        removeItem: (name) => localStorage.removeItem(name),
      },
      // @ts-expect-error - partialize retourne un sous-ensemble du state
      partialize: (state) => ({
        sessions: state.sessions,
        activeSessionId: state.activeSessionId,
        maxSessions: state.maxSessions,
      }),
      onRehydrateStorage: () => (state) => {
        if (state) {
          // S'assurer que sessions est une Map apres rehydratation
          if (!(state.sessions instanceof Map)) {
            state.sessions = new Map();
          }
          state.isLoaded = true;
          console.log('[MultiSession] Rehydrated with', state.sessions.size, 'sessions');
        }
      },
    }
  )
);

// Selecteur pour obtenir une session par ID (reactif)
export const useSession = (sessionId: string | null) => {
  return useMultiSessionStore((state) =>
    sessionId ? state.sessions.get(sessionId) : undefined
  );
};

// Selecteur pour obtenir toutes les sessions en tableau (reactif)
export const useAllSessions = () => {
  return useMultiSessionStore((state) =>
    Array.from(state.sessions.values()).sort((a, b) => a.index - b.index)
  );
};

// Export pour compatibilite
export default useMultiSessionStore;
