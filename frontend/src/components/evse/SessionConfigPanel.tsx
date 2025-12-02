// frontend/src/components/evse/SessionConfigPanel.tsx
// Panneau de configuration inline pour une session

import React from 'react';
import type { SessionConfig } from '@/types/multiSession.types';
import type { Vehicle } from '@/types/evse.types';
import { ENV_URLS } from '@/constants/evse.constants';

interface SessionConfigPanelProps {
  config: SessionConfig;
  isTemporary: boolean;
  isConnected: boolean;
  vehicles: Vehicle[];
  onConfigChange: (updates: Partial<SessionConfig>) => void;
  onConnect: () => void;
  onDisconnect: () => void;
}

export function SessionConfigPanel({
  config,
  isTemporary,
  isConnected,
  vehicles,
  onConfigChange,
  onConnect,
  onDisconnect
}: SessionConfigPanelProps) {
  const canEdit = isTemporary || !isConnected;

  // Calculer l'URL WebSocket
  const wsUrl = ENV_URLS[config.environment]
    ? `${ENV_URLS[config.environment]}/${config.cpId}`
    : `wss://ocpp-${config.environment}.example.com/${config.cpId}`;

  return (
    <div className="rounded-lg border bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-gray-800">Configuration OCPP</h3>
        {isTemporary && (
          <span className="px-2 py-1 bg-yellow-100 text-yellow-700 text-xs rounded animate-pulse">
            Configurez puis connectez
          </span>
        )}
      </div>

      <div className="grid grid-cols-12 gap-4">
        {/* Environnement */}
        <div className="col-span-3">
          <label className="block text-xs text-gray-500 mb-1">Environnement</label>
          <select
            className="w-full border rounded px-3 py-2 text-sm bg-white disabled:bg-gray-100"
            value={config.environment}
            onChange={(e) => onConfigChange({ environment: e.target.value as 'test' | 'pp' })}
            disabled={!canEdit}
          >
            <option value="test">Test</option>
            <option value="pp">Pre-Production</option>
          </select>
        </div>

        {/* CP-ID */}
        <div className="col-span-3">
          <label className="block text-xs text-gray-500 mb-1">CP-ID</label>
          <input
            type="text"
            className="w-full border rounded px-3 py-2 text-sm disabled:bg-gray-100"
            value={config.cpId}
            onChange={(e) => onConfigChange({ cpId: e.target.value })}
            disabled={!canEdit}
            placeholder="Ex: SIM-001"
          />
        </div>

        {/* idTag */}
        <div className="col-span-3">
          <label className="block text-xs text-gray-500 mb-1">idTag (Badge)</label>
          <input
            type="text"
            className="w-full border rounded px-3 py-2 text-sm"
            value={config.idTag}
            onChange={(e) => onConfigChange({ idTag: e.target.value })}
            placeholder="Ex: TAG-001"
          />
        </div>

        {/* Bouton Connect/Disconnect */}
        <div className="col-span-3 flex items-end">
          {isTemporary || !isConnected ? (
            <button
              onClick={onConnect}
              className="w-full px-4 py-2 bg-emerald-600 text-white rounded hover:bg-emerald-700
                         font-medium transition-colors flex items-center justify-center gap-2"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              CONNECT
            </button>
          ) : (
            <button
              onClick={onDisconnect}
              className="w-full px-4 py-2 bg-gray-600 text-white rounded hover:bg-gray-700
                         font-medium transition-colors flex items-center justify-center gap-2"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728L5.636 5.636" />
              </svg>
              DISCONNECT
            </button>
          )}
        </div>

        {/* URL WebSocket (lecture seule) */}
        <div className="col-span-12">
          <label className="block text-xs text-gray-500 mb-1">WebSocket URL</label>
          <div className="w-full border rounded px-3 py-2 text-sm bg-gray-50 text-gray-600 font-mono truncate">
            {wsUrl}
          </div>
        </div>

        {/* Ligne 2 : Type EVSE, Max A, Vehicule */}
        <div className="col-span-2">
          <label className="block text-xs text-gray-500 mb-1">Type EVSE</label>
          <select
            className="w-full border rounded px-3 py-2 text-sm bg-white"
            value={config.evseType}
            onChange={(e) => onConfigChange({ evseType: e.target.value as SessionConfig['evseType'] })}
          >
            <option value="ac-mono">AC Mono (1ph)</option>
            <option value="ac-bi">AC Bi (2ph)</option>
            <option value="ac-tri">AC Tri (3ph)</option>
            <option value="dc">DC</option>
          </select>
        </div>

        <div className="col-span-2">
          <label className="block text-xs text-gray-500 mb-1">Courant Max (A)</label>
          <input
            type="number"
            className="w-full border rounded px-3 py-2 text-sm"
            value={config.maxA}
            onChange={(e) => onConfigChange({ maxA: Number(e.target.value) || 32 })}
            min={1}
            max={500}
          />
        </div>

        <div className="col-span-4">
          <label className="block text-xs text-gray-500 mb-1">Vehicule</label>
          <select
            className="w-full border rounded px-3 py-2 text-sm bg-white"
            value={config.vehicleId}
            onChange={(e) => onConfigChange({ vehicleId: e.target.value })}
          >
            {vehicles.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name} ({v.capacityKWh} kWh)
              </option>
            ))}
          </select>
        </div>

        <div className="col-span-2">
          <label className="block text-xs text-gray-500 mb-1">SoC Depart (%)</label>
          <input
            type="number"
            className="w-full border rounded px-3 py-2 text-sm"
            value={config.socStart}
            onChange={(e) => onConfigChange({ socStart: Number(e.target.value) || 20 })}
            min={0}
            max={100}
          />
        </div>

        <div className="col-span-2">
          <label className="block text-xs text-gray-500 mb-1">SoC Cible (%)</label>
          <input
            type="number"
            className="w-full border rounded px-3 py-2 text-sm"
            value={config.socTarget}
            onChange={(e) => onConfigChange({ socTarget: Number(e.target.value) || 80 })}
            min={0}
            max={100}
          />
        </div>
      </div>
    </div>
  );
}

export default SessionConfigPanel;
