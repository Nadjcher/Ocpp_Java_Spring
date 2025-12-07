// frontend/src/tabs/SimulGPMTab.tsx
// Vue d'ensemble GPM - Affiche toutes les sessions actives depuis l'API

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { fetchJSON } from '@/utils/evse.utils';

const COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#14B8A6', '#F97316'];

interface SessionData {
  id: string;
  cpId: string;
  status: string;
  isConnected: boolean;
  isCharging?: boolean;
  wsUrl?: string;
  txId?: number;
  meterWh?: number;
  soc?: number;
  energy?: number;
  metrics?: {
    soc?: number;
    activePower?: number;
    offeredPower?: number;
    energy?: number;
    voltage?: number;
    current?: number;
  };
}

interface ChartPoint {
  time: number;
  [key: string]: number;
}

export default function SimulGPMTab() {
  const [sessions, setSessions] = useState<SessionData[]>([]);
  const [chartData, setChartData] = useState<ChartPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chartDataRef = useRef<ChartPoint[]>([]);

  // Charger les sessions depuis l'API
  const loadSessions = useCallback(async () => {
    try {
      const data = await fetchJSON<SessionData[]>('/api/simu');
      setSessions(data || []);
      setError(null);
      return data || [];
    } catch (e: any) {
      console.error('Error loading sessions:', e);
      setError(e.message || 'Erreur de chargement');
      return [];
    }
  }, []);

  // Refresh sessions
  const refreshSessions = useCallback(async () => {
    setLoading(true);
    await loadSessions();
    setLoading(false);
  }, [loadSessions]);

  // Polling régulier
  useEffect(() => {
    refreshSessions();
    const interval = setInterval(loadSessions, 2000); // Refresh toutes les 2 secondes
    return () => clearInterval(interval);
  }, [loadSessions, refreshSessions]);

  // Mettre à jour les données du graphique
  useEffect(() => {
    const chargingSessions = sessions.filter(s =>
      s.status === 'charging' || s.status === 'started' || s.isCharging
    );
    if (chargingSessions.length === 0) return;

    const now = Date.now();
    const newPoint: ChartPoint = { time: now };

    chargingSessions.forEach(session => {
      const power = session.metrics?.activePower || 0;
      const soc = session.metrics?.soc || session.soc || 0;
      newPoint[`${session.cpId}_power`] = power / 1000;
      newPoint[`${session.cpId}_soc`] = soc;
    });

    chartDataRef.current = [...chartDataRef.current, newPoint].slice(-300); // 5 min @ 1s
    setChartData([...chartDataRef.current]);
  }, [sessions]);

  // Helper pour déterminer si une session est en charge
  const isSessionCharging = (session: SessionData) => {
    return session.status === 'charging' || session.status === 'started' || session.isCharging;
  };

  // Sessions en charge uniquement
  const chargingSessions = sessions.filter(isSessionCharging);
  const connectedSessions = sessions.filter(s => s.isConnected);

  // Statistiques calculées
  const stats = {
    totalSessions: sessions.length,
    connectedCount: connectedSessions.length,
    chargingCount: chargingSessions.length,
    totalPowerKW: chargingSessions.reduce((sum, s) => sum + (s.metrics?.activePower || 0), 0) / 1000,
    totalEnergyKWh: sessions.reduce((sum, s) => sum + (s.metrics?.energy || s.energy || s.meterWh || 0), 0) / 1000,
    avgSoc: chargingSessions.length > 0
      ? chargingSessions.reduce((sum, s) => sum + (s.metrics?.soc || s.soc || 0), 0) / chargingSessions.length
      : 0
  };

  // Fonction pour obtenir la couleur de statut
  const getStatusColor = (session: SessionData) => {
    if (isSessionCharging(session)) return 'border-emerald-500';
    if (session.isConnected) return 'border-blue-500';
    if (session.status === 'authorized') return 'border-yellow-500';
    return 'border-gray-300';
  };

  const getStatusBadge = (session: SessionData) => {
    if (isSessionCharging(session)) return { bg: 'bg-emerald-100', text: 'text-emerald-700', label: 'En charge' };
    if (session.status === 'authorized') return { bg: 'bg-yellow-100', text: 'text-yellow-700', label: 'Autorisé' };
    if (session.isConnected) return { bg: 'bg-blue-100', text: 'text-blue-700', label: 'Connecté' };
    return { bg: 'bg-gray-100', text: 'text-gray-700', label: session.status || 'Déconnecté' };
  };

  return (
    <div className="p-6 bg-gray-50 min-h-screen">
      {/* En-tête avec stats */}
      <div className="bg-white rounded-lg shadow-md p-6 mb-6">
        <div className="flex justify-between items-center mb-4">
          <h1 className="text-2xl font-bold text-gray-800">
            Vue d'ensemble GPM - Multi-Sessions
          </h1>
          <div className="flex items-center gap-3">
            {error && (
              <span className="text-red-500 text-sm">{error}</span>
            )}
            <button
              onClick={refreshSessions}
              disabled={loading}
              className={`px-4 py-2 bg-gray-100 hover:bg-gray-200 rounded flex items-center gap-2 text-gray-700 ${
                loading ? 'opacity-50 cursor-not-allowed' : ''
              }`}
            >
              {loading ? (
                <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              ) : (
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              )}
              Rafraîchir
            </button>
          </div>
        </div>

        <div className="grid grid-cols-6 gap-4">
          <div className="bg-blue-50 p-4 rounded-lg">
            <div className="text-sm text-blue-600">Total Sessions</div>
            <div className="text-3xl font-bold text-blue-800">{stats.totalSessions}</div>
          </div>
          <div className="bg-green-50 p-4 rounded-lg">
            <div className="text-sm text-green-600">Connectées</div>
            <div className="text-3xl font-bold text-green-800">{stats.connectedCount}</div>
          </div>
          <div className="bg-emerald-50 p-4 rounded-lg">
            <div className="text-sm text-emerald-600">En charge</div>
            <div className={`text-3xl font-bold text-emerald-800 ${stats.chargingCount > 0 ? 'animate-pulse' : ''}`}>
              {stats.chargingCount}
            </div>
          </div>
          <div className="bg-yellow-50 p-4 rounded-lg">
            <div className="text-sm text-yellow-600">Puissance totale</div>
            <div className="text-3xl font-bold text-yellow-800">{stats.totalPowerKW.toFixed(1)} kW</div>
          </div>
          <div className="bg-purple-50 p-4 rounded-lg">
            <div className="text-sm text-purple-600">Énergie totale</div>
            <div className="text-3xl font-bold text-purple-800">{stats.totalEnergyKWh.toFixed(1)} kWh</div>
          </div>
          <div className="bg-pink-50 p-4 rounded-lg">
            <div className="text-sm text-pink-600">SoC moyen</div>
            <div className="text-3xl font-bold text-pink-800">{stats.avgSoc.toFixed(0)}%</div>
          </div>
        </div>
      </div>

      {/* Graphique temps réel simple (SVG) */}
      {chargingSessions.length > 0 && chartData.length > 1 && (
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4 text-gray-800">Puissance en temps réel</h2>
          <div className="h-64 relative">
            <svg className="w-full h-full" viewBox="0 0 800 250" preserveAspectRatio="none">
              {/* Grid */}
              {[0, 25, 50, 75, 100].map((pct, i) => (
                <g key={i}>
                  <line x1="50" y1={200 - pct * 2} x2="780" y2={200 - pct * 2} stroke="#e5e7eb" strokeWidth="1" />
                  <text x="45" y={205 - pct * 2} fill="#9ca3af" fontSize="10" textAnchor="end">
                    {(pct * Math.max(stats.totalPowerKW, 1) / 100 * 1.5).toFixed(0)}
                  </text>
                </g>
              ))}

              {/* Lines for each session */}
              {chargingSessions.map((session, idx) => {
                const color = COLORS[idx % COLORS.length];
                const points = chartData.map((d, i) => {
                  const power = d[`${session.cpId}_power`] || 0;
                  const maxPower = Math.max(...chartData.map(dd => dd[`${session.cpId}_power`] || 0), 1);
                  const x = 50 + (i / Math.max(chartData.length - 1, 1)) * 730;
                  const y = 200 - (power / (maxPower * 1.2)) * 180;
                  return `${x},${y}`;
                }).join(' ');

                return (
                  <g key={session.id}>
                    <polyline
                      fill="none"
                      stroke={color}
                      strokeWidth="2"
                      points={points}
                    />
                  </g>
                );
              })}
            </svg>

            {/* Legend */}
            <div className="absolute top-2 right-2 flex flex-wrap gap-3">
              {chargingSessions.map((session, idx) => (
                <div key={session.id} className="flex items-center gap-1 text-xs">
                  <span
                    className="w-3 h-3 rounded-full"
                    style={{ backgroundColor: COLORS[idx % COLORS.length] }}
                  />
                  <span>{session.cpId}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Liste des sessions */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {sessions.map((session, i) => {
          const statusBadge = getStatusBadge(session);
          const soc = session.metrics?.soc || session.soc || 0;
          const power = session.metrics?.activePower || 0;
          const energy = session.metrics?.energy || session.energy || session.meterWh || 0;
          const current = session.metrics?.current || 0;
          const charging = isSessionCharging(session);

          return (
            <div
              key={session.id}
              className={`bg-white rounded-lg shadow-md p-4 border-l-4 ${getStatusColor(session)}
                         hover:shadow-lg transition-shadow duration-200`}
            >
              <div className="flex justify-between items-center mb-3">
                <h3 className="font-semibold text-lg flex items-center gap-2 text-gray-800">
                  <span
                    className={`w-3 h-3 rounded-full ${charging ? 'animate-pulse' : ''}`}
                    style={{ backgroundColor: COLORS[i % COLORS.length] }}
                  />
                  {session.cpId}
                </h3>
                <span className={`px-2 py-1 rounded text-sm ${statusBadge.bg} ${statusBadge.text}`}>
                  {statusBadge.label}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <span className="text-gray-500">SoC:</span>
                  <span className="ml-2 font-semibold text-gray-800">{soc.toFixed(1)}%</span>
                </div>
                <div>
                  <span className="text-gray-500">Puissance:</span>
                  <span className="ml-2 font-semibold text-gray-800">{(power / 1000).toFixed(2)} kW</span>
                </div>
                <div>
                  <span className="text-gray-500">Énergie:</span>
                  <span className="ml-2 font-semibold text-gray-800">{(energy / 1000).toFixed(2)} kWh</span>
                </div>
                <div>
                  <span className="text-gray-500">Courant:</span>
                  <span className="ml-2 font-semibold text-gray-800">{current.toFixed(1)} A</span>
                </div>
              </div>

              {/* Progress bar SoC */}
              <div className="mt-3">
                <div className="flex justify-between text-xs text-gray-500 mb-1">
                  <span>SoC</span>
                  <span>{soc.toFixed(0)}%</span>
                </div>
                <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                  <div
                    className={`h-full transition-all duration-500 ${
                      charging ? 'bg-emerald-500' : 'bg-blue-500'
                    }`}
                    style={{ width: `${Math.min(soc, 100)}%` }}
                  />
                </div>
              </div>

              {session.txId && (
                <div className="mt-2 text-xs text-gray-500 flex items-center gap-1">
                  <span className="text-emerald-500">●</span>
                  Transaction: #{session.txId}
                </div>
              )}

              {session.wsUrl && (
                <div className="mt-1 text-xs text-gray-400 truncate" title={session.wsUrl}>
                  {session.wsUrl.replace('wss://', '').split('/')[0]}
                </div>
              )}
            </div>
          );
        })}

        {sessions.length === 0 && (
          <div className="col-span-full bg-white rounded-lg shadow-md p-8 text-center text-gray-500">
            <div className="text-4xl mb-4">📡</div>
            <p className="text-lg">Aucune session active</p>
            <p className="text-sm mt-2">Créez une session dans l'onglet "Simu EVSE"</p>
          </div>
        )}
      </div>

      {/* Footer avec info */}
      <div className="mt-6 text-center text-xs text-gray-400">
        Mise à jour automatique toutes les 2 secondes • {sessions.length} session(s) chargée(s)
      </div>
    </div>
  );
}
