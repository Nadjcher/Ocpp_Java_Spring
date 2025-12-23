// frontend/src/tabs/MLAnalysisTab.tsx
import React, { useEffect, useState, useRef } from "react";
import { config } from "@/config/env";

/* ========================================================================== */
/* Types                                                                       */
/* ========================================================================== */

type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

interface Anomaly {
    id: string;
    timestamp: string;
    sessionId: string;
    type: string;
    severity: Severity;
    description: string;
    recommendation?: string;
}

interface Prediction {
    sessionId: string;
    currentSoc: number;
    targetSoc: number;
    currentEnergyKwh: number;
    remainingEnergyKwh: number;
    remainingMinutes: number;
    estimatedEndTime: string | null;
}

interface Metrics {
    sessionId: string;
    state: string;
    isCharging: boolean;
    powerKw: number;
    maxPowerKw: number;
    efficiency: number;
    energyKwh: number;
    soc: number;
    targetSoc: number;
    socRemaining: number;
    durationMinutes?: number;
}

interface AnalysisResult {
    metrics: Metrics;
    anomalies: Anomaly[];
    prediction: Prediction;
}

/* ========================================================================== */
/* Config & Helpers                                                           */
/* ========================================================================== */

const API_BASE = (() => {
    const isDev = typeof import.meta !== "undefined" && import.meta.env?.DEV;
    if (isDev) return "";
    if (typeof window !== 'undefined') {
        const stored = window.localStorage.getItem("runner_api");
        if (stored) return stored;
    }
    return config.apiUrl || "http://localhost:8887";
})();

const getSeverityStyle = (severity: Severity) => {
    const styles = {
        LOW: "border-blue-400 bg-blue-50",
        MEDIUM: "border-yellow-400 bg-yellow-50",
        HIGH: "border-orange-400 bg-orange-50",
        CRITICAL: "border-red-400 bg-red-50"
    };
    return styles[severity];
};

const formatMinutes = (mins: number) => {
    if (mins < 60) return `${mins} min`;
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return `${h}h${m.toString().padStart(2, '0')}`;
};

/* ========================================================================== */
/* Composant principal                                                         */
/* ========================================================================== */

