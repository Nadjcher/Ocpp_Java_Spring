// frontend/src/components/simu-evse/Connector.tsx
// Composant visuel pour le connecteur de charge - Version améliorée

import React, { useState } from 'react';
import { ASSETS } from '@/constants/evse.constants';

interface ConnectorProps {
  side: 'left' | 'right';
  glowing?: boolean;
  size?: number;
  rotation?: number;
  status?: 'idle' | 'connected' | 'charging' | 'error';
  showLabel?: boolean;
}

export function Connector({
  side,
  glowing = false,
  size = 180,
  rotation = 0,
  status = 'idle',
  showLabel = false,
}: ConnectorProps) {
  const src = side === 'left' ? ASSETS.connectors.left : ASSETS.connectors.right;
  const [imageError, setImageError] = useState(false);

  // Couleurs selon le statut
  const statusColors = {
    idle: { bg: '#64748b', glow: 'rgba(100, 116, 139, 0.4)', ring: '#94a3b8' },
    connected: { bg: '#3b82f6', glow: 'rgba(59, 130, 246, 0.5)', ring: '#60a5fa' },
    charging: { bg: '#10b981', glow: 'rgba(16, 185, 129, 0.7)', ring: '#34d399' },
    error: { bg: '#ef4444', glow: 'rgba(239, 68, 68, 0.5)', ring: '#f87171' },
  };

  const effectiveStatus = glowing ? 'charging' : status;
  const colors = statusColors[effectiveStatus];

  // Animation class pour le statut charging
  const animationClass = effectiveStatus === 'charging'
    ? 'animate-[connector-pulse_1.5s_ease-in-out_infinite]'
    : '';

  return (
    <div
      className={`absolute -translate-x-1/2 -translate-y-1/2 ${animationClass}`}
      style={{
        width: size,
        height: size,
        transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
        zIndex: 60,
      }}
    >
      {/* Halo externe animé pour le mode charging */}
      {effectiveStatus === 'charging' && (
        <div
          className="absolute inset-0 rounded-full animate-ping"
          style={{
            background: `radial-gradient(circle, ${colors.glow} 0%, transparent 70%)`,
            opacity: 0.4,
          }}
        />
      )}

      {/* Anneau de statut */}
      <div
        className="absolute inset-1 rounded-full transition-all duration-300"
        style={{
          border: `3px solid ${colors.ring}`,
          boxShadow: effectiveStatus === 'charging'
            ? `0 0 20px ${colors.glow}, 0 0 40px ${colors.glow}`
            : `0 0 10px ${colors.glow}`,
        }}
      />

      {/* Conteneur principal */}
      <div
        className="relative w-full h-full rounded-full overflow-hidden"
        style={{
          background: `linear-gradient(145deg, ${colors.bg}22, ${colors.bg}11)`,
          backdropFilter: 'blur(8px)',
        }}
      >
        {!imageError ? (
          <img
            src={src}
            alt={`Connecteur ${side}`}
            className="w-full h-full object-contain p-2 transition-all duration-300"
            style={{
              filter: effectiveStatus === 'charging'
                ? `drop-shadow(0 0 12px ${colors.glow}) brightness(1.1)`
                : 'drop-shadow(0 2px 4px rgba(0,0,0,0.3))',
            }}
            onError={() => {
              console.error(`Image non trouvée: ${src}`);
              setImageError(true);
            }}
          />
        ) : (
          /* Fallback visuel amélioré */
          <div
            className="w-full h-full flex flex-col items-center justify-center transition-all duration-300"
            style={{
              background: `linear-gradient(135deg, ${colors.bg}ee 0%, ${colors.bg}aa 100%)`,
              boxShadow: effectiveStatus === 'charging'
                ? `inset 0 0 20px rgba(255,255,255,0.2), 0 0 30px ${colors.glow}`
                : 'inset 0 0 10px rgba(255,255,255,0.1)',
            }}
          >
            {/* Icône de connecteur stylisée */}
            <svg
              width={size * 0.4}
              height={size * 0.4}
              viewBox="0 0 24 24"
              fill="none"
              stroke="white"
              strokeWidth="2"
              className={effectiveStatus === 'charging' ? 'animate-pulse' : ''}
            >
              <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" strokeLinejoin="round" strokeLinecap="round" />
            </svg>
            {showLabel && (
              <span className="text-white text-xs mt-1 font-medium opacity-80">
                {side === 'left' ? 'EVSE' : 'EV'}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Indicateur de statut (petit point) */}
      <div
        className="absolute bottom-1 right-1 w-3 h-3 rounded-full border-2 border-white transition-all duration-300"
        style={{
          backgroundColor: colors.bg,
          boxShadow: `0 0 6px ${colors.glow}`,
        }}
      />
    </div>
  );
}

export default Connector;
