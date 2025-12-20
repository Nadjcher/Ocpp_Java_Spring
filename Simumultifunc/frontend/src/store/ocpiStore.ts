/**
 * OCPI Store - State Management avec Zustand
 * Gestion centralisée des partenaires, tests et résultats OCPI
 */

import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';
import {
  OCPIPartner,
  OCPIEnvironment,
  OCPIQuickTest,
  OCPITestScenario,
  OCPITestResult,
  OCPITab,
  OCPIFilters,
  OCPIModule,
  OCPIRole,
  TestStatus,
  ScenarioCategory,
  createEmptyPartner,
  GlobalConfig,
  GlobalEnvironment,
  GlobalEnvId,
  DEFAULT_GLOBAL_CONFIG,
} from '@/components/ocpi/types';
import {
  partnersApi,
  scenariosApi,
  testsApi,
  resultsApi,
  quickTestsApi,
  dashboardApi,
  Partner as ApiPartner,
  TestScenario as ApiTestScenario,
  TestResult as ApiTestResult,
} from '@/services/ocpiApi';

// ─────────────────────────────────────────────────────────────────────────────
// Types Store
// ─────────────────────────────────────────────────────────────────────────────

interface OCPIState {
  // UI State
  activeTab: OCPITab;
  filters: OCPIFilters;

  // Global Environment Config
  globalConfig: GlobalConfig;
  showEnvConfigModal: boolean;

  // Data
  partners: OCPIPartner[];
  quickTests: OCPIQuickTest[];
  scenarios: OCPITestScenario[];
  results: OCPITestResult[];

  // Selection
  selectedPartnerId: string | null;
  selectedEnvironmentId: string | null;
  selectedQuickTestId: string | null;
  selectedScenarioId: string | null;
  selectedResultId: string | null;

  // Modal State
  modalOpen: 'partner' | 'quicktest' | 'scenario' | 'result' | null;
  editingPartner: OCPIPartner | null;
  editingQuickTest: OCPIQuickTest | null;
  editingScenario: OCPITestScenario | null;

  // Loading & Error
  loading: boolean;
  loadingPartners: boolean;
  loadingQuickTests: boolean;
  loadingScenarios: boolean;
  loadingResults: boolean;
  error: string | null;

  // Running Tests
  runningTests: Set<string>;
}

interface OCPIActions {
  // UI Actions
  setActiveTab: (tab: OCPITab) => void;
  setFilters: (filters: Partial<OCPIFilters>) => void;
  resetFilters: () => void;

  // Global Environment Actions
  setGlobalEnvironment: (envId: GlobalEnvId) => void;
  updateGlobalConfig: (config: GlobalConfig) => void;
  updateGlobalEnvironment: (env: GlobalEnvironment) => void;
  setShowEnvConfigModal: (show: boolean) => void;
  getActiveGlobalEnv: () => GlobalEnvironment | undefined;

  // Selection
  selectPartner: (id: string | null) => void;
  selectEnvironment: (id: string | null) => void;
  selectQuickTest: (id: string | null) => void;
  selectScenario: (id: string | null) => void;
  selectResult: (id: string | null) => void;

  // Modals
  openModal: (modal: OCPIState['modalOpen'], data?: any) => void;
  closeModal: () => void;

  // Partners CRUD
  fetchPartners: () => Promise<void>;
  createPartner: (partner: Partial<OCPIPartner>) => Promise<OCPIPartner>;
  updatePartner: (id: string, updates: Partial<OCPIPartner>) => Promise<OCPIPartner>;
  deletePartner: (id: string) => Promise<void>;
  testPartnerConnection: (partnerId: string, environmentId?: string) => Promise<{ success: boolean; error?: string }>;
  discoverPartnerEndpoints: (partnerId: string) => Promise<{ success: boolean; endpoints?: Record<string, string>; error?: string }>;
  switchPartnerEnvironment: (partnerId: string, environmentId: string) => Promise<void>;

  // Scenarios CRUD
  fetchScenarios: () => Promise<void>;
  createScenario: (scenario: Partial<OCPITestScenario>) => Promise<OCPITestScenario>;
  updateScenario: (id: string, updates: Partial<OCPITestScenario>) => Promise<OCPITestScenario>;
  deleteScenario: (id: string) => Promise<void>;

