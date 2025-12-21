/**
 * GPMConfigPanel - Configuration de la simulation GPM
 */

import React from 'react';
import { useGPMStore } from '@/store/gpmStore';
import { GPMSimulationMode } from '@/types/gpm.types';

interface Props {
  disabled?: boolean;
}

export const GPMConfigPanel: React.FC<Props> = ({ disabled = false }) => {
  const { formState, setFormState, configStatus } = useGPMStore();

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <h3 className="text-lg font-semibold text-gray-800 mb-4">
        Configuration Simulation
      </h3>

      <div className="grid grid-cols-2 gap-4">
        {/* Nom */}
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Nom de la simulation
          </label>
          <input
            type="text"
            value={formState.simulationName}
            onChange={(e) => setFormState({ simulationName: e.target.value })}
            disabled={disabled}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            placeholder="Ma simulation GPM"
          />
        </div>

        {/* Root Node ID */}
        <div className="col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Root Node ID
          </label>
          <input
            type="text"
            value={formState.rootNodeId}
            onChange={(e) => setFormState({ rootNodeId: e.target.value })}
            disabled={disabled}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            placeholder="node-123"
          />
        </div>

        {/* Intervalle des ticks */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Intervalle (minutes)
          </label>
          <input
            type="number"
            min={1}
            max={60}
            value={formState.tickIntervalMinutes}
            onChange={(e) => setFormState({ tickIntervalMinutes: parseInt(e.target.value) || 15 })}
            disabled={disabled}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
          />
        </div>

        {/* Nombre de ticks */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Nombre de ticks
          </label>
          <input
            type="number"
            min={1}
            max={288}
            value={formState.numberOfTicks}
            onChange={(e) => setFormState({ numberOfTicks: parseInt(e.target.value) || 96 })}
            disabled={disabled}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
          />
          <span className="text-xs text-gray-500">
            Durée: {(formState.tickIntervalMinutes * formState.numberOfTicks / 60).toFixed(1)}h
          </span>
        </div>

        {/* Time Scale */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Accélération (x)
          </label>
          <select
            value={formState.timeScale}
            onChange={(e) => setFormState({ timeScale: parseFloat(e.target.value) })}
            disabled={disabled}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
          >
            <option value={1}>1x (temps réel)</option>
            <option value={10}>10x</option>
            <option value={60}>60x (1 tick/sec)</option>
            <option value={300}>300x (5 ticks/sec)</option>
            <option value={900}>900x (15 ticks/sec)</option>
          </select>
        </div>

        {/* Mode */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Mode
          </label>
          <select
            value={formState.mode}
            onChange={(e) => setFormState({ mode: e.target.value as GPMSimulationMode })}
            disabled={disabled}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
          >
            <option value="DRY_RUN">Dry-Run (API TTE)</option>
            <option value="LOCAL">Local (sans API)</option>
          </select>
        </div>
      </div>

      {/* Status API */}
      {configStatus && (
        <div className="mt-4 p-3 bg-gray-50 rounded-md">
          <div className="flex items-center gap-2 text-sm">
            <span className={`w-2 h-2 rounded-full ${configStatus.connectionOk ? 'bg-green-500' : 'bg-red-500'}`} />
            <span className="text-gray-700">
              API: {configStatus.connectionOk ? 'Connectée' : 'Non disponible'}
            </span>
            <span className="text-gray-400 ml-auto">
              {configStatus.vehicleTypesCount} types de véhicules
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

export default GPMConfigPanel;
