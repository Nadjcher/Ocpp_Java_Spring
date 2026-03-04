// src/components/simu-evse/MeterValuesSection.tsx
// Section accordeon "MeterValues" avec presets et slider de periode

import React, { useMemo } from 'react';
import { AccordionSection } from './AccordionSection';

interface MeterValuesMask {
  powerActive: boolean;
  energy: boolean;
  soc: boolean;
  powerOffered: boolean;
}

// Helper to access mask fields dynamically
function getMaskValue(mask: MeterValuesMask, key: string): boolean {
  return (mask as unknown as Record<string, boolean>)[key] ?? false;
}

type Preset = 'standard' | 'complete' | 'minimal' | 'custom';

interface MeterValuesSectionProps {
  mvEvery: number;
  onMvEveryChange: (val: number) => void;
  mvMask: MeterValuesMask;
  onMvMaskChange: (mask: MeterValuesMask) => void;
  onApply: () => void;
  disabled?: boolean;
}

const PRESETS: Record<Exclude<Preset, 'custom'>, { label: string; description: string; mask: MeterValuesMask }> = {
  standard: {
    label: 'Standard',
    description: 'Power + Energy',
    mask: { powerActive: true, energy: true, soc: false, powerOffered: false },
  },
  complete: {
    label: 'Complet',
    description: 'Toutes les mesures',
    mask: { powerActive: true, energy: true, soc: true, powerOffered: true },
  },
  minimal: {
    label: 'Minimal',
    description: 'Energy seul',
    mask: { powerActive: false, energy: true, soc: false, powerOffered: false },
  },
};

const MEASURES = [
  { key: 'powerActive', label: 'Power.Active.Import', short: 'Power' },
  { key: 'energy', label: 'Energy.Active.Import.Register', short: 'Energy' },
  { key: 'soc', label: 'SoC', short: 'SoC' },
  { key: 'powerOffered', label: 'Power.Offered', short: 'Offered' },
];

function detectPreset(mask: MeterValuesMask): Preset {
  for (const [name, preset] of Object.entries(PRESETS)) {
    const match = MEASURES.every(m => getMaskValue(preset.mask, m.key) === getMaskValue(mask, m.key));
    if (match) return name as Preset;
  }
  return 'custom';
}

export function MeterValuesSection({
  mvEvery,
  onMvEveryChange,
  mvMask,
  onMvMaskChange,
  onApply,
  disabled,
}: MeterValuesSectionProps) {
  const currentPreset = useMemo(() => detectPreset(mvMask), [mvMask]);

  // Resume en une ligne
  const enabledMeasures = MEASURES.filter(m => getMaskValue(mvMask, m.key)).map(m => m.short);
  const summary = `MV toutes les ${mvEvery}s — ${enabledMeasures.join(', ') || 'aucune'}`;

  return (
    <AccordionSection
      title="MeterValues"
      defaultOpen={false}
      summary={summary}
      icon={
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
      }
      badge={
        <span className="px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-600">
          {mvEvery}s
        </span>
      }
    >
      {/* Periode - slider */}
      <div className="mb-4">
        <div className="flex items-center justify-between mb-1">
          <div className="text-xs text-slate-500">Periode d'envoi</div>
          <div className="text-sm font-bold text-slate-700">{mvEvery}s</div>
        </div>
        <input
          type="range"
          min={5}
          max={60}
          step={5}
          value={mvEvery}
          onChange={(e) => onMvEveryChange(Number(e.target.value))}
          disabled={disabled}
          className="w-full h-2 bg-slate-100 rounded-lg appearance-none cursor-pointer accent-blue-500"
        />
        <div className="flex justify-between text-[10px] text-slate-400 mt-0.5">
          <span>5s</span>
          <span>15s</span>
          <span>30s</span>
          <span>60s</span>
        </div>
      </div>

      {/* Presets */}
      <div className="mb-3">
        <div className="text-xs text-slate-500 mb-2">Presets</div>
        <div className="grid grid-cols-4 gap-2">
          {Object.entries(PRESETS).map(([name, preset]) => (
            <button
              key={name}
              onClick={() => onMvMaskChange({ ...preset.mask })}
              disabled={disabled}
              className={`p-2 rounded-lg border-2 text-left transition-all ${
                currentPreset === name
                  ? 'border-blue-500 bg-blue-50 shadow-sm'
                  : 'border-slate-200 hover:border-slate-300 bg-white'
              } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
            >
              <div className="text-xs font-semibold">{preset.label}</div>
              <div className="text-[10px] text-slate-500">{preset.description}</div>
            </button>
          ))}
          <div
            className={`p-2 rounded-lg border-2 text-left ${
              currentPreset === 'custom'
                ? 'border-orange-400 bg-orange-50'
                : 'border-slate-200 bg-white opacity-50'
            }`}
          >
            <div className="text-xs font-semibold">Custom</div>
            <div className="text-[10px] text-slate-500">Selection manuelle</div>
          </div>
        </div>
      </div>

      {/* Checkboxes */}
      <div className="mb-3">
        <div className="text-xs text-slate-500 mb-2">Mesures envoyees</div>
        <div className="grid grid-cols-2 gap-2">
          {MEASURES.map(({ key, label }) => (
            <label
              key={key}
              className={`flex items-center gap-2 p-2 rounded-lg border cursor-pointer transition-colors ${
                getMaskValue(mvMask, key)
                  ? 'border-blue-200 bg-blue-50'
                  : 'border-slate-200 bg-white hover:bg-slate-50'
              }`}
            >
              <input
                type="checkbox"
                checked={getMaskValue(mvMask, key)}
                onChange={(e) => onMvMaskChange({ ...mvMask, [key]: e.target.checked })}
                disabled={disabled}
                className="w-4 h-4 accent-blue-500 rounded"
              />
              <span className="text-xs text-slate-700">{label}</span>
            </label>
          ))}
        </div>
      </div>

      {/* Bouton Appliquer */}
      <button
        onClick={onApply}
        disabled={disabled}
        className="w-full px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
      >
        Appliquer MeterValues
      </button>
    </AccordionSection>
  );
}

export default MeterValuesSection;
