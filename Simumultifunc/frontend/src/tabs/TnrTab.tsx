/* =========================================================
 * TnrTab.tsx – VERSION AMÉLIORÉE avec organisation par dossiers/tags
 * et exécution parallèle de plusieurs scénarios
 * ======================================================= */

import React, { useEffect, useMemo, useRef, useState, useCallback } from "react";
import { RUNNER as API_BASE } from "@/lib/apiBase";

/* ---------------------- Types locaux ---------------------- */
type TnrScenario = {
    id: string;
    name?: string;
    description?: string;
    category?: string;
    folder?: string;
    tags?: string[];
    eventsCount?: number;
    sessionsCount?: number;
    duration?: number;
    status?: string;
    config?: { url?: string; cpId?: string; [k: string]: any };
};

type TnrMode = "fast" | "realtime" | "identical";

type TnrExecMeta = {
    executionId: string;
    scenarioId: string;
    timestamp: string;
    passed?: boolean;
    metrics?: { differences?: number; totalEvents?: number; serverCalls?: number; durationMs?: number; [k: string]: any };
};

type TnrDiffType = "missing" | "extra" | "different" | "error" | "count";
type TnrDiff = { path: string; type: TnrDiffType; expected?: any; actual?: any };

type TnrEvent = {
    index?: number;
    ts?: string;
    direction?: "in" | "out";
    action?: string;
    payload?: any;
    passed?: boolean;
    [k: string]: any;
};

type TnrExecFull = {
    executionId?: string;
    scenarioId: string;
    status: "running" | "success" | "failed" | "error" | string;
    startedAt: string;
    finishedAt?: string;
    metrics?: { differences?: number; totalEvents?: number; serverCalls?: number; durationMs?: number; [k: string]: any };
    differences?: TnrDiff[];
    events?: TnrEvent[];
    logs?: Array<string | { ts?: string; line?: string }>;
    error?: string;
    inputs?: { url?: string; mode?: "fast" | "realtime" | "instant"; speed?: number; [k: string]: any };
};

// Running scenario status for parallel execution
type RunningScenario = {
    scenarioId: string;
    executionId: string;
    status: "running" | "success" | "failed" | "error";
    startedAt: Date;
    logs: string[];
};

/* ---------------------- HTTP helpers avec API_BASE ---------------------- */
async function jget<T>(path: string): Promise<T> {
    const url = `${API_BASE}${path}`;
    try {
        const r = await fetch(url);
        if (!r.ok) throw new Error(await r.text());
        return r.json();
    } catch (e) {
        console.error(`GET ${url} failed:`, e);
        throw e;
    }
}

async function jpost<T = any>(path: string, body?: any): Promise<T> {
    const url = `${API_BASE}${path}`;
    try {
        const r = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: body ? JSON.stringify(body) : "{}",
        });
        if (!r.ok) throw new Error(await r.text());
        return r.json();
    } catch (e) {
        console.error(`POST ${url} failed:`, e);
        throw e;
    }
}

async function jput<T = any>(path: string, body?: any): Promise<T> {
    const url = `${API_BASE}${path}`;
    try {
        const r = await fetch(url, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: body ? JSON.stringify(body) : "{}",
        });
        if (!r.ok) throw new Error(await r.text());
        return r.json();
    } catch (e) {
        console.error(`PUT ${url} failed:`, e);
        throw e;
    }
}

