/**
 * GPM Store - State Management avec Zustand
 * Gestion centralisée des simulations GPM Dry-Run
 */

import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';
import {
  GPMSimulation,
  EVTypeConfig,
  GPMChargeType,
  GPMFormState,
  GPMVehicleFormState,
  GPMConfigStatus,
  CreateSimulationRequest,
  AddVehicleRequest,
  DEFAULT_FORM_STATE,
  DEFAULT_VEHICLE_FORM,
} from '@/types/gpm.types';

// ─────────────────────────────────────────────────────────────────────────────
// API Service
// ─────────────────────────────────────────────────────────────────────────────

const API_BASE = '/api/gpm';

async function fetchApi<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || `HTTP ${response.status}`);
  }

  return response.json();
}

// ─────────────────────────────────────────────────────────────────────────────
// Types Store
// ─────────────────────────────────────────────────────────────────────────────

interface GPMState {
  // Configuration
  configStatus: GPMConfigStatus | null;
  vehicleTypes: EVTypeConfig[];
  vehiclesByType: Record<GPMChargeType, EVTypeConfig[]>;

  // Current Simulation
  simulation: GPMSimulation | null;
  simulations: GPMSimulation[];

  // Forms
  formState: GPMFormState;
  vehicleForm: GPMVehicleFormState;
  selectedChargeType: GPMChargeType;

  // UI State
  loading: boolean;
  error: string | null;
  pollingInterval: number | null;
}

interface GPMActions {
  // Configuration
  fetchConfigStatus: () => Promise<void>;
  fetchVehicleTypes: () => Promise<void>;

  // Simulation CRUD
  createSimulation: (request?: CreateSimulationRequest) => Promise<GPMSimulation>;
  fetchSimulation: (id: string) => Promise<void>;
  fetchAllSimulations: () => Promise<void>;
  deleteSimulation: (id: string) => Promise<void>;

  // Vehicles
  addVehicle: (request: AddVehicleRequest) => Promise<void>;
  removeVehicle: (evseId: string) => Promise<void>;

  // Control
  startSimulation: () => Promise<void>;
  stopSimulation: () => Promise<void>;

  // Polling
  startPolling: (intervalMs?: number) => void;
  stopPolling: () => void;

  // Forms
  setFormState: (state: Partial<GPMFormState>) => void;
  resetFormState: () => void;
  setVehicleForm: (state: Partial<GPMVehicleFormState>) => void;
  resetVehicleForm: () => void;
  setSelectedChargeType: (type: GPMChargeType) => void;

  // Utils
  clearError: () => void;
  reset: () => void;
}

type GPMStore = GPMState & GPMActions;

// ─────────────────────────────────────────────────────────────────────────────
// Initial State
// ─────────────────────────────────────────────────────────────────────────────

const initialState: GPMState = {
  configStatus: null,
  vehicleTypes: [],
  vehiclesByType: { MONO: [], TRI: [], DC: [] },
  simulation: null,
  simulations: [],
  formState: { ...DEFAULT_FORM_STATE },
  vehicleForm: { ...DEFAULT_VEHICLE_FORM },
  selectedChargeType: 'DC',
  loading: false,
  error: null,
  pollingInterval: null,
};

// ─────────────────────────────────────────────────────────────────────────────
// Store
// ─────────────────────────────────────────────────────────────────────────────

