// frontend/src/tabs/MLAnalysisTab.tsx
import React, { useEffect, useState, useRef } from "react";
import { config } from "@/config/env";

/* ========================================================================== */
/* Types                                                                       */
/* ========================================================================== */

type Severity = "LOW" | "MEDIUM" | "HIGH";

interface Alert {
    id: string;
    sessionId: string;
    type: string;
    severity: Severity;
    description: string;
    timestamp: string;
}

interface SessionMetrics {
    sessionId: string;
    cpId: string;
    state: string;
    isCharging: boolean;
    powerKw: number;
    maxPowerKw: number;
    capacityUsage: number;
    soc: number;
    targetSoc: number;
    energyKwh: number;
    hasScp: boolean;
    scpLimitKw?: number;
}

interface ParkSummary {
    totalStations: number;
    charging: number;
    available: number;
    totalCapacityKw: number;
    usedCapacityKw: number;
    availableCapacityKw: number;
    utilizationPercent: number;
    totalEnergyKwh: number;
}

interface ScpAnalysis {
    sessionsWithScp: number;
    sessionsWithoutScp: number;
    scpCoverage: number;
    totalScpLimitKw?: number;
    totalActualPowerKw?: number;
    avgScpUtilization?: number;
    wastedCapacityKw?: number;
}

interface ParkAnalysis {
    summary: ParkSummary;
    scpAnalysis: ScpAnalysis;
    alerts: Alert[];
    sessions: SessionMetrics[];
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
        LOW: "border-blue-400 bg-blue-50 text-blue-800",
        MEDIUM: "border-yellow-400 bg-yellow-50 text-yellow-800",
        HIGH: "border-red-400 bg-red-50 text-red-800"
    };
    return styles[severity];
};

const getScpStatusStyle = (hasScp: boolean, utilization?: number) => {
    if (!hasScp) return "bg-gray-100 text-gray-600";
    if (utilization === undefined) return "bg-green-100 text-green-700";
    if (utilization >= 70) return "bg-green-100 text-green-700";
    if (utilization >= 50) return "bg-yellow-100 text-yellow-700";
    return "bg-red-100 text-red-700";
};

/* ========================================================================== */
/* Composant principal                                                         */
/* ========================================================================== */

