/**
 * GPMDryRunTab - Tab principal pour le simulateur GPM Dry-Run
 */

import React, { useEffect } from 'react';
import { useGPMStore } from '@/store/gpmStore';
import {
  GPMConfigPanel,
  GPMVehicleSelector,
  GPMVehicleList,
  GPMResultsPanel,
  GPMStatusBar,
} from '@/components/gpm';

export const GPMDryRunTab: React.FC = () => {
  const {
    simulation,
    loading,
    error,
    fetchConfigStatus,
    fetchVehicleTypes,
    addVehicle,
    vehicleForm,
    resetVehicleForm,
    clearError,
  } = useGPMStore();

  // Charger les données au montage
  useEffect(() => {
    fetchConfigStatus();
    fetchVehicleTypes();
  }, [fetchConfigStatus, fetchVehicleTypes]);

  // Cleanup polling on unmount
  useEffect(() => {
    return () => {
      useGPMStore.getState().stopPolling();
    };
  }, []);

  const isRunning = simulation?.status === 'RUNNING';
  const isCreated = simulation?.status === 'CREATED';

  const handleAddVehicle = async () => {
    try {
      await addVehicle({
        evseId: vehicleForm.evseId,
        evTypeId: vehicleForm.evTypeId,
        initialSoc: vehicleForm.initialSoc,
        targetSoc: vehicleForm.targetSoc,
      });
      resetVehicleForm();
    } catch (err) {
      console.error('Failed to add vehicle:', err);
    }
  };

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="p-4 border-b border-gray-200 bg-white">
        <h1 className="text-2xl font-bold text-gray-800">
          Simulateur GPM Dry-Run
        </h1>
        <p className="text-gray-600 text-sm mt-1">
          Simulation de charge avec feedback loop GPM et API TTE
        </p>
      </div>

      {/* Error banner */}
      {error && (
        <div className="mx-4 mt-4 p-3 bg-red-50 border border-red-200 rounded-lg flex justify-between items-center">
          <span className="text-red-700 text-sm">{error}</span>
          <button
            onClick={clearError}
            className="text-red-500 hover:text-red-700"
          >
            ✕
          </button>
        </div>
      )}

      {/* Status Bar */}
      <div className="p-4">
        <GPMStatusBar />
      </div>

      {/* Main content */}
      <div className="flex-1 overflow-auto p-4">
        <div className="grid grid-cols-12 gap-4">
          {/* Colonne gauche: Config + Ajout véhicule */}
          <div className="col-span-4 space-y-4">
            <GPMConfigPanel disabled={isRunning || loading} />
            {simulation && isCreated && (
              <GPMVehicleSelector
                disabled={isRunning || loading}
                onAddVehicle={handleAddVehicle}
              />
            )}
          </div>

          {/* Colonne centre: Liste véhicules */}
          <div className="col-span-4">
            {simulation && (
              <GPMVehicleList disabled={isRunning} />
            )}
            {!simulation && (
              <div className="bg-white rounded-lg border border-gray-200 p-8 text-center text-gray-500">
                <div className="text-4xl mb-4">🚗</div>
                <p>Créez une simulation pour ajouter des véhicules</p>
              </div>
            )}
          </div>

          {/* Colonne droite: Résultats */}
          <div className="col-span-4">
            {simulation && simulation.tickResults && simulation.tickResults.length > 0 ? (
              <GPMResultsPanel />
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 p-8 text-center text-gray-500">
                <div className="text-4xl mb-4">📊</div>
                <p>Les résultats apparaîtront ici</p>
                <p className="text-sm mt-2">
                  {simulation
                    ? 'Démarrez la simulation pour voir les résultats'
                    : 'Créez une simulation pour commencer'}
                </p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Loading overlay */}
      {loading && (
        <div className="fixed inset-0 bg-black bg-opacity-20 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 shadow-xl">
            <div className="animate-spin w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full mx-auto"></div>
            <p className="mt-3 text-gray-600">Chargement...</p>
          </div>
        </div>
      )}
    </div>
  );
};

export default GPMDryRunTab;
