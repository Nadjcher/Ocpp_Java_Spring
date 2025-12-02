// frontend/src/tabs/SimulGPMTab.tsx
// Vue d'ensemble GPM - Affiche toutes les sessions en temps réel via localStorage

import React, { useState, useEffect, useRef, useCallback } from 'react';

const COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#14B8A6', '#F97316'];
const STORAGE_KEY = 'evse-sessions-broadcast';

interface BroadcastSession {
  sessionId: string;
  cpId: string;
  status: string;
  isConnected: boolean;
  isCharging: boolean;
  soc: number;
  activePower: number;
  offeredPower: number;
  energy: number;
  voltage: number;
  current: number;
  transactionId?: number;
}

export default function SimulGPMTab() {
  const [sessions, setSessions] = useState<BroadcastSession[]>([]);
  const [chartData, setChartData] = useState<any[]>([]);
  const chartDataRef = useRef<any[]>([]);

  // Charger les sessions depuis localStorage
  const loadSessions = useCallback(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const data = JSON.parse(raw);
        return data.sessions || [];
      }
    } catch (e) {
      console.error('Error loading sessions:', e);
    }
    return [];
  }, []);

  // Refresh sessions
  const refreshSessions = useCallback(() => {
    const data = loadSessions();
    setSessions(data);
  }, [loadSessions]);

  // Polling régulier + écoute storage
  useEffect(() => {
    refreshSessions();
    const interval = setInterval(refreshSessions, 1000);

    const handleStorage = (e: StorageEvent) => {
      if (e.key === STORAGE_KEY) {
        refreshSessions();
      }
    };
    window.addEventListener('storage', handleStorage);

    return () => {
      clearInterval(interval);
      window.removeEventListener('storage', handleStorage);
    };
  }, [refreshSessions]);

  // Mettre a jour les donnees du graphique
  useEffect(() => {
    const chargingSessions = sessions.filter(s => s.isCharging);
    if (chargingSessions.length === 0) return;

    const now = Date.now();
    const newPoint: any = { time: now };

    chargingSessions.forEach(session => {
      newPoint[`${session.cpId}_power`] = session.activePower / 1000;
      newPoint[`${session.cpId}_soc`] = session.soc;
    });

    chartDataRef.current = [...chartDataRef.current, newPoint].slice(-300); // 5 min @ 1s
    setChartData([...chartDataRef.current]);
  }, [sessions]);

  // Sessions en charge uniquement
  const chargingSessions = sessions.filter(s => s.isCharging);
  const allConnected = sessions.filter(s => s.isConnected);

  // Statistiques calculées
  const stats = {
    totalSessions: sessions.length,
    connectedCount: allConnected.length,
    chargingCount: chargingSessions.length,
    totalPowerKW: chargingSessions.reduce((sum, s) => sum + s.activePower, 0) / 1000,
    totalEnergyKWh: sessions.reduce((sum, s) => sum + s.energy, 0) / 1000,
    avgSoc: chargingSessions.length > 0
      ? chargingSessions.reduce((sum, s) => sum + s.soc, 0) / chargingSessions.length
      : 0
  };

  return (
    <div className="p-6 bg-gray-50 min-h-screen">
      {/* En-tete avec stats */}
      <div className="bg-white rounded-lg shadow-md p-6 mb-6">
        <div className="flex justify-between items-center mb-4">
          <h1 className="text-2xl font-bold text-gray-800">Vue d'ensemble GPM</h1>
          <button
            onClick={refreshSessions}
            className="px-4 py-2 bg-gray-100 hover:bg-gray-200 rounded flex items-center gap-2 text-gray-700"
          >
            Rafraichir
          </button>
        </div>

        <div className="grid grid-cols-6 gap-4">
          <div className="bg-blue-50 p-4 rounded-lg">
            <div className="text-sm text-blue-600">Total Sessions</div>
            <div className="text-3xl font-bold text-blue-800">{stats.totalSessions}</div>
          </div>
          <div className="bg-green-50 p-4 rounded-lg">
            <div className="text-sm text-green-600">Connectees</div>
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
            <div className="text-sm text-purple-600">Energie totale</div>
            <div className="text-3xl font-bold text-purple-800">{stats.totalEnergyKWh.toFixed(1)} kWh</div>
          </div>
          <div className="bg-pink-50 p-4 rounded-lg">
            <div className="text-sm text-pink-600">SoC moyen</div>
            <div className="text-3xl font-bold text-pink-800">{stats.avgSoc.toFixed(0)}%</div>
          </div>
        </div>
      </div>

      {/* Graphique temps reel simple (SVG) */}
      {chargingSessions.length > 0 && chartData.length > 1 && (
        <div className="bg-white rounded-lg shadow-md p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4 text-gray-800">Puissance en temps reel</h2>
          <div className="h-64 relative">
            <svg className="w-full h-full" viewBox="0 0 800 250" preserveAspectRatio="none">
              {/* Grid */}
              {[0, 25, 50, 75, 100].map((pct, i) => (
                <g key={i}>
                  <line x1="50" y1={200 - pct * 2} x2="780" y2={200 - pct * 2} stroke="#e5e7eb" strokeWidth="1" />
                  <text x="45" y={205 - pct * 2} fill="#9ca3af" fontSize="10" textAnchor="end">
                    {(pct * stats.totalPowerKW / 100 * 1.5).toFixed(0)}
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
                  <g key={session.sessionId}>
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
                <div key={session.sessionId} className="flex items-center gap-1 text-xs">
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
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {sessions.map((session, i) => (
          <div
            key={session.sessionId}
            className={`bg-white rounded-lg shadow-md p-4 border-l-4 ${
              session.isCharging
                ? 'border-emerald-500'
                : session.isConnected
                  ? 'border-blue-500'
                  : 'border-gray-300'
            }`}
          >
            <div className="flex justify-between items-center mb-3">
              <h3 className="font-semibold text-lg flex items-center gap-2 text-gray-800">
                <span
                  className="w-3 h-3 rounded-full"
                  style={{ backgroundColor: COLORS[i % COLORS.length] }}
                />
                {session.cpId}
              </h3>
              <span className={`px-2 py-1 rounded text-sm ${
                session.isCharging
                  ? 'bg-emerald-100 text-emerald-700'
                  : session.isConnected
                    ? 'bg-blue-100 text-blue-700'
                    : 'bg-gray-100 text-gray-700'
              }`}>
                {session.isCharging ? 'En charge' : session.status}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="text-gray-500">SoC:</span>
                <span className="ml-2 font-semibold text-gray-800">{session.soc.toFixed(1)}%</span>
              </div>
              <div>
                <span className="text-gray-500">Puissance:</span>
                <span className="ml-2 font-semibold text-gray-800">{(session.activePower / 1000).toFixed(2)} kW</span>
              </div>
              <div>
                <span className="text-gray-500">Energie:</span>
                <span className="ml-2 font-semibold text-gray-800">{(session.energy / 1000).toFixed(2)} kWh</span>
              </div>
              <div>
                <span className="text-gray-500">Courant:</span>
                <span className="ml-2 font-semibold text-gray-800">{session.current.toFixed(1)} A</span>
              </div>
            </div>

            {/* Progress bar SoC */}
            <div className="mt-3">
              <div className="flex justify-between text-xs text-gray-500 mb-1">
                <span>SoC</span>
                <span>{session.soc.toFixed(0)}%</span>
              </div>
              <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className={`h-full transition-all duration-500 ${
                    session.isCharging ? 'bg-emerald-500' : 'bg-blue-500'
                  }`}
                  style={{ width: `${Math.min(session.soc, 100)}%` }}
                />
              </div>
            </div>

            {session.transactionId && (
              <div className="mt-2 text-xs text-gray-500">
                Transaction: #{session.transactionId}
              </div>
            )}
          </div>
        ))}

        {sessions.length === 0 && (
          <div className="col-span-full bg-white rounded-lg shadow-md p-8 text-center text-gray-500">
            <div className="text-4xl mb-4">📡</div>
            <p className="text-lg">Aucune session detectee</p>
            <p className="text-sm mt-2">Creez une session dans l'onglet SimuEVSE</p>
          </div>
        )}
      </div>
    </div>
  );
}
