/**
 * GPMVehicleSelector - Sélection et ajout de véhicules
 */

import React from 'react';
import { useGPMStore } from '@/store/gpmStore';
import { GPMChargeType, CHARGE_TYPES, EVTypeConfig } from '@/types/gpm.types';

interface Props {
  disabled?: boolean;
  onAddVehicle: () => void;
}

const CHARGE_TYPE_TABS: { type: GPMChargeType; icon: string; color: string }[] = [
  { type: 'DC', icon: '⚡', color: 'blue' },
  { type: 'TRI', icon: '🔌', color: 'green' },
  { type: 'MONO', icon: '🏠', color: 'orange' },
];

export const GPMVehicleSelector: React.FC<Props> = ({ disabled = false, onAddVehicle }) => {
  const {
    vehiclesByType,
    selectedChargeType,
    setSelectedChargeType,
    vehicleForm,
    setVehicleForm,
  } = useGPMStore();

  const currentVehicles = vehiclesByType[selectedChargeType] || [];
  const chargeTypeConfig = CHARGE_TYPES[selectedChargeType];

  const handleAddClick = () => {
    if (!vehicleForm.evseId || !vehicleForm.evTypeId) {
      return;
    }
    onAddVehicle();
  };

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <h3 className="text-lg font-semibold text-gray-800 mb-4">
        Ajouter un véhicule
      </h3>

      {/* Tabs type de charge */}
      <div className="flex gap-1 mb-4 bg-gray-100 p-1 rounded-lg">
        {CHARGE_TYPE_TABS.map(({ type, icon, color }) => (
          <button
            key={type}
            onClick={() => setSelectedChargeType(type)}
            disabled={disabled}
            className={`flex-1 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
              selectedChargeType === type
                ? `bg-${color}-500 text-white`
                : 'text-gray-600 hover:bg-gray-200'
            } disabled:opacity-50`}
            style={selectedChargeType === type ? {
              backgroundColor: color === 'blue' ? '#3b82f6' : color === 'green' ? '#22c55e' : '#f97316'
            } : {}}
          >
            {icon} {CHARGE_TYPES[type].label}
          </button>
        ))}
      </div>

      {/* Info type de charge */}
      <div className="mb-4 p-3 bg-gray-50 rounded-md text-sm text-gray-600">
        <div className="flex justify-between">
          <span>Phases: {chargeTypeConfig.phases || 'DC'}</span>
          <span>Tension: {chargeTypeConfig.voltageV}V</span>
          <span>Max: {(chargeTypeConfig.maxPowerW / 1000).toFixed(1)} kW</span>
        </div>
      </div>

      {/* Formulaire */}
      <div className="space-y-4">
        {/* EVSE ID */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            EVSE ID
          </label>
          <input
            type="text"
            value={vehicleForm.evseId}
            onChange={(e) => setVehicleForm({ evseId: e.target.value })}
            disabled={disabled}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            placeholder="EVSE-001"
          />
        </div>

        {/* Type de véhicule */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Type de véhicule
          </label>
          <select
            value={vehicleForm.evTypeId}
            onChange={(e) => setVehicleForm({ evTypeId: e.target.value })}
            disabled={disabled}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
          >
            <option value="">Sélectionner...</option>
            {currentVehicles.map((v: EVTypeConfig) => (
              <option key={v.id} value={v.id}>
                {v.name} ({(v.maxPowerW / 1000).toFixed(0)} kW)
              </option>
            ))}
          </select>
        </div>

        {/* SOC */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              SOC Initial (%)
            </label>
            <input
              type="number"
              min={0}
              max={100}
              value={vehicleForm.initialSoc}
              onChange={(e) => setVehicleForm({ initialSoc: parseInt(e.target.value) || 20 })}
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              SOC Cible (%)
            </label>
            <input
              type="number"
              min={0}
              max={100}
              value={vehicleForm.targetSoc}
              onChange={(e) => setVehicleForm({ targetSoc: parseInt(e.target.value) || 80 })}
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            />
          </div>
        </div>

        {/* Bouton ajouter */}
        <button
          onClick={handleAddClick}
          disabled={disabled || !vehicleForm.evseId || !vehicleForm.evTypeId}
          className="w-full py-2 px-4 bg-blue-500 text-white font-medium rounded-md hover:bg-blue-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
        >
          + Ajouter le véhicule
        </button>
      </div>
    </div>
  );
};

export default GPMVehicleSelector;
