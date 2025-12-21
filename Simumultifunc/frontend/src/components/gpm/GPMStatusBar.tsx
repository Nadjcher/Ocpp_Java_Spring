/**
 * GPMStatusBar - Barre de status et contrôles de simulation
 */

import React from 'react';
import { useGPMStore } from '@/store/gpmStore';
import { GPMSimulationStatus } from '@/types/gpm.types';

export const GPMStatusBar: React.FC = () => {
  const {
    simulation,
    loading,
    startSimulation,
    stopSimulation,
    createSimulation,
    fetchSimulation,
    formState,
  } = useGPMStore();

  const status = simulation?.status || 'NONE';
  const isRunning = status === 'RUNNING';
  const canStart = simulation && status === 'CREATED' && simulation.vehicles.length > 0;
  const canCreate = !simulation || ['COMPLETED', 'FAILED', 'CANCELLED'].includes(status);

  const getStatusColor = (s: GPMSimulationStatus | 'NONE'): string => {
    switch (s) {
      case 'CREATED': return 'bg-gray-500';
      case 'RUNNING': return 'bg-blue-500';
      case 'PAUSED': return 'bg-yellow-500';
      case 'COMPLETED': return 'bg-green-500';
      case 'FAILED': return 'bg-red-500';
      case 'CANCELLED': return 'bg-orange-500';
      default: return 'bg-gray-300';
    }
  };

  const getStatusLabel = (s: GPMSimulationStatus | 'NONE'): string => {
    switch (s) {
      case 'CREATED': return 'Prête';
      case 'RUNNING': return 'En cours';
      case 'PAUSED': return 'En pause';
      case 'COMPLETED': return 'Terminée';
      case 'FAILED': return 'Échec';
      case 'CANCELLED': return 'Annulée';
      default: return 'Aucune';
    }
  };

  const handleCreate = async () => {
    if (!formState.rootNodeId) {
      alert('Veuillez renseigner le Root Node ID');
      return;
    }
    await createSimulation();
  };

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <div className="flex items-center justify-between">
        {/* Status */}
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className={`w-3 h-3 rounded-full ${getStatusColor(status)} ${isRunning ? 'animate-pulse' : ''}`} />
            <span className="font-medium text-gray-800">
              {getStatusLabel(status)}
            </span>
          </div>

          {simulation && (
            <>
              <div className="text-sm text-gray-600">
                Tick: <span className="font-medium">{simulation.currentTick}</span>
                /{simulation.totalTicks}
              </div>
              <div className="text-sm text-gray-600">
                Progression: <span className="font-medium">{simulation.progressPercent.toFixed(1)}%</span>
              </div>
            </>
          )}
        </div>

        {/* Contrôles */}
        <div className="flex items-center gap-2">
          {canCreate && (
            <button
              onClick={handleCreate}
              disabled={loading}
              className="px-4 py-2 bg-blue-500 text-white font-medium rounded-md hover:bg-blue-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? 'Création...' : 'Nouvelle simulation'}
            </button>
          )}

          {canStart && (
            <button
              onClick={startSimulation}
              disabled={loading}
              className="px-4 py-2 bg-green-500 text-white font-medium rounded-md hover:bg-green-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
            >
              ▶ Démarrer
            </button>
          )}

          {isRunning && (
            <button
              onClick={stopSimulation}
              disabled={loading}
              className="px-4 py-2 bg-red-500 text-white font-medium rounded-md hover:bg-red-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
            >
              ⬛ Arrêter
            </button>
          )}

          {/* Refresh button */}
          {simulation && (
            <button
              onClick={() => fetchSimulation(simulation.id)}
              disabled={loading}
              className="px-3 py-2 bg-gray-100 text-gray-700 font-medium rounded-md hover:bg-gray-200 disabled:bg-gray-50 disabled:cursor-not-allowed transition-colors"
              title="Rafraîchir le statut"
            >
              ↻
            </button>
          )}
        </div>
      </div>

      {/* Progress bar */}
      {simulation && simulation.totalTicks > 0 && (
        <div className="mt-3">
          <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
            <div
              className={`h-full transition-all duration-500 ${
                status === 'COMPLETED' ? 'bg-green-500' :
                status === 'FAILED' ? 'bg-red-500' :
                'bg-blue-500'
              }`}
              style={{ width: `${simulation.progressPercent}%` }}
            />
          </div>
        </div>
      )}

      {/* Dry Run ID */}
      {simulation?.dryRunId && (
        <div className="mt-2 text-xs text-gray-500">
          Dry-Run ID: <code className="bg-gray-100 px-1 rounded">{simulation.dryRunId}</code>
        </div>
      )}
    </div>
  );
};

export default GPMStatusBar;
