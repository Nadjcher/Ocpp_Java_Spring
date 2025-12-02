// frontend/src/services/api.ts
// Client API typé pour parler au runner HTTP (port 8877 par défaut)

import type {
    SessionState,
    PerformanceMetrics,
    TNRScenario,
    OCPPMessage,
    ChargingProfile
} from '@/types';

// =============================================================================
// API TYPES
// =============================================================================

/** Réponse générique avec status */
export interface ApiResponse<T = unknown> {
    ok: boolean;
    data?: T;
    error?: string;
}

/** Statut du runner de performance */
export interface PerfStatus {
    running: boolean;
    sessionsActive: number;
    sessionsTotal: number;
    messagesProcessed: number;
    errors: number;
    startTime?: string;
}

/** Configuration de test de performance */
export interface PerfConfig {
    sessions: number;
    duration: number;
    rampUp?: number;
    targetMPS?: number;
}

/** Statistiques de performance */
export interface PerfStats {
    activeSessions: number;
    totalMessages: number;
    messagesPerSecond: number;
    errors: number;
    avgLatencyMs: number;
    p95LatencyMs?: number;
    p99LatencyMs?: number;
}

/** Métriques du serveur */
export interface ServerMetrics {
    cpu: number;
    memory: number;
    connections: number;
    uptime: number;
}

/** Résultat TNR */
export interface TNRResult {
    id: string;
    scenarioId: string;
    status: 'success' | 'failed' | 'running' | 'pending';
    startTime: string;
    endTime?: string;
    errors: string[];
    metrics?: {
        messagesMatched: number;
        messagesMissed: number;
        avgLatency: number;
    };
}

/** Exécution TNR */
export interface TNRExecution {
    id: string;
    scenarioId: string;
    scenarioName: string;
    status: 'success' | 'failed' | 'running';
    startTime: string;
    endTime?: string;
}

/** Session simulée */
export interface SimuSession {
    id: string;
    cpId: string;
    wsUrl: string;
    connected: boolean;
    status: string;
    txId?: number;
    idTag?: string;
    meterWh: number;
    soc: number;
    createdAt: string;
}

/** Paramètres de création de session */
export interface CreateSimuParams {
    url: string;
    cpId: string;
    idTag?: string;
    auto?: boolean;
    holdSec?: number;
    mvEverySec?: number;
}

/** Session UI */
export interface UISession {
    cpId: string;
    idTag?: string;
    status?: string;
    txId?: number | null;
    lastError?: string | null;
}

// =============================================================================
// CONFIG
// =============================================================================

interface ImportMeta {
    env?: {
        VITE_PERF_API?: string;
        DEV?: boolean;
    };
}

const OVERRIDE_ORIGIN =
    (import.meta as unknown as ImportMeta).env?.VITE_PERF_API?.replace(/\/$/, "") ||
    window.localStorage.getItem("perfApi")?.replace(/\/$/, "") ||
    "";

const IS_DEV = Boolean((import.meta as unknown as ImportMeta).env?.DEV);
const USE_PROXY = !OVERRIDE_ORIGIN && IS_DEV;

// Origin utilisée par fetch. Chaîne vide = même origine (Vite servira et proxifiera).
export const API_BASE =
    OVERRIDE_ORIGIN || (IS_DEV ? "" : `${location.protocol}//${location.hostname}:8877`);

export const RUNNER = API_BASE;

// En mode proxy, on s'assure que *tous* les chemins passent par /api
function withBase(path: string): string {
    if (USE_PROXY) {
        return path.startsWith("/api") ? path : `/api${path}`;
    }
    return path;
}

// =============================================================================
// HTTP CLIENT
// =============================================================================

type HttpMethod = "GET" | "POST" | "DELETE" | "PUT" | "PATCH";

interface HttpOptions {
    method?: HttpMethod;
    body?: unknown;
    asText?: boolean;
    headers?: Record<string, string>;
}

