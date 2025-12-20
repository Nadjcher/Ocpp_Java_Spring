/**
 * OCPI API Service
 * Client pour les tests multi-partenaires OCPI 2.2.1
 *
 * Basé sur les spécifications EVRoaming Foundation:
 * - https://evroaming.org/ocpi/
 * - OCPI 2.2.1-d2 specification
 */

import { API_BASE } from '@/lib/apiBase';

// ============================================================================
// Types OCPI
// ============================================================================

export type OCPIRole = 'CPO' | 'MSP' | 'HUB' | 'NAP' | 'NSP' | 'SCSP';
export type OCPIVersion = 'V2_1_1' | 'V2_2_1';
export type OCPIModule = 'credentials' | 'locations' | 'sessions' | 'cdrs' | 'tariffs' | 'tokens' | 'commands' | 'chargingprofiles' | 'hubclientinfo';

export interface EnvironmentConfig {
    baseUrl: string;
    versionsUrl?: string;
    tokenA?: string;    // Token recu du partenaire
    tokenB?: string;    // Token envoye au partenaire
    tokenC?: string;    // Token pour tests
    cognito?: {
        tokenUrl: string;
        clientId: string;
        clientSecret: string;
    };
}

export interface BusinessDetails {
    name: string;
    website?: string;
    logo?: string;
}

export interface Partner {
    id: string;
    name: string;
    countryCode: string;
    partyId: string;
    role: OCPIRole;
    version: OCPIVersion;
    activeEnvironment: string;
    environments: Record<string, EnvironmentConfig>;
    versionsUrl?: string;
    endpoints: Record<string, string>;
    modules: OCPIModule[];
    businessDetails?: BusinessDetails;
    active: boolean;
    lastSync?: string;
    credentialsExpiry?: string;
}

export interface TestStep {
    id: string;
    name: string;
    description?: string;
    type: 'HTTP_REQUEST' | 'VERSION_DISCOVERY' | 'CREDENTIALS_HANDSHAKE' | 'WAIT' | 'WEBHOOK_WAIT' | 'EVSE_SIMULATE';
    module?: OCPIModule;
    method?: string;
    endpoint?: string;
    timeoutMs?: number;
    assertions?: TestAssertion[];
}

export interface TestAssertion {
    name: string;
    type: 'HTTP_STATUS' | 'OCPI_STATUS' | 'JSON_PATH' | 'HEADER' | 'LATENCY' | 'SCHEMA';
    path?: string;
    expected?: any;
    operator?: string;
    critical: boolean;
}

export interface TestScenario {
    id: string;
    name: string;
    description?: string;
    category: string;
    tags?: string[];
    requiredModules?: OCPIModule[];
    steps: TestStep[];
    stopOnFailure?: boolean;
}

export type TestStatus = 'PENDING' | 'RUNNING' | 'PASSED' | 'FAILED' | 'SKIPPED' | 'ERROR';

export interface AssertionResult {
    name: string;
    passed: boolean;
    expected?: string;
    actual?: string;
    message?: string;
    critical: boolean;
}

export interface StepResult {
    stepId: string;
    stepName: string;
    status: TestStatus;
    startTime: string;
    endTime?: string;
    durationMs: number;
    request?: {
        method: string;
        url: string;
        headers: Record<string, string>;
        body?: string;
    };
    response?: {
        httpStatus: number;
        ocpiStatus: number;
        ocpiMessage?: string;
        headers: Record<string, string>;
        body?: string;
        latencyMs: number;
    };
    assertionResults?: AssertionResult[];
    extractedValues?: Record<string, any>;
    errorMessage?: string;
}

export interface TestSummary {
    totalSteps: number;
    passedSteps: number;
    failedSteps: number;
    skippedSteps: number;
    totalAssertions: number;
    passedAssertions: number;
    failedAssertions: number;
    warningAssertions: number;
    successRate: number;
    avgLatencyMs: number;
    maxLatencyMs: number;
    minLatencyMs: number;
}

