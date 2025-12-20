// frontend/src/components/simu-evse/ChargeDisplay.tsx
// Composant d'affichage de charge - Version améliorée avec design system

import React from 'react';

interface ChargeDisplayProps {
  soc: number;
  powerKw: number;
  energyKWh: number;
  elapsedTime: string;
  isCharging: boolean;
  vehicleName?: string;
  targetSoc?: number;
  estimatedTime?: string;
}

/**
 * Affiche les informations de charge en cours
 * Design moderne avec cercle de progression SoC et métriques
 */
export const ChargeDisplay: React.FC<ChargeDisplayProps> = ({
  soc,
  powerKw,
  energyKWh,
  elapsedTime,
  isCharging,
  vehicleName,
  targetSoc = 80,
  estimatedTime,
}) => {
  const strokeDasharray = `${(soc / 100) * 251.2}, 251.2`;
  const targetDasharray = `${(targetSoc / 100) * 251.2}, 251.2`;

  // Couleur selon le niveau de SoC
  const getSocColor = (value: number) => {
    if (value >= 80) return { main: '#10b981', light: '#d1fae5', text: 'text-emerald-600' };
    if (value >= 50) return { main: '#3b82f6', light: '#dbeafe', text: 'text-blue-600' };
    if (value >= 20) return { main: '#f59e0b', light: '#fef3c7', text: 'text-amber-600' };
    return { main: '#ef4444', light: '#fee2e2', text: 'text-red-600' };
  };

  const socColor = getSocColor(soc);

  return (
    <div
      className="rounded-2xl px-5 py-4 select-none transition-all duration-300"
      style={{
        minWidth: 320,
        zIndex: 60,
        background: 'linear-gradient(135deg, rgba(255,255,255,0.98) 0%, rgba(248,250,252,0.95) 100%)',
        backdropFilter: 'blur(12px)',
        border: '1px solid rgba(226, 232, 240, 0.8)',
        boxShadow: isCharging
          ? `0 20px 40px rgba(0,0,0,0.15), 0 0 40px ${socColor.main}22`
          : '0 10px 30px rgba(0,0,0,0.1)',
      }}
    >
      {/* En-tête avec nom du véhicule */}
      {vehicleName && (
        <div className="flex items-center justify-between mb-3 pb-2 border-b border-slate-200">
          <span className="text-xs font-medium text-slate-500 uppercase tracking-wide">
            {vehicleName}
          </span>
          {isCharging && powerKw > 0.1 && (
            <span className="flex items-center gap-1.5">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500" />
              </span>
              <span className="text-xs font-semibold text-emerald-600">En charge</span>
            </span>
          )}
        </div>
      )}

      <div className="flex items-center gap-5">
        {/* Cercle de progression SoC amélioré */}
        <div className="relative">
          <svg width="100" height="100" viewBox="0 0 100 100">
            {/* Fond du cercle */}
            <circle
              cx="50"
              cy="50"
              r="42"
              fill="none"
              stroke="#f1f5f9"
              strokeWidth="10"
            />
            {/* Indicateur de cible (en pointillé) */}
            {targetSoc && (
              <circle
                cx="50"
                cy="50"
                r="42"
                fill="none"
                stroke={socColor.main}
                strokeWidth="10"
                strokeDasharray={targetDasharray}
                strokeLinecap="round"
                opacity="0.2"
                transform="rotate(-90 50 50)"
              />
            )}
            {/* Progression actuelle */}
            <circle
              cx="50"
              cy="50"
              r="42"
              fill="none"
              stroke={socColor.main}
              strokeWidth="10"
              strokeDasharray={strokeDasharray}
              strokeLinecap="round"
              transform="rotate(-90 50 50)"
              style={{
                transition: 'stroke-dasharray 0.5s ease-out, stroke 0.3s ease',
                filter: isCharging ? `drop-shadow(0 0 6px ${socColor.main}88)` : 'none',
              }}
            />
            {/* Texte central */}
            <text x="50" y="44" textAnchor="middle" dominantBaseline="middle">
              <tspan fontSize="26" fontWeight="bold" fill="#0f172a">
                {Math.round(soc)}
              </tspan>
            </text>
            <text x="50" y="62" textAnchor="middle" fontSize="11" fill="#64748b" fontWeight="500">
              %
            </text>
          </svg>
          {/* Badge de cible */}
          {targetSoc && targetSoc !== soc && (
            <div
              className="absolute -bottom-1 left-1/2 -translate-x-1/2 px-2 py-0.5 rounded-full text-[10px] font-semibold"
              style={{ backgroundColor: socColor.light, color: socColor.main }}
            >
              Cible {targetSoc}%
            </div>
          )}
        </div>

        {/* Métriques */}
        <div className="flex-1 grid grid-cols-2 gap-x-4 gap-y-2">
          {/* Puissance */}
          <div>
            <div className="text-[10px] uppercase tracking-wider text-slate-400 font-medium mb-0.5">
              Puissance
            </div>
            <div className="flex items-baseline gap-1">
              <span className={`text-2xl font-bold ${socColor.text}`}>
                {powerKw.toFixed(1)}
              </span>
              <span className="text-sm text-slate-500">kW</span>
            </div>
          </div>

          {/* Énergie */}
          <div>
            <div className="text-[10px] uppercase tracking-wider text-slate-400 font-medium mb-0.5">
              Énergie
            </div>
            <div className="flex items-baseline gap-1">
              <span className="text-xl font-semibold text-slate-700">
                {energyKWh.toFixed(2)}
              </span>
              <span className="text-sm text-slate-500">kWh</span>
            </div>
          </div>

          {/* Temps écoulé */}
          <div>
            <div className="text-[10px] uppercase tracking-wider text-slate-400 font-medium mb-0.5">
              Durée
            </div>
            <div className="text-lg font-mono font-semibold text-slate-600">
              {elapsedTime}
            </div>
          </div>

          {/* Temps estimé restant */}
          {estimatedTime && isCharging && (
            <div>
              <div className="text-[10px] uppercase tracking-wider text-slate-400 font-medium mb-0.5">
                Restant
              </div>
              <div className="text-lg font-mono text-slate-500">
                ~{estimatedTime}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Barre de progression linéaire en bas */}
      <div className="mt-3 pt-2 border-t border-slate-100">
        <div className="h-1.5 bg-slate-100 rounded-full overflow-hidden">
          <div
            className="h-full rounded-full transition-all duration-500"
            style={{
              width: `${Math.min(100, soc)}%`,
              background: `linear-gradient(90deg, ${socColor.main} 0%, ${socColor.main}cc 100%)`,
              boxShadow: isCharging ? `0 0 8px ${socColor.main}66` : 'none',
            }}
          />
        </div>
      </div>
    </div>
  );
};

export default ChargeDisplay;