  // Quick Tests
  fetchQuickTests: () => Promise<void>;
  createQuickTest: (test: Partial<OCPIQuickTest>) => Promise<OCPIQuickTest>;
  updateQuickTest: (id: string, updates: Partial<OCPIQuickTest>) => Promise<OCPIQuickTest>;
  deleteQuickTest: (id: string) => Promise<void>;
  executeQuickTest: (testId: string, partnerId: string, params?: Record<string, any>) => Promise<OCPITestResult>;

  // Scenarios Execution
  executeScenario: (scenarioId: string, partnerId: string) => Promise<OCPITestResult>;
  executeAllScenarios: (partnerId: string) => Promise<OCPITestResult[]>;

  // Results
  fetchResults: (limit?: number) => Promise<void>;
  fetchResultsForPartner: (partnerId: string) => Promise<void>;
  deleteResult: (id: string) => Promise<void>;
  cleanupResults: (keepCount?: number) => Promise<void>;

  // Helpers
  getSelectedPartner: () => OCPIPartner | null;
  getSelectedEnvironment: () => OCPIEnvironment | null;
  getFilteredPartners: () => OCPIPartner[];
  getFilteredScenarios: () => OCPITestScenario[];
  getFilteredResults: () => OCPITestResult[];
}

type OCPIStore = OCPIState & OCPIActions;

// ─────────────────────────────────────────────────────────────────────────────
// Initial State
// ─────────────────────────────────────────────────────────────────────────────

const initialFilters: OCPIFilters = {
  partners: {
    search: '',
    roles: [],
    versions: [],
  },
  quickTests: {
    modules: [],
    categories: [],
  },
  scenarios: {
    search: '',
    tags: [],
    categories: [],
    priorities: [],
  },
  history: {},
};

const initialState: OCPIState = {
  activeTab: 'partners',
  filters: initialFilters,
  globalConfig: DEFAULT_GLOBAL_CONFIG,
  showEnvConfigModal: false,
  partners: [],
  quickTests: [],
  scenarios: [],
  results: [],
  selectedPartnerId: null,
  selectedEnvironmentId: null,
  selectedQuickTestId: null,
  selectedScenarioId: null,
  selectedResultId: null,
  modalOpen: null,
  editingPartner: null,
  editingQuickTest: null,
  editingScenario: null,
  loading: false,
  loadingPartners: false,
  loadingQuickTests: false,
  loadingScenarios: false,
  loadingResults: false,
  error: null,
  runningTests: new Set(),
};

// ─────────────────────────────────────────────────────────────────────────────
// Helpers de conversion API -> Types locaux
// ─────────────────────────────────────────────────────────────────────────────

function convertApiPartner(api: ApiPartner): OCPIPartner {
  const environments: OCPIEnvironment[] = Object.entries(api.environments || {}).map(([id, env]) => ({
    id,
    name: id.charAt(0).toUpperCase() + id.slice(1),
    baseUrl: env.baseUrl,
    versionsUrl: env.versionsUrl,
    authType: env.cognito ? 'cognito' : 'token',
    token: env.tokenA || env.tokenB || env.tokenC,
    tokenA: env.tokenA,
    tokenB: env.tokenB,
    tokenC: env.tokenC,
    cognito: env.cognito ? {
      tokenUrl: env.cognito.tokenUrl,
      clientId: env.cognito.clientId,
      clientSecret: env.cognito.clientSecret,
    } : undefined,
    isActive: api.activeEnvironment === id,
    lastTestResult: api.lastSync ? 'success' : 'unknown',
  }));

  return {
    id: api.id,
    name: api.name,
    code: api.partyId,
    countryCode: api.countryCode,
    partyId: api.partyId,
    role: api.role as OCPIRole,
    ocpiVersion: api.version === 'V2_2_1' ? '2.2.1' : '2.1.1',
    environments,
    activeEnvironmentId: api.activeEnvironment || environments[0]?.id || 'test',
    modules: api.modules as OCPIModule[],
    endpoints: api.endpoints,
    logo: api.businessDetails?.logo,
    notes: '',
    createdAt: api.lastSync || new Date().toISOString(),
    updatedAt: api.lastSync || new Date().toISOString(),
  };
}