async function http<T>(
    path: string,
    options: HttpOptions = {}
): Promise<T> {
    const { method = "GET", body, asText = false, headers: customHeaders = {} } = options;
    const url = `${API_BASE}${withBase(path)}`;
    const headers: Record<string, string> = { ...customHeaders };
    let payload: BodyInit | undefined;

    if (body instanceof FormData) {
        payload = body; // multipart
    } else if (body !== undefined) {
        headers["Content-Type"] = "application/json";
        payload = JSON.stringify(body);
    }

    const res = await fetch(url, { method, headers, body: payload });
    const text = await res.text(); // on lit une fois

    if (!res.ok) {
        // essaie d'extraire un message JSON sinon renvoie le texte
        try {
            const j = JSON.parse(text) as { error?: string };
            throw new Error(j?.error || res.statusText);
        } catch {
            throw new Error(text || res.statusText);
        }
    }

    if (asText) return text as unknown as T;
    if (!text) return {} as T;

    try {
        return JSON.parse(text) as T;
    } catch {
        // pas du JSON => renvoie brut
        return text as unknown as T;
    }
}

// Helpers pour les méthodes courantes
const get = <T>(path: string) => http<T>(path, { method: "GET" });
const post = <T>(path: string, body?: unknown) => http<T>(path, { method: "POST", body });
const del = <T>(path: string) => http<T>(path, { method: "DELETE" });

// =============================================================================
// PERF (runner)
// =============================================================================

export const perf = {
    status: () => get<PerfStatus>("/api/perf/status"),
    run: () => get<ApiResponse>("/api/perf/run"),
    start: (cfg: PerfConfig) => post<ApiResponse>("/api/perf/start", cfg),
    stop: () => post<ApiResponse>("/api/perf/stop", {}),
    importCsv: (csvText: string) => post<ApiResponse>("/api/perf/import", { csv: csvText }),
    csvTemplate: () => http<string>("/api/perf/csv-template", { asText: true }),
    stats: () => get<PerfStats>("/stats"),
    metrics: () => get<ServerMetrics>("/api/metrics"),
    logs: () => http<string>("/logs", { asText: true }),
};

// =============================================================================
// TNR (Test Non-Régression)
// =============================================================================

export const tnr = {
    list: () => get<TNRScenario[]>("/api/tnr"),
    get: (id: string) => get<TNRScenario>(`/api/tnr/${id}`),
    record: (scenario: Partial<TNRScenario>) =>
        post<{ ok: boolean; id: string }>("/api/tnr/record", scenario),
    del: (id: string) => del<ApiResponse>(`/api/tnr/${id}`),
    remove: (id: string) => del<ApiResponse>(`/api/tnr/${id}`), // alias pour compatibilité
    replay: (id: string) =>
        post<{ ok: boolean; resultId: string }>(`/api/tnr/replay/${id}`),
    result: (id: string) => get<TNRResult>(`/api/tnr/result/${id}`),
    results: () => get<TNRResult[]>("/api/tnr/results"),

    // Recorder
    recorderStart: (name?: string) =>
        post<ApiResponse>("/api/tnr/recorder/start", name ? { name } : {}),
    recorderStop: (id?: string) =>
        post<ApiResponse>("/api/tnr/recorder/stop", id ? { id } : {}),
    cancelRecording: () =>
        post<ApiResponse>("/api/tnr/recorder/cancel"),

    // Méthodes additionnelles pour TNRPanel
    executions: () => get<TNRExecution[]>("/api/tnr/executions"),
    scenarios: () => get<TNRScenario[]>("/api/tnr/scenarios"),
    exportScenario: (id: string) => get<TNRScenario>(`/api/tnr/${id}`),
    importScenario: (formData: FormData) =>
        fetch(`${RUNNER}/api/tnr/import`, {
            method: "POST",
            body: formData,
        }).then(res => {
            if (!res.ok) throw new Error(`Import failed: ${res.status}`);
            return res.json() as Promise<ApiResponse>;
        }),
};

// =============================================================================
// SIMU (Node)
// =============================================================================