export default function MLAnalysisTab() {
    const [parkData, setParkData] = useState<ParkAnalysis | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [autoRefresh, setAutoRefresh] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const intervalRef = useRef<NodeJS.Timeout>();

    // Récupérer l'analyse du parc
    const fetchParkAnalysis = async () => {
        if (isLoading) return;
        setIsLoading(true);
        setError(null);

        try {
            const res = await fetch(`${API_BASE}/api/ml/park`);
            if (!res.ok) throw new Error("Erreur API");
            const data = await res.json();
            setParkData(data);
        } catch (e) {
            setError("Impossible de charger l'analyse du parc");
        } finally {
            setIsLoading(false);
        }
    };

    // Effet: rafraîchissement périodique
    useEffect(() => {
        fetchParkAnalysis();

        if (autoRefresh) {
            intervalRef.current = setInterval(fetchParkAnalysis, 10000);
        }

        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [autoRefresh]);

    /* ======== Rendu ======== */

    return (
        <div className="p-6 space-y-6 max-w-6xl mx-auto">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-xl font-bold text-gray-900">Optimisation du Parc</h1>
                    <p className="text-sm text-gray-500">Analyse SCP et répartition énergétique</p>
                </div>

                <div className="flex items-center gap-4">
                    <label className="flex items-center gap-2 text-sm">
                        <input
                            type="checkbox"
                            checked={autoRefresh}
                            onChange={e => setAutoRefresh(e.target.checked)}
                            className="rounded"
                        />
                        Auto (10s)
                    </label>

                    <button
                        onClick={fetchParkAnalysis}
                        disabled={isLoading}
                        className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
                    >
                        {isLoading ? 'Chargement...' : 'Actualiser'}
                    </button>
                </div>
            </div>

            {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
                    {error}
                </div>
            )}

            {parkData && (
                <>
                    {/* Vue globale du parc */}
                    <div className="grid grid-cols-4 gap-4">
                        <div className="bg-white border rounded-lg p-4 shadow-sm">
                            <div className="text-3xl font-bold text-blue-600">
                                {parkData.summary.totalStations}
                            </div>
                            <div className="text-sm text-gray-500">Bornes totales</div>
                            <div className="text-xs text-gray-400 mt-1">
                                {parkData.summary.charging} en charge / {parkData.summary.available} disponibles
                            </div>
                        </div>

                        <div className="bg-white border rounded-lg p-4 shadow-sm">
                            <div className="text-3xl font-bold text-green-600">
                                {parkData.summary.usedCapacityKw.toFixed(1)} kW
                            </div>
                            <div className="text-sm text-gray-500">Puissance utilisée</div>
                            <div className="text-xs text-gray-400 mt-1">
                                sur {parkData.summary.totalCapacityKw.toFixed(1)} kW capacité
                            </div>
                        </div>

                        <div className="bg-white border rounded-lg p-4 shadow-sm">
                            <div className={`text-3xl font-bold ${
                                parkData.summary.utilizationPercent >= 70 ? 'text-green-600' :
                                parkData.summary.utilizationPercent >= 40 ? 'text-yellow-600' : 'text-red-600'
                            }`}>
                                {parkData.summary.utilizationPercent.toFixed(0)}%
                            </div>
                            <div className="text-sm text-gray-500">Utilisation parc</div>
                            <div className="h-2 bg-gray-200 rounded mt-2">
                                <div
                                    className="h-full bg-blue-500 rounded transition-all"
                                    style={{ width: `${Math.min(parkData.summary.utilizationPercent, 100)}%` }}
                                />
                            </div>
                        </div>

                        <div className="bg-white border rounded-lg p-4 shadow-sm">
                            <div className="text-3xl font-bold text-purple-600">
                                {parkData.summary.totalEnergyKwh.toFixed(1)} kWh
                            </div>
                            <div className="text-sm text-gray-500">Énergie totale</div>
                            <div className="text-xs text-gray-400 mt-1">délivrée aujourd'hui</div>
                        </div>
                    </div>

                    {/* Analyse SCP */}
                    <div className="bg-white border rounded-lg p-5 shadow-sm">
                        <h2 className="text-lg font-semibold text-gray-800 mb-4">Analyse SCP (Smart Charging Profiles)</h2>

                        <div className="grid grid-cols-2 gap-6">
                            {/* Couverture SCP */}
                            <div>
                                <div className="flex items-center justify-between mb-2">
                                    <span className="text-sm text-gray-600">Couverture SCP</span>
                                    <span className={`text-lg font-bold ${
                                        parkData.scpAnalysis.scpCoverage >= 80 ? 'text-green-600' :
                                        parkData.scpAnalysis.scpCoverage >= 50 ? 'text-yellow-600' : 'text-red-600'
                                    }`}>
                                        {parkData.scpAnalysis.scpCoverage.toFixed(0)}%
                                    </span>
                                </div>
                                <div className="h-3 bg-gray-200 rounded">
                                    <div
                                        className={`h-full rounded transition-all ${
                                            parkData.scpAnalysis.scpCoverage >= 80 ? 'bg-green-500' :
                                            parkData.scpAnalysis.scpCoverage >= 50 ? 'bg-yellow-500' : 'bg-red-500'
                                        }`}
                                        style={{ width: `${parkData.scpAnalysis.scpCoverage}%` }}
                                    />
                                </div>
                                <div className="flex justify-between text-xs text-gray-500 mt-1">
                                    <span>{parkData.scpAnalysis.sessionsWithScp} avec SCP</span>
                                    <span>{parkData.scpAnalysis.sessionsWithoutScp} sans SCP</span>
                                </div>
                            </div>

                            {/* Utilisation SCP */}
                            {parkData.scpAnalysis.avgScpUtilization !== undefined && (
                                <div>
                                    <div className="flex items-center justify-between mb-2">
                                        <span className="text-sm text-gray-600">Utilisation moyenne SCP</span>
                                        <span className={`text-lg font-bold ${
                                            parkData.scpAnalysis.avgScpUtilization >= 70 ? 'text-green-600' :
                                            parkData.scpAnalysis.avgScpUtilization >= 50 ? 'text-yellow-600' : 'text-red-600'
                                        }`}>
                                            {parkData.scpAnalysis.avgScpUtilization.toFixed(0)}%
                                        </span>
                                    </div>
                                    <div className="h-3 bg-gray-200 rounded">
                                        <div
                                            className={`h-full rounded transition-all ${
                                                parkData.scpAnalysis.avgScpUtilization >= 70 ? 'bg-green-500' :
                                                parkData.scpAnalysis.avgScpUtilization >= 50 ? 'bg-yellow-500' : 'bg-red-500'
                                            }`}
                                            style={{ width: `${parkData.scpAnalysis.avgScpUtilization}%` }}
                                        />
                                    </div>
                                    <div className="flex justify-between text-xs text-gray-500 mt-1">
                                        <span>{parkData.scpAnalysis.totalActualPowerKw?.toFixed(1)} kW utilisés</span>
                                        <span>{parkData.scpAnalysis.wastedCapacityKw?.toFixed(1)} kW gaspillés</span>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Alertes d'optimisation */}
                    {parkData.alerts.length > 0 && (
                        <div className="space-y-3">
                            <h2 className="text-lg font-semibold text-gray-800">
                                Alertes d'optimisation ({parkData.alerts.length})
                            </h2>
                            <div className="space-y-2">
                                {parkData.alerts.map(alert => (
                                    <div
                                        key={alert.id}
                                        className={`border-l-4 p-3 rounded ${getSeverityStyle(alert.severity)}`}
                                    >
                                        <div className="flex items-center justify-between">
                                            <div className="flex items-center gap-3">
                                                <span className="font-medium">{alert.type}</span>
                                                <span className="text-xs px-2 py-0.5 bg-white rounded border">
                                                    {alert.sessionId === 'PARK' ? 'Parc' : alert.sessionId.slice(0, 8)}
                                                </span>
                                            </div>
                                            <span className="text-xs opacity-70">
                                                {alert.severity}
                                            </span>
                                        </div>
                                        <p className="text-sm mt-1">{alert.description}</p>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Sessions en charge avec statut SCP */}
                    {parkData.sessions.length > 0 && (
                        <div className="space-y-3">
                            <h2 className="text-lg font-semibold text-gray-800">
                                Sessions en charge ({parkData.sessions.length})
                            </h2>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead className="bg-gray-50">
                                        <tr>
                                            <th className="text-left px-4 py-2">Borne</th>
                                            <th className="text-left px-4 py-2">État</th>
                                            <th className="text-right px-4 py-2">Puissance</th>
                                            <th className="text-right px-4 py-2">SoC</th>
                                            <th className="text-right px-4 py-2">Énergie</th>
                                            <th className="text-center px-4 py-2">SCP</th>
                                            <th className="text-right px-4 py-2">Limite SCP</th>
                                            <th className="text-right px-4 py-2">Utilisation</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y">
                                        {parkData.sessions.map(session => {
                                            const scpUtil = session.scpLimitKw && session.scpLimitKw > 0
                                                ? (session.powerKw / session.scpLimitKw) * 100
                                                : undefined;

                                            return (
                                                <tr key={session.sessionId} className="hover:bg-gray-50">
                                                    <td className="px-4 py-3">
                                                        <div className="font-medium">{session.cpId}</div>
                                                        <div className="text-xs text-gray-400">
                                                            {session.sessionId.slice(0, 8)}...
                                                        </div>
                                                    </td>
                                                    <td className="px-4 py-3">
                                                        <span className={`inline-flex items-center gap-1 ${
                                                            session.isCharging ? 'text-green-600' : 'text-gray-500'
                                                        }`}>
                                                            <span className={`w-2 h-2 rounded-full ${
                                                                session.isCharging ? 'bg-green-500' : 'bg-gray-400'
                                                            }`} />
                                                            {session.state}
                                                        </span>
                                                    </td>
                                                    <td className="px-4 py-3 text-right font-medium">
                                                        {session.powerKw.toFixed(1)} kW
                                                    </td>
                                                    <td className="px-4 py-3 text-right">
                                                        {session.soc.toFixed(0)}% → {session.targetSoc.toFixed(0)}%
                                                    </td>
                                                    <td className="px-4 py-3 text-right">
                                                        {session.energyKwh.toFixed(2)} kWh
                                                    </td>
                                                    <td className="px-4 py-3 text-center">
                                                        <span className={`px-2 py-1 rounded text-xs font-medium ${
                                                            getScpStatusStyle(session.hasScp, scpUtil)
                                                        }`}>
                                                            {session.hasScp ? 'Actif' : 'Aucun'}
                                                        </span>
                                                    </td>
                                                    <td className="px-4 py-3 text-right">
                                                        {session.scpLimitKw != null
                                                            ? `${session.scpLimitKw.toFixed(1)} kW`
                                                            : '-'}
                                                    </td>
                                                    <td className="px-4 py-3 text-right">
                                                        {scpUtil !== undefined ? (
                                                            <span className={`font-medium ${
                                                                scpUtil >= 70 ? 'text-green-600' :
                                                                scpUtil >= 50 ? 'text-yellow-600' : 'text-red-600'
                                                            }`}>
                                                                {scpUtil.toFixed(0)}%
                                                            </span>
                                                        ) : (
                                                            <span className="text-gray-400">-</span>
                                                        )}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}

                    {/* Message si aucune session en charge */}
                    {parkData.sessions.length === 0 && (
                        <div className="text-center py-12 bg-gray-50 rounded-lg">
                            <p className="text-gray-500">Aucune session en charge</p>
                            <p className="text-sm text-gray-400 mt-2">
                                Les métriques d'optimisation apparaîtront lorsqu'une charge sera active
                            </p>
                        </div>
                    )}
                </>
            )}

            {/* Chargement initial */}
            {!parkData && isLoading && (
                <div className="text-center py-12">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
                    <p className="text-gray-500 mt-4">Chargement de l'analyse...</p>
                </div>
            )}
        </div>
    );
}