export interface TestResult {
    id: string;
    scenarioId: string;
    scenarioName: string;
    partnerId: string;
    partnerName: string;
    environment: string;
    startTime: string;
    endTime?: string;
    durationMs: number;
    status: TestStatus;
    stepResults: StepResult[];
    summary: TestSummary;
}

export interface DashboardData {
    partners: {
        total: number;
        active: number;
    };
    scenarios: {
        total: number;
    };
    tests: {
        total: number;
        passed: number;
        failed: number;
        successRate: number;
    };
    partnerStats: Record<string, {
        name: string;
        active: boolean;
        totalTests: number;
        passed: number;
        failed: number;
        lastSync: string;
    }>;
    activeTests: number;
}

// ============================================================================
// API Client
// ============================================================================

const OCPI_API = `${API_BASE}/api/ocpi`;

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const url = `${OCPI_API}${path}`;
    try {
        const response = await fetch(url, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...options.headers,
            },
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP ${response.status}: ${errorText}`);
        }

        return response.json();
    } catch (error) {
        console.error(`OCPI API request failed: ${url}`, error);
        throw error;
    }
}

// ============================================================================
// Partners API
// ============================================================================

export const partnersApi = {
    getAll: () => request<Partner[]>('/partners'),

    getActive: () => request<Partner[]>('/partners/active'),

    getById: (id: string) => request<Partner>(`/partners/${encodeURIComponent(id)}`),

    create: (partner: Partial<Partner>) =>
        request<Partner>('/partners', {
            method: 'POST',
            body: JSON.stringify(partner),
        }),

    update: (id: string, partner: Partial<Partner>) =>
        request<Partner>(`/partners/${encodeURIComponent(id)}`, {
            method: 'PUT',
            body: JSON.stringify(partner),
        }),

    delete: (id: string) =>
        request<void>(`/partners/${encodeURIComponent(id)}`, { method: 'DELETE' }),

    switchEnvironment: (id: string, environment: string) =>
        request<Partner>(`/partners/${encodeURIComponent(id)}/switch-environment?environment=${encodeURIComponent(environment)}`, {
            method: 'POST',
        }),

    validate: (id: string) =>
        request<{ valid: boolean; errors: string[] }>(`/partners/${encodeURIComponent(id)}/validate`),

    reload: () =>
        request<{ ok: boolean; count: number }>('/partners/reload', { method: 'POST' }),

    discover: (id: string) =>
        request<{ ok: boolean; versions?: any[]; error?: string; latencyMs?: number }>(`/partners/${encodeURIComponent(id)}/discover`, {
            method: 'POST',
        }),

    discoverFull: (id: string) =>
        request<{ ok: boolean; version?: string; endpoints?: Record<string, string>; error?: string }>(`/partners/${encodeURIComponent(id)}/discover-full`, {
            method: 'POST',
        }),
};

// ============================================================================
// Scenarios API
// ============================================================================

export const scenariosApi = {
    getAll: () => request<TestScenario[]>('/scenarios'),

    getByCategory: (category: string) =>
        request<TestScenario[]>(`/scenarios/category/${encodeURIComponent(category)}`),

    getById: (id: string) =>
        request<TestScenario>(`/scenarios/${encodeURIComponent(id)}`),

    create: (scenario: Partial<TestScenario>) =>
        request<TestScenario>('/scenarios', {
            method: 'POST',
            body: JSON.stringify(scenario),
        }),

    update: (id: string, scenario: Partial<TestScenario>) =>
        request<TestScenario>(`/scenarios/${encodeURIComponent(id)}`, {
            method: 'PUT',
            body: JSON.stringify(scenario),
        }),

    delete: (id: string) =>
        request<void>(`/scenarios/${encodeURIComponent(id)}`, { method: 'DELETE' }),

    reload: () =>
        request<{ ok: boolean; count: number }>('/scenarios/reload', { method: 'POST' }),
};

// ============================================================================
// Test Execution API
// ============================================================================

export const testsApi = {
    run: (scenarioId: string, partnerId: string) =>
        request<TestResult>(`/test/run?scenarioId=${encodeURIComponent(scenarioId)}&partnerId=${encodeURIComponent(partnerId)}`, {
            method: 'POST',
        }),

    runAsync: (scenarioId: string, partnerId: string) =>
        request<{ ok: boolean; message: string }>(`/test/run-async?scenarioId=${encodeURIComponent(scenarioId)}&partnerId=${encodeURIComponent(partnerId)}`, {
            method: 'POST',
        }),

    runAll: (partnerId: string) =>
        request<{ ok: boolean; total: number; passed: number; failed: number; results: TestResult[] }>(`/test/run-all?partnerId=${encodeURIComponent(partnerId)}`, {
            method: 'POST',
        }),

    getActive: () => request<TestResult[]>('/test/active'),
};

// ============================================================================
// Results API
// ============================================================================

export const resultsApi = {
    getAll: () => request<TestResult[]>('/results'),

    getRecent: (limit = 20) =>
        request<TestResult[]>(`/results/recent?limit=${limit}`),

    getById: (id: string) =>
        request<TestResult>(`/results/${encodeURIComponent(id)}`),

    getForPartner: (partnerId: string) =>
        request<TestResult[]>(`/results/partner/${encodeURIComponent(partnerId)}`),

    getForScenario: (scenarioId: string) =>
        request<TestResult[]>(`/results/scenario/${encodeURIComponent(scenarioId)}`),

    delete: (id: string) =>
        request<void>(`/results/${encodeURIComponent(id)}`, { method: 'DELETE' }),

    cleanup: (keepCount = 10) =>
        request<{ ok: boolean }>(`/results/cleanup?keepCount=${keepCount}`, { method: 'POST' }),
};

// ============================================================================
// Quick Tests API
// ============================================================================

export const quickTestsApi = {
    locations: (partnerId: string) =>
        request<{ ok: boolean; statusCode?: number; data?: any[]; latencyMs?: number; error?: string }>(`/quick-test/locations?partnerId=${encodeURIComponent(partnerId)}`, {
            method: 'POST',
        }),

    sessions: (partnerId: string) =>
        request<{ ok: boolean; statusCode?: number; data?: any[]; latencyMs?: number; error?: string }>(`/quick-test/sessions?partnerId=${encodeURIComponent(partnerId)}`, {
            method: 'POST',
        }),

    cdrs: (partnerId: string) =>
        request<{ ok: boolean; statusCode?: number; data?: any[]; latencyMs?: number; error?: string }>(`/quick-test/cdrs?partnerId=${encodeURIComponent(partnerId)}`, {
            method: 'POST',
        }),

    tokens: (partnerId: string) =>
        request<{ ok: boolean; statusCode?: number; data?: any[]; latencyMs?: number; error?: string }>(`/quick-test/tokens?partnerId=${encodeURIComponent(partnerId)}`, {
            method: 'POST',
        }),

    tariffs: (partnerId: string) =>
        request<{ ok: boolean; statusCode?: number; data?: any[]; latencyMs?: number; error?: string }>(`/quick-test/tariffs?partnerId=${encodeURIComponent(partnerId)}`, {
            method: 'POST',
        }),
};

// ============================================================================
// Dashboard API
// ============================================================================

export const dashboardApi = {
    get: () => request<DashboardData>('/dashboard'),
};

// ============================================================================
// Request Builder API (Postman-like)
// ============================================================================

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface RequestConfig {
    partnerId?: string;
    method: HttpMethod;
    url?: string;
    endpoint?: string;
    module?: OCPIModule;
    headers?: Record<string, string>;
    queryParams?: Record<string, string>;
    body?: any;
}

export interface RequestTemplate {
    id: string;
    name: string;
    description: string;
    method: HttpMethod;
    module?: string;
    endpoint?: string;
    pathTemplate?: string;
    queryParams?: Record<string, string>;
    body?: any;
    category: string;
}

export interface RawRequestResponse {
    ok: boolean;
    httpStatus: number;
    ocpiStatus: number;
    ocpiMessage: string;
    headers: Record<string, string>;
    data: any;
    rawBody: string;
    latencyMs: number;
    requestUrl: string;
    requestMethod: string;
    error?: string;
    errorType?: string;
}

export const requestBuilderApi = {
    /**
     * Execute a custom OCPI request
     */
    execute: (config: RequestConfig) =>
        request<RawRequestResponse>('/request', {
            method: 'POST',
            body: JSON.stringify(config),
        }),

    /**
     * Get predefined request templates
     */
    getTemplates: () =>
        request<RequestTemplate[]>('/request/templates'),

    /**
     * Get request history (not persistent yet)
     */
    getHistory: (partnerId?: string, limit = 20) => {
        const params = new URLSearchParams();
        if (partnerId) params.append('partnerId', partnerId);
        params.append('limit', String(limit));
        return request<any[]>(`/request/history?${params.toString()}`);
    },
};

// ============================================================================
// OCPI Status Codes (per specification)
// ============================================================================

export const OCPI_STATUS_CODES = {
    // Success
    1000: 'Generic success',

    // Client errors (2xxx)
    2000: 'Generic client error',
    2001: 'Invalid or missing parameters',
    2002: 'Not enough information',
    2003: 'Unknown location',

    // Server errors (3xxx)
    3000: 'Generic server error',
    3001: 'Unable to use client API',
    3002: 'Unsupported version',
    3003: 'No matching endpoints',

    // Hub errors (4xxx)
    4000: 'Generic hub error',
    4001: 'Unknown receiver',
    4002: 'Timeout on forwarded request',
    4003: 'Connection problem',
} as const;

export function getOcpiStatusMessage(code: number): string {
    return OCPI_STATUS_CODES[code as keyof typeof OCPI_STATUS_CODES] || `Unknown status code: ${code}`;
}

export function isOcpiSuccess(code: number): boolean {
    return code >= 1000 && code < 2000;
}

// ============================================================================
// Badge Test API (OCPP automated charge tests)
// ============================================================================

const BADGE_API = `${API_BASE}/api/badge-test`;

export interface BadgeTestRequest {
    ocppId: string;
    emsp: string;
    tag: string;
    durationMinutes?: number;
    csmsUrl?: string;
    testType?: 'transaction' | 'authorize' | 'locations' | 'sessions' | 'cdrs' | 'tokens' | 'tariffs' | 'commands';
    partnerId?: string;
    ocpiVersion?: '2.1.1' | '2.2.1';
}

export interface BadgeTestStatus {
    ocppId: string;
    emsp: string;
    tag: string;
    testType?: string;
    ocpiVersion?: string;
    status: 'pending' | 'connecting' | 'authorizing' | 'charging' | 'stopping' | 'completed' | 'failed' | 'testing';
    message?: string;
    startTime?: string;
    endTime?: string;
    success: boolean;
    energyWh: number;
    steps: string[];
}

export interface BadgeBatchResult {
    total: number;
    passed: number;
    failed: number;
    results: BadgeTestStatus[];
}

export const badgeTestApi = {
    /**
     * Run a single badge/EMSP test
     */
    run: async (request: BadgeTestRequest): Promise<BadgeTestStatus> => {
        const response = await fetch(`${BADGE_API}/run`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request),
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        }
        return response.json();
    },

    /**
     * Run multiple badge tests in batch
     */
    runBatch: async (requests: BadgeTestRequest[]): Promise<BadgeBatchResult> => {
        const response = await fetch(`${BADGE_API}/run-batch`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requests),
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        }
        return response.json();
    },

    /**
     * Get status of a specific test
     */
    getStatus: async (ocppId: string, emsp: string): Promise<BadgeTestStatus | null> => {
        const response = await fetch(`${BADGE_API}/status/${encodeURIComponent(ocppId)}/${encodeURIComponent(emsp)}`);
        if (response.status === 404) return null;
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        }
        return response.json();
    },

    /**
     * Get all test statuses
     */
    getAllStatuses: async (): Promise<BadgeTestStatus[]> => {
        const response = await fetch(`${BADGE_API}/status`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        }
        return response.json();
    },

    /**
     * Clear all test results
     */
    clear: async (): Promise<{ cleared: number }> => {
        const response = await fetch(`${BADGE_API}/clear`, { method: 'DELETE' });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        }
        return response.json();
    },
};