export const simu = {
    list: () => get<SimuSession[]>("/api/simu"),
    create: (params: CreateSimuParams) =>
        post<SimuSession>("/api/simu/session", params),
    del: (id: string) =>
        del<ApiResponse>(`/api/simu/${encodeURIComponent(id)}`),

    authorize: (id: string, idTag?: string) =>
        post<{ status: string }>(`/api/simu/${encodeURIComponent(id)}/authorize`, { idTag }),
    startTx: (id: string) =>
        post<{ transactionId: number }>(`/api/simu/${encodeURIComponent(id)}/startTx`, {}),
    stopTx: (id: string) =>
        post<ApiResponse>(`/api/simu/${encodeURIComponent(id)}/stopTx`, {}),
    mvStart: (id: string, periodSec: number) =>
        post<ApiResponse>(`/api/simu/${encodeURIComponent(id)}/mv/start`, { periodSec }),
    mvStop: (id: string) =>
        post<ApiResponse>(`/api/simu/${encodeURIComponent(id)}/mv/stop`, {}),
    ocpp: <T = unknown>(id: string, action: string, payload: Record<string, unknown> = {}) =>
        post<T>(`/api/simu/${encodeURIComponent(id)}/ocpp`, { action, payload }),
};

// =============================================================================
// UI
// =============================================================================

export const ui = {
    pushSessions: (rows: UISession[]) =>
        post<ApiResponse>("/api/ui/sessions", { list: rows }),
    getSessionsMirror: () => get<UISession[]>("/api/ui/sessions"),
    mergedSessions: () => get<SessionState[]>("/api/sessions"),
};

// =============================================================================
// SESSIONS (compatible avec stores)
// =============================================================================

export const api = {
    // Sessions
    getSessions: () => get<SessionState[]>("/api/sessions"),
    createSession: (title: string) => post<SessionState>("/api/sessions", { title }),
    updateSession: (id: string, updates: Partial<SessionState>) =>
        http<SessionState>(`/api/sessions/${id}`, { method: "PUT", body: updates }),
    deleteSession: (id: string) => del<ApiResponse>(`/api/sessions/${id}`),

    // Performance
    getPerformanceMetrics: () => get<PerformanceMetrics>("/api/metrics"),
    startPerformanceTest: (sessions: number, duration: number) =>
        post<ApiResponse>("/api/perf/start", { sessions, duration }),
    stopPerformanceTest: () => post<ApiResponse>("/api/perf/stop"),

    // Charging Profiles
    getChargingProfiles: () => get<ChargingProfile[]>("/api/charging-profiles"),
    saveChargingProfile: (profile: Partial<ChargingProfile>) =>
        post<ChargingProfile>("/api/charging-profiles", profile),
    applyChargingProfile: (params: {
        sessionId: string;
        connectorId: number;
        profileId: number;
        stackLevel: number;
        purpose: string;
        kind: string;
        unit: string;
        periods: Array<{ startPeriod: number; limit: number; numberPhases?: number }>;
    }) => post<ApiResponse>("/api/charging-profiles/apply", params),
    clearChargingProfile: (params: { sessionId: string; profileId: number; connectorId: number }) =>
        post<ApiResponse>("/api/charging-profiles/clear", params),
    applyCentralProfile: (params: {
        evpId: string;
        bearerToken: string;
        connectorId: number;
        profileId: number;
        stackLevel: number;
        purpose: string;
        kind: string;
        unit: string;
        periods: Array<{ startPeriod: number; limit: number; numberPhases?: number }>;
    }) => post<ApiResponse>("/api/central/charging-profile", params),

    // Session connection
    connectSession: (sessionId: string) => post<ApiResponse>(`/api/sessions/${sessionId}/connect`),
    disconnectSession: (sessionId: string) => post<ApiResponse>(`/api/sessions/${sessionId}/disconnect`),

    // TNR shortcuts
    tnr: () => get<TNRScenario[]>("/api/tnr"),
    record: () => post<{ ok: boolean; id: string }>("/api/tnr/record"),
};

// Export default pour compatibilité
export default api;