/* ---------------------- UI helpers ---------------------- */
const Mono: React.FC<React.PropsWithChildren> = ({ children }) => (
    <span style={{ fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace" }}>{children}</span>
);

function Section(props: React.PropsWithChildren<{ title?: string; right?: React.ReactNode; style?: React.CSSProperties }>) {
    return (
        <section
            style={{
                background: "#fff",
                border: "1px solid #e5e7eb",
                borderRadius: 8,
                padding: 12,
                ...(props.style || {}),
            }}
        >
            {(props.title || props.right) && (
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                    <strong>{props.title}</strong>
                    <div>{props.right}</div>
                </div>
            )}
            {props.children}
        </section>
    );
}

function kpiColor(status?: string) {
    if (status === "running") return "#2563eb";
    if (status === "success" || status === "PASSED") return "#10b981";
    if (status === "failed" || status === "FAILED") return "#ef4444";
    if (status === "error" || status === "ERROR") return "#f59e0b";
    return "#6b7280";
}

function formatDur(ms?: number) {
    if (!ms || ms < 1) return "–";
    if (ms < 1000) return `${ms}ms`;
    const s = ms / 1000;
    if (s < 60) return `${s.toFixed(2)}s`;
    const m = Math.floor(s / 60);
    const r = (s % 60).toFixed(1);
    return `${m}m${r}s`;
}

function truncate(s?: string, n = 180) {
    if (!s) return "";
    return s.length > n ? s.slice(0, n) + "…" : s;
}

/* ---------------------- Predefined categories/folders ---------------------- */
const CATEGORY_CONFIG: Record<string, { label: string; color: string; icon: string }> = {
    "gpm": { label: "GPM", color: "#8b5cf6", icon: "🔌" },
    "ocpp": { label: "OCPP", color: "#3b82f6", icon: "⚡" },
    "smart-charging": { label: "Smart Charging", color: "#10b981", icon: "🔋" },
    "recorded": { label: "Enregistrés", color: "#f59e0b", icon: "📹" },
    "regression": { label: "Régression", color: "#ef4444", icon: "🔄" },
    "performance": { label: "Performance", color: "#ec4899", icon: "📊" },
    "other": { label: "Autres", color: "#6b7280", icon: "📁" },
};

function getCategoryConfig(category?: string) {
    if (!category) return CATEGORY_CONFIG["other"];
    const key = category.toLowerCase();
    return CATEGORY_CONFIG[key] || CATEGORY_CONFIG["other"];
}

/* ---------------------- Tag colors ---------------------- */
const TAG_COLORS: Record<string, string> = {
    "gpm": "#8b5cf6",
    "ocpp": "#3b82f6",
    "auto-recorded": "#f59e0b",
    "tnr": "#10b981",
    "regression": "#ef4444",
    "performance": "#ec4899",
    "boot": "#06b6d4",
    "authorize": "#14b8a6",
    "transaction": "#f97316",
    "metervalues": "#84cc16",
    "smart-charging": "#6366f1",
};

function getTagColor(tag: string): string {
    const key = tag.toLowerCase();
    return TAG_COLORS[key] || "#6b7280";
}

/* ---------------------- Buckets pour le résumé ---------------------- */
type BucketKey =
    | "config"
    | "session"
    | "callsMissing"
    | "callsExtras"
    | "callsPayload"
    | "meterValues"
    | "tx"
    | "results"
    | "other";

const BUCKET_LABEL: Record<BucketKey, string> = {
    config: "Config",
    session: "Session",
    callsMissing: "Appels manquants",
    callsExtras: "Appels en trop",
    callsPayload: "Payload d'appels",
    meterValues: "MeterValues",
    tx: "TX/TXDP",
    results: "Résultats",
    other: "Autre",
};

function bucketOfPath(p?: string): BucketKey {
    const path = (p || "").toLowerCase();
    if (path.includes("/config")) return "config";
    if (path.includes("/session") || path.includes("/auth")) return "session";
    if (path.includes("missing") && (path.includes("/call") || path.includes("/calls"))) return "callsMissing";
    if (path.includes("extra") && (path.includes("/call") || path.includes("/calls"))) return "callsExtras";
    if (path.includes("/call") || path.includes("/payload")) return "callsPayload";
    if (path.includes("/metervalue")) return "meterValues";
    if (path.includes("/tx") || path.includes("/transaction") || path.includes("txp") || path.includes("txdp")) return "tx";
    if (path.includes("/result") || path.includes("/summary")) return "results";
    return "other";
}

/* ---------------------- Parsing "events" depuis les logs ---------------------- */
function parseEventsFromLogs(raw: Array<string | { ts?: string; line?: string }>): TnrEvent[] {
    const lines = raw.map((l) => (typeof l === "string" ? l : l.line || "")).filter(Boolean);
    const events: TnrEvent[] = [];
    let idx = 0;
    for (const line of lines) {
        const m1 = line.match(/→\s*([A-Za-z]+[A-Za-z0-9]*)/);
        if (m1) {
            events.push({ index: idx++, action: m1[1], direction: "out" });
            continue;
        }
        const m2 = line.match(/\[OK\]\s*([A-Za-z]+[A-Za-z0-9]*)\s+sent/);
        if (m2) {
            events.push({ index: idx++, action: m2[1], direction: "out" });
            continue;
        }
    }
    return events;
}

/* ---------------------- Backoff util ---------------------- */
function nextBackoff(current: number) {
    return Math.min(Math.round(current * 1.8), 10000);
}

/* =========================================================
 *  Composant principal
 * ======================================================= */
export default function TnrTab() {
    /* Enregistrement (gauche) */
    const [defaultUrl, setDefaultUrl] = useState<string>(() => {
        try { return localStorage.getItem("tnr.defaultUrl") || ""; } catch { return ""; }
    });
    const saveDefaultUrl = (u: string) => {
        setDefaultUrl(u);
        try { localStorage.setItem("tnr.defaultUrl", u); } catch {}
    };

    /* Métadonnées pour l'enregistrement */
    const [recordingName, setRecordingName] = useState<string>("");
    const [recordingTags, setRecordingTags] = useState<string>("");
    const [recordingCategory, setRecordingCategory] = useState<string>("recorded");
    const [isRecording, setIsRecording] = useState(false);

    /* Edition de scénario */
    const [editingScenario, setEditingScenario] = useState<TnrScenario | null>(null);
    const [editName, setEditName] = useState("");
    const [editCategory, setEditCategory] = useState("");
    const [editTags, setEditTags] = useState("");
    const [editDescription, setEditDescription] = useState("");

    /* Scénarios & run */
    const [scenarios, setScenarios] = useState<TnrScenario[]>([]);
    const [selScenarioId, setSelScenarioId] = useState<string>("");
    const [mode, setMode] = useState<TnrMode>("fast");
    const [speed, setSpeed] = useState<number>(1);

    /* Multi-select pour exécution parallèle - using object for stable React updates */
    const [selectedScenariosMap, setSelectedScenariosMap] = useState<Record<string, boolean>>({});
    const selectedScenarios = useMemo(() => {
        return new Set(Object.keys(selectedScenariosMap).filter(k => selectedScenariosMap[k]));
    }, [selectedScenariosMap]);
    const [runningScenarios, setRunningScenarios] = useState<Map<string, RunningScenario>>(new Map());

    /* Filtres */
    const [categoryFilter, setCategoryFilter] = useState<string>("");
    const [tagFilter, setTagFilter] = useState<string>("");
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [viewMode, setViewMode] = useState<"list" | "grid" | "tree">("list");

    /* Exécutions */
    const [execs, setExecs] = useState<TnrExecMeta[]>([]);
    const [execId, setExecId] = useState<string>("");
    const [exec, setExec] = useState<TnrExecFull | null>(null);

    /* Tabs centre */
    const [tab, setTab] = useState<"info" | "logs" | "events" | "diffs" | "parallel">("logs");
    const logBoxRef = useRef<HTMLDivElement>(null);
    const [autoScroll, setAutoScroll] = useState(true);

    /* Runner status (pour bannière) */
    const [runnerStatus, setRunnerStatus] = useState<"ok" | "reconnecting">("ok");

    /* Filtres diffs */
    const [q, setQ] = useState("");
    const [typeFilter, setTypeFilter] = useState<"" | TnrDiffType>("");
    const [bucketFilter, setBucketFilter] = useState<"" | BucketKey>("");

    /* Expanded folders */
    const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set(["recorded", "gpm", "ocpp"]));

    /* ---------- Chargements init ---------- */
    useEffect(() => {
        loadScenarios();
        loadExecutions();
    }, []);

    const loadScenarios = async () => {
        try {
            const data = await jget<any[]>("/api/tnr/list");
            setScenarios(data.map((x) => (typeof x === "string" ? { id: x } : x)));
        } catch {
            try {
                const ids = await jget<string[]>("/api/tnr");
                setScenarios(ids.map((id) => ({ id })));
            } catch (e) {
                console.error("Failed to load scenarios:", e);
            }
        }
    };

    const loadExecutions = async () => {
        try {
            const list = await jget<TnrExecMeta[]>("/api/tnr/executions");
            setExecs(list);
        } catch (e) {
            console.error("Failed to load executions:", e);
        }
    };

    /* ---------- Extract all unique tags and categories ---------- */
    const allTags = useMemo(() => {
        const tags = new Set<string>();
        scenarios.forEach(s => {
            (s.tags || []).forEach(t => tags.add(t));
        });
        return Array.from(tags).sort();
    }, [scenarios]);

    const allCategories = useMemo(() => {
        const cats = new Set<string>();
        scenarios.forEach(s => {
            cats.add(s.category || s.folder || "other");
        });
        return Array.from(cats).sort();
    }, [scenarios]);

    /* ---------- Group scenarios by category ---------- */
    const scenariosByCategory = useMemo(() => {
        const groups = new Map<string, TnrScenario[]>();

        scenarios.forEach(s => {
            const cat = s.category || s.folder || "other";
            if (!groups.has(cat)) {
                groups.set(cat, []);
            }
            groups.get(cat)!.push(s);
        });

        return groups;
    }, [scenarios]);

    /* ---------- Filter scenarios ---------- */
    const filteredScenarios = useMemo(() => {
        return scenarios.filter(s => {
            // Category filter
            if (categoryFilter && (s.category || s.folder || "other") !== categoryFilter) {
                return false;
            }
            // Tag filter
            if (tagFilter && !(s.tags || []).includes(tagFilter)) {
                return false;
            }
            // Search query
            if (searchQuery) {
                const query = searchQuery.toLowerCase();
                const searchable = `${s.id} ${s.name || ""} ${s.description || ""} ${(s.tags || []).join(" ")}`.toLowerCase();
                if (!searchable.includes(query)) {
                    return false;
                }
            }
            return true;
        });
    }, [scenarios, categoryFilter, tagFilter, searchQuery]);

    /* ---------- Suivi exécution ---------- */
    useEffect(() => {
        if (!execId) return;
        let stopped = false;
        let timer: any = null;
        let delay = 1000;

        const tick = () => {
            jget<TnrExecFull>(`/api/tnr/executions/${encodeURIComponent(execId)}`)
                .then((d) => {
                    if (stopped) return;
                    setExec(d);
                    delay = d.status === "running" ? 1000 : 3000;
                    timer = setTimeout(tick, delay);
                })
                .catch(() => {
                    if (stopped) return;
                    timer = setTimeout(tick, nextBackoff(delay));
                });
        };
        tick();
        return () => { stopped = true; if (timer) clearTimeout(timer); };
    }, [execId]);

    /* ---------- Logs polling ---------- */
    useEffect(() => {
        if (!execId || tab !== "logs") return;
        let stopped = false;
        let timer: any = null;
        let delay = 1000;

        const tick = () => {
            fetch(`${API_BASE}/api/tnr/executions/${encodeURIComponent(execId)}/logs`)
                .then((r) => (r.ok ? r.json() : Promise.reject()))
                .then((logs) => {
                    if (stopped) return;
                    setRunnerStatus("ok");
                    setExec((e) => (e ? { ...e, logs } : e));
                    delay = 1000;
                    timer = setTimeout(tick, delay);
                })
                .catch(() => {
                    if (stopped) return;
                    setRunnerStatus("reconnecting");
                    delay = nextBackoff(delay);
                    timer = setTimeout(tick, delay);
                });
        };
        tick();

        return () => { stopped = true; if (timer) clearTimeout(timer); };
    }, [execId, tab]);

    /* ---------- Auto-scroll logs ---------- */
    useEffect(() => {
        if (!autoScroll || !logBoxRef.current) return;
        logBoxRef.current.scrollTop = logBoxRef.current.scrollHeight;
    }, [exec?.logs, autoScroll]);

    /* ---------- Events ---------- */
    const [events, setEvents] = useState<TnrEvent[]>([]);
    useEffect(() => {
        let cancelled = false;
        async function load() {
            if (!execId) return;
            try {
                const ev = await jget<TnrEvent[]>(`/api/tnr/executions/${encodeURIComponent(execId)}/events`);
                if (!cancelled) { setEvents(ev || []); return; }
            } catch {}
            if (exec?.events && exec.events.length) {
                if (!cancelled) setEvents(exec.events);
                return;
            }
            const parsed = parseEventsFromLogs(exec?.logs || []);
            if (!cancelled) setEvents(parsed);
        }
        load();
    }, [execId, exec?.events, (exec?.logs || []).length]);

    /* ---------- Polling for parallel running scenarios ---------- */
    useEffect(() => {
        if (runningScenarios.size === 0) return;

        const interval = setInterval(async () => {
            const updates = new Map(runningScenarios);
            let hasChanges = false;

            for (const [scenarioId, running] of updates) {
                if (running.status !== "running") continue;

                try {
                    const result = await jget<TnrExecFull>(`/api/tnr/executions/${encodeURIComponent(running.executionId)}`);
                    if (result.status !== "running") {
                        updates.set(scenarioId, {
                            ...running,
                            status: result.status as any,
                            logs: result.logs?.map(l => typeof l === "string" ? l : l.line || "") || [],
                        });
                        hasChanges = true;
                    }
                } catch (e) {
                    console.error(`Failed to poll status for ${scenarioId}:`, e);
                }
            }

            if (hasChanges) {
                setRunningScenarios(updates);
            }
        }, 2000);

        return () => clearInterval(interval);
    }, [runningScenarios]);

    /* ---------- Actions ---------- */
    async function startRecording() {
        try {
            const name = recordingName || `record-${Date.now()}`;
            await jpost("/api/tnr/recorder/start", {
                url: defaultUrl,
                name,
                category: recordingCategory,
                tags: recordingTags.split(",").map(t => t.trim()).filter(Boolean)
            });
            setIsRecording(true);
            alert("Enregistrement démarré: " + name);
        } catch (e: any) {
            alert(`Erreur: ${e.message}`);
        }
    }

    async function stopRecording() {
        try {
            const name = recordingName || undefined;
            const tags = recordingTags.split(",").map(t => t.trim()).filter(Boolean);
            await jpost("/api/tnr/recorder/stop", {
                name,
                category: recordingCategory,
                tags: tags.length > 0 ? tags : undefined
            });
            setIsRecording(false);
            setRecordingName("");
            setRecordingTags("");
            await loadScenarios();
            await loadExecutions();
            alert("Enregistrement sauvegardé");
        } catch (e: any) {
            alert(`Erreur: ${e.message}`);
        }
    }

    /* ---------- Edit scenario ---------- */
    function openEditModal(scenario: TnrScenario) {
        setEditingScenario(scenario);
        setEditName(scenario.name || scenario.id);
        setEditCategory(scenario.category || scenario.folder || "recorded");
        setEditTags((scenario.tags || []).join(", "));
        setEditDescription(scenario.description || "");
    }

    function closeEditModal() {
        setEditingScenario(null);
        setEditName("");
        setEditCategory("");
        setEditTags("");
        setEditDescription("");
    }

    async function saveScenarioEdit() {
        if (!editingScenario) return;

        try {
            const tagsArray = editTags.split(",").map(t => t.trim()).filter(Boolean);
            await jput(`/api/tnr/scenarios/${encodeURIComponent(editingScenario.id)}`, {
                name: editName,
                category: editCategory,
                tags: tagsArray,
                description: editDescription
            });
            await loadScenarios();
            closeEditModal();
            alert("Scénario mis à jour");
        } catch (e: any) {
            alert(`Erreur: ${e.message}`);
        }
    }

    async function runScenario(scenarioId?: string) {
        const id = scenarioId || selScenarioId;
        console.log("[TNR] runScenario called with id:", id);
        if (!id) {
            console.warn("[TNR] No scenario ID provided");
            return;
        }

        const requestMode = mode === "identical" ? "instant" : mode;
        const qs = new URLSearchParams();
        if (defaultUrl) qs.set("url", defaultUrl);
        qs.set("mode", requestMode);
        if (requestMode === "realtime") qs.set("speed", String(speed));

        const url = `/api/tnr/run/${encodeURIComponent(id)}?${qs.toString()}`;
        console.log("[TNR] POST", url);

        try {
            const r = await jpost<{ executionId?: string; ok?: boolean; error?: string }>(url);
            console.log("[TNR] Response:", r);

            if (r?.executionId) {
                setExecId(r.executionId);
                setTab("logs");
                console.log("[TNR] Set execId to:", r.executionId);
            }

            if (r?.ok === false) {
                console.warn("[TNR] Execution returned ok=false:", r.error || "unknown error");
            }

            await loadExecutions();
            return r?.executionId;
        } catch (e: any) {
            console.error("[TNR] Run error:", e);
            alert(`Run error: ${e?.message || e}`);
            return null;
        }
    }

    /* ---------- Run multiple scenarios in parallel ---------- */
    async function runSelectedScenarios() {
        if (selectedScenarios.size === 0) {
            alert("Sélectionnez au moins un scénario");
            return;
        }

        const requestMode = mode === "identical" ? "instant" : mode;
        const qs = new URLSearchParams();
        if (defaultUrl) qs.set("url", defaultUrl);
        qs.set("mode", requestMode);
        if (requestMode === "realtime") qs.set("speed", String(speed));

        const newRunning = new Map<string, RunningScenario>();

        // Launch all selected scenarios in parallel
        const promises = Array.from(selectedScenarios).map(async (scenarioId) => {
            try {
                const r = await jpost<{ executionId?: string }>(`/api/tnr/run/${encodeURIComponent(scenarioId)}?${qs.toString()}`);
                if (r?.executionId) {
                    newRunning.set(scenarioId, {
                        scenarioId,
                        executionId: r.executionId,
                        status: "running",
                        startedAt: new Date(),
                        logs: [],
                    });
                }
            } catch (e: any) {
                newRunning.set(scenarioId, {
                    scenarioId,
                    executionId: "",
                    status: "error",
                    startedAt: new Date(),
                    logs: [`Error: ${e?.message || e}`],
                });
            }
        });

        await Promise.all(promises);
        setRunningScenarios(newRunning);
        setTab("parallel");
        await loadExecutions();
    }

    /* ---------- Selection helpers ---------- */
    const toggleScenarioSelection = useCallback((id: string) => {
        setSelectedScenariosMap(prev => ({
            ...prev,
            [id]: !prev[id]
        }));
    }, []);

    const selectAllInCategory = useCallback((category: string) => {
        const scenariosInCat = scenariosByCategory.get(category) || [];
        setSelectedScenariosMap(prev => {
            const next = { ...prev };
            scenariosInCat.forEach(s => { next[s.id] = true; });
            return next;
        });
    }, [scenariosByCategory]);

    const deselectAllInCategory = useCallback((category: string) => {
        const scenariosInCat = scenariosByCategory.get(category) || [];
        setSelectedScenariosMap(prev => {
            const next = { ...prev };
            scenariosInCat.forEach(s => { next[s.id] = false; });
            return next;
        });
    }, [scenariosByCategory]);

    const toggleFolder = (folder: string) => {
        setExpandedFolders(prev => {
            const next = new Set(prev);
            if (next.has(folder)) {
                next.delete(folder);
            } else {
                next.add(folder);
            }
            return next;
        });
    };

    /* ---------- KPI ---------- */
    const kpiStatus = exec?.status || "–";
    const kpiEvents = exec?.metrics?.totalEvents ?? events.length ?? "–";
    const kpiDiffs = exec?.metrics?.differences ?? exec?.differences?.length ?? "–";
    const kpiServer = exec?.metrics?.serverCalls ?? "–";
    const kpiDur = exec?.metrics?.durationMs ?? (exec?.finishedAt && exec?.startedAt ? new Date(exec.finishedAt).getTime() - new Date(exec.startedAt).getTime() : undefined);

    /* ---------- Intelligence : éléments comparés ---------- */
    const actionsSeen = useMemo(() => {
        const set = new Set<string>();
        for (const ev of events || []) if (ev?.action) set.add(String(ev.action));
        ["BootNotification","Authorize","StartTransaction","MeterValues","StopTransaction","SetChargingProfile","ClearChargingProfile"].forEach(a=>set.add(a));
        return Array.from(set).sort();
    }, [events]);

    const diffByAction = useMemo(() => {
        const m = new Map<string, number>();
        for (const a of actionsSeen) m.set(a, 0);
        for (const d of exec?.differences || []) {
            const p = (d.path || "").toLowerCase();
            for (const a of actionsSeen) {
                const al = a.toLowerCase();
                if (p.includes(al) || /\/calls\//.test(p)) m.set(a, (m.get(a) || 0) + 1);
            }
        }
        return m;
    }, [actionsSeen, exec?.differences]);

    /* ---------- Diffs filtrés + résumé ---------- */
    const diffs: TnrDiff[] = (exec?.differences || []) as TnrDiff[];
    const bucketCounts = useMemo(() => {
        const m = new Map<BucketKey, number>();
        for (const d of diffs) m.set(bucketOfPath(d.path), (m.get(bucketOfPath(d.path)) || 0) + 1);
        return m;
    }, [diffs]);

    const filteredDiffs = useMemo(() => {
        const qq = q.trim().toLowerCase();
        return diffs.filter((d) => {
            if (typeFilter && d.type !== typeFilter) return false;
            if (bucketFilter && bucketOfPath(d.path) !== bucketFilter) return false;
            if (!qq) return true;
            const blob = ((d.path || "") + " " + JSON.stringify(d.expected ?? "") + " " + JSON.stringify(d.actual ?? "")).toLowerCase();
            return blob.includes(qq);
        });
    }, [diffs, q, typeFilter, bucketFilter]);

    /* ====================== Rendu ====================== */
    return (
        <div style={{ padding: 12 }}>
            <h2 style={{ margin: "0 0 8px" }}>TNR - Tests Non Régressifs</h2>

            {/* Info API + Stats rapides */}
            <div style={{
                marginBottom: 12,
                padding: 8,
                background: "#f0f9ff",
                border: "1px solid #0ea5e9",
                borderRadius: 6,
                fontSize: 12,
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center"
            }}>
                <div>
                    <strong>API:</strong> {API_BASE}
                    {" "}
                    <button
                        onClick={() => {
                            const newBase = prompt("URL du backend:", API_BASE);
                            if (newBase) {
                                localStorage.setItem("runner_api", newBase);
                                window.location.reload();
                            }
                        }}
                        style={{
                            marginLeft: 8,
                            padding: "2px 8px",
                            fontSize: 11,
                            background: "#0ea5e9",
                            color: "white",
                            border: "none",
                            borderRadius: 4,
                            cursor: "pointer"
                        }}
                    >
                        Changer
                    </button>
                </div>
                <div style={{ display: "flex", gap: 16 }}>
                    <span><strong>{scenarios.length}</strong> scénarios</span>
                    <span><strong>{allCategories.length}</strong> catégories</span>
                    <span><strong>{selectedScenarios.size}</strong> sélectionnés</span>
                </div>
            </div>

            {/* Bandeau principal : Enregistrement + Exécutions */}
            <div style={{ display: "grid", gap: 12, gridTemplateColumns: "380px 1fr" }}>
                {/* Enregistrement */}
                <Section
                    title="Enregistrement TNR"
                    right={<span style={{ fontSize: 12, color: isRecording ? "#ef4444" : "#10b981" }}>
                        {isRecording ? "● REC" : "● prêt"}
                    </span>}
                >
                    <div style={{ display: "grid", gap: 6 }}>
                        <input
                            value={recordingName}
                            onChange={(e) => setRecordingName(e.target.value)}
                            placeholder="Nom du scénario (ex: test-gpm-boot)"
                            style={{ border: "1px solid #d1d5db", borderRadius: 6, padding: "6px 8px", fontSize: 12 }}
                        />
                        <div style={{ display: "flex", gap: 6 }}>
                            <select
                                value={recordingCategory}
                                onChange={(e) => setRecordingCategory(e.target.value)}
                                style={{ flex: 1, padding: "6px 8px", borderRadius: 6, border: "1px solid #d1d5db", fontSize: 12 }}
                            >
                                <option value="gpm">GPM</option>
                                <option value="ocpp">OCPP</option>
                                <option value="smart-charging">Smart Charging</option>
                                <option value="recorded">Enregistrés</option>
                                <option value="regression">Régression</option>
                                <option value="performance">Performance</option>
                            </select>
                            <input
                                value={recordingTags}
                                onChange={(e) => setRecordingTags(e.target.value)}
                                placeholder="Tags (gpm, boot, authorize...)"
                                style={{ flex: 2, border: "1px solid #d1d5db", borderRadius: 6, padding: "6px 8px", fontSize: 12 }}
                            />
                        </div>
                        <div style={{ display: "flex", gap: 8 }}>
                            <button
                                onClick={startRecording}
                                disabled={isRecording}
                                style={{
                                    flex: 1,
                                    padding: "6px 12px",
                                    borderRadius: 6,
                                    background: isRecording ? "#d1d5db" : "#16a34a",
                                    color: "#fff",
                                    border: "none",
                                    cursor: isRecording ? "not-allowed" : "pointer"
                                }}
                            >
                                ● Start
                            </button>
                            <button
                                onClick={stopRecording}
                                disabled={!isRecording}
                                style={{
                                    flex: 1,
                                    padding: "6px 12px",
                                    borderRadius: 6,
                                    background: !isRecording ? "#d1d5db" : "#ef4444",
                                    color: "#fff",
                                    border: "none",
                                    cursor: !isRecording ? "not-allowed" : "pointer"
                                }}
                            >
                                ■ Stop & Save
                            </button>
                        </div>
                    </div>
                </Section>

                {/* Exécutions */}
                <Section
                    title="Exécution"
                    right={
                        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                            <select value={mode} onChange={(e) => setMode(e.target.value as TnrMode)} style={{ padding: 6, borderRadius: 6, border: "1px solid #d1d5db" }}>
                                <option value="fast">Fast</option>
                                <option value="realtime">Temps réel</option>
                                <option value="identical">Identique</option>
                            </select>
                            {mode === "realtime" && (
                                <input
                                    type="number"
                                    step={0.1}
                                    min={0.1}
                                    value={speed}
                                    onChange={(e) => setSpeed(Number(e.target.value))}
                                    style={{ width: 70, padding: 6, border: "1px solid #d1d5db", borderRadius: 6 }}
                                    title="speed"
                                />
                            )}
                            <button
                                onClick={() => runScenario()}
                                disabled={!selScenarioId}
                                style={{
                                    padding: "6px 12px",
                                    borderRadius: 6,
                                    background: selScenarioId ? "#2563eb" : "#d1d5db",
                                    color: "#fff",
                                    border: "none",
                                    cursor: selScenarioId ? "pointer" : "not-allowed"
                                }}
                            >
                                ▶ Lancer 1
                            </button>
                            <button
                                onClick={runSelectedScenarios}
                                disabled={selectedScenarios.size === 0}
                                style={{
                                    padding: "6px 12px",
                                    borderRadius: 6,
                                    background: selectedScenarios.size > 0 ? "#8b5cf6" : "#d1d5db",
                                    color: "#fff",
                                    border: "none",
                                    cursor: selectedScenarios.size > 0 ? "pointer" : "not-allowed"
                                }}
                            >
                                ▶▶ Lancer {selectedScenarios.size} en parallèle
                            </button>
                        </div>
                    }
                >
                    {/* KPI */}
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(6, 1fr)", gap: 8 }}>
                        <Kpi label="Statut" value={String(kpiStatus)} color={kpiColor(exec?.status)} />
                        <Kpi label="Events" value={String(kpiEvents)} />
                        <Kpi label="Différences" value={String(kpiDiffs)} />
                        <Kpi label="Server calls" value={String(kpiServer)} />
                        <Kpi label="Durée" value={formatDur(typeof kpiDur === "number" ? kpiDur : undefined)} />
                        <Kpi label="Résultat" value={exec?.status === "success" ? "PASS" : exec?.status === "failed" ? "FAIL" : "–"} />
                    </div>
                </Section>
            </div>

            {/* Filtres et recherche */}
            <div style={{ marginTop: 12, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <input
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="🔍 Rechercher un scénario..."
                    style={{ flex: 1, minWidth: 200, padding: "6px 12px", border: "1px solid #d1d5db", borderRadius: 6 }}
                />
                <select
                    value={categoryFilter}
                    onChange={(e) => setCategoryFilter(e.target.value)}
                    style={{ padding: 6, borderRadius: 6, border: "1px solid #d1d5db" }}
                >
                    <option value="">Toutes catégories</option>
                    {allCategories.map(cat => {
                        const config = getCategoryConfig(cat);
                        return <option key={cat} value={cat}>{config.icon} {config.label}</option>;
                    })}
                </select>
                <select
                    value={tagFilter}
                    onChange={(e) => setTagFilter(e.target.value)}
                    style={{ padding: 6, borderRadius: 6, border: "1px solid #d1d5db" }}
                >
                    <option value="">Tous tags</option>
                    {allTags.map(tag => (
                        <option key={tag} value={tag}>{tag}</option>
                    ))}
                </select>
                <div style={{ display: "flex", gap: 4 }}>
                    <button
                        onClick={() => setViewMode("list")}
                        style={{
                            padding: "6px 10px",
                            borderRadius: 6,
                            border: viewMode === "list" ? "2px solid #2563eb" : "1px solid #d1d5db",
                            background: viewMode === "list" ? "#eff6ff" : "#fff",
                            cursor: "pointer"
                        }}
                    >
                        ≡ Liste
                    </button>
                    <button
                        onClick={() => setViewMode("tree")}
                        style={{
                            padding: "6px 10px",
                            borderRadius: 6,
                            border: viewMode === "tree" ? "2px solid #2563eb" : "1px solid #d1d5db",
                            background: viewMode === "tree" ? "#eff6ff" : "#fff",
                            cursor: "pointer"
                        }}
                    >
                        🗂 Dossiers
                    </button>
                </div>
            </div>

            {/* Tags actifs */}
            {allTags.length > 0 && (
                <div style={{ marginTop: 8, display: "flex", gap: 6, flexWrap: "wrap" }}>
                    {allTags.slice(0, 15).map(tag => (
                        <button
                            key={tag}
                            onClick={() => setTagFilter(tagFilter === tag ? "" : tag)}
                            style={{
                                padding: "4px 10px",
                                borderRadius: 9999,
                                borderWidth: tagFilter === tag ? 2 : 1,
                                borderStyle: "solid",
                                borderColor: getTagColor(tag),
                                background: tagFilter === tag ? getTagColor(tag) : "#fff",
                                color: tagFilter === tag ? "#fff" : getTagColor(tag),
                                fontSize: 12,
                                fontWeight: 500,
                                cursor: "pointer"
                            }}
                        >
                            {tag}
                        </button>
                    ))}
                </div>
            )}

            {/* Colonne scénarios + panneau central */}
            <div style={{ display: "grid", gap: 12, gridTemplateColumns: "minmax(320px, 400px) 1fr", marginTop: 12 }}>
                {/* Scénarios */}
                <Section title={`Scénarios (${filteredScenarios.length}/${scenarios.length})`}>
                    {/* Actions de sélection groupée */}
                    <div style={{ marginBottom: 8, display: "flex", gap: 8 }}>
                        <button
                            onClick={() => {
                                const newMap: Record<string, boolean> = {};
                                filteredScenarios.forEach(s => { newMap[s.id] = true; });
                                setSelectedScenariosMap(newMap);
                            }}
                            style={{ padding: "4px 8px", borderRadius: 4, border: "1px solid #d1d5db", fontSize: 11, cursor: "pointer" }}
                        >
                            ☑ Tout sélectionner
                        </button>
                        <button
                            onClick={() => setSelectedScenariosMap({})}
                            style={{ padding: "4px 8px", borderRadius: 4, border: "1px solid #d1d5db", fontSize: 11, cursor: "pointer" }}
                        >
                            ☐ Tout désélectionner
                        </button>
                    </div>

                    <div style={{ maxHeight: "calc(100vh - 350px)", minHeight: 300, overflow: "auto", overflowX: "hidden" }}>
                        {viewMode === "tree" ? (
                            /* Vue par dossiers/catégories */
                            <div style={{ display: "grid", gap: 4 }}>
                                {Array.from(scenariosByCategory.entries()).map(([category, scenariosInCat]) => {
                                    const config = getCategoryConfig(category);
                                    const isExpanded = expandedFolders.has(category);
                                    const filteredInCat = scenariosInCat.filter(s => {
                                        if (tagFilter && !(s.tags || []).includes(tagFilter)) return false;
                                        if (searchQuery) {
                                            const query = searchQuery.toLowerCase();
                                            const searchable = `${s.id} ${s.name || ""} ${s.description || ""}`.toLowerCase();
                                            if (!searchable.includes(query)) return false;
                                        }
                                        return true;
                                    });

                                    if (filteredInCat.length === 0) return null;

                                    const selectedCount = filteredInCat.filter(s => selectedScenarios.has(s.id)).length;

                                    return (
                                        <div key={category}>
                                            {/* Folder header */}
                                            <div
                                                onClick={() => toggleFolder(category)}
                                                style={{
                                                    display: "flex",
                                                    alignItems: "center",
                                                    gap: 8,
                                                    padding: "8px 10px",
                                                    background: "#f9fafb",
                                                    borderRadius: 6,
                                                    cursor: "pointer",
                                                    userSelect: "none"
                                                }}
                                            >
                                                <span style={{ fontSize: 16 }}>{isExpanded ? "📂" : "📁"}</span>
                                                <span style={{ fontWeight: 600, color: config.color }}>
                                                    {config.icon} {config.label}
                                                </span>
                                                <span style={{ fontSize: 12, color: "#6b7280" }}>
                                                    ({filteredInCat.length})
                                                </span>
                                                {selectedCount > 0 && (
                                                    <span style={{
                                                        marginLeft: "auto",
                                                        padding: "2px 8px",
                                                        background: "#8b5cf6",
                                                        color: "#fff",
                                                        borderRadius: 9999,
                                                        fontSize: 11
                                                    }}>
                                                        {selectedCount} sélectionnés
                                                    </span>
                                                )}
                                            </div>

                                            {/* Folder content */}
                                            {isExpanded && (
                                                <div style={{ marginLeft: 20, marginTop: 4, display: "grid", gap: 4, contain: "layout" }}>
                                                    {/* Quick select for folder */}
                                                    <div style={{ display: "flex", gap: 4, marginBottom: 4 }}>
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); selectAllInCategory(category); }}
                                                            style={{ padding: "2px 6px", borderRadius: 4, border: "1px solid #d1d5db", fontSize: 10, cursor: "pointer" }}
                                                        >
                                                            + Tout
                                                        </button>
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); deselectAllInCategory(category); }}
                                                            style={{ padding: "2px 6px", borderRadius: 4, border: "1px solid #d1d5db", fontSize: 10, cursor: "pointer" }}
                                                        >
                                                            - Tout
                                                        </button>
                                                    </div>
                                                    {filteredInCat.map(s => (
                                                        <ScenarioCard
                                                            key={s.id}
                                                            scenario={s}
                                                            isSelected={!!selectedScenariosMap[s.id]}
                                                            isActive={selScenarioId === s.id}
                                                            onToggleSelect={() => toggleScenarioSelection(s.id)}
                                                            onSelect={() => setSelScenarioId(s.id)}
                                                            onRun={() => runScenario(s.id)}
                                                            onEdit={() => openEditModal(s)}
                                                            runningStatus={runningScenarios.get(s.id)?.status}
                                                        />
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            /* Vue liste */
                            <div style={{ display: "grid", gap: 8, contain: "layout" }}>
                                {filteredScenarios.map(s => (
                                    <ScenarioCard
                                        key={s.id}
                                        scenario={s}
                                        isSelected={!!selectedScenariosMap[s.id]}
                                        isActive={selScenarioId === s.id}
                                        onToggleSelect={() => toggleScenarioSelection(s.id)}
                                        onSelect={() => setSelScenarioId(s.id)}
                                        onRun={() => runScenario(s.id)}
                                        onEdit={() => openEditModal(s)}
                                        runningStatus={runningScenarios.get(s.id)?.status}
                                        showCategory
                                    />
                                ))}
                            </div>
                        )}
                    </div>
                </Section>

                {/* Panneau central */}
                <Section
                    title={`Exécution ${execId || "–"}`}
                    right={
                        <div>
                            <TabButton active={tab === "info"} onClick={() => setTab("info")}>Info</TabButton>{" "}
                            <TabButton active={tab === "logs"} onClick={() => setTab("logs")}>Logs</TabButton>{" "}
                            <TabButton active={tab === "events"} onClick={() => setTab("events")}>Events</TabButton>{" "}
                            <TabButton active={tab === "diffs"} onClick={() => setTab("diffs")}>Différences</TabButton>{" "}
                            <TabButton active={tab === "parallel"} onClick={() => setTab("parallel")}>
                                Parallèle {runningScenarios.size > 0 && `(${runningScenarios.size})`}
                            </TabButton>
                        </div>
                    }
                >
                    {/* Bannière runner */}
                    {tab === "logs" && runnerStatus === "reconnecting" && (
                        <div style={{ marginBottom: 8, padding: "6px 8px", background: "#fff7ed", border: "1px solid #fdba74", color: "#9a3412", borderRadius: 6 }}>
                            runner reconnecting…
                        </div>
                    )}

                    {/* INFO */}
                    {tab === "info" && (
                        <div style={{ display: "grid", gap: 6 }}>
                            <div><strong>Scénario:</strong> <Mono>{exec?.scenarioId || "–"}</Mono></div>
                            <div><strong>Début:</strong> {exec?.startedAt ? new Date(exec.startedAt).toLocaleString() : "–"}</div>
                            <div><strong>Fin:</strong> {exec?.finishedAt ? new Date(exec.finishedAt).toLocaleString() : "–"}</div>
                            <div><strong>URL:</strong> <Mono>{exec?.inputs?.url || defaultUrl || "–"}</Mono></div>
                            <div><strong>Mode:</strong> {exec?.inputs?.mode || "–"}</div>
                            {exec?.error && <div style={{ color: "#ef4444" }}><strong>Erreur:</strong> {exec.error}</div>}
                        </div>
                    )}

                    {/* LOGS */}
                    {tab === "logs" && (
                        <>
                            <div style={{ marginBottom: 6 }}>
                                <label style={{ fontSize: 12 }}>
                                    <input type="checkbox" checked={autoScroll} onChange={(e) => setAutoScroll(e.target.checked)} /> auto-scroll
                                </label>
                            </div>
                            <div
                                ref={logBoxRef}
                                style={{
                                    height: 360,
                                    overflow: "auto",
                                    background: "#0b1020",
                                    color: "#d1d5db",
                                    borderRadius: 8,
                                    padding: 8,
                                    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
                                    fontSize: 12,
                                    whiteSpace: "pre-wrap",
                                }}
                            >
                                {(exec?.logs || []).map((l, i) => {
                                    const line = typeof l === "string" ? l : l.line || "";
                                    return <div key={i}>{line}</div>;
                                })}
                                {!exec?.logs?.length && <div style={{ opacity: .6 }}>Aucun log pour le moment…</div>}
                            </div>
                        </>
                    )}

                    {/* EVENTS */}
                    {tab === "events" && (
                        <div style={{ overflow: "auto", maxHeight: 420 }}>
                            <table style={{ width: "100%", borderCollapse: "collapse" }}>
                                <thead>
                                <tr style={{ background: "#f9fafb" }}>
                                    <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #e5e7eb" }}>#</th>
                                    <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #e5e7eb" }}>ts</th>
                                    <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #e5e7eb" }}>dir</th>
                                    <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #e5e7eb" }}>action</th>
                                    <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #e5e7eb" }}>status</th>
                                    <th style={{ textAlign: "left", padding: 8, borderBottom: "1px solid #e5e7eb" }}>payload</th>
                                </tr>
                                </thead>
                                <tbody>
                                {(events || []).map((ev, i) => (
                                    <tr key={i} style={{ background: ev.passed === false ? "#fef2f2" : undefined }}>
                                        <td style={{ padding: 8, borderBottom: "1px solid #f3f4f6" }}>{ev.index ?? i}</td>
                                        <td style={{ padding: 8, borderBottom: "1px solid #f3f4f6" }}>{ev.ts ? new Date(ev.ts).toLocaleTimeString() : ""}</td>
                                        <td style={{ padding: 8, borderBottom: "1px solid #f3f4f6" }}>{ev.direction || ""}</td>
                                        <td style={{ padding: 8, borderBottom: "1px solid #f3f4f6", fontWeight: 600 }}>{ev.action || ""}</td>
                                        <td style={{ padding: 8, borderBottom: "1px solid #f3f4f6" }}>
                                            {ev.passed === true && <span style={{ color: "#10b981" }}>✓</span>}
                                            {ev.passed === false && <span style={{ color: "#ef4444" }}>✗</span>}
                                        </td>
                                        <td style={{ padding: 8, borderBottom: "1px solid #f3f4f6" }}><Mono>{truncate(JSON.stringify(ev.payload))}</Mono></td>
                                    </tr>
                                ))}
                                {!events.length && (
                                    <tr><td colSpan={6} style={{ padding: 12, color: "#6b7280" }}>Aucun event détecté.</td></tr>
                                )}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {/* DIFFS */}
                    {tab === "diffs" && (
                        <>
                            {/* Éléments comparés */}
                            <div style={{ marginBottom: 8 }}>
                                <div style={{ fontWeight: 600, marginBottom: 6 }}>Éléments comparés</div>
                                <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                                    {actionsSeen.map((a) => {
                                        const n = diffByAction.get(a) || 0;
                                        const ok = n === 0;
                                        return (
                                            <button
                                                key={a}
                                                onClick={() => setQ(a)}
                                                style={{
                                                    display: "inline-flex",
                                                    alignItems: "center",
                                                    gap: 4,
                                                    padding: "4px 8px",
                                                    borderRadius: 9999,
                                                    border: `1px solid ${ok ? "#10b981" : "#ef4444"}`,
                                                    background: ok ? "#ecfdf5" : "#fef2f2",
                                                    color: ok ? "#065f46" : "#7f1d1d",
                                                    fontSize: 11,
                                                    fontWeight: 600,
                                                    cursor: "pointer"
                                                }}
                                            >
                                                {ok ? "✓" : "✗"} {a}
                                            </button>
                                        );
                                    })}
                                </div>
                            </div>

                            {/* Résumé buckets */}
                            <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8, marginBottom: 8 }}>
                                {(Object.keys(BUCKET_LABEL) as BucketKey[]).map((b) => (
                                    <button
                                        key={b}
                                        onClick={() => setBucketFilter(bucketFilter === b ? "" : b)}
                                        style={{
                                            padding: "8px 10px",
                                            borderRadius: 8,
                                            border: bucketFilter === b ? "2px solid #2563eb" : "1px solid #e5e7eb",
                                            background: "#fff",
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "space-between",
                                            cursor: "pointer"
                                        }}
                                    >
                                        <span style={{ fontSize: 12 }}>{BUCKET_LABEL[b]}</span>
                                        <strong style={{ color: (bucketCounts.get(b) || 0) ? "#ef4444" : "#10b981" }}>{bucketCounts.get(b) || 0}</strong>
                                    </button>
                                ))}
                            </div>

                            {/* Filtres */}
                            <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: 8, marginBottom: 8 }}>
                                <input
                                    value={q}
                                    onChange={(e) => setQ(e.target.value)}
                                    placeholder="Rechercher..."
                                    style={{ padding: "6px 8px", border: "1px solid #d1d5db", borderRadius: 6 }}
                                />
                                <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as any)} style={{ padding: 6, borderRadius: 6, border: "1px solid #d1d5db" }}>
                                    <option value="">Type – Tous</option>
                                    <option value="different">different</option>
                                    <option value="missing">missing</option>
                                    <option value="extra">extra</option>
                                </select>
                                <select value={bucketFilter} onChange={(e) => setBucketFilter(e.target.value as any)} style={{ padding: 6, borderRadius: 6, border: "1px solid #d1d5db" }}>
                                    <option value="">Bucket – Tous</option>
                                    {(Object.keys(BUCKET_LABEL) as BucketKey[]).map((b) => (
                                        <option key={b} value={b}>{BUCKET_LABEL[b]}</option>
                                    ))}
                                </select>
                            </div>

                            {/* Tableau des différences */}
                            <div style={{ overflow: "auto", maxHeight: 300 }}>
                                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                                    <thead>
                                    <tr style={{ background: "#f9fafb" }}>
                                        <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Type</th>
                                        <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Chemin</th>
                                        <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Expected</th>
                                        <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Actual</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {filteredDiffs.length === 0 ? (
                                        <tr>
                                            <td colSpan={4} style={{ padding: 12, color: "#6b7280" }}>Aucune différence</td>
                                        </tr>
                                    ) : (
                                        filteredDiffs.slice(0, 50).map((d, i) => (
                                            <tr key={i}>
                                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6" }}>{d.type}</td>
                                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6" }}><Mono>{truncate(d.path, 60)}</Mono></td>
                                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6" }}><Mono>{truncate(JSON.stringify(d.expected), 40)}</Mono></td>
                                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6" }}><Mono>{truncate(JSON.stringify(d.actual), 40)}</Mono></td>
                                            </tr>
                                        ))
                                    )}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}

                    {/* PARALLEL EXECUTION */}
                    {tab === "parallel" && (
                        <div>
                            <div style={{ marginBottom: 12 }}>
                                <strong>Exécutions en parallèle ({runningScenarios.size})</strong>
                            </div>

                            {runningScenarios.size === 0 ? (
                                <div style={{ padding: 20, textAlign: "center", color: "#6b7280" }}>
                                    <p>Sélectionnez plusieurs scénarios et cliquez sur "Lancer en parallèle"</p>
                                </div>
                            ) : (
                                <div style={{ display: "grid", gap: 8, maxHeight: 400, overflow: "auto" }}>
                                    {Array.from(runningScenarios.values()).map((running) => {
                                        const scenario = scenarios.find(s => s.id === running.scenarioId);
                                        return (
                                            <div
                                                key={running.scenarioId}
                                                style={{
                                                    border: "1px solid #e5e7eb",
                                                    borderRadius: 8,
                                                    padding: 12,
                                                    background: running.status === "running" ? "#eff6ff" :
                                                               running.status === "success" ? "#ecfdf5" :
                                                               running.status === "failed" ? "#fef2f2" : "#fff"
                                                }}
                                            >
                                                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                                                    <div>
                                                        <strong>{scenario?.name || running.scenarioId}</strong>
                                                        <div style={{ fontSize: 11, color: "#6b7280" }}>
                                                            {running.executionId}
                                                        </div>
                                                    </div>
                                                    <div style={{
                                                        padding: "4px 10px",
                                                        borderRadius: 9999,
                                                        background: kpiColor(running.status),
                                                        color: "#fff",
                                                        fontSize: 12,
                                                        fontWeight: 600
                                                    }}>
                                                        {running.status === "running" && "⏳ En cours..."}
                                                        {running.status === "success" && "✓ Succès"}
                                                        {running.status === "failed" && "✗ Échec"}
                                                        {running.status === "error" && "⚠ Erreur"}
                                                    </div>
                                                </div>
                                                {running.executionId && (
                                                    <button
                                                        onClick={() => { setExecId(running.executionId); setTab("logs"); }}
                                                        style={{
                                                            padding: "4px 10px",
                                                            borderRadius: 4,
                                                            border: "1px solid #2563eb",
                                                            background: "#2563eb",
                                                            color: "#fff",
                                                            fontSize: 11,
                                                            cursor: "pointer"
                                                        }}
                                                    >
                                                        Voir les logs
                                                    </button>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}

                            {/* Summary */}
                            {runningScenarios.size > 0 && (
                                <div style={{ marginTop: 12, padding: 12, background: "#f9fafb", borderRadius: 8 }}>
                                    <div style={{ display: "flex", gap: 16 }}>
                                        <span>
                                            <strong style={{ color: "#2563eb" }}>
                                                {Array.from(runningScenarios.values()).filter(r => r.status === "running").length}
                                            </strong> en cours
                                        </span>
                                        <span>
                                            <strong style={{ color: "#10b981" }}>
                                                {Array.from(runningScenarios.values()).filter(r => r.status === "success").length}
                                            </strong> réussis
                                        </span>
                                        <span>
                                            <strong style={{ color: "#ef4444" }}>
                                                {Array.from(runningScenarios.values()).filter(r => r.status === "failed" || r.status === "error").length}
                                            </strong> échoués
                                        </span>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </Section>
            </div>

            {/* Historique */}
            <Section title="Historique d'exécutions" style={{ marginTop: 12 }}>
                <div style={{ overflow: "auto", maxHeight: 200 }}>
                    <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                        <thead>
                        <tr style={{ background: "#f9fafb" }}>
                            <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Heure</th>
                            <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Scénario</th>
                            <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Statut</th>
                            <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Durée</th>
                            <th style={{ textAlign: "left", padding: 6, borderBottom: "1px solid #e5e7eb" }}>Action</th>
                        </tr>
                        </thead>
                        <tbody>
                        {execs.slice(0, 20).map((e) => (
                            <tr key={e.executionId}>
                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6" }}>{new Date(e.timestamp).toLocaleString()}</td>
                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6" }}><Mono>{e.scenarioId}</Mono></td>
                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6", color: e.passed ? "#10b981" : "#ef4444" }}>
                                    {e.passed ? "✓ success" : "✗ failed"}
                                </td>
                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6" }}>{formatDur(e.metrics?.durationMs)}</td>
                                <td style={{ padding: 6, borderBottom: "1px solid #f3f4f6" }}>
                                    <button
                                        onClick={() => { setExecId(e.executionId); setTab("logs"); }}
                                        style={{ padding: "2px 8px", borderRadius: 4, border: "1px solid #2563eb", background: "#2563eb", color: "#fff", fontSize: 11, cursor: "pointer" }}
                                    >
                                        Voir
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            </Section>

            {/* Edit Modal */}
            {editingScenario && (
                <div style={{
                    position: "fixed",
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: "rgba(0,0,0,0.5)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    zIndex: 1000
                }}>
                    <div style={{
                        background: "#fff",
                        borderRadius: 12,
                        padding: 24,
                        width: 480,
                        maxWidth: "90%",
                        boxShadow: "0 20px 25px -5px rgba(0,0,0,0.1)"
                    }}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
                            <h3 style={{ margin: 0 }}>Modifier le scénario</h3>
                            <button
                                onClick={closeEditModal}
                                style={{
                                    background: "none",
                                    border: "none",
                                    fontSize: 20,
                                    cursor: "pointer",
                                    color: "#6b7280"
                                }}
                            >
                                ×
                            </button>
                        </div>

                        <div style={{ display: "grid", gap: 12 }}>
                            <div>
                                <label style={{ display: "block", marginBottom: 4, fontSize: 12, fontWeight: 600 }}>
                                    Nom du scénario
                                </label>
                                <input
                                    value={editName}
                                    onChange={(e) => setEditName(e.target.value)}
                                    style={{
                                        width: "100%",
                                        padding: "8px 12px",
                                        border: "1px solid #d1d5db",
                                        borderRadius: 6,
                                        fontSize: 14
                                    }}
                                />
                            </div>

                            <div>
                                <label style={{ display: "block", marginBottom: 4, fontSize: 12, fontWeight: 600 }}>
                                    Catégorie
                                </label>
                                <select
                                    value={editCategory}
                                    onChange={(e) => setEditCategory(e.target.value)}
                                    style={{
                                        width: "100%",
                                        padding: "8px 12px",
                                        border: "1px solid #d1d5db",
                                        borderRadius: 6,
                                        fontSize: 14
                                    }}
                                >
                                    <option value="gpm">GPM</option>
                                    <option value="ocpp">OCPP</option>
                                    <option value="smart-charging">Smart Charging</option>
                                    <option value="recorded">Enregistrés</option>
                                    <option value="regression">Régression</option>
                                    <option value="performance">Performance</option>
                                    <option value="other">Autre</option>
                                </select>
                            </div>

                            <div>
                                <label style={{ display: "block", marginBottom: 4, fontSize: 12, fontWeight: 600 }}>
                                    Tags (séparés par des virgules)
                                </label>
                                <input
                                    value={editTags}
                                    onChange={(e) => setEditTags(e.target.value)}
                                    placeholder="gpm, boot, authorize, regression..."
                                    style={{
                                        width: "100%",
                                        padding: "8px 12px",
                                        border: "1px solid #d1d5db",
                                        borderRadius: 6,
                                        fontSize: 14
                                    }}
                                />
                            </div>

                            <div>
                                <label style={{ display: "block", marginBottom: 4, fontSize: 12, fontWeight: 600 }}>
                                    Description
                                </label>
                                <textarea
                                    value={editDescription}
                                    onChange={(e) => setEditDescription(e.target.value)}
                                    rows={3}
                                    style={{
                                        width: "100%",
                                        padding: "8px 12px",
                                        border: "1px solid #d1d5db",
                                        borderRadius: 6,
                                        fontSize: 14,
                                        resize: "vertical"
                                    }}
                                />
                            </div>
                        </div>

                        <div style={{ display: "flex", gap: 8, marginTop: 20, justifyContent: "flex-end" }}>
                            <button
                                onClick={closeEditModal}
                                style={{
                                    padding: "8px 16px",
                                    borderRadius: 6,
                                    border: "1px solid #d1d5db",
                                    background: "#fff",
                                    cursor: "pointer"
                                }}
                            >
                                Annuler
                            </button>
                            <button
                                onClick={saveScenarioEdit}
                                style={{
                                    padding: "8px 16px",
                                    borderRadius: 6,
                                    border: "none",
                                    background: "#2563eb",
                                    color: "#fff",
                                    cursor: "pointer"
                                }}
                            >
                                Sauvegarder
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

/* ---------------------- Petits composants ---------------------- */

function Kpi({ label, value, color }: { label: string; value: string; color?: string }) {
    return (
        <div style={{ border: "1px solid #e5e7eb", borderRadius: 8, padding: "6px 8px", textAlign: "center" }}>
            <div style={{ fontSize: 11, color: "#6b7280" }}>{label}</div>
            <div style={{ fontSize: 16, fontWeight: 700, color: color || "#111827" }}>{value}</div>
        </div>
    );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
    return (
        <button
            onClick={onClick}
            style={{
                padding: "4px 8px",
                border: active ? "1px solid #2563eb" : "1px solid #d1d5db",
                color: active ? "#2563eb" : "#6b7280",
                background: "white",
                borderRadius: 4,
                cursor: "pointer",
                fontWeight: active ? 600 : 400
            }}
        >
            {children}
        </button>
    );
}

const ScenarioCard = React.memo(function ScenarioCard({
    scenario,
    isSelected,
    isActive,
    onToggleSelect,
    onSelect,
    onRun,
    onEdit,
    runningStatus,
    showCategory = false
}: {
    scenario: TnrScenario;
    isSelected: boolean;
    isActive: boolean;
    onToggleSelect: () => void;
    onSelect: () => void;
    onRun: () => void;
    onEdit: () => void;
    runningStatus?: string;
    showCategory?: boolean;
}) {
    const catConfig = getCategoryConfig(scenario.category || scenario.folder);

    // Stop propagation pour éviter les conflits avec le parent
    const handleCheckboxChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        e.stopPropagation();
        onToggleSelect();
    }, [onToggleSelect]);

    return (
        <div
            style={{
                position: "relative",
                border: isActive ? "2px solid #2563eb" : isSelected ? "2px solid #8b5cf6" : "1px solid #e5e7eb",
                borderRadius: 8,
                padding: 10,
                background: runningStatus === "running" ? "#eff6ff" :
                           runningStatus === "success" ? "#ecfdf5" :
                           runningStatus === "failed" ? "#fef2f2" : "#fff",
                overflow: "hidden",
                isolation: "isolate",
                contain: "layout"
            }}
        >
            <div style={{ display: "flex", alignItems: "flex-start", gap: 8, minWidth: 0, width: "100%" }}>
                {/* Checkbox - positioned absolutely to avoid layout shifts */}
                <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={handleCheckboxChange}
                    onClick={(e) => e.stopPropagation()}
                    style={{
                        marginTop: 4,
                        cursor: "pointer",
                        flexShrink: 0,
                        width: 16,
                        height: 16
                    }}
                />

                <div style={{ flex: 1, minWidth: 0 }}>
                    {/* Header */}
                    <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 4 }}>
                        {showCategory && (
                            <span style={{
                                padding: "2px 6px",
                                borderRadius: 4,
                                background: catConfig.color + "20",
                                color: catConfig.color,
                                fontSize: 10,
                                fontWeight: 600
                            }}>
                                {catConfig.icon} {catConfig.label}
                            </span>
                        )}
                        {runningStatus && (
                            <span style={{
                                padding: "2px 6px",
                                borderRadius: 4,
                                background: kpiColor(runningStatus),
                                color: "#fff",
                                fontSize: 10
                            }}>
                                {runningStatus}
                            </span>
                        )}
                        {scenario.status && !runningStatus && (
                            <span style={{
                                padding: "2px 6px",
                                borderRadius: 4,
                                background: kpiColor(scenario.status),
                                color: "#fff",
                                fontSize: 10
                            }}>
                                {scenario.status}
                            </span>
                        )}
                    </div>

                    {/* Name */}
                    <div style={{
                        fontWeight: 600,
                        fontSize: 13,
                        marginBottom: 2,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap"
                    }}>
                        {scenario.name || scenario.id}
                    </div>

                    {/* Description */}
                    {scenario.description && (
                        <div style={{ fontSize: 11, color: "#6b7280", marginBottom: 4 }}>
                            {scenario.description.slice(0, 80)}
                        </div>
                    )}

                    {/* Tags */}
                    {scenario.tags && scenario.tags.length > 0 && (
                        <div style={{ display: "flex", gap: 4, flexWrap: "wrap", marginBottom: 4 }}>
                            {scenario.tags.slice(0, 4).map(tag => (
                                <span
                                    key={tag}
                                    style={{
                                        padding: "1px 6px",
                                        borderRadius: 9999,
                                        background: getTagColor(tag) + "20",
                                        color: getTagColor(tag),
                                        fontSize: 10,
                                        fontWeight: 500
                                    }}
                                >
                                    {tag}
                                </span>
                            ))}
                        </div>
                    )}

                    {/* Meta */}
                    <div style={{ fontSize: 10, color: "#9ca3af" }}>
                        {scenario.eventsCount && `${scenario.eventsCount} events`}
                        {scenario.config?.cpId && ` • ${scenario.config.cpId}`}
                    </div>
                </div>

                {/* Actions */}
                <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                    <button
                        onClick={onSelect}
                        style={{
                            padding: "4px 8px",
                            borderRadius: 4,
                            border: "1px solid #d1d5db",
                            background: "#fff",
                            fontSize: 11,
                            cursor: "pointer"
                        }}
                    >
                        Select
                    </button>
                    <button
                        onClick={onEdit}
                        style={{
                            padding: "4px 8px",
                            borderRadius: 4,
                            border: "1px solid #f59e0b",
                            background: "#fff",
                            color: "#f59e0b",
                            fontSize: 11,
                            cursor: "pointer"
                        }}
                    >
                        ✎ Edit
                    </button>
                    <button
                        onClick={() => { console.log("[TNR] Run button clicked!"); onRun(); }}
                        disabled={runningStatus === "running"}
                        style={{
                            padding: "4px 8px",
                            borderRadius: 4,
                            border: "none",
                            background: runningStatus === "running" ? "#d1d5db" : "#2563eb",
                            color: "#fff",
                            fontSize: 11,
                            cursor: runningStatus === "running" ? "not-allowed" : "pointer"
                        }}
                    >
                        ▶ Run
                    </button>
                </div>
            </div>
        </div>
    );
});
