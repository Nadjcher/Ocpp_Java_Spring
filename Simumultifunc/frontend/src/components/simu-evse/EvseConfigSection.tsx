// src/components/simu-evse/EvseConfigSection.tsx
// Section accordeon "Borne (EVSE)" - type connecteur, puissance, phasage

import React, { useMemo, useState } from 'react';
import { AccordionSection } from './AccordionSection';
import { NumericInput } from '@/components/ui/NumericInput';

type EvseType = 'ac-mono' | 'ac-bi' | 'ac-tri' | 'dc';

interface EvseConfigSectionProps {
  evseType: EvseType;
  onEvseTypeChange: (type: EvseType) => void;
  maxA: number;
  onMaxAChange: (val: number) => void;
  connectorId: number;
  onConnectorIdChange: (id: number) => void;
  // DC specifique
  isDC?: boolean;
  dcMaxPowerKw?: number;
  onDcMaxPowerKwChange?: (val: number) => void;
  // Mode test
  bypassVehicleLimits?: boolean;
  onBypassVehicleLimitsChange?: (val: boolean) => void;
  // Phasage avance (composant enfant)
  phasingSection?: React.ReactNode;
  // Connecteurs vehicule (optionnel)
  vehicleConnectors?: Array<{ index: number; label: string; evseType: EvseType }>;
  selectedConnectorIdx?: number;
  onSelectedConnectorIdxChange?: (idx: number) => void;
  compatibleEvseTypes?: Array<{ value: string; label: string }>;
  disabled?: boolean;
}

const DEFAULT_VOLTAGE = 230;
const PHASES: Record<EvseType, number> = { 'ac-mono': 1, 'ac-bi': 2, 'ac-tri': 3, dc: 3 };

// Presets de type EVSE combines
const EVSE_PRESETS: Array<{ value: EvseType; label: string; typicalA: number; description: string }> = [
  { value: 'ac-mono', label: 'AC Mono', typicalA: 32, description: '230V 1 phase — ~7.4 kW max' },
  { value: 'ac-bi', label: 'AC Bi', typicalA: 32, description: '230V 2 phases — ~14.7 kW max' },
  { value: 'ac-tri', label: 'AC Tri', typicalA: 32, description: '400V 3 phases — ~22 kW max' },
  { value: 'dc', label: 'DC', typicalA: 125, description: 'Courant continu — jusqu\'a 350 kW' },
];

