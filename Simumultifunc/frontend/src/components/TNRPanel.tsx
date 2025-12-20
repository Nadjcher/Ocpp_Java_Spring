// frontend/src/components/TNRPanel.tsx
import React, { useState, useEffect } from 'react';
import { useTNRStore } from '@/store/tnrStore';
import { tnr, TnrTemplate, TemplateParams } from '@/lib/apiBase';

// Types pour les données
interface Scenario {
    id: string;
    name: string;
    description: string;
    tags?: string[];
    category?: string;
    folder?: string;
    sessions?: Array<{
        id: string;
        cpId: string;
        idTag?: string;
        title?: string;
        vehicleProfile?: string;
    }>;
    events?: Array<{
        t?: number;
        type?: string;
        action?: string;
        payload?: any;
        latency?: number;
    }>;
    steps?: Array<{
        order: number;
        type: string;
        text: string;
    }>;
    validationRules?: Array<{
        type: string;
        target: string;
        tolerance?: number;
    }>;
    metadata?: {
        runCount?: number;
        passCount?: number;
        failCount?: number;
        lastRunAt?: string;
        lastRunStatus?: string;
    };
    createdAt?: string;
}

export function TNRPanel() {
    const {
        scenarios,
        templates,
        executions,
        isRecording,
        isReplaying,
        isLoading,
        error,
        activeTab,
        recordingEvents,
        loadScenarios,
        loadTemplates,
        loadExecutions,
        startRecording,
        stopRecording,
        cancelRecording,
        replayScenario,
        deleteScenario,
        instantiateTemplate,
        runTemplate,
        setActiveTab,
        setError
    } = useTNRStore();

    const [selectedScenario, setSelectedScenario] = useState<Scenario | null>(null);
    const [selectedTemplate, setSelectedTemplate] = useState<TnrTemplate | null>(null);
    const [templateParams, setTemplateParams] = useState<TemplateParams>({});
    const [showTemplateModal, setShowTemplateModal] = useState(false);

    useEffect(() => {
        void loadScenarios();
        void loadTemplates();
        void loadExecutions();
    }, [loadScenarios, loadTemplates, loadExecutions]);

    const handleStartRecording = async () => {
        const name = prompt('Nom du scénario:');
        if (name) {
            await startRecording(name);
        }
    };

    const handleStopRecording = async () => {
        const description = prompt('Description du scénario:');
        await stopRecording('', description || '');
    };

    const handleReplayScenario = async (scenario: Scenario) => {
        await replayScenario(scenario.id);
    };

    const handleExportScenario = async (scenario: Scenario) => {
        try {
            const data = await tnr.get(scenario.id);
            const json = JSON.stringify(data, null, 2);
            const blob = new Blob([json], { type: 'application/json' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `tnr_scenario_${scenario.id}.json`;
            a.click();
            window.URL.revokeObjectURL(url);
        } catch (error) {
            console.error('Failed to export scenario:', error);
        }
    };

    const handleImportScenario = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;

        const formData = new FormData();
        formData.append('file', file);

        try {
            await tnr.importScenario(formData);
            await loadScenarios();
        } catch (error) {
            console.error('Failed to import scenario:', error);
        }
    };

    const handleDeleteScenario = async (scenarioId: string) => {
        if (confirm('Supprimer ce scénario ?')) {
            await deleteScenario(scenarioId);
            setSelectedScenario(null);
        }
    };

    const handleInstantiateTemplate = async () => {
        if (!selectedTemplate) return;

        const scenario = await instantiateTemplate(selectedTemplate.id, templateParams);
        if (scenario) {
            setShowTemplateModal(false);
            setSelectedTemplate(null);
            setTemplateParams({});
            setActiveTab('scenarios');
        }
    };

    const handleRunTemplate = async () => {
        if (!selectedTemplate) return;

        await runTemplate(selectedTemplate.id, templateParams);
        setShowTemplateModal(false);
        setSelectedTemplate(null);
        setTemplateParams({});
        setActiveTab('executions');
    };

    const openTemplateModal = (template: TnrTemplate) => {
        setSelectedTemplate(template);
        setTemplateParams({
            name: `${template.name} - ${new Date().toLocaleString()}`,
            cpId: 'CP001',
            connectorId: 1,
            vehicleId: 'TESLA_MODEL3_LR',
            idTag: 'DEFAULT_TAG',
            initialSoc: 20,
            targetSoc: 80
        });
        setShowTemplateModal(true);
    };

    return (
        <div className="p-6">
            {/* Header */}
            <div className="mb-6 flex justify-between items-center">
                <h2 className="text-2xl font-bold">Tests Non Régressifs (TNR)</h2>
                <div className="flex space-x-2">
                    {!isRecording ? (
                        <>
                            <button
                                onClick={() => void handleStartRecording()}
                                className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700"
                            >
                                [REC] Enregistrer
                            </button>
                            <label className="px-4 py-2 bg-gray-600 text-white rounded hover:bg-gray-700 cursor-pointer">
                                [FILE] Importer
                                <input
                                    type="file"
                                    accept=".json"
                                    onChange={(e) => void handleImportScenario(e)}
                                    className="hidden"
                                />
                            </label>
                        </>
                    ) : (
                        <>
                            <button
                                onClick={() => void handleStopRecording()}
                                className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 animate-pulse"
                            >
                                [STOP] Arrêter ({recordingEvents} événements)
                            </button>
                            <button
                                onClick={() => void cancelRecording()}
                                className="px-4 py-2 bg-gray-600 text-white rounded hover:bg-gray-700"
                            >
                                Annuler
                            </button>
                        </>
                    )}
                </div>
            </div>

            {/* Error display */}
            {error && (
                <div className="mb-4 p-3 bg-red-900/50 border border-red-500 rounded text-red-200">
                    {error}
                    <button
                        onClick={() => setError(null)}
                        className="ml-4 text-red-400 hover:text-red-200"
                    >
                        ✕
                    </button>
                </div>
            )}

            {/* Tabs */}
            <div className="mb-6 border-b border-gray-700">
                <div className="flex space-x-1">
                    {(['scenarios', 'templates', 'executions'] as const).map((tab) => (
                        <button
                            key={tab}
                            onClick={() => setActiveTab(tab)}
                            className={`px-4 py-2 rounded-t font-medium transition-colors ${
                                activeTab === tab
                                    ? 'bg-gray-700 text-white border-b-2 border-blue-500'
                                    : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
                            }`}
                        >
                            {tab === 'scenarios' && `📋 Scénarios (${scenarios.length})`}
                            {tab === 'templates' && `📦 Templates (${templates.length})`}
                            {tab === 'executions' && `📊 Exécutions (${executions.length})`}
                        </button>
                    ))}
                </div>
            </div>

            {/* Tab Content */}
            {activeTab === 'scenarios' && (
                <ScenariosTab
                    scenarios={scenarios as Scenario[]}
                    selectedScenario={selectedScenario}
                    isReplaying={isReplaying}
                    isLoading={isLoading}
                    onSelect={setSelectedScenario}
                    onReplay={handleReplayScenario}
                    onExport={handleExportScenario}
                    onDelete={handleDeleteScenario}
                    onRefresh={loadScenarios}
                />
            )}

            {activeTab === 'templates' && (
                <TemplatesTab
                    templates={templates}
                    isLoading={isLoading}
                    onInstantiate={openTemplateModal}
                    onRefresh={loadTemplates}
                />
            )}

            {activeTab === 'executions' && (
                <ExecutionsTab
                    executions={executions}
                    scenarios={scenarios as Scenario[]}
                    isLoading={isLoading}
                    onRefresh={loadExecutions}
                />
            )}

            {/* Template Modal */}
            {showTemplateModal && selectedTemplate && (
                <TemplateModal
                    template={selectedTemplate}
                    params={templateParams}
                    onParamsChange={setTemplateParams}
                    onInstantiate={handleInstantiateTemplate}
                    onRun={handleRunTemplate}
                    onClose={() => {
                        setShowTemplateModal(false);
                        setSelectedTemplate(null);
                    }}
                />
            )}
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// SCENARIOS TAB
// ═══════════════════════════════════════════════════════════════════

interface ScenariosTabProps {
    scenarios: Scenario[];
    selectedScenario: Scenario | null;
    isReplaying: boolean;
    isLoading: boolean;
    onSelect: (s: Scenario) => void;
    onReplay: (s: Scenario) => void;
    onExport: (s: Scenario) => void;
    onDelete: (id: string) => void;
    onRefresh: () => void;
}

function ScenariosTab({
    scenarios,
    selectedScenario,
    isReplaying,
    isLoading,
    onSelect,
    onReplay,
    onExport,
    onDelete,
    onRefresh
}: ScenariosTabProps) {
    return (
        <div className="grid grid-cols-2 gap-6">
            {/* Liste des scénarios */}
            <div className="bg-gray-800 rounded-lg p-6">
                <div className="flex justify-between items-center mb-4">
                    <h3 className="text-lg font-semibold">Scénarios enregistrés</h3>
                    <button
                        onClick={onRefresh}
                        disabled={isLoading}
                        className="px-3 py-1 bg-gray-600 rounded hover:bg-gray-700 disabled:opacity-50"
                    >
                        {isLoading ? '...' : '🔄'}
                    </button>
                </div>
                <div className="space-y-2 max-h-96 overflow-y-auto">
                    {scenarios.length === 0 ? (
                        <p className="text-gray-400">Aucun scénario enregistré</p>
                    ) : (
                        scenarios.map((scenario) => (
                            <div
                                key={scenario.id}
                                onClick={() => onSelect(scenario)}
                                className={`p-4 bg-gray-700 rounded cursor-pointer hover:bg-gray-600 ${
                                    selectedScenario?.id === scenario.id ? 'ring-2 ring-blue-500' : ''
                                }`}
                            >
                                <div className="flex justify-between items-start">
                                    <div className="flex-1">
                                        <h4 className="font-semibold">{scenario.name}</h4>
                                        <p className="text-sm text-gray-400">{scenario.description}</p>
                                        <div className="flex flex-wrap gap-1 mt-1">
                                            {scenario.category && (
                                                <span className="text-xs px-2 py-0.5 bg-blue-600 rounded">
                                                    {scenario.category}
                                                </span>
                                            )}
                                            {scenario.tags?.slice(0, 3).map(tag => (
                                                <span key={tag} className="text-xs px-2 py-0.5 bg-gray-600 rounded">
                                                    {tag}
                                                </span>
                                            ))}
                                        </div>
                                        <div className="text-xs text-gray-500 mt-1">
                                            {scenario.steps?.length || scenario.events?.length || 0} steps •
                                            {scenario.metadata?.runCount || 0} runs •
                                            {scenario.metadata?.lastRunStatus && (
                                                <span className={scenario.metadata.lastRunStatus === 'PASSED' ? 'text-green-400' : 'text-red-400'}>
                                                    {scenario.metadata.lastRunStatus}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                    <div className="flex space-x-1 ml-2">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); onReplay(scenario); }}
                                            disabled={isReplaying}
                                            className="p-1 bg-blue-600 rounded hover:bg-blue-700 disabled:opacity-50"
                                            title="Exécuter"
                                        >
                                            ▶️
                                        </button>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); onExport(scenario); }}
                                            className="p-1 bg-gray-600 rounded hover:bg-gray-700"
                                            title="Exporter"
                                        >
                                            💾
                                        </button>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); onDelete(scenario.id); }}
                                            className="p-1 bg-red-600 rounded hover:bg-red-700"
                                            title="Supprimer"
                                        >
                                            🗑️
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Détails du scénario */}
            <div className="bg-gray-800 rounded-lg p-6">
                <h3 className="text-lg font-semibold mb-4">Détails du scénario</h3>
                {selectedScenario ? (
                    <div className="space-y-4">
                        <div>
                            <h4 className="font-medium mb-2">Steps</h4>
                            <div className="max-h-48 overflow-y-auto space-y-1">
                                {(selectedScenario.steps || []).map((step, idx) => (
                                    <div key={idx} className="text-xs bg-gray-700 p-2 rounded">
                                        <span className="font-semibold text-blue-400">{step.type}</span>
                                        <span className="ml-2">{step.text}</span>
                                    </div>
                                ))}
                                {!selectedScenario.steps?.length && selectedScenario.events?.slice(0, 20).map((event, idx) => (
                                    <div key={idx} className="text-xs bg-gray-700 p-2 rounded">
                                        <span className={`font-semibold ${
                                            event.type === 'connect' ? 'text-blue-400' :
                                            event.type === 'disconnect' ? 'text-red-400' :
                                            event.action === 'StartTransaction' ? 'text-green-400' :
                                            event.action === 'StopTransaction' ? 'text-orange-400' :
                                            'text-gray-400'
                                        }`}>
                                            {event.action || event.type}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </div>

                        {selectedScenario.metadata && (
                            <div>
                                <h4 className="font-medium mb-2">Statistiques</h4>
                                <div className="grid grid-cols-3 gap-2">
                                    <div className="bg-gray-700 p-2 rounded text-center">
                                        <div className="text-lg font-bold">{selectedScenario.metadata.runCount || 0}</div>
                                        <div className="text-xs text-gray-400">Exécutions</div>
                                    </div>
                                    <div className="bg-gray-700 p-2 rounded text-center">
                                        <div className="text-lg font-bold text-green-400">{selectedScenario.metadata.passCount || 0}</div>
                                        <div className="text-xs text-gray-400">Succès</div>
                                    </div>
                                    <div className="bg-gray-700 p-2 rounded text-center">
                                        <div className="text-lg font-bold text-red-400">{selectedScenario.metadata.failCount || 0}</div>
                                        <div className="text-xs text-gray-400">Échecs</div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                ) : (
                    <p className="text-gray-400">Sélectionnez un scénario pour voir les détails</p>
                )}
            </div>
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// TEMPLATES TAB
// ═══════════════════════════════════════════════════════════════════

interface TemplatesTabProps {
    templates: TnrTemplate[];
    isLoading: boolean;
    onInstantiate: (t: TnrTemplate) => void;
    onRefresh: () => void;
}

function TemplatesTab({ templates, isLoading, onInstantiate, onRefresh }: TemplatesTabProps) {
    const categoryColors: Record<string, string> = {
        'smoke': 'bg-green-600',
        'smart-charging': 'bg-purple-600',
        'multi-session': 'bg-blue-600',
        'edge-case': 'bg-orange-600',
        'regression': 'bg-yellow-600',
    };

    return (
        <div className="bg-gray-800 rounded-lg p-6">
            <div className="flex justify-between items-center mb-4">
                <h3 className="text-lg font-semibold">Templates prédéfinis</h3>
                <button
                    onClick={onRefresh}
                    disabled={isLoading}
                    className="px-3 py-1 bg-gray-600 rounded hover:bg-gray-700 disabled:opacity-50"
                >
                    {isLoading ? '...' : '🔄'}
                </button>
            </div>

            <div className="grid grid-cols-2 gap-4">
                {templates.length === 0 ? (
                    <p className="text-gray-400 col-span-2">Aucun template disponible</p>
                ) : (
                    templates.map((template) => (
                        <div
                            key={template.id}
                            className="p-4 bg-gray-700 rounded hover:bg-gray-600 transition-colors"
                        >
                            <div className="flex justify-between items-start mb-2">
                                <div>
                                    <h4 className="font-semibold text-lg">{template.name}</h4>
                                    <span className={`text-xs px-2 py-0.5 rounded ${categoryColors[template.category] || 'bg-gray-600'}`}>
                                        {template.category}
                                    </span>
                                </div>
                                <span className="text-xs bg-blue-500 px-2 py-1 rounded">TEMPLATE</span>
                            </div>
                            <p className="text-sm text-gray-400 mb-3">{template.description}</p>
                            <div className="flex flex-wrap gap-1 mb-3">
                                {template.tags?.filter(t => t !== 'template').map(tag => (
                                    <span key={tag} className="text-xs px-2 py-0.5 bg-gray-600 rounded">
                                        {tag}
                                    </span>
                                ))}
                            </div>
                            <button
                                onClick={() => onInstantiate(template)}
                                className="w-full py-2 bg-blue-600 rounded hover:bg-blue-700 font-medium"
                            >
                                📋 Utiliser ce template
                            </button>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// EXECUTIONS TAB
// ═══════════════════════════════════════════════════════════════════

interface ExecutionsTabProps {
    executions: any[];
    scenarios: Scenario[];
    isLoading: boolean;
    onRefresh: () => void;
}

function ExecutionsTab({ executions, scenarios, isLoading, onRefresh }: ExecutionsTabProps) {
    const getScenarioName = (scenarioId: string) => {
        return scenarios.find(s => s.id === scenarioId)?.name || scenarioId || 'Scénario inconnu';
    };

    const statusColors: Record<string, string> = {
        'PASSED': 'bg-green-600',
        'FAILED': 'bg-red-600',
        'RUNNING': 'bg-blue-600 animate-pulse',
        'ERROR': 'bg-orange-600',
        'COMPLETED': 'bg-green-600',
    };

    return (
        <div className="bg-gray-800 rounded-lg p-6">
            <div className="flex justify-between items-center mb-4">
                <h3 className="text-lg font-semibold">Historique des exécutions</h3>
                <button
                    onClick={onRefresh}
                    disabled={isLoading}
                    className="px-3 py-1 bg-gray-600 rounded hover:bg-gray-700 disabled:opacity-50"
                >
                    {isLoading ? '...' : '🔄'}
                </button>
            </div>

            <div className="space-y-2 max-h-[500px] overflow-y-auto">
                {executions.length === 0 ? (
                    <p className="text-gray-400">Aucune exécution disponible</p>
                ) : (
                    executions.map((exec) => (
                        <div key={exec.id} className="bg-gray-700 rounded p-4">
                            <div className="flex justify-between items-start mb-2">
                                <div>
                                    <h4 className="font-semibold">
                                        {exec.scenarioName || getScenarioName(exec.scenarioId)}
                                    </h4>
                                    <p className="text-sm text-gray-400">
                                        {exec.startedAt ? new Date(exec.startedAt).toLocaleString() : 'Date inconnue'}
                                    </p>
                                </div>
                                <span className={`px-3 py-1 rounded text-sm font-medium ${statusColors[exec.status] || 'bg-gray-600'}`}>
                                    {exec.status === 'PASSED' && '✓ PASSÉ'}
                                    {exec.status === 'FAILED' && '✗ ÉCHOUÉ'}
                                    {exec.status === 'RUNNING' && '⏳ EN COURS'}
                                    {exec.status === 'COMPLETED' && '✓ TERMINÉ'}
                                    {exec.status === 'ERROR' && '⚠️ ERREUR'}
                                    {!['PASSED', 'FAILED', 'RUNNING', 'COMPLETED', 'ERROR'].includes(exec.status) && exec.status}
                                </span>
                            </div>

                            <div className="grid grid-cols-4 gap-2 text-xs">
                                <div className="bg-gray-800 p-2 rounded">
                                    <div className="text-gray-400">Événements</div>
                                    <div className="font-semibold">{exec.eventCount || 0}</div>
                                </div>
                                <div className="bg-gray-800 p-2 rounded">
                                    <div className="text-gray-400">Durée</div>
                                    <div className="font-semibold">
                                        {exec.durationMs ? `${(exec.durationMs / 1000).toFixed(1)}s` : '-'}
                                    </div>
                                </div>
                                <div className="bg-gray-800 p-2 rounded">
                                    <div className="text-gray-400">Passés</div>
                                    <div className="font-semibold text-green-400">{exec.passedSteps || 0}</div>
                                </div>
                                <div className="bg-gray-800 p-2 rounded">
                                    <div className="text-gray-400">Échoués</div>
                                    <div className="font-semibold text-red-400">{exec.failedSteps || 0}</div>
                                </div>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}

// ═══════════════════════════════════════════════════════════════════
// TEMPLATE MODAL
// ═══════════════════════════════════════════════════════════════════

interface TemplateModalProps {
    template: TnrTemplate;
    params: TemplateParams;
    onParamsChange: (p: TemplateParams) => void;
    onInstantiate: () => void;
    onRun: () => void;
    onClose: () => void;
}

function TemplateModal({ template, params, onParamsChange, onInstantiate, onRun, onClose }: TemplateModalProps) {
    // Local state for number inputs to allow free editing
    const [localConnectorId, setLocalConnectorId] = useState(String(params.connectorId ?? 1));
    const [localInitialSoc, setLocalInitialSoc] = useState(String(params.initialSoc ?? 20));
    const [localTargetSoc, setLocalTargetSoc] = useState(String(params.targetSoc ?? 80));

    const updateParam = (key: keyof TemplateParams, value: any) => {
        onParamsChange({ ...params, [key]: value });
    };

    // Handle number input changes - update local state immediately, sync on blur
    const handleNumberChange = (
        setter: React.Dispatch<React.SetStateAction<string>>,
        paramKey: keyof TemplateParams
    ) => (e: React.ChangeEvent<HTMLInputElement>) => {
        const val = e.target.value;
        setter(val);
        // Also update params if it's a valid number
        const num = parseInt(val, 10);
        if (!isNaN(num)) {
            updateParam(paramKey, num);
        }
    };

    const handleNumberBlur = (
        localValue: string,
        setter: React.Dispatch<React.SetStateAction<string>>,
        paramKey: keyof TemplateParams,
        defaultValue: number,
        min?: number,
        max?: number
    ) => () => {
        let num = parseInt(localValue, 10);
        if (isNaN(num)) {
            num = defaultValue;
        }
        if (min !== undefined && num < min) num = min;
        if (max !== undefined && num > max) num = max;
        setter(String(num));
        updateParam(paramKey, num);
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div className="bg-gray-800 rounded-lg p-6 w-full max-w-lg">
                <div className="flex justify-between items-center mb-4">
                    <h3 className="text-xl font-bold">Configurer: {template.name}</h3>
                    <button onClick={onClose} className="text-gray-400 hover:text-white text-xl">✕</button>
                </div>

                <p className="text-gray-400 mb-4">{template.description}</p>

                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium mb-1">Nom du scénario</label>
                        <input
                            type="text"
                            value={params.name || ''}
                            onChange={(e) => updateParam('name', e.target.value)}
                            className="w-full bg-gray-700 rounded px-3 py-2 text-white"
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium mb-1">CP ID</label>
                            <input
                                type="text"
                                value={params.cpId || ''}
                                onChange={(e) => updateParam('cpId', e.target.value)}
                                className="w-full bg-gray-700 rounded px-3 py-2 text-white"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium mb-1">Connector</label>
                            <input
                                type="text"
                                inputMode="numeric"
                                pattern="[0-9]*"
                                value={localConnectorId}
                                onChange={handleNumberChange(setLocalConnectorId, 'connectorId')}
                                onBlur={handleNumberBlur(localConnectorId, setLocalConnectorId, 'connectorId', 1, 1)}
                                className="w-full bg-gray-700 rounded px-3 py-2 text-white"
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm font-medium mb-1">Véhicule</label>
                        <select
                            value={params.vehicleId || ''}
                            onChange={(e) => updateParam('vehicleId', e.target.value)}
                            className="w-full bg-gray-700 rounded px-3 py-2 text-white"
                        >
                            <option value="TESLA_MODEL3_LR">Tesla Model 3 LR</option>
                            <option value="TESLA_MODEL_Y">Tesla Model Y</option>
                            <option value="RENAULT_ZOE">Renault Zoe</option>
                            <option value="PEUGEOT_E208">Peugeot e-208</option>
                            <option value="VW_ID3">VW ID.3</option>
                            <option value="VW_ID4">VW ID.4</option>
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium mb-1">ID Tag</label>
                        <input
                            type="text"
                            value={params.idTag || ''}
                            onChange={(e) => updateParam('idTag', e.target.value)}
                            className="w-full bg-gray-700 rounded px-3 py-2 text-white"
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium mb-1">SOC Initial (%)</label>
                            <input
                                type="text"
                                inputMode="numeric"
                                pattern="[0-9]*"
                                value={localInitialSoc}
                                onChange={handleNumberChange(setLocalInitialSoc, 'initialSoc')}
                                onBlur={handleNumberBlur(localInitialSoc, setLocalInitialSoc, 'initialSoc', 20, 0, 100)}
                                className="w-full bg-gray-700 rounded px-3 py-2 text-white"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium mb-1">SOC Cible (%)</label>
                            <input
                                type="text"
                                inputMode="numeric"
                                pattern="[0-9]*"
                                value={localTargetSoc}
                                onChange={handleNumberChange(setLocalTargetSoc, 'targetSoc')}
                                onBlur={handleNumberBlur(localTargetSoc, setLocalTargetSoc, 'targetSoc', 80, 0, 100)}
                                className="w-full bg-gray-700 rounded px-3 py-2 text-white"
                            />
                        </div>
                    </div>
                </div>

                <div className="flex space-x-3 mt-6">
                    <button
                        onClick={onInstantiate}
                        className="flex-1 py-2 bg-blue-600 rounded hover:bg-blue-700 font-medium"
                    >
                        📋 Créer scénario
                    </button>
                    <button
                        onClick={onRun}
                        className="flex-1 py-2 bg-green-600 rounded hover:bg-green-700 font-medium"
                    >
                        ▶️ Exécuter maintenant
                    </button>
                </div>
            </div>
        </div>
    );
}
