// frontend/src/components/simu-evse/ChargeDisplay.tsx
// Composant d'affichage de charge extrait de SimuEvseTab.tsx

import React from 'react';

interface ChargeDisplayProps {
  soc: number;
  powerKw: number;
  energyKWh: number;
  elapsedTime: string;
  isCharging: boolean;
}

/**
 * Affiche les informations de charge en cours
 * - Cercle de progression SoC
 * - Puissance active
 * - Énergie délivrée
 * - Temps écoulé
 */
export const ChargeDisplay: React.FC<ChargeDisplayProps> = ({
  soc,
  powerKw,
  energyKWh,
  elapsedTime,
  isCharging,
}) => {
  const strokeDasharray = `${(soc / 100) * 251.2}, 251.2`;

  return (
    <div
      className="absolute rounded-xl px-4 py-3
                bg-white/95 backdrop-blur-md border-2 border-slate-300 shadow-2xl select-none"
      style={{ minWidth: 280, zIndex: 60 }}
    >
      <div className="flex items-center gap-4">
        <div className="relative">
          <svg width="84" height="84" viewBox="0 0 90 90">
            <circle
              cx="45"
              cy="45"
              r="40"
              fill="none"
              stroke="#e5e7eb"
              strokeWidth="8"
            />
            <circle
              cx="45"
              cy="45"
              r="40"
              fill="none"
              stroke={soc > 80 ? "#10b981" : soc > 20 ? "#3b82f6" : "#ef4444"}
              strokeWidth="8"
              strokeDasharray={strokeDasharray}
              strokeLinecap="round"
              transform="rotate(-90 45 45)"
              style={{ transition: "stroke-dasharray .35s ease" }}
            />
            <text
              x="45"
              y="45"
              textAnchor="middle"
              dominantBaseline="middle"
              className="fill-slate-900"
            >
              <tspan fontSize="20" fontWeight="bold">
                {Math.round(soc)}
              </tspan>
              <tspan fontSize="12" dy="12" x="45">
                %
              </tspan>
            </text>
          </svg>
        </div>

        <div className="flex flex-col gap-1">
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-bold text-slate-900">
              {powerKw.toFixed(1)}
            </span>
            <span className="text-sm text-slate-600">kW</span>
          </div>
          <div className="flex items-baseline gap-1">
            <span className="text-lg font-semibold text-slate-700">
              {energyKWh.toFixed(2)}
            </span>
            <span className="text-sm text-slate-500">kWh</span>
          </div>
          <div className="flex items-center gap-1 text-xs text-slate-600">
            <span className="font-mono">{elapsedTime}</span>
            {isCharging && powerKw > 0.1 && (
              <span className="ml-1 px-2 py-0.5 rounded-full text-xs bg-emerald-100 text-emerald-700 animate-[charging-pulse_2s_ease-in-out_infinite]">
                en charge
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ChargeDisplay;
