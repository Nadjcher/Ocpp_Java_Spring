/**
 * OCPITab.tsx - Onglet de tests OCPI multi-partenaires
 *
 * Fonctionnalites:
 * - Dashboard avec KPIs globaux
 * - Gestion des partenaires (ShellRecharge, Dnetz, etc.)
 * - Execution de scenarios de tests OCPI 2.2.1
 * - Visualisation des resultats detailles
 * - Tests rapides par module
 *
 * Conforme aux specifications OCPI 2.2.1 (EVRoaming Foundation)
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
    partnersApi,
    scenariosApi,
    testsApi,
    resultsApi,
    quickTestsApi,
    dashboardApi,
    requestBuilderApi,
    badgeTestApi,
    type Partner,
    type TestScenario,
    type TestResult,
    type DashboardData,
    type TestStatus,
    type OCPIModule,
    type HttpMethod,
    type RequestConfig,
    type RequestTemplate,
    type RawRequestResponse,
    type BadgeTestStatus,
    getOcpiStatusMessage,
    isOcpiSuccess,
} from '@/services/ocpiApi';
import { PartnersList, QuickTestsPanel, ScenariosPanel, HistoryPanel, ConnectionPanel, EnvironmentConfigModal } from '@/components/ocpi';
import { useOCPIStore, selectGlobalConfig, selectActiveGlobalEnv } from '@/store/ocpiStore';
import type { GlobalEnvId } from '@/components/ocpi/types';

// ============================================================================
// Styles
// ============================================================================

const styles = {
    container: { padding: 16, fontFamily: 'system-ui, -apple-system, sans-serif' },
    header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
    title: { margin: 0, fontSize: 24, fontWeight: 600 },
    subtitle: { color: '#6b7280', fontSize: 14 },

    tabs: { display: 'flex', gap: 4, marginBottom: 16, borderBottom: '1px solid #e5e7eb', paddingBottom: 8 },
    tab: (active: boolean) => ({
        padding: '8px 16px',
        border: 'none',
        background: active ? '#2563eb' : 'transparent',
        color: active ? '#fff' : '#374151',
        borderRadius: 6,
        cursor: 'pointer',
        fontWeight: active ? 600 : 400,
        fontSize: 14,
    }),

    grid: { display: 'grid', gap: 16 },
    grid2: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 },
    grid3: { display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 },
    grid4: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 },

    card: {
        background: '#fff',
        border: '1px solid #e5e7eb',
        borderRadius: 8,
        padding: 16,
    },
    cardTitle: { fontWeight: 600, marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' },

    kpi: {
        textAlign: 'center' as const,
        padding: 16,
        background: '#f9fafb',
        borderRadius: 8,
        border: '1px solid #e5e7eb',
    },
    kpiLabel: { fontSize: 12, color: '#6b7280', marginBottom: 4 },
    kpiValue: (color?: string) => ({ fontSize: 24, fontWeight: 700, color: color || '#111827' }),

    btn: (variant: 'primary' | 'secondary' | 'success' | 'danger' | 'warning') => ({
        padding: '8px 16px',
        border: 'none',
        borderRadius: 6,
        cursor: 'pointer',
        fontWeight: 500,
        fontSize: 14,
        background: variant === 'primary' ? '#2563eb' :
            variant === 'success' ? '#10b981' :
                variant === 'danger' ? '#ef4444' :
                    variant === 'warning' ? '#f59e0b' : '#f3f4f6',
        color: variant === 'secondary' ? '#374151' : '#fff',
    }),
    btnSmall: { padding: '4px 8px', fontSize: 12 },

    select: { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 14 },
    input: { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 14, width: '100%' },

    badge: (status: TestStatus | string) => ({
        display: 'inline-block',
        padding: '2px 8px',
        borderRadius: 9999,
        fontSize: 12,
        fontWeight: 600,
        background: status === 'PASSED' || status === 'success' ? '#dcfce7' :
            status === 'FAILED' || status === 'failed' ? '#fee2e2' :
                status === 'RUNNING' ? '#dbeafe' :
                    status === 'SKIPPED' ? '#f3f4f6' : '#fef3c7',
        color: status === 'PASSED' || status === 'success' ? '#166534' :
            status === 'FAILED' || status === 'failed' ? '#991b1b' :
                status === 'RUNNING' ? '#1e40af' :
                    status === 'SKIPPED' ? '#374151' : '#92400e',
    }),

    table: { width: '100%', borderCollapse: 'collapse' as const },
    th: { textAlign: 'left' as const, padding: 12, borderBottom: '1px solid #e5e7eb', background: '#f9fafb', fontSize: 13, fontWeight: 600 },
    td: { padding: 12, borderBottom: '1px solid #f3f4f6', fontSize: 14 },

    mono: { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace', fontSize: 13 },

    alert: (type: 'info' | 'success' | 'warning' | 'error') => ({
        padding: 12,
        borderRadius: 6,
        marginBottom: 12,
        background: type === 'success' ? '#dcfce7' :
            type === 'error' ? '#fee2e2' :
                type === 'warning' ? '#fef3c7' : '#dbeafe',
        border: `1px solid ${type === 'success' ? '#86efac' :
            type === 'error' ? '#fca5a5' :
                type === 'warning' ? '#fcd34d' : '#93c5fd'}`,
        color: type === 'success' ? '#166534' :
            type === 'error' ? '#991b1b' :
                type === 'warning' ? '#92400e' : '#1e40af',
    }),

    progressBar: (percent: number, color: string) => ({
        height: 8,
        background: '#e5e7eb',
        borderRadius: 4,
        overflow: 'hidden' as const,
        position: 'relative' as const,
    }),
    progressFill: (percent: number, color: string) => ({
        position: 'absolute' as const,
        top: 0,
        left: 0,
        height: '100%',
        width: `${percent}%`,
        background: color,
        transition: 'width 0.3s ease',
    }),
};

// ============================================================================
// Components
// ============================================================================

function Kpi({ label, value, color, subtext }: { label: string; value: string | number; color?: string; subtext?: string }) {
    return (
        <div style={styles.kpi}>
            <div style={styles.kpiLabel}>{label}</div>
            <div style={styles.kpiValue(color)}>{value}</div>
            {subtext && <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 4 }}>{subtext}</div>}
        </div>
    );
}

function ProgressBar({ percent, color = '#10b981' }: { percent: number; color?: string }) {
    return (
        <div style={styles.progressBar(percent, color)}>
            <div style={styles.progressFill(percent, color)} />
        </div>
    );
}

function StatusBadge({ status }: { status: TestStatus | string }) {
    return <span style={styles.badge(status)}>{status}</span>;
}

function ModuleBadge({ module, supported }: { module: string; supported: boolean }) {
    return (
        <span style={{
            display: 'inline-block',
            padding: '2px 6px',
            borderRadius: 4,
            fontSize: 11,
            fontWeight: 500,
            background: supported ? '#dcfce7' : '#f3f4f6',
            color: supported ? '#166534' : '#9ca3af',
            marginRight: 4,
            marginBottom: 4,
        }}>
            {module}
        </span>
    );
}

// ============================================================================
// Main Component
// ============================================================================

type TabKey = 'dashboard' | 'partners' | 'scenarios' | 'results' | 'quick-tests' | 'request-builder' | 'badge-tests';

// Badge/OCPI test configuration
type OCPITestType = 'transaction' | 'authorize' | 'locations' | 'sessions' | 'cdrs' | 'tokens' | 'tariffs' | 'commands';
type OCPIVersionType = '2.1.1' | '2.2.1';

interface BadgeTestConfig {
    ocppId: string;
    emsp: string;
    tag: string;
    durationMinutes: number;
    testType?: OCPITestType;  // Type of test to run
    partnerId?: string;       // For OCPI module tests
    ocpiVersion?: OCPIVersionType;  // OCPI version: 2.1.1 or 2.2.1
}

const OCPI_VERSIONS: { value: OCPIVersionType; label: string }[] = [
    { value: '2.2.1', label: 'OCPI 2.2.1' },
    { value: '2.1.1', label: 'OCPI 2.1.1' },
];

const OCPI_TEST_TYPES: { value: OCPITestType; label: string; description: string }[] = [
    { value: 'transaction', label: 'Full Transaction', description: 'Connect → Authorize → Start → MeterValues → Stop' },
    { value: 'authorize', label: 'Authorize Only', description: 'Test badge authorization without transaction' },
    { value: 'locations', label: 'OCPI Locations', description: 'GET /ocpi/cpo/2.2.1/locations' },
    { value: 'sessions', label: 'OCPI Sessions', description: 'GET /ocpi/cpo/2.2.1/sessions' },
    { value: 'cdrs', label: 'OCPI CDRs', description: 'GET /ocpi/cpo/2.2.1/cdrs' },
    { value: 'tokens', label: 'OCPI Tokens', description: 'POST /ocpi/emsp/2.2.1/tokens/{token_uid}/authorize' },
    { value: 'tariffs', label: 'OCPI Tariffs', description: 'GET /ocpi/cpo/2.2.1/tariffs' },
    { value: 'commands', label: 'OCPI Commands', description: 'POST /ocpi/emsp/2.2.1/commands/START_SESSION' },
];

const DEFAULT_BADGE_TESTS: BadgeTestConfig[] = [
    { ocppId: 'ocppbridge001', emsp: 'BEMO', tag: '2525252525', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge002', emsp: 'YESEMSP', tag: '3535353535', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge003', emsp: 'OCTOPUS', tag: 'DEBDD709C86654', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge004', emsp: 'DEFTPOWER', tag: '04047312DB7181', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge006', emsp: 'VATTENFALL', tag: '022354A955711', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge007', emsp: 'SHELLRECHARGE', tag: '0945684956', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge008', emsp: 'OCE', tag: '04A41C5ADC0F94', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge010', emsp: 'GREENFLUX', tag: 'c788fb962a0ee7', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'CU-camden-arielroad-001', emsp: 'HUBJECT', tag: '84B25B3C', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge012', emsp: 'BONNET', tag: 'c7a9242471a456', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge013', emsp: 'CHARGEPOINT', tag: '9710E60C', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge014', emsp: 'BECHARGE', tag: 'IT-MXP-C1MRKCZX4-4', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
    { ocppId: 'ocppbridge015', emsp: 'THREEFORCEBV', tag: '0446F68A133C80', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' },
];

export default function OCPITab() {
    const [activeTab, setActiveTab] = useState<TabKey>('dashboard');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Data
    const [dashboard, setDashboard] = useState<DashboardData | null>(null);
    const [partners, setPartners] = useState<Partner[]>([]);
    const [scenarios, setScenarios] = useState<TestScenario[]>([]);
    const [results, setResults] = useState<TestResult[]>([]);

    // Selection
    const [selectedPartner, setSelectedPartner] = useState<string>('');
    const [selectedScenario, setSelectedScenario] = useState<string>('');
    const [selectedResult, setSelectedResult] = useState<TestResult | null>(null);

    // Quick test results
    const [quickTestResult, setQuickTestResult] = useState<any>(null);

    // Request Builder state
    const [requestMethod, setRequestMethod] = useState<HttpMethod>('GET');
    const [requestUrl, setRequestUrl] = useState('');
    const [requestModule, setRequestModule] = useState<string>('');
    const [requestHeaders, setRequestHeaders] = useState('');
    const [requestBody, setRequestBody] = useState('');
    const [requestQueryParams, setRequestQueryParams] = useState('');
    const [requestResponse, setRequestResponse] = useState<RawRequestResponse | null>(null);
    const [requestTemplates, setRequestTemplates] = useState<RequestTemplate[]>([]);

    // Scenario editor state
    const [scenarioEditMode, setScenarioEditMode] = useState(false);
    const [editingScenarioJson, setEditingScenarioJson] = useState('');

    // Badge test state
    const [badgeTests, setBadgeTests] = useState<BadgeTestConfig[]>(DEFAULT_BADGE_TESTS);
    const [badgeTestResults, setBadgeTestResults] = useState<Record<string, { status: string; message?: string; startTime?: string; steps?: string[]; energyWh?: number }>>({});
    const [selectedBadgeTests, setSelectedBadgeTests] = useState<Set<string>>(new Set());
    const [newBadgeTest, setNewBadgeTest] = useState<BadgeTestConfig>({ ocppId: '', emsp: '', tag: '', durationMinutes: 3, testType: 'transaction', ocpiVersion: '2.2.1' });

    // Global Environment state (from store)
    const globalConfig = useOCPIStore(selectGlobalConfig);
    const activeGlobalEnv = useOCPIStore(selectActiveGlobalEnv);
    const setGlobalEnvironment = useOCPIStore(state => state.setGlobalEnvironment);
    const updateGlobalConfig = useOCPIStore(state => state.updateGlobalConfig);
    const updateGlobalEnvironment = useOCPIStore(state => state.updateGlobalEnvironment);
    const showEnvConfigModal = useOCPIStore(state => state.showEnvConfigModal);
    const setShowEnvConfigModal = useOCPIStore(state => state.setShowEnvConfigModal);

    // =========================================================================
    // Data Loading
    // =========================================================================

    const loadDashboard = useCallback(async () => {
        try {
            const data = await dashboardApi.get();
            setDashboard(data);
        } catch (e: any) {
            console.error('Failed to load dashboard:', e);
        }
    }, []);

    const loadPartners = useCallback(async () => {
        try {
            const data = await partnersApi.getAll();
            setPartners(data);
            if (data.length > 0 && !selectedPartner) {
                setSelectedPartner(data[0].id);
            }
        } catch (e: any) {
            console.error('Failed to load partners:', e);
        }
    }, [selectedPartner]);

    const loadScenarios = useCallback(async () => {
        try {
            const data = await scenariosApi.getAll();
            setScenarios(data);
        } catch (e: any) {
            console.error('Failed to load scenarios:', e);
        }
    }, []);

    const loadResults = useCallback(async () => {
        try {
            const data = await resultsApi.getRecent(50);
            setResults(data);
        } catch (e: any) {
            console.error('Failed to load results:', e);
        }
    }, []);

    const loadTemplates = useCallback(async () => {
        try {
            const data = await requestBuilderApi.getTemplates();
            setRequestTemplates(data);
        } catch (e: any) {
            console.error('Failed to load templates:', e);
        }
    }, []);

    useEffect(() => {
        loadDashboard();
        loadPartners();
        loadScenarios();
        loadResults();
        loadTemplates();
    }, []);

    // =========================================================================
    // Actions
    // =========================================================================

    const handleDiscover = async (partnerId: string) => {
        setLoading(true);
        setError(null);
        try {
            const result = await partnersApi.discoverFull(partnerId);
            if (result.ok) {
                await loadPartners();
                alert(`Discovery successful! Found ${Object.keys(result.endpoints || {}).length} endpoints for version ${result.version}`);
            } else {
                setError(result.error || 'Discovery failed');
            }
        } catch (e: any) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    };

    const handleRunTest = async () => {
        if (!selectedPartner || !selectedScenario) {
            setError('Please select a partner and scenario');
            return;
        }

        setLoading(true);
        setError(null);
        try {
            const result = await testsApi.run(selectedScenario, selectedPartner);
            setSelectedResult(result);
            await loadResults();
            await loadDashboard();
        } catch (e: any) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    };

    const handleRunAllTests = async () => {
        if (!selectedPartner) {
            setError('Please select a partner');
            return;
        }

        setLoading(true);
        setError(null);
        try {
            const result = await testsApi.runAll(selectedPartner);
            alert(`Tests completed: ${result.passed} passed, ${result.failed} failed out of ${result.total} total`);
            await loadResults();
            await loadDashboard();
        } catch (e: any) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    };

    const handleQuickTest = async (module: string) => {
        if (!selectedPartner) {
            setError('Please select a partner first');
            return;
        }

        setLoading(true);
        setQuickTestResult(null);
        try {
            let result;
            switch (module) {
                case 'locations':
                    result = await quickTestsApi.locations(selectedPartner);
                    break;
                case 'sessions':
                    result = await quickTestsApi.sessions(selectedPartner);
                    break;
                case 'cdrs':
                    result = await quickTestsApi.cdrs(selectedPartner);
                    break;
                case 'tokens':
                    result = await quickTestsApi.tokens(selectedPartner);
                    break;
                case 'tariffs':
                    result = await quickTestsApi.tariffs(selectedPartner);
                    break;
                default:
                    throw new Error(`Unknown module: ${module}`);
            }
            setQuickTestResult({ module, ...result });
        } catch (e: any) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    };

    const handleExecuteRequest = async () => {
        if (!selectedPartner) {
            setError('Please select a partner');
            return;
        }

        setLoading(true);
        setRequestResponse(null);
        setError(null);

        try {
            // Parse headers and query params
            let headers: Record<string, string> = {};
            let queryParams: Record<string, string> = {};
            let body: any = undefined;

            if (requestHeaders.trim()) {
                try {
                    headers = JSON.parse(requestHeaders);
                } catch {
                    // Try to parse as key: value lines
                    requestHeaders.split('\n').forEach(line => {
                        const [key, ...rest] = line.split(':');
                        if (key && rest.length) {
                            headers[key.trim()] = rest.join(':').trim();
                        }
                    });
                }
            }

            if (requestQueryParams.trim()) {
                try {
                    queryParams = JSON.parse(requestQueryParams);
                } catch {
                    // Try to parse as key=value lines
                    requestQueryParams.split('\n').forEach(line => {
                        const [key, ...rest] = line.split('=');
                        if (key && rest.length) {
                            queryParams[key.trim()] = rest.join('=').trim();
                        }
                    });
                }
            }

            if (requestBody.trim() && requestMethod !== 'GET') {
                try {
                    body = JSON.parse(requestBody);
                } catch {
                    body = requestBody;
                }
            }

            const config: RequestConfig = {
                partnerId: selectedPartner,
                method: requestMethod,
                ...(requestUrl ? { url: requestUrl } : {}),
                ...(requestModule ? { module: requestModule as OCPIModule } : {}),
                headers: Object.keys(headers).length > 0 ? headers : undefined,
                queryParams: Object.keys(queryParams).length > 0 ? queryParams : undefined,
                body,
            };

            const response = await requestBuilderApi.execute(config);
            setRequestResponse(response);
        } catch (e: any) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    };

    const applyTemplate = (template: RequestTemplate) => {
        setRequestMethod(template.method);
        setRequestModule(template.module || '');
        setRequestUrl('');
        if (template.queryParams) {
            setRequestQueryParams(JSON.stringify(template.queryParams, null, 2));
        } else {
            setRequestQueryParams('');
        }
        if (template.body) {
            setRequestBody(JSON.stringify(template.body, null, 2));
        } else {
            setRequestBody('');
        }
    };

    // =========================================================================
    // Render Functions
    // =========================================================================

    const renderDashboard = () => (
        <div style={styles.grid}>
            {/* KPIs */}
            <div style={styles.grid4}>
                <Kpi
                    label="Partners"
                    value={dashboard?.partners.total || 0}
                    subtext={`${dashboard?.partners.active || 0} active`}
                />
                <Kpi
                    label="Scenarios"
                    value={dashboard?.scenarios.total || 0}
                />
                <Kpi
                    label="Tests Run"
                    value={dashboard?.tests.total || 0}
                    subtext={`${dashboard?.tests.passed || 0} passed`}
                />
                <Kpi
                    label="Success Rate"
                    value={`${(dashboard?.tests.successRate || 0).toFixed(1)}%`}
                    color={dashboard?.tests.successRate && dashboard.tests.successRate >= 80 ? '#10b981' : '#ef4444'}
                />
            </div>

            {/* Partner Status */}
            <div style={styles.card}>
                <div style={styles.cardTitle}>
                    <span>Partner Status</span>
                    <button style={{ ...styles.btn('secondary'), ...styles.btnSmall }} onClick={loadPartners}>
                        Refresh
                    </button>
                </div>
                <table style={styles.table}>
                    <thead>
                    <tr>
                        <th style={styles.th}>Partner</th>
                        <th style={styles.th}>Role</th>
                        <th style={styles.th}>Status</th>
                        <th style={styles.th}>Tests</th>
                        <th style={styles.th}>Success</th>
                        <th style={styles.th}>Last Sync</th>
                    </tr>
                    </thead>
                    <tbody>
                    {Object.entries(dashboard?.partnerStats || {}).map(([id, stats]) => (
                        <tr key={id}>
                            <td style={styles.td}>
                                <strong>{stats.name}</strong>
                                <div style={{ fontSize: 12, color: '#6b7280' }}>{id}</div>
                            </td>
                            <td style={styles.td}>{partners.find(p => p.id === id)?.role || '-'}</td>
                            <td style={styles.td}>
                                <StatusBadge status={stats.active ? 'PASSED' : 'SKIPPED'} />
                            </td>
                            <td style={styles.td}>{stats.totalTests}</td>
                            <td style={styles.td}>
                                {stats.totalTests > 0 && (
                                    <div>
                                        <div style={{ marginBottom: 4 }}>
                                            {stats.passed}/{stats.totalTests}
                                        </div>
                                        <ProgressBar
                                            percent={(stats.passed / stats.totalTests) * 100}
                                            color={stats.passed === stats.totalTests ? '#10b981' : '#f59e0b'}
                                        />
                                    </div>
                                )}
                            </td>
                            <td style={styles.td}>
                                <span style={{ fontSize: 12, color: '#6b7280' }}>
                                    {stats.lastSync !== 'never' ? new Date(stats.lastSync).toLocaleString() : 'Never'}
                                </span>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            {/* Recent Results */}
            <div style={styles.card}>
                <div style={styles.cardTitle}>Recent Test Results</div>
                <table style={styles.table}>
                    <thead>
                    <tr>
                        <th style={styles.th}>Time</th>
                        <th style={styles.th}>Scenario</th>
                        <th style={styles.th}>Partner</th>
                        <th style={styles.th}>Status</th>
                        <th style={styles.th}>Duration</th>
                        <th style={styles.th}>Steps</th>
                    </tr>
                    </thead>
                    <tbody>
                    {results.slice(0, 10).map((r) => (
                        <tr key={r.id} style={{ cursor: 'pointer' }} onClick={() => setSelectedResult(r)}>
                            <td style={styles.td}>{new Date(r.startTime).toLocaleString()}</td>
                            <td style={styles.td}>{r.scenarioName}</td>
                            <td style={styles.td}>{r.partnerName}</td>
                            <td style={styles.td}><StatusBadge status={r.status} /></td>
                            <td style={styles.td}>{r.durationMs}ms</td>
                            <td style={styles.td}>
                                {r.summary?.passedSteps}/{r.summary?.totalSteps}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        </div>
    );

    const renderPartners = () => (
        <PartnersList />
    );

    const renderScenarios = () => (
        <ScenariosPanel />
    );

    // Legacy renderScenarios code kept for reference (now replaced by ScenariosPanel)
    const renderScenariosLegacy = () => {
        const currentScenario = scenarios.find(s => s.id === selectedScenario);

        const handleEditScenario = () => {
            if (currentScenario) {
                setEditingScenarioJson(JSON.stringify(currentScenario, null, 2));
                setScenarioEditMode(true);
            }
        };

        const handleSaveScenario = async () => {
            try {
                const updated = JSON.parse(editingScenarioJson);
                await scenariosApi.update(updated.id, updated);
                setScenarioEditMode(false);
                await loadScenarios();
                alert('Scenario saved successfully');
            } catch (e: any) {
                alert('Error saving scenario: ' + e.message);
            }
        };

        return (
            <div style={styles.grid}>
                {/* Run Panel */}
                <div style={styles.card}>
                    <div style={styles.cardTitle}>Execute Tests</div>
                    <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 16 }}>
                        <select
                            style={styles.select}
                            value={selectedPartner}
                            onChange={(e) => setSelectedPartner(e.target.value)}
                        >
                            <option value="">-- Select Partner --</option>
                            {partners.map((p) => (
                                <option key={p.id} value={p.id}>{p.name} ({p.role})</option>
                            ))}
                        </select>

                        <select
                            style={styles.select}
                            value={selectedScenario}
                            onChange={(e) => { setSelectedScenario(e.target.value); setScenarioEditMode(false); }}
                        >
                            <option value="">-- Select Scenario --</option>
                            {scenarios.map((s) => (
                                <option key={s.id} value={s.id}>{s.name}</option>
                            ))}
                        </select>

                        <button
                            style={styles.btn('primary')}
                            onClick={handleRunTest}
                            disabled={loading || !selectedPartner || !selectedScenario}
                        >
                            {loading ? 'Running...' : 'Run Test'}
                        </button>

                        <button
                            style={styles.btn('success')}
                            onClick={handleRunAllTests}
                            disabled={loading || !selectedPartner}
                        >
                            Run All Tests
                        </button>
                    </div>
                </div>

                {/* Scenarios List & Details */}
                <div style={styles.grid2}>
                    {/* Scenarios List */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>
                            <span>Available Scenarios ({scenarios.length})</span>
                            <button style={{ ...styles.btn('secondary'), ...styles.btnSmall }} onClick={() => scenariosApi.reload().then(loadScenarios)}>
                                Reload
                            </button>
                        </div>
                        <div style={{ display: 'grid', gap: 8, maxHeight: 500, overflow: 'auto' }}>
                            {scenarios.map((s) => (
                                <div
                                    key={s.id}
                                    style={{
                                        padding: 12,
                                        border: selectedScenario === s.id ? '2px solid #2563eb' : '1px solid #e5e7eb',
                                        borderRadius: 6,
                                        cursor: 'pointer',
                                        background: selectedScenario === s.id ? '#eff6ff' : '#fff',
                                    }}
                                    onClick={() => { setSelectedScenario(s.id); setScenarioEditMode(false); }}
                                >
                                    <div style={{ fontWeight: 600, marginBottom: 4 }}>{s.name}</div>
                                    <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 4 }}>
                                        {s.description}
                                    </div>
                                    <div>
                                        <span style={{
                                            display: 'inline-block',
                                            padding: '2px 6px',
                                            background: '#f3f4f6',
                                            borderRadius: 4,
                                            fontSize: 11,
                                            marginRight: 4,
                                        }}>
                                            {s.category}
                                        </span>
                                        <span style={{ fontSize: 11, color: '#9ca3af' }}>
                                            {s.steps?.length || 0} steps
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Scenario Details / Editor */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>
                            <span>Scenario Details</span>
                            {currentScenario && (
                                <div style={{ display: 'flex', gap: 8 }}>
                                    {scenarioEditMode ? (
                                        <>
                                            <button style={{ ...styles.btn('success'), ...styles.btnSmall }} onClick={handleSaveScenario}>
                                                Save
                                            </button>
                                            <button style={{ ...styles.btn('secondary'), ...styles.btnSmall }} onClick={() => setScenarioEditMode(false)}>
                                                Cancel
                                            </button>
                                        </>
                                    ) : (
                                        <button style={{ ...styles.btn('primary'), ...styles.btnSmall }} onClick={handleEditScenario}>
                                            Edit JSON
                                        </button>
                                    )}
                                </div>
                            )}
                        </div>

                        {currentScenario ? (
                            scenarioEditMode ? (
                                <textarea
                                    style={{
                                        ...styles.input,
                                        fontFamily: 'monospace',
                                        fontSize: 12,
                                        minHeight: 500,
                                        width: '100%',
                                    }}
                                    value={editingScenarioJson}
                                    onChange={(e) => setEditingScenarioJson(e.target.value)}
                                />
                            ) : (
                                <div>
                                    {/* Scenario Info */}
                                    <div style={{ marginBottom: 16, padding: 12, background: '#f9fafb', borderRadius: 6 }}>
                                        <div style={{ fontWeight: 600, fontSize: 16 }}>{currentScenario.name}</div>
                                        <div style={{ color: '#6b7280', marginBottom: 8 }}>{currentScenario.description}</div>
                                        <div style={{ display: 'flex', gap: 16, fontSize: 13 }}>
                                            <span><strong>Category:</strong> {currentScenario.category}</span>
                                            <span><strong>Stop on Failure:</strong> {currentScenario.stopOnFailure ? 'Yes' : 'No'}</span>
                                        </div>
                                        {currentScenario.tags && currentScenario.tags.length > 0 && (
                                            <div style={{ marginTop: 8 }}>
                                                {currentScenario.tags.map(tag => (
                                                    <span key={tag} style={{
                                                        display: 'inline-block',
                                                        padding: '2px 6px',
                                                        background: '#dbeafe',
                                                        color: '#1e40af',
                                                        borderRadius: 4,
                                                        fontSize: 11,
                                                        marginRight: 4,
                                                    }}>
                                                        {tag}
                                                    </span>
                                                ))}
                                            </div>
                                        )}
                                    </div>

                                    {/* Steps */}
                                    <div style={{ marginBottom: 16 }}>
                                        <div style={{ fontWeight: 600, marginBottom: 8 }}>
                                            Steps ({currentScenario.steps?.length || 0})
                                        </div>
                                        <div style={{ maxHeight: 350, overflow: 'auto' }}>
                                            {currentScenario.steps?.map((step, i) => (
                                                <details key={step.id || i} style={{ marginBottom: 8 }}>
                                                    <summary style={{
                                                        cursor: 'pointer',
                                                        padding: 10,
                                                        background: '#f9fafb',
                                                        borderRadius: 4,
                                                        border: '1px solid #e5e7eb',
                                                    }}>
                                                        <span style={{
                                                            display: 'inline-block',
                                                            padding: '2px 6px',
                                                            borderRadius: 4,
                                                            fontSize: 10,
                                                            fontWeight: 600,
                                                            marginRight: 8,
                                                            background: step.type === 'HTTP_REQUEST' ? '#dcfce7' :
                                                                step.type === 'VERSION_DISCOVERY' ? '#dbeafe' : '#fef3c7',
                                                            color: step.type === 'HTTP_REQUEST' ? '#166534' :
                                                                step.type === 'VERSION_DISCOVERY' ? '#1e40af' : '#92400e',
                                                        }}>
                                                            {step.type}
                                                        </span>
                                                        <strong>{step.name}</strong>
                                                        {step.method && (
                                                            <span style={{ marginLeft: 8, color: '#6b7280', fontSize: 12 }}>
                                                                {step.method}
                                                            </span>
                                                        )}
                                                    </summary>
                                                    <div style={{ padding: 12, background: '#fafafa', borderRadius: '0 0 4px 4px', fontSize: 13 }}>
                                                        {step.description && (
                                                            <div style={{ marginBottom: 8, color: '#6b7280' }}>{step.description}</div>
                                                        )}
                                                        {step.endpoint && (
                                                            <div style={{ marginBottom: 8 }}>
                                                                <strong>Endpoint:</strong>{' '}
                                                                <code style={{ background: '#e5e7eb', padding: '2px 6px', borderRadius: 3 }}>
                                                                    {step.endpoint}
                                                                </code>
                                                            </div>
                                                        )}
                                                        {step.timeoutMs && (
                                                            <div style={{ marginBottom: 8 }}>
                                                                <strong>Timeout:</strong> {step.timeoutMs}ms
                                                            </div>
                                                        )}
                                                        {step.assertions && step.assertions.length > 0 && (
                                                            <div style={{ marginBottom: 8 }}>
                                                                <strong>Assertions ({step.assertions.length}):</strong>
                                                                <ul style={{ margin: '4px 0', paddingLeft: 20, fontSize: 12 }}>
                                                                    {step.assertions.map((a, j) => (
                                                                        <li key={j} style={{ color: a.critical ? '#991b1b' : '#374151' }}>
                                                                            {a.name}: {a.type} {a.operator} {String(a.expected)}
                                                                            {a.critical && <span style={{ color: '#ef4444', marginLeft: 4 }}>(critical)</span>}
                                                                        </li>
                                                                    ))}
                                                                </ul>
                                                            </div>
                                                        )}
                                                    </div>
                                                </details>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            )
                        ) : (
                            <div style={{ color: '#6b7280', textAlign: 'center', padding: 32 }}>
                                Select a scenario to view its details
                            </div>
                        )}
                    </div>
                </div>

                {/* Test Result Details */}
                {selectedResult && (
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>Last Test Result</div>
                        <div style={{ marginBottom: 12 }}>
                            <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
                                <StatusBadge status={selectedResult.status} />
                                <span style={{ fontWeight: 600 }}>{selectedResult.scenarioName}</span>
                            </div>
                            <div style={{ fontSize: 13, color: '#6b7280' }}>
                                Partner: {selectedResult.partnerName} | Duration: {selectedResult.durationMs}ms
                            </div>
                        </div>

                        {/* Summary */}
                        {selectedResult.summary && (
                            <div style={{ ...styles.grid4, marginBottom: 16 }}>
                                <Kpi label="Total Steps" value={selectedResult.summary.totalSteps} />
                                <Kpi label="Passed" value={selectedResult.summary.passedSteps} color="#10b981" />
                                <Kpi label="Failed" value={selectedResult.summary.failedSteps} color="#ef4444" />
                                <Kpi label="Avg Latency" value={`${selectedResult.summary.avgLatencyMs}ms`} />
                            </div>
                        )}

                        {/* Step Results */}
                        <div style={{ maxHeight: 300, overflow: 'auto' }}>
                            {selectedResult.stepResults?.map((step, i) => (
                                <details key={i} style={{ marginBottom: 8 }}>
                                    <summary style={{
                                        cursor: 'pointer',
                                        padding: 8,
                                        background: '#f9fafb',
                                        borderRadius: 4,
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                    }}>
                                        <span>
                                            <StatusBadge status={step.status} />
                                            <span style={{ marginLeft: 8 }}>{step.stepName}</span>
                                        </span>
                                        <span style={{ fontSize: 12, color: '#6b7280' }}>
                                            {step.durationMs}ms
                                        </span>
                                    </summary>
                                    <div style={{ padding: 12, fontSize: 13 }}>
                                        {step.request && (
                                            <div style={{ marginBottom: 8 }}>
                                                <strong>Request:</strong>{' '}
                                                <span style={styles.mono}>{step.request.method} {step.request.url}</span>
                                            </div>
                                        )}
                                        {step.response && (
                                            <div style={{ marginBottom: 8 }}>
                                                <strong>Response:</strong>{' '}
                                                HTTP {step.response.httpStatus},
                                                OCPI {step.response.ocpiStatus}
                                                {step.response.ocpiMessage && ` - ${step.response.ocpiMessage}`}
                                            </div>
                                        )}
                                        {step.assertionResults && step.assertionResults.length > 0 && (
                                            <div>
                                                <strong>Assertions:</strong>
                                                <ul style={{ margin: '4px 0', paddingLeft: 20 }}>
                                                    {step.assertionResults.map((a, j) => (
                                                        <li key={j} style={{ color: a.passed ? '#10b981' : '#ef4444' }}>
                                                            {a.passed ? '[OK]' : '[ERR]'} {a.name}
                                                            {!a.passed && a.message && ` - ${a.message}`}
                                                        </li>
                                                    ))}
                                                </ul>
                                            </div>
                                        )}
                                        {step.errorMessage && (
                                            <div style={{ color: '#ef4444' }}>
                                                <strong>Error:</strong> {step.errorMessage}
                                            </div>
                                        )}
                                    </div>
                                </details>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        );
    };

    const renderBadgeTests = () => {
        const toggleSelectBadgeTest = (key: string) => {
            const newSelected = new Set(selectedBadgeTests);
            if (newSelected.has(key)) {
                newSelected.delete(key);
            } else {
                newSelected.add(key);
            }
            setSelectedBadgeTests(newSelected);
        };

        const selectAllBadgeTests = () => {
            setSelectedBadgeTests(new Set(badgeTests.map(t => `${t.ocppId}-${t.emsp}`)));
        };

        const deselectAllBadgeTests = () => {
            setSelectedBadgeTests(new Set());
        };

        const addBadgeTest = () => {
            if (newBadgeTest.ocppId && newBadgeTest.emsp && newBadgeTest.tag) {
                setBadgeTests([...badgeTests, { ...newBadgeTest }]);
                setNewBadgeTest({ ocppId: '', emsp: '', tag: '', durationMinutes: 3 });
            }
        };

        const removeBadgeTest = (index: number) => {
            const updated = [...badgeTests];
            updated.splice(index, 1);
            setBadgeTests(updated);
        };

        const updateBadgeTest = (index: number, field: keyof BadgeTestConfig, value: string | number) => {
            const updated = [...badgeTests];
            updated[index] = { ...updated[index], [field]: value };
            setBadgeTests(updated);
        };

        const runSelectedBadgeTests = async () => {
            if (selectedBadgeTests.size === 0) {
                setError('Please select at least one badge test to run');
                return;
            }

            setLoading(true);
            setError(null);

            const testsToRun = badgeTests.filter(t => selectedBadgeTests.has(`${t.ocppId}-${t.emsp}`));

            for (const test of testsToRun) {
                const key = `${test.ocppId}-${test.emsp}`;
                setBadgeTestResults(prev => ({
                    ...prev,
                    [key]: { status: 'running', startTime: new Date().toISOString() }
                }));

                try {
                    // Call the badge test API
                    const result = await badgeTestApi.run(test);
                    setBadgeTestResults(prev => ({
                        ...prev,
                        [key]: {
                            status: result.success ? 'passed' : 'failed',
                            message: result.message,
                            steps: result.steps,
                            energyWh: result.energyWh,
                            startTime: result.startTime,
                            endTime: result.endTime,
                        }
                    }));
                } catch (e: any) {
                    setBadgeTestResults(prev => ({
                        ...prev,
                        [key]: { status: 'error', message: e.message }
                    }));
                }
            }

            setLoading(false);
        };

        return (
            <div style={styles.grid}>
                {/* Info */}
                <div style={styles.alert('info')}>
                    <strong>Tests de charge automatises avec badges EMSP</strong>
                    <p style={{ margin: '8px 0 0', fontSize: 13 }}>
                        Configure et lance des tests de charge OCPP avec autorisation de badge.
                        Sequence: Connect EVSE → Authorize Tag → StartTransaction → MeterValues → StopTransaction
                    </p>
                </div>

                <div style={styles.grid2}>
                    {/* Badge Tests Table */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>
                            <span>Badge Test Configurations ({badgeTests.length})</span>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <button style={{ ...styles.btn('secondary'), ...styles.btnSmall }} onClick={selectAllBadgeTests}>
                                    Select All
                                </button>
                                <button style={{ ...styles.btn('secondary'), ...styles.btnSmall }} onClick={deselectAllBadgeTests}>
                                    Deselect All
                                </button>
                            </div>
                        </div>

                        <div style={{ maxHeight: 400, overflow: 'auto' }}>
                            <table style={styles.table}>
                                <thead>
                                    <tr>
                                        <th style={styles.th}>
                                            <input
                                                type="checkbox"
                                                checked={selectedBadgeTests.size === badgeTests.length && badgeTests.length > 0}
                                                onChange={() => selectedBadgeTests.size === badgeTests.length ? deselectAllBadgeTests() : selectAllBadgeTests()}
                                            />
                                        </th>
                                        <th style={styles.th}>Test Type</th>
                                        <th style={styles.th}>Version</th>
                                        <th style={styles.th}>OCPP ID</th>
                                        <th style={styles.th}>EMSP</th>
                                        <th style={styles.th}>Tag/Badge</th>
                                        <th style={styles.th}>Dur.</th>
                                        <th style={styles.th}>Status</th>
                                        <th style={styles.th}>Act.</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {badgeTests.map((test, i) => {
                                        const key = `${test.ocppId}-${test.emsp}`;
                                        const result = badgeTestResults[key];
                                        return (
                                            <tr key={i}>
                                                <td style={styles.td}>
                                                    <input
                                                        type="checkbox"
                                                        checked={selectedBadgeTests.has(key)}
                                                        onChange={() => toggleSelectBadgeTest(key)}
                                                    />
                                                </td>
                                                <td style={styles.td}>
                                                    <select
                                                        value={test.testType || 'transaction'}
                                                        onChange={(e) => updateBadgeTest(i, 'testType', e.target.value)}
                                                        style={{ ...styles.input, fontSize: 10, padding: '3px 4px', width: 90 }}
                                                    >
                                                        {OCPI_TEST_TYPES.map(t => (
                                                            <option key={t.value} value={t.value}>{t.label}</option>
                                                        ))}
                                                    </select>
                                                </td>
                                                <td style={styles.td}>
                                                    <select
                                                        value={test.ocpiVersion || '2.2.1'}
                                                        onChange={(e) => updateBadgeTest(i, 'ocpiVersion', e.target.value)}
                                                        style={{ ...styles.input, fontSize: 10, padding: '3px 4px', width: 65 }}
                                                    >
                                                        {OCPI_VERSIONS.map(v => (
                                                            <option key={v.value} value={v.value}>{v.value}</option>
                                                        ))}
                                                    </select>
                                                </td>
                                                <td style={styles.td}>
                                                    <input
                                                        type="text"
                                                        value={test.ocppId}
                                                        onChange={(e) => updateBadgeTest(i, 'ocppId', e.target.value)}
                                                        style={{ ...styles.input, ...styles.mono, fontSize: 10, padding: '3px 4px', width: 100 }}
                                                    />
                                                </td>
                                                <td style={styles.td}>
                                                    <input
                                                        type="text"
                                                        value={test.emsp}
                                                        onChange={(e) => updateBadgeTest(i, 'emsp', e.target.value.toUpperCase())}
                                                        style={{ ...styles.input, fontSize: 10, padding: '3px 4px', width: 80, fontWeight: 600 }}
                                                    />
                                                </td>
                                                <td style={styles.td}>
                                                    <input
                                                        type="text"
                                                        value={test.tag}
                                                        onChange={(e) => updateBadgeTest(i, 'tag', e.target.value)}
                                                        style={{ ...styles.input, ...styles.mono, fontSize: 10, padding: '3px 4px', width: 110 }}
                                                        placeholder="Badge ID"
                                                    />
                                                </td>
                                                <td style={styles.td}>
                                                    <input
                                                        type="number"
                                                        value={test.durationMinutes}
                                                        onChange={(e) => updateBadgeTest(i, 'durationMinutes', parseInt(e.target.value) || 3)}
                                                        style={{ ...styles.input, fontSize: 10, padding: '3px 4px', width: 40 }}
                                                        min={1}
                                                        max={60}
                                                    />
                                                </td>
                                                <td style={styles.td}>
                                                    {result ? (
                                                        <StatusBadge status={result.status === 'passed' ? 'PASSED' :
                                                            result.status === 'failed' ? 'FAILED' :
                                                                result.status === 'running' ? 'RUNNING' : 'SKIPPED'} />
                                                    ) : (
                                                        <span style={{ color: '#9ca3af' }}>-</span>
                                                    )}
                                                </td>
                                                <td style={styles.td}>
                                                    <button
                                                        style={{ ...styles.btn('danger'), ...styles.btnSmall }}
                                                        onClick={() => removeBadgeTest(i)}
                                                    >
                                                        X
                                                    </button>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>

                        {/* Run Button */}
                        <div style={{ marginTop: 16, display: 'flex', gap: 12 }}>
                            <button
                                style={styles.btn('primary')}
                                onClick={runSelectedBadgeTests}
                                disabled={loading || selectedBadgeTests.size === 0}
                            >
                                {loading ? 'Running...' : `Run ${selectedBadgeTests.size} Selected Tests`}
                            </button>
                            <span style={{ color: '#6b7280', fontSize: 13, alignSelf: 'center' }}>
                                {selectedBadgeTests.size} of {badgeTests.length} selected
                            </span>
                        </div>
                    </div>

                    {/* Add New Badge Test */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>Add New Test</div>

                        <div style={{ display: 'grid', gap: 12 }}>
                            <div>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>Test Type</label>
                                <select
                                    style={styles.input}
                                    value={newBadgeTest.testType || 'transaction'}
                                    onChange={(e) => setNewBadgeTest({ ...newBadgeTest, testType: e.target.value as OCPITestType })}
                                >
                                    {OCPI_TEST_TYPES.map(t => (
                                        <option key={t.value} value={t.value}>{t.label} - {t.description}</option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>OCPI Version</label>
                                <select
                                    style={styles.input}
                                    value={newBadgeTest.ocpiVersion || '2.2.1'}
                                    onChange={(e) => setNewBadgeTest({ ...newBadgeTest, ocpiVersion: e.target.value as OCPIVersionType })}
                                >
                                    {OCPI_VERSIONS.map(v => (
                                        <option key={v.value} value={v.value}>{v.label}</option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>OCPP ID (Charge Point)</label>
                                <input
                                    style={styles.input}
                                    type="text"
                                    placeholder="e.g., ocppbridge001"
                                    value={newBadgeTest.ocppId}
                                    onChange={(e) => setNewBadgeTest({ ...newBadgeTest, ocppId: e.target.value })}
                                />
                            </div>

                            <div>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>EMSP (Mobility Service Provider)</label>
                                <input
                                    style={styles.input}
                                    type="text"
                                    placeholder="e.g., SHELLRECHARGE, HUBJECT, GREENFLUX"
                                    value={newBadgeTest.emsp}
                                    onChange={(e) => setNewBadgeTest({ ...newBadgeTest, emsp: e.target.value.toUpperCase() })}
                                />
                            </div>

                            <div>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>Tag / Badge ID</label>
                                <input
                                    style={styles.input}
                                    type="text"
                                    placeholder="e.g., 0945684956, DEBDD709C86654"
                                    value={newBadgeTest.tag}
                                    onChange={(e) => setNewBadgeTest({ ...newBadgeTest, tag: e.target.value })}
                                />
                            </div>

                            <div>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>Duration (minutes)</label>
                                <input
                                    style={styles.input}
                                    type="number"
                                    min={1}
                                    max={60}
                                    value={newBadgeTest.durationMinutes}
                                    onChange={(e) => setNewBadgeTest({ ...newBadgeTest, durationMinutes: parseInt(e.target.value) || 3 })}
                                />
                            </div>

                            <button
                                style={styles.btn('success')}
                                onClick={addBadgeTest}
                                disabled={!newBadgeTest.ocppId || !newBadgeTest.emsp || !newBadgeTest.tag}
                            >
                                Add Test
                            </button>
                        </div>

                        {/* Quick EMSP Presets */}
                        <div style={{ marginTop: 24 }}>
                            <div style={{ fontWeight: 600, marginBottom: 8 }}>Quick Add EMSP Presets</div>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                                {['SHELLRECHARGE', 'HUBJECT', 'GREENFLUX', 'CHARGEPOINT', 'BONNET', 'OCTOPUS', 'VATTENFALL', 'BECHARGE'].map(emsp => (
                                    <button
                                        key={emsp}
                                        style={{
                                            padding: '4px 10px',
                                            border: '1px solid #d1d5db',
                                            borderRadius: 4,
                                            background: '#fff',
                                            cursor: 'pointer',
                                            fontSize: 12,
                                        }}
                                        onClick={() => setNewBadgeTest({ ...newBadgeTest, emsp })}
                                    >
                                        {emsp}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>

                {/* Test Results Summary */}
                {Object.keys(badgeTestResults).length > 0 && (
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>Test Results Summary</div>
                        <div style={styles.grid4}>
                            <Kpi
                                label="Total Tests"
                                value={Object.keys(badgeTestResults).length}
                            />
                            <Kpi
                                label="Passed"
                                value={Object.values(badgeTestResults).filter(r => r.status === 'passed').length}
                                color="#10b981"
                            />
                            <Kpi
                                label="Failed"
                                value={Object.values(badgeTestResults).filter(r => r.status === 'failed').length}
                                color="#ef4444"
                            />
                            <Kpi
                                label="Running"
                                value={Object.values(badgeTestResults).filter(r => r.status === 'running').length}
                                color="#3b82f6"
                            />
                        </div>

                        {/* Detailed Results */}
                        <div style={{ marginTop: 16, maxHeight: 300, overflow: 'auto' }}>
                            {Object.entries(badgeTestResults).map(([key, result]) => (
                                <div key={key} style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    padding: 8,
                                    borderBottom: '1px solid #e5e7eb',
                                }}>
                                    <span style={styles.mono}>{key}</span>
                                    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                                        {result.message && (
                                            <span style={{ fontSize: 12, color: '#6b7280' }}>{result.message}</span>
                                        )}
                                        <StatusBadge status={result.status === 'passed' ? 'PASSED' :
                                            result.status === 'failed' ? 'FAILED' :
                                                result.status === 'running' ? 'RUNNING' : 'SKIPPED'} />
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        );
    };

    const renderResults = () => (
        <HistoryPanel />
    );

    const renderQuickTests = () => (
        <QuickTestsPanel />
    );

    const renderRequestBuilder = () => {
        const categories = [...new Set(requestTemplates.map(t => t.category))];

        return (
            <div style={styles.grid}>
                <div style={styles.grid2}>
                    {/* Request Form */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>Request Builder</div>

                        {/* Partner Selection */}
                        <div style={{ marginBottom: 12 }}>
                            <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>Partner</label>
                            <select
                                style={{ ...styles.select, width: '100%' }}
                                value={selectedPartner}
                                onChange={(e) => setSelectedPartner(e.target.value)}
                            >
                                <option value="">-- Select Partner --</option>
                                {partners.map((p) => (
                                    <option key={p.id} value={p.id}>{p.name} ({p.role})</option>
                                ))}
                            </select>
                        </div>

                        {/* Method + Module/URL */}
                        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                            <div style={{ width: 120 }}>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>Method</label>
                                <select
                                    style={{ ...styles.select, width: '100%' }}
                                    value={requestMethod}
                                    onChange={(e) => setRequestMethod(e.target.value as HttpMethod)}
                                >
                                    <option value="GET">GET</option>
                                    <option value="POST">POST</option>
                                    <option value="PUT">PUT</option>
                                    <option value="PATCH">PATCH</option>
                                    <option value="DELETE">DELETE</option>
                                </select>
                            </div>
                            <div style={{ flex: 1 }}>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>Module (or use URL)</label>
                                <select
                                    style={{ ...styles.select, width: '100%' }}
                                    value={requestModule}
                                    onChange={(e) => setRequestModule(e.target.value)}
                                >
                                    <option value="">-- Select Module --</option>
                                    <option value="credentials">credentials</option>
                                    <option value="locations">locations</option>
                                    <option value="sessions">sessions</option>
                                    <option value="cdrs">cdrs</option>
                                    <option value="tariffs">tariffs</option>
                                    <option value="tokens">tokens</option>
                                    <option value="commands">commands</option>
                                    <option value="chargingprofiles">chargingprofiles</option>
                                </select>
                            </div>
                        </div>

                        {/* Direct URL */}
                        <div style={{ marginBottom: 12 }}>
                            <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>
                                Direct URL (optional, overrides module)
                            </label>
                            <input
                                style={styles.input}
                                type="text"
                                placeholder="https://api.partner.com/ocpi/2.2.1/locations"
                                value={requestUrl}
                                onChange={(e) => setRequestUrl(e.target.value)}
                            />
                        </div>

                        {/* Query Params */}
                        <div style={{ marginBottom: 12 }}>
                            <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>
                                Query Parameters (JSON or key=value per line)
                            </label>
                            <textarea
                                style={{ ...styles.input, minHeight: 60, fontFamily: 'monospace', fontSize: 12 }}
                                placeholder={'{"limit": "10", "offset": "0"}'}
                                value={requestQueryParams}
                                onChange={(e) => setRequestQueryParams(e.target.value)}
                            />
                        </div>

                        {/* Headers */}
                        <div style={{ marginBottom: 12 }}>
                            <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>
                                Custom Headers (JSON or Key: Value per line)
                            </label>
                            <textarea
                                style={{ ...styles.input, minHeight: 60, fontFamily: 'monospace', fontSize: 12 }}
                                placeholder={'{"X-Custom-Header": "value"}'}
                                value={requestHeaders}
                                onChange={(e) => setRequestHeaders(e.target.value)}
                            />
                        </div>

                        {/* Body */}
                        {requestMethod !== 'GET' && (
                            <div style={{ marginBottom: 12 }}>
                                <label style={{ display: 'block', marginBottom: 4, fontSize: 13, fontWeight: 500 }}>
                                    Request Body (JSON)
                                </label>
                                <textarea
                                    style={{ ...styles.input, minHeight: 120, fontFamily: 'monospace', fontSize: 12 }}
                                    placeholder={'{\n  "key": "value"\n}'}
                                    value={requestBody}
                                    onChange={(e) => setRequestBody(e.target.value)}
                                />
                            </div>
                        )}

                        {/* Send Button */}
                        <button
                            style={{ ...styles.btn('primary'), width: '100%' }}
                            onClick={handleExecuteRequest}
                            disabled={loading || !selectedPartner}
                        >
                            {loading ? 'Sending...' : 'Send Request'}
                        </button>
                    </div>

                    {/* Templates */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>Request Templates</div>
                        <p style={{ color: '#6b7280', fontSize: 13, marginBottom: 12 }}>
                            Click a template to pre-fill the request form.
                        </p>

                        <div style={{ maxHeight: 500, overflow: 'auto' }}>
                            {categories.map(category => (
                                <div key={category} style={{ marginBottom: 16 }}>
                                    <div style={{
                                        fontWeight: 600,
                                        fontSize: 13,
                                        textTransform: 'uppercase',
                                        color: '#6b7280',
                                        marginBottom: 8,
                                        borderBottom: '1px solid #e5e7eb',
                                        paddingBottom: 4,
                                    }}>
                                        {category}
                                    </div>
                                    <div style={{ display: 'grid', gap: 8 }}>
                                        {requestTemplates.filter(t => t.category === category).map(template => (
                                            <div
                                                key={template.id}
                                                style={{
                                                    padding: 10,
                                                    border: '1px solid #e5e7eb',
                                                    borderRadius: 6,
                                                    cursor: 'pointer',
                                                    background: '#fff',
                                                    transition: 'background 0.15s',
                                                }}
                                                onClick={() => applyTemplate(template)}
                                                onMouseOver={(e) => (e.currentTarget.style.background = '#f9fafb')}
                                                onMouseOut={(e) => (e.currentTarget.style.background = '#fff')}
                                            >
                                                <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4 }}>
                                                    <span style={{
                                                        padding: '2px 6px',
                                                        borderRadius: 4,
                                                        fontSize: 11,
                                                        fontWeight: 600,
                                                        background: template.method === 'GET' ? '#dbeafe' :
                                                            template.method === 'POST' ? '#dcfce7' :
                                                                template.method === 'PUT' ? '#fef3c7' :
                                                                    template.method === 'PATCH' ? '#fae8ff' : '#fee2e2',
                                                        color: template.method === 'GET' ? '#1e40af' :
                                                            template.method === 'POST' ? '#166534' :
                                                                template.method === 'PUT' ? '#92400e' :
                                                                    template.method === 'PATCH' ? '#86198f' : '#991b1b',
                                                    }}>
                                                        {template.method}
                                                    </span>
                                                    <span style={{ fontWeight: 500, fontSize: 14 }}>{template.name}</span>
                                                </div>
                                                <div style={{ fontSize: 12, color: '#6b7280' }}>
                                                    {template.description}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                {/* Response */}
                {requestResponse && (
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>
                            <span>Response</span>
                            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                                <StatusBadge status={requestResponse.ok ? 'PASSED' : 'FAILED'} />
                                <span style={{ fontSize: 13, color: '#6b7280' }}>
                                    {requestResponse.latencyMs}ms
                                </span>
                            </div>
                        </div>

                        {requestResponse.error ? (
                            <div style={styles.alert('error')}>
                                <strong>Error:</strong> {requestResponse.error}
                                {requestResponse.errorType && (
                                    <span style={{ marginLeft: 8 }}>({requestResponse.errorType})</span>
                                )}
                            </div>
                        ) : (
                            <div>
                                {/* Status Info */}
                                <div style={{ display: 'flex', gap: 24, marginBottom: 16, flexWrap: 'wrap' }}>
                                    <div>
                                        <div style={{ fontSize: 12, color: '#6b7280' }}>HTTP Status</div>
                                        <div style={{
                                            fontWeight: 600,
                                            color: requestResponse.httpStatus >= 200 && requestResponse.httpStatus < 300 ? '#10b981' : '#ef4444'
                                        }}>
                                            {requestResponse.httpStatus}
                                        </div>
                                    </div>
                                    <div>
                                        <div style={{ fontSize: 12, color: '#6b7280' }}>OCPI Status</div>
                                        <div style={{
                                            fontWeight: 600,
                                            color: isOcpiSuccess(requestResponse.ocpiStatus) ? '#10b981' : '#ef4444'
                                        }}>
                                            {requestResponse.ocpiStatus}
                                            <span style={{ fontWeight: 400, marginLeft: 8, fontSize: 13 }}>
                                                {getOcpiStatusMessage(requestResponse.ocpiStatus)}
                                            </span>
                                        </div>
                                    </div>
                                    {requestResponse.ocpiMessage && (
                                        <div>
                                            <div style={{ fontSize: 12, color: '#6b7280' }}>OCPI Message</div>
                                            <div style={{ fontWeight: 500 }}>{requestResponse.ocpiMessage}</div>
                                        </div>
                                    )}
                                </div>

                                {/* Request Info */}
                                <div style={{ marginBottom: 16 }}>
                                    <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 4 }}>Request</div>
                                    <div style={styles.mono}>
                                        {requestResponse.requestMethod} {requestResponse.requestUrl}
                                    </div>
                                </div>

                                {/* Response Headers */}
                                {requestResponse.headers && Object.keys(requestResponse.headers).length > 0 && (
                                    <details style={{ marginBottom: 16 }}>
                                        <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: 13 }}>
                                            Response Headers ({Object.keys(requestResponse.headers).length})
                                        </summary>
                                        <div style={{
                                            background: '#f9fafb',
                                            padding: 12,
                                            borderRadius: 6,
                                            marginTop: 8,
                                            fontSize: 12,
                                            fontFamily: 'monospace',
                                        }}>
                                            {Object.entries(requestResponse.headers).map(([key, value]) => (
                                                <div key={key}>
                                                    <strong>{key}:</strong> {value}
                                                </div>
                                            ))}
                                        </div>
                                    </details>
                                )}

                                {/* Response Data */}
                                <div>
                                    <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>Response Body</div>
                                    <pre style={{
                                        background: '#1e293b',
                                        color: '#e2e8f0',
                                        padding: 16,
                                        borderRadius: 8,
                                        overflow: 'auto',
                                        maxHeight: 400,
                                        fontSize: 12,
                                        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
                                    }}>
                                        {requestResponse.rawBody ?
                                            (() => {
                                                try {
                                                    return JSON.stringify(JSON.parse(requestResponse.rawBody), null, 2);
                                                } catch {
                                                    return requestResponse.rawBody;
                                                }
                                            })()
                                            : 'No response body'
                                        }
                                    </pre>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>
        );
    };

    // =========================================================================
    // Main Render
    // =========================================================================

    return (
        <div style={styles.container}>
            <div style={styles.header}>
                <div>
                    <h2 style={styles.title}>OCPI Multi-Partner Testing</h2>
                    <p style={styles.subtitle}>
                        Test OCPI 2.2.1 interoperability with roaming partners (EVRoaming compliant)
                    </p>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                    <button style={styles.btn('secondary')} onClick={() => {
                        loadDashboard();
                        loadPartners();
                        loadScenarios();
                        loadResults();
                    }}>
                        Refresh All
                    </button>
                </div>
            </div>

            {/* Global Environment Connection Panel */}
            <ConnectionPanel
                config={globalConfig}
                onEnvironmentChange={(envId: GlobalEnvId) => setGlobalEnvironment(envId)}
                onConfigUpdate={updateGlobalConfig}
                onOpenConfig={() => setShowEnvConfigModal(true)}
            />

            {/* Environment Config Modal */}
            {showEnvConfigModal && activeGlobalEnv && (
                <EnvironmentConfigModal
                    environment={activeGlobalEnv}
                    onSave={(env) => {
                        updateGlobalEnvironment(env);
                        setShowEnvConfigModal(false);
                    }}
                    onClose={() => setShowEnvConfigModal(false)}
                />
            )}

            {/* Error Alert */}
            {error && (
                <div style={styles.alert('error')}>
                    <strong>Error:</strong> {error}
                    <button
                        style={{ marginLeft: 12, background: 'transparent', border: 'none', cursor: 'pointer' }}
                        onClick={() => setError(null)}
                    >
                        X
                    </button>
                </div>
            )}

            {/* Tabs */}
            <div style={styles.tabs}>
                {([
                    { key: 'dashboard', label: 'Dashboard' },
                    { key: 'partners', label: 'Partners' },
                    { key: 'scenarios', label: 'Test Scenarios' },
                    { key: 'results', label: 'Results History' },
                    { key: 'quick-tests', label: 'Quick Tests' },
                    { key: 'request-builder', label: 'Request Builder' },
                    { key: 'badge-tests', label: 'Badge Tests' },
                ] as { key: TabKey; label: string }[]).map((tab) => (
                    <button
                        key={tab.key}
                        style={styles.tab(activeTab === tab.key)}
                        onClick={() => setActiveTab(tab.key)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Content */}
            {activeTab === 'dashboard' && renderDashboard()}
            {activeTab === 'partners' && renderPartners()}
            {activeTab === 'scenarios' && renderScenarios()}
            {activeTab === 'results' && renderResults()}
            {activeTab === 'quick-tests' && renderQuickTests()}
            {activeTab === 'request-builder' && renderRequestBuilder()}
            {activeTab === 'badge-tests' && renderBadgeTests()}
        </div>
    );
}