export function EvseConfigSection({
  evseType,
  onEvseTypeChange,
  maxA,
  onMaxAChange,
  connectorId,
  onConnectorIdChange,
  isDC,
  dcMaxPowerKw,
  onDcMaxPowerKwChange,
  bypassVehicleLimits,
  onBypassVehicleLimitsChange,
  phasingSection,
  vehicleConnectors,
  selectedConnectorIdx,
  onSelectedConnectorIdxChange,
  compatibleEvseTypes,
  disabled,
}: EvseConfigSectionProps) {
  const [showAdvanced, setShowAdvanced] = useState(false);
  const isdc = isDC ?? evseType === 'dc';

  // Puissance max deduite
  const maxPowerKw = useMemo(() => {
    if (isdc) return dcMaxPowerKw || 50;
    const phases = PHASES[evseType] || 1;
    const voltage = phases === 1 ? DEFAULT_VOLTAGE : DEFAULT_VOLTAGE * Math.sqrt(3);
    return (maxA * voltage * (phases === 1 ? 1 : phases)) / 1000;
  }, [evseType, maxA, isdc, dcMaxPowerKw]);

  const summary = `${EVSE_PRESETS.find(p => p.value === evseType)?.label || evseType} — ${isdc ? (dcMaxPowerKw || 50) : maxA}${isdc ? ' kW' : 'A'} — ~${maxPowerKw.toFixed(1)} kW`;

  return (
    <AccordionSection
      title="Borne (EVSE)"
      defaultOpen
      summary={summary}
      icon={
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
        </svg>
      }
      badge={
        <span className={`px-2 py-0.5 rounded text-xs font-medium ${isdc ? 'bg-purple-100 text-purple-700' : 'bg-blue-100 text-blue-700'}`}>
          {isdc ? 'DC' : evseType.replace('ac-', 'AC ').replace('mono', 'Mono').replace('bi', 'Bi').replace('tri', 'Tri')}
        </span>
      }
    >
      {/* Type EVSE - cartes selectionnables */}
      <div className="grid grid-cols-4 gap-2 mb-3">
        {(compatibleEvseTypes || EVSE_PRESETS).map((preset) => {
          const p = 'description' in preset ? preset : EVSE_PRESETS.find(pp => pp.value === preset.value) || { value: preset.value, label: preset.label, description: '', typicalA: 32 };
          const isSelected = evseType === p.value;
          return (
            <button
              key={p.value}
              onClick={() => {
                onEvseTypeChange(p.value as EvseType);
                if ('typicalA' in p && p.typicalA) onMaxAChange(p.typicalA);
              }}
              disabled={disabled}
              className={`p-2 rounded-lg border-2 text-left transition-all ${
                isSelected
                  ? 'border-blue-500 bg-blue-50 shadow-sm'
                  : 'border-slate-200 hover:border-slate-300 bg-white'
              } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
            >
              <div className="text-sm font-semibold">{p.label}</div>
              {'description' in p && p.description ? (
                <div className="text-[10px] text-slate-500 mt-0.5">{(p as any).description}</div>
              ) : null}
            </button>
          );
        })}
      </div>

      {/* Puissance */}
      <div className="flex items-center gap-4 mb-3">
        <div className="flex-1">
          <div className="text-xs text-slate-500 mb-1">{isdc ? 'Puissance max (kW)' : 'Intensite max (A)'}</div>
          <NumericInput
            className={`w-full border rounded-lg px-3 py-1.5 ${bypassVehicleLimits ? 'border-orange-400 bg-orange-50' : 'border-slate-200'}`}
            value={isdc ? (dcMaxPowerKw || 50) : maxA}
            onChange={(val) => isdc ? onDcMaxPowerKwChange?.(val) : onMaxAChange(val)}
            min={1}
            max={isdc ? 500 : 500}
          />
        </div>

        {/* Puissance deduite */}
        <div className="flex-1">
          <div className="text-xs text-slate-500 mb-1">Puissance max deduite</div>
          <div className="px-3 py-1.5 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-700 font-bold text-sm">
            {maxPowerKw.toFixed(1)} kW
          </div>
        </div>

        {/* Mode test */}
        {!isdc && onBypassVehicleLimitsChange && (
          <div className="flex items-center">
            <label className="flex items-center gap-2 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={bypassVehicleLimits || false}
                onChange={(e) => onBypassVehicleLimitsChange(e.target.checked)}
                className="w-4 h-4 accent-orange-500 rounded"
              />
              <div>
                <div className={`text-xs font-medium ${bypassVehicleLimits ? 'text-orange-600' : 'text-slate-400'}`}>
                  Mode test
                </div>
                <div className="text-[10px] text-slate-400">Ignorer limites VE</div>
              </div>
            </label>
          </div>
        )}
      </div>

      {/* Vehicule connector selector (si disponible) */}
      {vehicleConnectors && vehicleConnectors.length > 1 && (
        <div className="mb-3">
          <div className="text-xs text-slate-500 mb-1">Connecteur vehicule</div>
          <select
            className="w-full border border-slate-200 rounded-lg px-3 py-1.5 text-sm"
            value={selectedConnectorIdx}
            onChange={(e) => onSelectedConnectorIdxChange?.(Number(e.target.value))}
            disabled={disabled}
          >
            {vehicleConnectors.map((c) => (
              <option key={c.index} value={c.index}>{c.label}</option>
            ))}
          </select>
        </div>
      )}

      {/* Mode avance - phasage */}
      {phasingSection && (
        <div className="mt-3">
          <button
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="text-xs text-slate-500 hover:text-slate-700 flex items-center gap-1 mb-2"
          >
            <svg className={`w-3 h-3 transition-transform ${showAdvanced ? 'rotate-90' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
            Phasage avance
          </button>
          {showAdvanced && phasingSection}
        </div>
      )}
    </AccordionSection>
  );
}

export default EvseConfigSection;