export default function MLAnalysisTab() {
    const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
    const [analyses, setAnalyses] = useState<AnalysisResult[]>([]);
    const [activeSessions, setActiveSessions] = useState<string[]>([]);
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [autoAnalysis, setAutoAnalysis] = useState(true);
    const [status, setStatus] = useState<{ ready: boolean; sessionsCharging: number } | null>(null);

    const intervalRef = useRef<NodeJS.Timeout>();

    // Récupérer les sessions actives
    const fetchActiveSessions = async () => {
        try {
            const res = await fetch(`${API_BASE}/api/simu`);
            const sessions = await res.json();
            return sessions
                .filter((s: any) => s.status === 'started' || s.status === 'charging')
                .map((s: any) => s.id);
        } catch {
            return [];
        }
    };

    // Analyser une session
    const analyzeSession = async (sessionId: string): Promise<AnalysisResult | null> => {
        try {
            const res = await fetch(`${API_BASE}/api/ml/analyze/${sessionId}`, { method: 'POST' });
            return res.json();
        } catch {
            return null;
        }
    };

    // Récupérer le statut
    const fetchStatus = async () => {
        try {
            const res = await fetch(`${API_BASE}/api/ml/status`);
            setStatus(await res.json());
        } catch { }
    };

    // Lancer l'analyse
    const performAnalysis = async () => {
        if (isAnalyzing) return;
        setIsAnalyzing(true);

        try {
            const sessions = await fetchActiveSessions();
            setActiveSessions(sessions);

            if (sessions.length === 0) {
                setAnalyses([]);
                return;
            }

            const results = await Promise.all(sessions.map(analyzeSession));
            const validResults = results.filter((r): r is AnalysisResult => r !== null);

            setAnalyses(validResults);

            // Collecter les nouvelles anomalies
            const newAnomalies = validResults.flatMap(r => r.anomalies);
            if (newAnomalies.length > 0) {
                setAnomalies(prev => {
                    const existingIds = new Set(prev.map(a => a.id));
                    const unique = newAnomalies.filter(a => !existingIds.has(a.id));
                    return [...unique, ...prev].slice(0, 30);
                });
            }

            await fetchStatus();
        } finally {
            setIsAnalyzing(false);
        }
    };

    // Effet: analyse périodique
    useEffect(() => {
        fetchStatus();

        if (autoAnalysis) {
            performAnalysis();
            intervalRef.current = setInterval(performAnalysis, 15000); // 15 secondes
        }

        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [autoAnalysis]);

    /* ======== Rendu ======== */

    return (
        <div className="p-6 space-y-6 max-w-6xl mx-auto">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-xl font-bold text-gray-900">Analyse des sessions</h1>
                    <p className="text-sm text-gray-500">Surveillance en temps réel</p>
                </div>

                <div className="flex items-center gap-4">
                    <label className="flex items-center gap-2 text-sm">
                        <input
                            type="checkbox"
                            checked={autoAnalysis}
                            onChange={e => setAutoAnalysis(e.target.checked)}
                            className="rounded"
                        />
                        Auto (15s)
                    </label>

                    <button
                        onClick={performAnalysis}
                        disabled={isAnalyzing}
                        className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
                    >
                        {isAnalyzing ? 'Analyse...' : 'Analyser'}
                    </button>
                </div>
            </div>

            {/* Statut rapide */}
            <div className="flex gap-4">
                <div className="px-4 py-2 bg-gray-100 rounded">
                    <span className="text-gray-600 text-sm">Sessions actives: </span>
                    <span className="font-semibold">{activeSessions.length}</span>
                </div>
                <div className="px-4 py-2 bg-gray-100 rounded">
                    <span className="text-gray-600 text-sm">En charge: </span>
                    <span className="font-semibold">{status?.sessionsCharging ?? 0}</span>
                </div>
                <div className="px-4 py-2 bg-gray-100 rounded">
                    <span className="text-gray-600 text-sm">Anomalies: </span>
                    <span className={`font-semibold ${anomalies.length > 0 ? 'text-orange-600' : 'text-green-600'}`}>
                        {anomalies.length}
                    </span>
                </div>
            </div>

            {/* Métriques des sessions en charge */}
            {analyses.length > 0 && (
                <div className="space-y-4">
                    <h2 className="text-lg font-semibold text-gray-800">Sessions en cours</h2>

                    <div className="grid gap-4">
                        {analyses.map(analysis => (
                            <div key={analysis.metrics.sessionId} className="border rounded-lg p-4 bg-white shadow-sm">
                                <div className="flex items-center justify-between mb-3">
                                    <div className="flex items-center gap-2">
                                        <span className={`w-2 h-2 rounded-full ${analysis.metrics.isCharging ? 'bg-green-500' : 'bg-gray-400'}`} />
                                        <span className="font-medium">{analysis.metrics.sessionId.slice(0, 8)}...</span>
                                        <span className="text-sm text-gray-500">{analysis.metrics.state}</span>
                                    </div>
                                    {analysis.metrics.durationMinutes !== undefined && (
                                        <span className="text-sm text-gray-500">
                                            {formatMinutes(analysis.metrics.durationMinutes)}
                                        </span>
                                    )}
                                </div>

                                {/* Métriques clés */}
                                <div className="grid grid-cols-5 gap-4 text-center">
                                    <div>
                                        <div className="text-2xl font-bold text-blue-600">
                                            {analysis.metrics.powerKw.toFixed(1)}
                                        </div>
                                        <div className="text-xs text-gray-500">kW</div>
                                    </div>
                                    <div>
                                        <div className="text-2xl font-bold text-green-600">
                                            {analysis.metrics.soc.toFixed(0)}%
                                        </div>
                                        <div className="text-xs text-gray-500">SoC</div>
                                    </div>
                                    <div>
                                        <div className="text-2xl font-bold text-purple-600">
                                            {analysis.metrics.energyKwh.toFixed(1)}
                                        </div>
                                        <div className="text-xs text-gray-500">kWh</div>
                                    </div>
                                    <div>
                                        <div className={`text-2xl font-bold ${
                                            analysis.metrics.efficiency >= 70 ? 'text-green-600' :
                                            analysis.metrics.efficiency >= 50 ? 'text-yellow-600' : 'text-red-600'
                                        }`}>
                                            {analysis.metrics.efficiency.toFixed(0)}%
                                        </div>
                                        <div className="text-xs text-gray-500">Efficacité</div>
                                    </div>
                                    <div>
                                        <div className="text-2xl font-bold text-orange-600">
                                            {analysis.prediction.remainingMinutes > 0
                                                ? formatMinutes(analysis.prediction.remainingMinutes)
                                                : '-'}
                                        </div>
                                        <div className="text-xs text-gray-500">Restant</div>
                                    </div>
                                </div>

                                {/* Barre de progression SoC */}
                                <div className="mt-3">
                                    <div className="flex justify-between text-xs text-gray-500 mb-1">
                                        <span>SoC: {analysis.metrics.soc.toFixed(0)}%</span>
                                        <span>Cible: {analysis.metrics.targetSoc.toFixed(0)}%</span>
                                    </div>
                                    <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                                        <div
                                            className="h-full bg-green-500 transition-all"
                                            style={{ width: `${(analysis.metrics.soc / analysis.metrics.targetSoc) * 100}%` }}
                                        />
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Message si pas de session */}
            {analyses.length === 0 && !isAnalyzing && (
                <div className="text-center py-12 text-gray-500">
                    <p>Aucune session en cours</p>
                    <p className="text-sm mt-2">Démarrez une charge pour voir les métriques</p>
                </div>
            )}

            {/* Anomalies */}
            {anomalies.length > 0 && (
                <div className="space-y-3">
                    <div className="flex items-center justify-between">
                        <h2 className="text-lg font-semibold text-gray-800">Anomalies détectées</h2>
                        <button
                            onClick={() => setAnomalies([])}
                            className="text-sm text-gray-500 hover:text-gray-700"
                        >
                            Effacer
                        </button>
                    </div>

                    <div className="space-y-2">
                        {anomalies.map(anomaly => (
                            <div
                                key={anomaly.id}
                                className={`border-l-4 p-3 rounded ${getSeverityStyle(anomaly.severity)}`}
                            >
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-2">
                                        <span className="font-medium">{anomaly.type}</span>
                                        <span className="text-xs px-2 py-0.5 bg-white rounded">
                                            {anomaly.severity}
                                        </span>
                                    </div>
                                    <span className="text-xs text-gray-500">
                                        {new Date(anomaly.timestamp).toLocaleTimeString()}
                                    </span>
                                </div>
                                <p className="text-sm text-gray-700 mt-1">{anomaly.description}</p>
                                {anomaly.recommendation && (
                                    <p className="text-xs text-gray-500 mt-1">
                                        💡 {anomaly.recommendation}
                                    </p>
                                )}
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
