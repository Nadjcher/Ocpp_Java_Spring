// src/components/simu-evse/VehicleConfigSection.tsx
// Section accordeon "Vehicule (EV)" - selecteur vehicule, slider SoC, idTag

import React, { useMemo } from 'react';
import { AccordionSection } from './AccordionSection';
import type { VehicleProfile } from '@/services/vehAdapter';

interface VehicleConfigSectionProps {
  vehicleId: string;
  onVehicleIdChange: (id: string) => void;
  vehicles: VehicleProfile[] | string[];
  selectedVehicle?: VehicleProfile | null;

  socStart: number;
  onSocStartChange: (val: number) => void;
  socTarget: number;
  onSocTargetChange: (val: number) => void;

  idTag: string;
  onIdTagChange: (tag: string) => void;

  disabled?: boolean;
}

function generateRandomIdTag(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let tag = 'TAG-';
  for (let i = 0; i < 6; i++) {
    tag += chars[Math.floor(Math.random() * chars.length)];
  }
  return tag;
}

export function VehicleConfigSection({
  vehicleId,
  onVehicleIdChange,
  vehicles,
  selectedVehicle,
  socStart,
  onSocStartChange,
  socTarget,
  onSocTargetChange,
  idTag,
  onIdTagChange,
  disabled,
}: VehicleConfigSectionProps) {
  const vehicleName = selectedVehicle?.name
    || (selectedVehicle ? `${selectedVehicle.manufacturer || ''} ${selectedVehicle.model || ''}`.trim() : vehicleId);

  const summary = `${vehicleName} — SoC ${socStart}% → ${socTarget}% — ${idTag || 'pas de tag'}`;

  // Check if vehicles is string array or VehicleProfile array
  const isStringArray = vehicles.length > 0 && typeof vehicles[0] === 'string';

  return (
    <AccordionSection
      title="Vehicule (EV)"
      defaultOpen
      summary={summary}
      icon={
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
        </svg>
      }
    >
      {/* Selecteur vehicule */}
      <div className="mb-3">
        <div className="text-xs text-slate-500 mb-1">Vehicule</div>
        <select
          className="w-full border border-slate-200 rounded-lg px-3 py-1.5 text-sm"
          value={vehicleId}
          onChange={(e) => onVehicleIdChange(e.target.value)}
          disabled={disabled}
        >
          {isStringArray
            ? (vehicles as string[]).map((name) => (
                <option key={name} value={name}>{name}</option>
              ))
            : (vehicles as VehicleProfile[]).map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name || `${v.manufacturer ?? ''} ${v.model ?? ''} ${v.variant ?? ''}`.trim()}
                </option>
              ))
          }
        </select>

        {/* Info vehicule rapide */}
        {selectedVehicle && (
          <div className="flex gap-3 mt-2 text-xs">
            <span className="px-2 py-0.5 rounded bg-blue-50 text-blue-700 border border-blue-100">
              {selectedVehicle.capacityKWh} kWh
            </span>
            <span className="px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-100">
              Max {selectedVehicle.maxPowerKW} kW
            </span>
            {selectedVehicle.connectorType && (
              <span className="px-2 py-0.5 rounded bg-purple-50 text-purple-700 border border-purple-100">
                {selectedVehicle.connectorType}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Slider SoC dual */}
      <div className="mb-3">
        <div className="flex items-center justify-between mb-1">
          <div className="text-xs text-slate-500">SoC depart / cible</div>
          <div className="text-xs font-mono text-slate-600">
            {socStart}% → {socTarget}%
          </div>
        </div>

        {/* Barre visuelle SoC */}
        <div className="relative h-8 bg-slate-100 rounded-lg overflow-hidden mb-2">
          {/* Zone de charge (entre depart et cible) */}
          <div
            className="absolute h-full bg-gradient-to-r from-blue-200 to-emerald-200 rounded"
            style={{ left: `${socStart}%`, width: `${Math.max(0, socTarget - socStart)}%` }}
          />
          {/* Indicateur depart */}
          <div
            className="absolute h-full w-1 bg-blue-500 rounded"
            style={{ left: `${socStart}%` }}
          />
          {/* Indicateur cible */}
          <div
            className="absolute h-full w-1 bg-emerald-500 rounded"
            style={{ left: `${socTarget}%` }}
          />
          {/* Labels */}
          <div className="absolute inset-0 flex items-center justify-between px-2 text-xs font-medium">
            <span className="text-blue-700">{socStart}%</span>
            <span className="text-emerald-700">{socTarget}%</span>
          </div>
        </div>

        {/* Sliders */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="text-xs text-blue-600 font-medium">Depart</label>
            <input
              type="range"
              min={0}
              max={100}
              value={socStart}
              onChange={(e) => {
                const val = Number(e.target.value);
                onSocStartChange(Math.min(val, socTarget - 1));
              }}
              disabled={disabled}
              className="w-full h-2 bg-blue-100 rounded-lg appearance-none cursor-pointer accent-blue-500"
            />
          </div>
          <div>
            <label className="text-xs text-emerald-600 font-medium">Cible</label>
            <input
              type="range"
              min={0}
              max={100}
              value={socTarget}
              onChange={(e) => {
                const val = Number(e.target.value);
                onSocTargetChange(Math.max(val, socStart + 1));
              }}
              disabled={disabled}
              className="w-full h-2 bg-emerald-100 rounded-lg appearance-none cursor-pointer accent-emerald-500"
            />
          </div>
        </div>
      </div>

      {/* idTag */}
      <div>
        <div className="text-xs text-slate-500 mb-1">idTag (badge RFID)</div>
        <div className="flex gap-2">
          <input
            className="flex-1 border border-slate-200 rounded-lg px-3 py-1.5 text-sm"
            value={idTag}
            onChange={(e) => onIdTagChange(e.target.value)}
            placeholder="Entrez l'idTag..."
            disabled={disabled}
          />
          <button
            onClick={() => onIdTagChange(generateRandomIdTag())}
            disabled={disabled}
            className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs text-slate-600 hover:bg-slate-50 transition-colors disabled:opacity-50"
            title="Generer un idTag aleatoire"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>
    </AccordionSection>
  );
}

export default VehicleConfigSection;
