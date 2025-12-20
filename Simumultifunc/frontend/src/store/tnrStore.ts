// frontend/src/store/tnrStore.ts
import { create } from 'zustand';
import { api } from '../services/api';
import { tnr, TnrTemplate, TemplateParams, TnrTolerances } from '@/lib/apiBase';
import type { TNRScenario } from '@/types';

export interface TnrExecution {
    id: string;
    scenarioId?: string;
    scenarioName?: string;
    status: string;
    startedAt?: string;
    completedAt?: string;
    durationMs?: number;
    eventCount?: number;
    passedSteps?: number;
    failedSteps?: number;
    totalSteps?: number;
}

export interface TNRStore {
    // State
    scenarios: TNRScenario[];
    templates: TnrTemplate[];
    executions: TnrExecution[];
    tolerancePresets: Record<string, TnrTolerances>;
    isRecording: boolean;
    isReplaying: boolean;
    recordingEvents: number;
    recordingSteps: number;
    currentRecordingId: string | null;
    activeTab: 'scenarios' | 'templates' | 'executions' | 'recordings';
    isLoading: boolean;
    error: string | null;

    // Actions - Scenarios
    loadScenarios: () => Promise<void>;
    startRecording: (name: string) => Promise<void>;
    stopRecording: (name: string, description: string) => Promise<void>;
    cancelRecording: () => Promise<void>;
    replayScenario: (scenarioId: string) => Promise<void>;
    deleteScenario: (scenarioId: string) => Promise<void>;

    // Actions - Templates
    loadTemplates: () => Promise<void>;
    instantiateTemplate: (templateId: string, params: TemplateParams) => Promise<TNRScenario | null>;
    runTemplate: (templateId: string, params: TemplateParams) => Promise<TnrExecution | null>;

    // Actions - Executions
    loadExecutions: () => Promise<void>;
    validateExecution: (executionId: string, scenarioId?: string) => Promise<any>;

    // Actions - Tolerances
    loadTolerancePresets: () => Promise<void>;

    // Actions - UI
    setActiveTab: (tab: 'scenarios' | 'templates' | 'executions' | 'recordings') => void;
    setError: (error: string | null) => void;
}

export const useTNRStore = create<TNRStore>((set, get) => ({
    // Initial state
    scenarios: [],
    templates: [],
    executions: [],
    tolerancePresets: {},
    isRecording: false,
    isReplaying: false,
    recordingEvents: 0,
    recordingSteps: 0,
    currentRecordingId: null,
    activeTab: 'scenarios',
    isLoading: false,
    error: null,

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIOS
    // ═══════════════════════════════════════════════════════════════════

    loadScenarios: async () => {
        set({ isLoading: true, error: null });
        try {
            const scenarios = await api.getTNRScenarios();
            set({ scenarios, isLoading: false });
        } catch (error) {
            console.error('Failed to load TNR scenarios:', error);
            set({ isLoading: false, error: 'Failed to load scenarios' });
        }
    },

    startRecording: async (name: string) => {
        set({ error: null });
        try {
            const result = await tnr.startRecording(name);
            set({
                isRecording: true,
                recordingEvents: 0,
                currentRecordingId: result?.executionId || null
            });
        } catch (error) {
            console.error('Failed to start recording:', error);
            set({ error: 'Failed to start recording' });
        }
    },

    stopRecording: async (name: string, description: string) => {
        set({ error: null });
        try {
            const execution = await tnr.stopRecording();
            set(state => ({
                isRecording: false,
                recordingEvents: 0,
                currentRecordingId: null,
                executions: execution ? [execution, ...state.executions] : state.executions
            }));
            // Reload scenarios
            await get().loadScenarios();
        } catch (error) {
            console.error('Failed to stop recording:', error);
            set({ error: 'Failed to stop recording' });
        }
    },

    cancelRecording: async () => {
        try {
            await tnr.cancelRecording();
            set({
                isRecording: false,
                recordingEvents: 0,
                currentRecordingId: null
            });
        } catch (error) {
            console.error('Failed to cancel recording:', error);
        }
    },

    replayScenario: async (scenarioId: string) => {
        set({ isReplaying: true, error: null });
        try {
            await tnr.runScenario(scenarioId);
            setTimeout(() => {
                set({ isReplaying: false });
                get().loadExecutions();
            }, 5000);
        } catch (error) {
            console.error('Failed to replay scenario:', error);
            set({ isReplaying: false, error: 'Failed to replay scenario' });
        }
    },

    deleteScenario: async (scenarioId: string) => {
        try {
            await tnr.remove(scenarioId);
            set(state => ({
                scenarios: state.scenarios.filter(s => s.id !== scenarioId)
            }));
        } catch (error) {
            console.error('Failed to delete scenario:', error);
            set({ error: 'Failed to delete scenario' });
        }
    },

    // ═══════════════════════════════════════════════════════════════════
    // TEMPLATES
    // ═══════════════════════════════════════════════════════════════════

    loadTemplates: async () => {
        set({ isLoading: true, error: null });
        try {
            const templates = await tnr.templates();
            set({ templates: templates || [], isLoading: false });
        } catch (error) {
            console.error('Failed to load templates:', error);
            set({ isLoading: false, error: 'Failed to load templates' });
        }
    },

    instantiateTemplate: async (templateId: string, params: TemplateParams) => {
        set({ isLoading: true, error: null });
        try {
            const scenario = await tnr.instantiateTemplate(templateId, params);
            if (scenario) {
                set(state => ({
                    scenarios: [scenario, ...state.scenarios],
                    isLoading: false
                }));
            }
            return scenario;
        } catch (error) {
            console.error('Failed to instantiate template:', error);
            set({ isLoading: false, error: 'Failed to instantiate template' });
            return null;
        }
    },

    runTemplate: async (templateId: string, params: TemplateParams) => {
        set({ isReplaying: true, error: null });
        try {
            const result = await tnr.runTemplate(templateId, params);
            set({ isReplaying: false });
            // Reload executions
            await get().loadExecutions();
            return result;
        } catch (error) {
            console.error('Failed to run template:', error);
            set({ isReplaying: false, error: 'Failed to run template' });
            return null;
        }
    },

    // ═══════════════════════════════════════════════════════════════════
    // EXECUTIONS
    // ═══════════════════════════════════════════════════════════════════

    loadExecutions: async () => {
        set({ isLoading: true, error: null });
        try {
            const executions = await tnr.recordedExecutions();
            set({ executions: executions || [], isLoading: false });
        } catch (error) {
            console.error('Failed to load executions:', error);
            set({ isLoading: false, error: 'Failed to load executions' });
        }
    },

    validateExecution: async (executionId: string, scenarioId?: string) => {
        try {
            return await tnr.validate(executionId, scenarioId);
        } catch (error) {
            console.error('Failed to validate execution:', error);
            set({ error: 'Failed to validate execution' });
            return null;
        }
    },

    // ═══════════════════════════════════════════════════════════════════
    // TOLERANCES
    // ═══════════════════════════════════════════════════════════════════

    loadTolerancePresets: async () => {
        try {
            const presets = await tnr.tolerancePresets();
            set({ tolerancePresets: presets || {} });
        } catch (error) {
            console.error('Failed to load tolerance presets:', error);
        }
    },

    // ═══════════════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════════════

    setActiveTab: (tab) => set({ activeTab: tab }),
    setError: (error) => set({ error }),
}));