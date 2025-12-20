// frontend/src/components/simu-evse/VehicleCard.tsx
// Composant d'affichage amélioré pour le véhicule électrique

import React, { useState } from 'react';

interface VehicleCardProps {
  name: string;
  imageUrl?: string;
  isParked: boolean;
  isPlugged: boolean;
  isCharging: boolean;
  soc: number;
  targetSoc?: number;
  capacityKWh?: number;
  maxPowerKW?: number;
  scale?: number;
  portRef?: React.RefObject<HTMLDivElement>;
  onImageError?: () => void;
}

/**
 * Composant d'affichage du véhicule électrique
 * Affiche l'image, le statut, et les infos du véhicule
 */
export const VehicleCard: React.FC<VehicleCardProps> = ({
  name,
  imageUrl = '/images/generic-ev.png',
  isParked,
  isPlugged,
  isCharging,
  soc,
  targetSoc = 80,
  capacityKWh,
  maxPowerKW,
  scale = 1.3,
  portRef,
  onImageError,
}) => {
  const [imgError, setImgError] = useState(false);

  // Couleur selon SoC
  const getSocColor = (value: number) => {
    if (value >= 80) return { bg: 'bg-emerald-500', text: 'text-emerald-600', light: 'bg-emerald-100' };
    if (value >= 50) return { bg: 'bg-blue-500', text: 'text-blue-600', light: 'bg-blue-100' };
    if (value >= 20) return { bg: 'bg-amber-500', text: 'text-amber-600', light: 'bg-amber-100' };
    return { bg: 'bg-red-500', text: 'text-red-600', light: 'bg-red-100' };
  };

  const socColor = getSocColor(soc);

  // Animation d'entrée/sortie
  const animationClass = isParked
    ? 'animate-[vehicle-arrive_0.5s_ease-out_forwards]'
    : '';

  if (!isParked) return null;

  return (
    <div
      className={`relative select-none flex-shrink-0 ${animationClass}`}
      style={{
        width: 380 * scale,
        height: 220 * scale,
      }}
    >
      {/* Container principal avec ombre et effet de profondeur */}
      <div
        className="absolute inset-0 transition-all duration-300"
        style={{
          filter: isCharging
            ? 'drop-shadow(0 10px 30px rgba(16, 185, 129, 0.3))'
            : 'drop-shadow(0 8px 20px rgba(0, 0, 0, 0.15))',
        }}
      >
        {/* Image du véhicule */}
        {!imgError ? (
          <img
            src={imageUrl}
            alt={name}
            className="w-full h-full object-contain transition-all duration-300"
            style={{
              filter: isCharging
                ? 'brightness(1.05) saturate(1.1)'
                : 'brightness(1)',
            }}
            onError={() => {
              setImgError(true);
              onImageError?.();
            }}
          />
        ) : (
          /* Fallback stylisé */
          <div
            className="w-full h-full rounded-2xl flex flex-col items-center justify-center transition-all duration-300"
            style={{
              background: 'linear-gradient(145deg, #3b82f6 0%, #2563eb 50%, #1d4ed8 100%)',
              boxShadow: isCharging
                ? '0 0 40px rgba(59, 130, 246, 0.4), inset 0 0 30px rgba(255,255,255,0.1)'
                : 'inset 0 0 20px rgba(255,255,255,0.1)',
            }}
          >
            {/* Icône de voiture stylisée */}
            <svg
              width={100 * scale}
              height={60 * scale}
              viewBox="0 0 100 60"
              fill="none"
              stroke="white"
              strokeWidth="2"
              className="opacity-90"
            >
              {/* Corps de la voiture */}
              <path d="M10 40 L15 25 L30 20 L70 20 L85 25 L90 40" strokeLinejoin="round" />
              <path d="M5 40 L95 40" />
              <path d="M5 40 Q5 50 15 50 L85 50 Q95 50 95 40" />
              {/* Roues */}
              <circle cx="25" cy="50" r="8" fill="white" opacity="0.3" />
              <circle cx="75" cy="50" r="8" fill="white" opacity="0.3" />
              {/* Fenêtres */}
              <path d="M35 22 L32 35 L45 35 L45 22 Z" fill="white" opacity="0.2" />
              <path d="M50 22 L50 35 L68 35 L72 22 Z" fill="white" opacity="0.2" />
              {/* Éclair (pour EV) */}
              <path d="M55 8 L48 18 L53 18 L47 28 L58 15 L52 15 L55 8" fill="#fbbf24" stroke="none" />
            </svg>
            <span className="text-white font-bold text-lg mt-2 opacity-90">{name}</span>
          </div>
        )}
      </div>

      {/* Port de charge (point d'ancrage pour le câble) */}
      <div
        ref={portRef}
        className="absolute w-4 h-4"
        style={{
          left: 20 * scale,
          top: '50%',
          transform: 'translateY(-50%)',
        }}
      />

      {/* Badge de statut en haut à gauche */}
      <div className="absolute -top-2 -left-2 z-10">
        <div
          className={`px-3 py-1.5 rounded-full text-xs font-semibold shadow-lg transition-all duration-300 ${
            isCharging
              ? 'bg-emerald-500 text-white'
              : isPlugged
              ? 'bg-blue-500 text-white'
              : 'bg-slate-600 text-white'
          }`}
          style={{
            boxShadow: isCharging
              ? '0 0 15px rgba(16, 185, 129, 0.5)'
              : '0 2px 8px rgba(0,0,0,0.2)',
          }}
        >
          {isCharging ? (
            <span className="flex items-center gap-1.5">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-white opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-white" />
              </span>
              En charge
            </span>
          ) : isPlugged ? (
            'Branché'
          ) : (
            'Stationné'
          )}
        </div>
      </div>

      {/* Indicateur SoC en bas */}
      <div className="absolute -bottom-3 left-1/2 -translate-x-1/2 z-10">
        <div
          className={`px-4 py-2 rounded-xl shadow-lg ${socColor.light} transition-all duration-300`}
          style={{
            backdropFilter: 'blur(8px)',
            minWidth: 120,
          }}
        >
          <div className="flex items-center justify-center gap-2">
            {/* Icône batterie */}
            <svg width="24" height="14" viewBox="0 0 24 14" className={socColor.text}>
              <rect x="1" y="1" width="20" height="12" rx="2" fill="none" stroke="currentColor" strokeWidth="1.5" />
              <rect x="21" y="4" width="2" height="6" rx="0.5" fill="currentColor" />
              <rect
                x="2.5"
                y="2.5"
                width={`${Math.min(100, soc) * 0.17}`}
                height="9"
                rx="1"
                fill="currentColor"
              />
            </svg>
            {/* Valeur SoC */}
            <span className={`font-bold ${socColor.text}`}>{Math.round(soc)}%</span>
          </div>
          {/* Barre de progression */}
          <div className="mt-1.5 h-1 bg-white/50 rounded-full overflow-hidden">
            <div
              className={`h-full ${socColor.bg} rounded-full transition-all duration-500`}
              style={{ width: `${Math.min(100, soc)}%` }}
            />
          </div>
          {/* Cible SoC */}
          {targetSoc && targetSoc !== Math.round(soc) && (
            <div className="text-[10px] text-center text-slate-500 mt-1">
              Cible: {targetSoc}%
            </div>
          )}
        </div>
      </div>

      {/* Info véhicule (coin supérieur droit) */}
      {(capacityKWh || maxPowerKW) && (
        <div className="absolute -top-2 -right-2 z-10">
          <div className="px-2 py-1 rounded-lg bg-white/90 shadow-md text-[10px] text-slate-600">
            {capacityKWh && <div>{capacityKWh} kWh</div>}
            {maxPowerKW && <div>Max {maxPowerKW} kW</div>}
          </div>
        </div>
      )}

      {/* Effet de halo pendant la charge */}
      {isCharging && (
        <div
          className="absolute inset-0 rounded-3xl pointer-events-none animate-pulse"
          style={{
            background: 'radial-gradient(ellipse at center, rgba(16, 185, 129, 0.1) 0%, transparent 70%)',
          }}
        />
      )}
    </div>
  );
};

export default VehicleCard;
