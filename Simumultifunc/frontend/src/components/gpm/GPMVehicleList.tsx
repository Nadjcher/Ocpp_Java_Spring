/**
 * GPMVehicleList - Liste des véhicules dans la simulation
 */

import React from 'react';
import { useGPMStore } from '@/store/gpmStore';
import { GPMVehicleState, CHARGE_TYPES } from '@/types/gpm.types';

interface Props {
  disabled?: boolean;
}

export const GPMVehicleList: React.FC<Props> = ({ disabled = false }) => {
  const { simulation, removeVehicle } = useGPMStore();

  const vehicles = simulation?.vehicles || [];

  if (vehicles.length === 0) {
    return (
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Véhicules ({vehicles.length})
        </h3>
        <div className="text-center py-8 text-gray-500">
          Aucun véhicule ajouté
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <h3 className="text-lg font-semibold text-gray-800 mb-4">
        Véhicules ({vehicles.length})
      </h3>

      <div className="space-y-3">
        {vehicles.map((vehicle: GPMVehicleState) => (
          <VehicleCard
            key={vehicle.evseId}
            vehicle={vehicle}
            onRemove={() => removeVehicle(vehicle.evseId)}
            disabled={disabled}
          />
        ))}
      </div>
    </div>
  );
};

interface VehicleCardProps {
  vehicle: GPMVehicleState;
  onRemove: () => void;
  disabled: boolean;
}

const VehicleCard: React.FC<VehicleCardProps> = ({ vehicle, onRemove, disabled }) => {
  const chargeType = CHARGE_TYPES[vehicle.chargeType];
  const progress = ((vehicle.currentSoc - vehicle.initialSoc) / (vehicle.targetSoc - vehicle.initialSoc)) * 100;
  const isComplete = vehicle.currentSoc >= vehicle.targetSoc;

  const getChargeTypeIcon = () => {
    switch (vehicle.chargeType) {
      case 'DC': return '⚡';
      case 'TRI': return '🔌';
      case 'MONO': return '🏠';
    }
  };

  const getChargeTypeColor = () => {
    switch (vehicle.chargeType) {
      case 'DC': return 'blue';
      case 'TRI': return 'green';
      case 'MONO': return 'orange';
    }
  };

  return (
    <div className={`p-3 rounded-lg border ${isComplete ? 'bg-green-50 border-green-200' : 'bg-gray-50 border-gray-200'}`}>
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span className="text-lg">{getChargeTypeIcon()}</span>
          <span className="font-medium text-gray-800">{vehicle.evseId}</span>
          <span className={`text-xs px-2 py-0.5 rounded-full bg-${getChargeTypeColor()}-100 text-${getChargeTypeColor()}-700`}
            style={{
              backgroundColor: getChargeTypeColor() === 'blue' ? '#dbeafe' : getChargeTypeColor() === 'green' ? '#dcfce7' : '#ffedd5',
              color: getChargeTypeColor() === 'blue' ? '#1d4ed8' : getChargeTypeColor() === 'green' ? '#15803d' : '#c2410c'
            }}>
            {chargeType.label}
          </span>
        </div>
        {!disabled && (
          <button
            onClick={onRemove}
            className="text-red-500 hover:text-red-700 text-sm"
          >
            ✕
          </button>
        )}
      </div>

      {/* Barre de progression SOC */}
      <div className="mb-2">
        <div className="flex justify-between text-xs text-gray-600 mb-1">
          <span>SOC: {vehicle.currentSoc.toFixed(1)}%</span>
          <span>Cible: {vehicle.targetSoc}%</span>
        </div>
        <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
          <div
            className={`h-full transition-all duration-500 ${isComplete ? 'bg-green-500' : 'bg-blue-500'}`}
            style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
          />
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-2 text-xs text-gray-600">
        <div>
          <span className="block text-gray-400">Puissance</span>
          <span className="font-medium">{(vehicle.currentPowerW / 1000).toFixed(1)} kW</span>
        </div>
        <div>
          <span className="block text-gray-400">Énergie</span>
          <span className="font-medium">{(vehicle.energyRegisterWh / 1000).toFixed(2)} kWh</span>
        </div>
        <div>
          <span className="block text-gray-400">Setpoint</span>
          <span className="font-medium">
            {vehicle.lastSetpointW ? `${(vehicle.lastSetpointW / 1000).toFixed(1)} kW` : '-'}
          </span>
        </div>
      </div>

      {/* Status */}
      {isComplete && (
        <div className="mt-2 text-xs text-green-600 font-medium">
          ✓ Charge terminée
        </div>
      )}
    </div>
  );
};

export default GPMVehicleList;