export const useGPMStore = create<GPMStore>()(
  devtools(
    persist(
      (set, get) => ({
        ...initialState,

        // ═══════════════════════════════════════════════════════════════════
        // CONFIGURATION
        // ═══════════════════════════════════════════════════════════════════

        fetchConfigStatus: async () => {
          try {
            const status = await fetchApi<GPMConfigStatus>(`${API_BASE}/config/status`);
            set({ configStatus: status });
          } catch (error) {
            console.error('Failed to fetch config status:', error);
          }
        },

        fetchVehicleTypes: async () => {
          try {
            set({ loading: true });
            const [types, grouped] = await Promise.all([
              fetchApi<EVTypeConfig[]>(`${API_BASE}/vehicles/types`),
              fetchApi<Record<GPMChargeType, EVTypeConfig[]>>(`${API_BASE}/vehicles/types/grouped`),
            ]);
            set({
              vehicleTypes: types,
              vehiclesByType: grouped,
              loading: false,
            });
          } catch (error) {
            set({ error: String(error), loading: false });
          }
        },

        // ═══════════════════════════════════════════════════════════════════
        // SIMULATION CRUD
        // ═══════════════════════════════════════════════════════════════════

        createSimulation: async (request) => {
          try {
            set({ loading: true, error: null });
            const { formState } = get();
            const req: CreateSimulationRequest = request || {
              name: formState.simulationName,
              rootNodeId: formState.rootNodeId,
              tickIntervalMinutes: formState.tickIntervalMinutes,
              numberOfTicks: formState.numberOfTicks,
              timeScale: formState.timeScale,
              mode: formState.mode,
            };

            const simulation = await fetchApi<GPMSimulation>(`${API_BASE}/simulations`, {
              method: 'POST',
              body: JSON.stringify(req),
            });

            set({ simulation, loading: false });
            return simulation;
          } catch (error) {
            set({ error: String(error), loading: false });
            throw error;
          }
        },

        fetchSimulation: async (id) => {
          try {
            const simulation = await fetchApi<GPMSimulation>(
              `${API_BASE}/simulations/${id}?includeResults=true`
            );
            set({ simulation });
          } catch (error) {
            set({ error: String(error) });
          }
        },

        fetchAllSimulations: async () => {
          try {
            const simulations = await fetchApi<GPMSimulation[]>(`${API_BASE}/simulations`);
            set({ simulations });
          } catch (error) {
            set({ error: String(error) });
          }
        },

        deleteSimulation: async (id) => {
          try {
            await fetchApi(`${API_BASE}/simulations/${id}`, { method: 'DELETE' });
            const { simulation, simulations } = get();
            if (simulation?.id === id) {
              set({ simulation: null });
            }
            set({ simulations: simulations.filter((s) => s.id !== id) });
          } catch (error) {
            set({ error: String(error) });
          }
        },

        // ═══════════════════════════════════════════════════════════════════
        // VEHICLES
        // ═══════════════════════════════════════════════════════════════════

        addVehicle: async (request) => {
          const { simulation } = get();
          if (!simulation) {
            throw new Error('No simulation active');
          }

          try {
            set({ loading: true, error: null });
            await fetchApi(`${API_BASE}/simulations/${simulation.id}/vehicles`, {
              method: 'POST',
              body: JSON.stringify(request),
            });

            // Refresh simulation to get updated vehicles
            await get().fetchSimulation(simulation.id);
            set({ loading: false });
          } catch (error) {
            set({ error: String(error), loading: false });
            throw error;
          }
        },

        removeVehicle: async (evseId) => {
          const { simulation } = get();
          if (!simulation) return;

          try {
            await fetchApi(`${API_BASE}/simulations/${simulation.id}/vehicles/${evseId}`, {
              method: 'DELETE',
            });
            await get().fetchSimulation(simulation.id);
          } catch (error) {
            set({ error: String(error) });
          }
        },

        // ═══════════════════════════════════════════════════════════════════
        // CONTROL
        // ═══════════════════════════════════════════════════════════════════

        startSimulation: async () => {
          const { simulation } = get();
          if (!simulation) return;

          try {
            set({ loading: true, error: null });
            await fetchApi(`${API_BASE}/simulations/${simulation.id}/start`, {
              method: 'POST',
            });

            // Start polling for updates
            get().startPolling(2000);
            set({ loading: false });
          } catch (error) {
            set({ error: String(error), loading: false });
          }
        },

        stopSimulation: async () => {
          const { simulation } = get();
          if (!simulation) return;

          try {
            await fetchApi(`${API_BASE}/simulations/${simulation.id}/stop`, {
              method: 'POST',
            });
            get().stopPolling();
            await get().fetchSimulation(simulation.id);
          } catch (error) {
            set({ error: String(error) });
          }
        },

        // ═══════════════════════════════════════════════════════════════════
        // POLLING
        // ═══════════════════════════════════════════════════════════════════

        startPolling: (intervalMs = 2000) => {
          const { simulation, pollingInterval } = get();
          if (pollingInterval) {
            console.log('[GPM Polling] Already polling, skipping');
            return;
          }

          console.log('[GPM Polling] Starting polling every', intervalMs, 'ms');

          const interval = window.setInterval(async () => {
            const currentSim = get().simulation;
            if (!currentSim) {
              console.log('[GPM Polling] No simulation, stopping');
              get().stopPolling();
              return;
            }

            try {
              console.log('[GPM Polling] Fetching simulation', currentSim.id, 'status:', currentSim.status);
              await get().fetchSimulation(currentSim.id);

              // Stop polling if simulation is complete
              const updatedSim = get().simulation;
              console.log('[GPM Polling] Updated status:', updatedSim?.status);
              if (
                updatedSim &&
                ['COMPLETED', 'FAILED', 'CANCELLED'].includes(updatedSim.status)
              ) {
                console.log('[GPM Polling] Simulation finished, stopping polling');
                get().stopPolling();
              }
            } catch (error) {
              console.error('[GPM Polling] Error:', error);
            }
          }, intervalMs);

          set({ pollingInterval: interval });
        },

        stopPolling: () => {
          const { pollingInterval } = get();
          if (pollingInterval) {
            console.log('[GPM Polling] Stopping polling');
            clearInterval(pollingInterval);
            set({ pollingInterval: null });
          }
        },

        // ═══════════════════════════════════════════════════════════════════
        // FORMS
        // ═══════════════════════════════════════════════════════════════════

        setFormState: (state) => {
          set({ formState: { ...get().formState, ...state } });
        },

        resetFormState: () => {
          set({ formState: { ...DEFAULT_FORM_STATE } });
        },

        setVehicleForm: (state) => {
          set({ vehicleForm: { ...get().vehicleForm, ...state } });
        },

        resetVehicleForm: () => {
          set({ vehicleForm: { ...DEFAULT_VEHICLE_FORM } });
        },

        setSelectedChargeType: (type) => {
          set({ selectedChargeType: type });
        },

        // ═══════════════════════════════════════════════════════════════════
        // UTILS
        // ═══════════════════════════════════════════════════════════════════

        clearError: () => set({ error: null }),

        reset: () => {
          get().stopPolling();
          set(initialState);
        },
      }),
      {
        name: 'gpm-storage',
        partialize: (state) => ({
          formState: state.formState,
          selectedChargeType: state.selectedChargeType,
        }),
      }
    ),
    { name: 'GPMStore' }
  )
);

export default useGPMStore;
