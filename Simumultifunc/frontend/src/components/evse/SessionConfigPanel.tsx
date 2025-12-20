// frontend/src/components/evse/SessionConfigPanel.tsx
import React, { useState } from 'react';
import type { SessionConfig, EvseType, Environment } from '@/types/session.types';
import { NumericInput } from '@/components/ui/NumericInput';

interface SessionConfigPanelProps {
  config: SessionConfig;
  onChange: (config: Partial<SessionConfig>) => void;
  disabled?: boolean;
}

export function SessionConfigPanel({
  config,
  onChange,
  disabled = false
}: SessionConfigPanelProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const evseTypes: { value: EvseType; label: string }[] = [
    { value: 'ac-mono', label: 'AC Mono (1 phase)' },
    { value: 'ac-bi', label: 'AC Bi (2 phases)' },
    { value: 'ac-tri', label: 'AC Tri (3 phases)' },
    { value: 'dc', label: 'DC' }
  ];

  const environments: { value: Environment; label: string }[] = [
    { value: 'test', label: 'Test' },
    { value: 'pp', label: 'Pre-Production' }
  ];

  return (
    <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full px-4 py-3 flex items-center justify-between bg-gray-50 hover:bg-gray-100"
        disabled={disabled}
      >
        <span className="font-medium text-gray-700">Configuration Session</span>
        <svg
          className={`w-5 h-5 text-gray-500 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {isExpanded && (
        <div className="p-4 space-y-4">
          {/* ChargePoint ID */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              ChargePoint ID
            </label>
            <input
              type="text"
              value={config.cpId}
              onChange={e => onChange({ cpId: e.target.value })}
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 disabled:bg-gray-100"
              placeholder="CP_001"
            />
          </div>

          {/* Environment */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Environnement
            </label>
            <select
              value={config.environment}
              onChange={e => onChange({ environment: e.target.value as Environment })}
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            >
              {environments.map(env => (
                <option key={env.value} value={env.value}>{env.label}</option>
              ))}
            </select>
          </div>

          {/* EVSE Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Type de borne
            </label>
            <select
              value={config.evseType}
              onChange={e => onChange({ evseType: e.target.value as EvseType })}
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            >
              {evseTypes.map(type => (
                <option key={type.value} value={type.value}>{type.label}</option>
              ))}
            </select>
          </div>

          {/* Max Amperage */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Ampérage Max (A)
            </label>
            <NumericInput
              value={config.maxA}
              onChange={val => onChange({ maxA: val })}
              disabled={disabled}
              min={1}
              max={500}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            />
          </div>

          {/* ID Tag */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              ID Tag
            </label>
            <input
              type="text"
              value={config.idTag}
              onChange={e => onChange({ idTag: e.target.value })}
              disabled={disabled}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
              placeholder="BADGE_001"
            />
          </div>

          {/* SoC Range */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                SoC Initial (%)
              </label>
              <NumericInput
                value={config.socStart}
                onChange={val => onChange({ socStart: val })}
                disabled={disabled}
                min={0}
                max={100}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                SoC Cible (%)
              </label>
              <NumericInput
                value={config.socTarget}
                onChange={val => onChange({ socTarget: val })}
                disabled={disabled}
                min={0}
                max={100}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
              />
            </div>
          </div>

          {/* MeterValues Interval */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Intervalle MeterValues (s)
            </label>
            <NumericInput
              value={config.mvEvery}
              onChange={val => onChange({ mvEvery: val })}
              disabled={disabled}
              min={1}
              max={3600}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            />
          </div>
        </div>
      )}
    </div>
  );
}

export default SessionConfigPanel;
