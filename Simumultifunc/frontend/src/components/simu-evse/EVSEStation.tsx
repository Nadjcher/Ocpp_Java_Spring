// frontend/src/components/simu-evse/EVSEStation.tsx
// Composant d'affichage amélioré pour la borne de charge EVSE

import React, { useState } from 'react';
import { ASSETS } from '@/constants/evse.constants';

type EVSEType = 'ac-mono' | 'ac-bi' | 'ac-tri' | 'dc';
type EVSEStatus = 'available' | 'preparing' | 'charging' | 'finishing' | 'faulted' | 'unavailable';

interface EVSEStationProps {
  type: EVSEType;
  status: EVSEStatus;
  cpId?: string;
  maxPowerKW?: number;
  currentPowerKW?: number;
  isConnected?: boolean;
  portRef?: React.RefObject<HTMLDivElement>;
  onImageError?: () => void;
}

/**
 * Composant d'affichage de la borne de charge EVSE
 * Affiche l'image, le statut, et les informations de la borne
 */
export const EVSEStation: React.FC<EVSEStationProps> = ({
  type,
  status,
  cpId,
  maxPowerKW,
  currentPowerKW = 0,
  isConnected = false,
  portRef,
  onImageError,
}) => {
  const [imgError, setImgError] = useState(false);

  // Sélection de l'image selon le type
  const imageSrc = type === 'dc' ? ASSETS.stationDC : ASSETS.stationAC;

  // Couleurs selon le statut
  const getStatusConfig = (s: EVSEStatus) => {
    switch (s) {
      case 'available':
        return { bg: 'bg-emerald-500', text: 'text-emerald-600', label: 'Disponible', glow: 'rgba(16, 185, 129, 0.4)' };
      case 'preparing':
        return { bg: 'bg-amber-500', text: 'text-amber-600', label: 'Préparation', glow: 'rgba(245, 158, 11, 0.4)' };
      case 'charging':
        return { bg: 'bg-blue-500', text: 'text-blue-600', label: 'En charge', glow: 'rgba(59, 130, 246, 0.5)' };
      case 'finishing':
        return { bg: 'bg-purple-500', text: 'text-purple-600', label: 'Finalisation', glow: 'rgba(168, 85, 247, 0.4)' };
      case 'faulted':
        return { bg: 'bg-red-500', text: 'text-red-600', label: 'Erreur', glow: 'rgba(239, 68, 68, 0.4)' };
      case 'unavailable':
        return { bg: 'bg-slate-500', text: 'text-slate-600', label: 'Indisponible', glow: 'rgba(100, 116, 139, 0.3)' };
      default:
        return { bg: 'bg-slate-400', text: 'text-slate-500', label: status, glow: 'rgba(100, 116, 139, 0.2)' };
    }
  };

  const statusConfig = getStatusConfig(status);
  const isCharging = status === 'charging';

  // Label du type de borne
  const typeLabels: Record<EVSEType, string> = {
    'ac-mono': 'AC Mono',
    'ac-bi': 'AC Bi',
    'ac-tri': 'AC Tri',
    'dc': 'DC Fast',
  };

  return (
    <div
      className="relative select-none flex-shrink-0"
      style={{ width: 200, height: 280 }}
    >
      {/* Effet de halo selon le statut */}
      <div
        className={`absolute inset-0 rounded-2xl transition-all duration-500 ${isCharging ? 'animate-pulse' : ''}`}
        style={{
          background: `radial-gradient(ellipse at center bottom, ${statusConfig.glow} 0%, transparent 70%)`,
          opacity: isConnected ? 1 : 0.5,
        }}
      />

      {/* Container de l'image */}
      <div
        className="relative w-full h-full transition-all duration-300"
        style={{
          filter: isConnected
            ? isCharging
              ? 'drop-shadow(0 8px 25px rgba(0, 0, 0, 0.25))'
              : 'drop-shadow(0 6px 20px rgba(0, 0, 0, 0.2))'
            : 'drop-shadow(0 4px 12px rgba(0, 0, 0, 0.15)) grayscale(0.3)',
        }}
      >
        {!imgError ? (
          <img
            src={imageSrc}
            alt={`Borne ${typeLabels[type]}`}
            className="w-full h-full object-contain transition-all duration-300"
            style={{
              filter: isCharging ? 'brightness(1.05)' : 'brightness(1)',
            }}
            onError={() => {
              setImgError(true);
              onImageError?.();
            }}
          />
        ) : (
          /* Fallback stylisé */
          <div
            className="w-full h-full rounded-xl flex flex-col items-center justify-center transition-all duration-300"
            style={{
              background: 'linear-gradient(180deg, #374151 0%, #1f2937 60%, #111827 100%)',
              boxShadow: isCharging
                ? `0 0 30px ${statusConfig.glow}, inset 0 0 20px rgba(255,255,255,0.05)`
                : 'inset 0 0 15px rgba(255,255,255,0.05)',
            }}
          >
            {/* Écran de la borne */}
            <div
              className={`w-20 h-14 rounded-lg mb-4 flex items-center justify-center transition-all duration-300 ${
                isCharging ? 'animate-pulse' : ''
              }`}
              style={{
                background: isConnected
                  ? 'linear-gradient(180deg, #10b981 0%, #059669 100%)'
                  : 'linear-gradient(180deg, #374151 0%, #1f2937 100%)',
                boxShadow: isConnected ? '0 0 15px rgba(16, 185, 129, 0.5)' : 'none',
              }}
            >
              {isConnected && (
                <svg width="40" height="30" viewBox="0 0 40 30" fill="none" stroke="white" strokeWidth="2">
                  <path d="M20 5 L12 17 L18 17 L18 25 L28 13 L22 13 L22 5" fill="white" opacity="0.9" />
                </svg>
              )}
            </div>

            {/* Corps de la borne */}
            <div className="w-24 h-20 rounded-lg bg-gray-600" />

            {/* Base */}
            <div className="w-28 h-8 rounded-b-xl bg-gray-700 mt-2" />

            <span className="text-white font-bold text-sm mt-3 opacity-80">{typeLabels[type]}</span>
          </div>
        )}

        {/* Port de la borne (point d'ancrage pour le câble) */}
        <div
          ref={portRef}
          className="absolute w-4 h-4"
          style={{
            right: 20,
            top: '50%',
            transform: 'translateY(-50%)',
          }}
        />
      </div>

      {/* Badge de statut */}
      <div className="absolute -top-2 left-1/2 -translate-x-1/2 z-10">
        <div
          className={`px-3 py-1.5 rounded-full text-xs font-semibold text-white shadow-lg transition-all duration-300 ${statusConfig.bg}`}
          style={{
            boxShadow: isCharging
              ? `0 0 15px ${statusConfig.glow}`
              : '0 2px 8px rgba(0,0,0,0.2)',
          }}
        >
          <span className="flex items-center gap-1.5">
            {isCharging && (
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-white opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-white" />
              </span>
            )}
            {statusConfig.label}
          </span>
        </div>
      </div>

      {/* CP-ID en bas */}
      {cpId && (
        <div className="absolute -bottom-3 left-1/2 -translate-x-1/2 z-10">
          <div className="px-3 py-1.5 rounded-lg bg-slate-800 text-white text-xs font-mono shadow-lg">
            {cpId}
          </div>
        </div>
      )}

      {/* Indicateur de puissance (quand en charge) */}
      {isCharging && maxPowerKW && (
        <div className="absolute top-16 right-0 z-10">
          <div className="px-2 py-1 rounded-l-lg bg-blue-600 text-white text-xs font-bold shadow-lg">
            <div>{currentPowerKW.toFixed(1)} kW</div>
            <div className="text-[10px] opacity-80">/ {maxPowerKW} kW</div>
          </div>
        </div>
      )}

      {/* Type de borne (coin supérieur gauche) */}
      <div className="absolute top-2 left-2 z-10">
        <div
          className={`px-2 py-1 rounded text-[10px] font-semibold ${
            type === 'dc' ? 'bg-orange-500 text-white' : 'bg-blue-500 text-white'
          }`}
        >
          {typeLabels[type]}
        </div>
      </div>

      {/* Indicateur de connexion */}
      <div className="absolute bottom-20 right-2">
        <div
          className={`w-3 h-3 rounded-full transition-all duration-300 ${
            isConnected ? 'bg-emerald-500' : 'bg-slate-400'
          }`}
          style={{
            boxShadow: isConnected ? '0 0 8px rgba(16, 185, 129, 0.6)' : 'none',
          }}
        />
      </div>
    </div>
  );
};

export default EVSEStation;
