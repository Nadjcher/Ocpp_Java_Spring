/**
 * GPMSetpointCard - Affichage des setpoints GPM pour Simu EVSE
 * Récupère et affiche les snapshots et setpoints depuis l'API GPM
 */

import React, { useState, useEffect, useCallback } from 'react';

interface GPMSetpoint {
  evseId: string;
  maxPowerW: number;
  maxCurrentA: number;
  status: string;
}

interface GPMSnapshot {
  nodeId: string;
  type: string;
  activePowerW: number;
  energyWhTotal: number;
  maxPowerW: number;
  connectedEvses: number;
  chargingEvses: number;
}

interface Props {
  cpId: string;
  rootNodeId?: string;
  enabled?: boolean;
  onSetpointReceived?: (setpointKw: number | null) => void;
}

export const GPMSetpointCard: React.FC<Props> = ({
  cpId,
  rootNodeId = '',
  enabled = true,
  onSetpointReceived,
}) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [setpoint, setSetpoint] = useState<GPMSetpoint | null>(null);
  const [snapshot, setSnapshot] = useState<GPMSnapshot | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);

  const fetchGPMData = useCallback(async () => {
    if (!enabled || !rootNodeId) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Fetch setpoints
      const setpointRes = await fetch(`/api/gpm/setpoints?rootNodeId=${rootNodeId}&evseId=${cpId}`);
      if (setpointRes.ok) {
        const data = await setpointRes.json();
        if (data.setpoints && data.setpoints.length > 0) {
          const sp = data.setpoints.find((s: GPMSetpoint) => s.evseId === cpId) || data.setpoints[0];
          setSetpoint(sp);
          onSetpointReceived?.(sp.maxPowerW / 1000);
        } else {
          setSetpoint(null);
          onSetpointReceived?.(null);
        }
      }

      // Fetch snapshot
      const snapshotRes = await fetch(`/api/gpm/snapshots?rootNodeId=${rootNodeId}`);
      if (snapshotRes.ok) {
        const data = await snapshotRes.json();
        if (data.nodes && data.nodes.length > 0) {
          // Find EVSE node or root node
          const evseNode = data.nodes.find((n: GPMSnapshot) => n.nodeId === cpId);
          const rootNode = data.nodes.find((n: GPMSnapshot) => n.type === 'ROOT');
          setSnapshot(evseNode || rootNode || data.nodes[0]);
        }
      }

      setLastUpdate(new Date());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erreur de connexion GPM');
      onSetpointReceived?.(null);
    } finally {
      setLoading(false);
    }
  }, [cpId, rootNodeId, enabled, onSetpointReceived]);

  // Auto-refresh
  useEffect(() => {
    if (!autoRefresh || !enabled) return;

    const interval = setInterval(fetchGPMData, 5000);
    return () => clearInterval(interval);
  }, [autoRefresh, enabled, fetchGPMData]);

  if (!enabled) {
    return null;
  }

  const setpointKw = setpoint ? setpoint.maxPowerW / 1000 : null;
  const snapshotPowerKw = snapshot ? snapshot.activePowerW / 1000 : null;

  return (
    <div className="bg-gradient-to-br from-violet-50 to-purple-50 rounded-lg border border-violet-200 p-3 shadow-sm">
      {/* Header */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span className="text-violet-600 text-lg">⚡</span>
          <span className="font-semibold text-violet-800 text-sm">GPM Setpoint</span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={fetchGPMData}
            disabled={loading || !rootNodeId}
            className="text-xs px-2 py-1 bg-violet-100 hover:bg-violet-200 text-violet-700 rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            title="Rafraîchir"
          >
            {loading ? '...' : '↻'}
          </button>
          <button
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={`text-xs px-2 py-1 rounded transition-colors ${
              autoRefresh
                ? 'bg-violet-500 text-white'
                : 'bg-violet-100 text-violet-700 hover:bg-violet-200'
            }`}
            title={autoRefresh ? 'Auto-refresh ON' : 'Auto-refresh OFF'}
          >
            {autoRefresh ? '●' : '○'}
          </button>
        </div>
      </div>

      {/* Root Node ID Input */}
      {!rootNodeId && (
        <div className="text-xs text-violet-600 bg-violet-100 rounded p-2 mb-2">
          Configurer le Root Node ID dans les paramètres GPM
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="text-xs text-red-600 bg-red-50 rounded p-2 mb-2">
          {error}
        </div>
      )}

      {/* Setpoint Display */}
      {setpoint && (
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs text-violet-600">Limite GPM</span>
            <span className="text-lg font-bold text-violet-800">
              {setpointKw?.toFixed(1)} kW
            </span>
          </div>
          <div className="flex items-center justify-between text-xs text-violet-600">
            <span>Courant max</span>
            <span className="font-medium">{setpoint.maxCurrentA?.toFixed(1)} A</span>
          </div>
          <div className="flex items-center gap-1">
            <span
              className={`w-2 h-2 rounded-full ${
                setpoint.status === 'ACTIVE' ? 'bg-green-500' : 'bg-gray-400'
              }`}
            />
            <span className="text-xs text-violet-600">{setpoint.status}</span>
          </div>
        </div>
      )}

      {/* Snapshot Display */}
      {snapshot && (
        <div className="mt-2 pt-2 border-t border-violet-200">
          <div className="text-xs text-violet-500 mb-1">Snapshot Node</div>
          <div className="grid grid-cols-2 gap-1 text-xs">
            <div className="text-violet-600">Puissance</div>
            <div className="text-right font-medium text-violet-800">
              {snapshotPowerKw?.toFixed(1)} kW
            </div>
            <div className="text-violet-600">Max</div>
            <div className="text-right font-medium text-violet-800">
              {(snapshot.maxPowerW / 1000).toFixed(1)} kW
            </div>
            {snapshot.connectedEvses > 0 && (
              <>
                <div className="text-violet-600">EVSE</div>
                <div className="text-right font-medium text-violet-800">
                  {snapshot.chargingEvses}/{snapshot.connectedEvses}
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* No Data */}
      {!loading && !error && !setpoint && !snapshot && rootNodeId && (
        <div className="text-xs text-violet-500 text-center py-2">
          Cliquer sur ↻ pour récupérer les données GPM
        </div>
      )}

      {/* Data fetched but no setpoints */}
      {!loading && !error && lastUpdate && !setpoint && (
        <div className="text-xs text-amber-600 bg-amber-50 rounded p-2 mb-2">
          ⚠️ Aucun setpoint actif pour ce Root Node.
          <br />
          <span className="text-[10px] text-amber-500">
            Une session de charge doit être active sur une borne du groupe.
          </span>
        </div>
      )}

      {/* Last Update */}
      {lastUpdate && (
        <div className="mt-2 text-[10px] text-violet-400 text-right">
          Mis à jour: {lastUpdate.toLocaleTimeString()}
        </div>
      )}
    </div>
  );
};

export default GPMSetpointCard;