function convertApiScenario(api: ApiTestScenario): OCPITestScenario {
  return {
    id: api.id,
    name: api.name,
    description: api.description || '',
    tags: api.tags || [],
    category: (api.category as ScenarioCategory) || 'custom',
    priority: 'medium',
    steps: api.steps.map((step, index) => ({
      id: step.id,
      order: index + 1,
      name: step.name,
      description: step.description,
      module: (step.module || 'credentials') as OCPIModule,
      action: step.type,
      method: (step.method as any) || 'GET',
      endpoint: step.endpoint || '',
      config: {},
      validations: step.assertions?.map(a => ({
        field: a.path || '',
        operator: 'equals' as const,
        expected: a.expected,
        message: a.name,
      })) || [],
      optional: !step.assertions?.some(a => a.critical),
      timeoutMs: step.timeoutMs,
    })),
    continueOnError: !api.stopOnFailure,
    timeout: 60,
    retryCount: 0,
    retryDelay: 1000,
    isBuiltIn: true,
    enabled: true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
}

function convertApiResult(api: ApiTestResult): OCPITestResult {
  return {
    id: api.id,
    type: 'scenario',
    testId: api.scenarioId,
    testName: api.scenarioName,
    partnerId: api.partnerId,
    partnerName: api.partnerName,
    environmentId: api.environment,
    environmentName: api.environment,
    startedAt: api.startTime,
    completedAt: api.endTime || api.startTime,
    durationMs: api.durationMs,
    status: api.status.toLowerCase() as TestStatus,
    stepResults: api.stepResults?.map(sr => ({
      stepId: sr.stepId,
      stepName: sr.stepName,
      stepOrder: 0,
      status: sr.status.toLowerCase() as TestStatus,
      durationMs: sr.durationMs,
      request: sr.request ? {
        method: sr.request.method,
        url: sr.request.url,
        headers: sr.request.headers,
        body: sr.request.body ? JSON.parse(sr.request.body) : undefined,
      } : undefined,
      response: sr.response ? {
        status: sr.response.httpStatus,
        statusText: '',
        headers: sr.response.headers,
        body: sr.response.body ? JSON.parse(sr.response.body) : undefined,
        durationMs: sr.response.latencyMs,
      } : undefined,
      validationResults: sr.assertionResults?.map(ar => ({
        validation: { field: '', operator: 'equals' as const, expected: ar.expected || null, message: ar.name },
        passed: ar.passed,
        actualValue: ar.actual,
        message: ar.message,
      })),
      errorMessage: sr.errorMessage,
    })),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Store
// ─────────────────────────────────────────────────────────────────────────────

export const useOCPIStore = create<OCPIStore>()(
  devtools(
    persist(
      (set, get) => ({
        ...initialState,

        // ═══════════════════════════════════════════════════════════════════
        // UI Actions
        // ═══════════════════════════════════════════════════════════════════

        setActiveTab: (tab) => set({ activeTab: tab }),

        setFilters: (newFilters) => set((state) => ({
          filters: {
            ...state.filters,
            ...newFilters,
            partners: { ...state.filters.partners, ...newFilters.partners },
            quickTests: { ...state.filters.quickTests, ...newFilters.quickTests },
            scenarios: { ...state.filters.scenarios, ...newFilters.scenarios },
            history: { ...state.filters.history, ...newFilters.history },
          },
        })),

        resetFilters: () => set({ filters: initialFilters }),

        // ═══════════════════════════════════════════════════════════════════
        // Global Environment
        // ═══════════════════════════════════════════════════════════════════

        setGlobalEnvironment: (envId) => set((state) => ({
          globalConfig: {
            ...state.globalConfig,
            activeEnvironmentId: envId,
            environments: state.globalConfig.environments.map(e => ({
              ...e,
              isActive: e.id === envId,
            })),
          },
        })),

        updateGlobalConfig: (config) => set({ globalConfig: config }),

        updateGlobalEnvironment: (env) => set((state) => ({
          globalConfig: {
            ...state.globalConfig,
            environments: state.globalConfig.environments.map(e =>
              e.id === env.id ? env : e
            ),
          },
        })),

        setShowEnvConfigModal: (show) => set({ showEnvConfigModal: show }),

        getActiveGlobalEnv: () => {
          const state = get();
          return state.globalConfig.environments.find(
            e => e.id === state.globalConfig.activeEnvironmentId
          );
        },

        // ═══════════════════════════════════════════════════════════════════
        // Selection
        // ═══════════════════════════════════════════════════════════════════

        selectPartner: (id) => {
          const partner = id ? get().partners.find(p => p.id === id) : null;
          set({
            selectedPartnerId: id,
            selectedEnvironmentId: partner?.activeEnvironmentId || null,
          });
        },

        selectEnvironment: (id) => set({ selectedEnvironmentId: id }),
        selectQuickTest: (id) => set({ selectedQuickTestId: id }),
        selectScenario: (id) => set({ selectedScenarioId: id }),
        selectResult: (id) => set({ selectedResultId: id }),

        // ═══════════════════════════════════════════════════════════════════
        // Modals
        // ═══════════════════════════════════════════════════════════════════

        openModal: (modal, data) => {
          const updates: Partial<OCPIState> = { modalOpen: modal };
          if (modal === 'partner') {
            updates.editingPartner = data || null;
          } else if (modal === 'quicktest') {
            updates.editingQuickTest = data || null;
          } else if (modal === 'scenario') {
            updates.editingScenario = data || null;
          }
          set(updates);
        },

        closeModal: () => set({
          modalOpen: null,
          editingPartner: null,
          editingQuickTest: null,
          editingScenario: null,
        }),

        // ═══════════════════════════════════════════════════════════════════
        // Partners CRUD
        // ═══════════════════════════════════════════════════════════════════

        fetchPartners: async () => {
          set({ loadingPartners: true, error: null });
          try {
            const apiPartners = await partnersApi.getAll();
            const partners = apiPartners.map(convertApiPartner);
            set({ partners, loadingPartners: false });
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to fetch partners';
            set({ error: message, loadingPartners: false });
            console.error('[OCPIStore] fetchPartners error:', error);
          }
        },

        createPartner: async (partnerData) => {
          set({ loading: true, error: null });
          try {
            // Convertir vers format API
            const apiPartner = await partnersApi.create(partnerData as any);
            const partner = convertApiPartner(apiPartner);
            set((state) => ({
              partners: [...state.partners, partner],
              loading: false,
            }));
            return partner;
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to create partner';
            set({ error: message, loading: false });
            throw error;
          }
        },

        updatePartner: async (id, updates) => {
          set({ loading: true, error: null });
          try {
            const apiPartner = await partnersApi.update(id, updates as any);
            const partner = convertApiPartner(apiPartner);
            set((state) => ({
              partners: state.partners.map(p => p.id === id ? partner : p),
              loading: false,
            }));
            return partner;
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to update partner';
            set({ error: message, loading: false });
            throw error;
          }
        },

        deletePartner: async (id) => {
          set({ loading: true, error: null });
          try {
            await partnersApi.delete(id);
            set((state) => ({
              partners: state.partners.filter(p => p.id !== id),
              selectedPartnerId: state.selectedPartnerId === id ? null : state.selectedPartnerId,
              loading: false,
            }));
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to delete partner';
            set({ error: message, loading: false });
            throw error;
          }
        },

        testPartnerConnection: async (partnerId, environmentId) => {
          try {
            const result = await partnersApi.discover(partnerId);
            // Mettre à jour le statut du partenaire
            set((state) => ({
              partners: state.partners.map(p => {
                if (p.id !== partnerId) return p;
                return {
                  ...p,
                  environments: p.environments.map(e => {
                    if (environmentId && e.id !== environmentId) return e;
                    if (!environmentId && e.id !== p.activeEnvironmentId) return e;
                    return {
                      ...e,
                      lastTestedAt: new Date().toISOString(),
                      lastTestResult: result.ok ? 'success' : 'failed',
                      lastTestError: result.error,
                    };
                  }),
                };
              }),
            }));
            return { success: result.ok, error: result.error };
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Connection test failed';
            return { success: false, error: message };
          }
        },

        discoverPartnerEndpoints: async (partnerId) => {
          try {
            const result = await partnersApi.discoverFull(partnerId);
            if (result.ok && result.endpoints) {
              set((state) => ({
                partners: state.partners.map(p =>
                  p.id === partnerId ? { ...p, endpoints: result.endpoints } : p
                ),
              }));
            }
            return {
              success: result.ok,
              endpoints: result.endpoints,
              error: result.error,
            };
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Discovery failed';
            return { success: false, error: message };
          }
        },

        switchPartnerEnvironment: async (partnerId, environmentId) => {
          try {
            await partnersApi.switchEnvironment(partnerId, environmentId);
            set((state) => ({
              partners: state.partners.map(p => {
                if (p.id !== partnerId) return p;
                return {
                  ...p,
                  activeEnvironmentId: environmentId,
                  environments: p.environments.map(e => ({
                    ...e,
                    isActive: e.id === environmentId,
                  })),
                };
              }),
              selectedEnvironmentId: environmentId,
            }));
          } catch (error) {
            console.error('[OCPIStore] switchEnvironment error:', error);
            throw error;
          }
        },

        // ═══════════════════════════════════════════════════════════════════
        // Scenarios CRUD
        // ═══════════════════════════════════════════════════════════════════

        fetchScenarios: async () => {
          set({ loadingScenarios: true, error: null });
          try {
            const apiScenarios = await scenariosApi.getAll();
            const scenarios = apiScenarios.map(convertApiScenario);
            set({ scenarios, loadingScenarios: false });
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to fetch scenarios';
            set({ error: message, loadingScenarios: false });
            console.error('[OCPIStore] fetchScenarios error:', error);
          }
        },

        createScenario: async (scenarioData) => {
          set({ loading: true, error: null });
          try {
            const apiScenario = await scenariosApi.create(scenarioData as any);
            const scenario = convertApiScenario(apiScenario);
            set((state) => ({
              scenarios: [...state.scenarios, scenario],
              loading: false,
            }));
            return scenario;
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to create scenario';
            set({ error: message, loading: false });
            throw error;
          }
        },

        updateScenario: async (id, updates) => {
          set({ loading: true, error: null });
          try {
            const apiScenario = await scenariosApi.update(id, updates as any);
            const scenario = convertApiScenario(apiScenario);
            set((state) => ({
              scenarios: state.scenarios.map(s => s.id === id ? scenario : s),
              loading: false,
            }));
            return scenario;
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to update scenario';
            set({ error: message, loading: false });
            throw error;
          }
        },

        deleteScenario: async (id) => {
          set({ loading: true, error: null });
          try {
            await scenariosApi.delete(id);
            set((state) => ({
              scenarios: state.scenarios.filter(s => s.id !== id),
              selectedScenarioId: state.selectedScenarioId === id ? null : state.selectedScenarioId,
              loading: false,
            }));
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to delete scenario';
            set({ error: message, loading: false });
            throw error;
          }
        },

        // ═══════════════════════════════════════════════════════════════════
        // Quick Tests
        // ═══════════════════════════════════════════════════════════════════

        fetchQuickTests: async () => {
          // Pour l'instant, les quick tests sont définis localement
          // TODO: Charger depuis le backend si persistés
          set({ quickTests: [] });
        },

        createQuickTest: async (test) => {
          const id = `qt-${Date.now()}`;
          const quickTest: OCPIQuickTest = {
            id,
            name: test.name || '',
            description: test.description || '',
            module: test.module || 'locations',
            method: test.method || 'GET',
            path: test.path || test.endpoint || '',
            endpoint: test.endpoint || '',
            params: test.params || [],
            pathParams: test.pathParams || [],
            queryParams: test.queryParams || [],
            expectedStatus: test.expectedStatus || 200,
            validations: test.validations || [],
            category: test.category || 'crud',
            isBuiltIn: false,
            enabled: true,
          };
          set((state) => ({
            quickTests: [...state.quickTests, quickTest],
          }));
          return quickTest;
        },

        updateQuickTest: async (id, updates) => {
          set((state) => ({
            quickTests: state.quickTests.map(t =>
              t.id === id ? { ...t, ...updates } : t
            ),
          }));
          return get().quickTests.find(t => t.id === id)!;
        },

        deleteQuickTest: async (id) => {
          set((state) => ({
            quickTests: state.quickTests.filter(t => t.id !== id),
          }));
        },

        executeQuickTest: async (testId, partnerId, params) => {
          const test = get().quickTests.find(t => t.id === testId);
          if (!test) throw new Error('Quick test not found');

          // Utiliser l'API quick test existante selon le module
          const module = test.module;
          let apiCall: Promise<any>;

          switch (module) {
            case 'locations':
              apiCall = quickTestsApi.locations(partnerId);
              break;
            case 'sessions':
              apiCall = quickTestsApi.sessions(partnerId);
              break;
            case 'cdrs':
              apiCall = quickTestsApi.cdrs(partnerId);
              break;
            case 'tokens':
              apiCall = quickTestsApi.tokens(partnerId);
              break;
            case 'tariffs':
              apiCall = quickTestsApi.tariffs(partnerId);
              break;
            default:
              throw new Error(`Module ${module} not supported for quick tests`);
          }

          const startTime = Date.now();
          const response = await apiCall;
          const endTime = Date.now();

          const partner = get().partners.find(p => p.id === partnerId);

          const result: OCPITestResult = {
            id: `result-${Date.now()}`,
            type: 'quick',
            testId,
            testName: test.name,
            partnerId,
            partnerName: partner?.name || partnerId,
            environmentId: partner?.activeEnvironmentId || '',
            environmentName: partner?.activeEnvironmentId || '',
            startedAt: new Date(startTime).toISOString(),
            completedAt: new Date(endTime).toISOString(),
            durationMs: response.latencyMs || (endTime - startTime),
            status: response.ok ? 'passed' : 'failed',
            errorMessage: response.error,
            response: {
              status: response.statusCode || (response.ok ? 200 : 500),
              statusText: response.ok ? 'OK' : 'Error',
              headers: {},
              body: response.data,
              durationMs: response.latencyMs || (endTime - startTime),
            },
          };

          set((state) => ({
            results: [result, ...state.results].slice(0, 500),
          }));

          return result;
        },

        // ═══════════════════════════════════════════════════════════════════
        // Scenario Execution
        // ═══════════════════════════════════════════════════════════════════

        executeScenario: async (scenarioId, partnerId) => {
          set((state) => ({
            runningTests: new Set([...state.runningTests, scenarioId]),
          }));

          try {
            const apiResult = await testsApi.run(scenarioId, partnerId);
            const result = convertApiResult(apiResult);

            set((state) => {
              const newRunning = new Set(state.runningTests);
              newRunning.delete(scenarioId);
              return {
                results: [result, ...state.results].slice(0, 500),
                runningTests: newRunning,
              };
            });

            return result;
          } catch (error) {
            set((state) => {
              const newRunning = new Set(state.runningTests);
              newRunning.delete(scenarioId);
              return { runningTests: newRunning };
            });
            throw error;
          }
        },

        executeAllScenarios: async (partnerId) => {
          const apiResponse = await testsApi.runAll(partnerId);
          const results = apiResponse.results.map(convertApiResult);

          set((state) => ({
            results: [...results, ...state.results].slice(0, 500),
          }));

          return results;
        },

        // ═══════════════════════════════════════════════════════════════════
        // Results
        // ═══════════════════════════════════════════════════════════════════

        fetchResults: async (limit = 50) => {
          set({ loadingResults: true, error: null });
          try {
            const apiResults = await resultsApi.getRecent(limit);
            const results = apiResults.map(convertApiResult);
            set({ results, loadingResults: false });
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to fetch results';
            set({ error: message, loadingResults: false });
            console.error('[OCPIStore] fetchResults error:', error);
          }
        },

        fetchResultsForPartner: async (partnerId) => {
          set({ loadingResults: true, error: null });
          try {
            const apiResults = await resultsApi.getForPartner(partnerId);
            const results = apiResults.map(convertApiResult);
            set((state) => ({
              results: [
                ...results,
                ...state.results.filter(r => r.partnerId !== partnerId),
              ].slice(0, 500),
              loadingResults: false,
            }));
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Failed to fetch results';
            set({ error: message, loadingResults: false });
          }
        },

        deleteResult: async (id) => {
          try {
            await resultsApi.delete(id);
            set((state) => ({
              results: state.results.filter(r => r.id !== id),
              selectedResultId: state.selectedResultId === id ? null : state.selectedResultId,
            }));
          } catch (error) {
            console.error('[OCPIStore] deleteResult error:', error);
            throw error;
          }
        },

        cleanupResults: async (keepCount = 50) => {
          try {
            await resultsApi.cleanup(keepCount);
            await get().fetchResults(keepCount);
          } catch (error) {
            console.error('[OCPIStore] cleanupResults error:', error);
            throw error;
          }
        },

        // ═══════════════════════════════════════════════════════════════════
        // Helpers / Selectors
        // ═══════════════════════════════════════════════════════════════════

        getSelectedPartner: () => {
          const { partners, selectedPartnerId } = get();
          return partners.find(p => p.id === selectedPartnerId) || null;
        },

        getSelectedEnvironment: () => {
          const partner = get().getSelectedPartner();
          const { selectedEnvironmentId } = get();
          if (!partner) return null;
          return partner.environments.find(e => e.id === selectedEnvironmentId) || null;
        },

        getFilteredPartners: () => {
          const { partners, filters } = get();
          const { search, roles, versions } = filters.partners;

          return partners.filter(p => {
            if (search) {
              const s = search.toLowerCase();
              if (!p.name.toLowerCase().includes(s) &&
                  !p.code.toLowerCase().includes(s) &&
                  !p.countryCode.toLowerCase().includes(s)) {
                return false;
              }
            }
            if (roles.length > 0 && !roles.includes(p.role)) {
              return false;
            }
            if (versions.length > 0 && !versions.includes(p.ocpiVersion)) {
              return false;
            }
            return true;
          });
        },

        getFilteredScenarios: () => {
          const { scenarios, filters } = get();
          const { search, tags, categories, priorities } = filters.scenarios;

          return scenarios.filter(s => {
            if (search) {
              const searchLower = search.toLowerCase();
              if (!s.name.toLowerCase().includes(searchLower) &&
                  !s.description.toLowerCase().includes(searchLower)) {
                return false;
              }
            }
            if (tags.length > 0 && !tags.some(t => s.tags.includes(t))) {
              return false;
            }
            if (categories.length > 0 && !categories.includes(s.category)) {
              return false;
            }
            if (priorities.length > 0 && !priorities.includes(s.priority)) {
              return false;
            }
            return true;
          });
        },

        getFilteredResults: () => {
          const { results, filters } = get();
          const { partnerId, status, type, dateFrom, dateTo } = filters.history;

          return results.filter(r => {
            if (partnerId && r.partnerId !== partnerId) return false;
            if (status && r.status !== status) return false;
            if (type && r.type !== type) return false;
            if (dateFrom && new Date(r.startedAt) < new Date(dateFrom)) return false;
            if (dateTo && new Date(r.startedAt) > new Date(dateTo)) return false;
            return true;
          });
        },
      }),
      {
        name: 'ocpi-store',
        partialize: (state) => ({
          activeTab: state.activeTab,
          selectedPartnerId: state.selectedPartnerId,
          filters: state.filters,
          globalConfig: state.globalConfig,
        }),
      }
    ),
    { name: 'OCPIStore' }
  )
);

// ─────────────────────────────────────────────────────────────────────────────
// Selectors (pour optimisation avec useShallow)
// ─────────────────────────────────────────────────────────────────────────────

export const selectPartners = (state: OCPIStore) => state.partners;
export const selectScenarios = (state: OCPIStore) => state.scenarios;
export const selectResults = (state: OCPIStore) => state.results;
export const selectActiveTab = (state: OCPIStore) => state.activeTab;
export const selectFilters = (state: OCPIStore) => state.filters;
export const selectGlobalConfig = (state: OCPIStore) => state.globalConfig;
export const selectActiveGlobalEnv = (state: OCPIStore) =>
  state.globalConfig.environments.find(e => e.id === state.globalConfig.activeEnvironmentId);
export const selectSelectedPartner = (state: OCPIStore) =>
  state.partners.find(p => p.id === state.selectedPartnerId) || null;
export const selectLoading = (state: OCPIStore) =>
  state.loading || state.loadingPartners || state.loadingScenarios || state.loadingResults;
