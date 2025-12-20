// frontend/src/components/simu-evse/ChargingCable.tsx
// Câble de charge réaliste avec un seul connecteur côté véhicule

import React from 'react';

interface ChargingCableProps {
  isPlugged: boolean;      // Câble branché au véhicule
  isCharging: boolean;     // Charge en cours
  powerKW?: number;        // Puissance pour adapter la vitesse d'animation
  className?: string;
}

export function ChargingCable({
  isPlugged,
  isCharging,
  powerKW = 0,
  className = ''
}: ChargingCableProps) {
  // Couleur du flux selon la puissance
  const getEnergyColors = () => {
    if (powerKW > 100) return { main: '#00d4ff', secondary: '#00ff88' }; // DC rapide
    if (powerKW > 22) return { main: '#3b82f6', secondary: '#60a5fa' }; // DC moyen
    return { main: '#10b981', secondary: '#34d399' }; // AC
  };

  // Vitesse d'animation selon la puissance
  const getAnimationDuration = () => {
    if (powerKW > 100) return '0.8s';
    if (powerKW > 50) return '1s';
    if (powerKW > 22) return '1.2s';
    return '1.5s';
  };

  const colors = getEnergyColors();
  const animDuration = getAnimationDuration();

  return (
    <div
      className={`charging-cable-wrapper ${className}`}
      style={{
        position: 'absolute',
        left: '8%',
        right: '26%',
        top: '30%',
        height: '140px',
        pointerEvents: 'none',
        zIndex: 50,
      }}
    >
      <svg
        viewBox="0 0 500 140"
        className="w-full h-full"
        preserveAspectRatio="xMidYMid meet"
        style={{ overflow: 'visible' }}
      >
        <defs>
          {/* Gradient du câble principal */}
          <linearGradient id="cableMainGradient" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#3a3a4a" />
            <stop offset="30%" stopColor="#1a1a2a" />
            <stop offset="70%" stopColor="#1a1a2a" />
            <stop offset="100%" stopColor="#3a3a4a" />
          </linearGradient>

          {/* Ombre du câble */}
          <filter id="cableShadowFilter" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="5" stdDeviation="4" floodOpacity="0.4"/>
          </filter>

          {/* Highlight du câble */}
          <linearGradient id="cableHighlight" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="rgba(255,255,255,0.2)" />
            <stop offset="50%" stopColor="rgba(255,255,255,0)" />
            <stop offset="100%" stopColor="rgba(0,0,0,0.1)" />
          </linearGradient>

          {/* Glow pour le flux d'énergie */}
          {isCharging && (
            <filter id="energyGlowFilter" x="-50%" y="-50%" width="200%" height="200%">
              <feGaussianBlur stdDeviation="4" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          )}

          {/* Gradient du flux d'énergie */}
          {isCharging && (
            <linearGradient id="energyFlowGradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor={colors.main} stopOpacity="0.2" />
              <stop offset="20%" stopColor={colors.main} stopOpacity="1" />
              <stop offset="50%" stopColor={colors.secondary} stopOpacity="1" />
              <stop offset="80%" stopColor={colors.main} stopOpacity="1" />
              <stop offset="100%" stopColor={colors.main} stopOpacity="0.2" />
            </linearGradient>
          )}
        </defs>

        {/* ══════════════════════════════════════════════════════════════
            CÂBLE PRINCIPAL - Courbe de Bézier naturelle
        ══════════════════════════════════════════════════════════════ */}

        {isPlugged ? (
          /* Câble CONNECTÉ - tendu avec légère courbe naturelle */
          <>
            {/* Ombre du câble */}
            <path
              d="M 0,50
                 C 100,50 120,90 250,95
                 S 400,85 500,60"
              fill="none"
              stroke="rgba(0,0,0,0.25)"
              strokeWidth="20"
              strokeLinecap="round"
              transform="translate(0, 8)"
            />

            {/* Câble principal - corps */}
            <path
              d="M 0,50
                 C 100,50 120,90 250,95
                 S 400,85 500,60"
              fill="none"
              stroke="url(#cableMainGradient)"
              strokeWidth="16"
              strokeLinecap="round"
              filter="url(#cableShadowFilter)"
            />

            {/* Câble - highlight */}
            <path
              d="M 0,50
                 C 100,50 120,90 250,95
                 S 400,85 500,60"
              fill="none"
              stroke="url(#cableHighlight)"
              strokeWidth="16"
              strokeLinecap="round"
            />

            {/* Texture rayures du câble */}
            <path
              d="M 0,50
                 C 100,50 120,90 250,95
                 S 400,85 500,60"
              fill="none"
              stroke="rgba(255,255,255,0.05)"
              strokeWidth="10"
              strokeLinecap="round"
              strokeDasharray="3 8"
            />

            {/* Bandes de renfort */}
            <path
              d="M 0,50
                 C 100,50 120,90 250,95
                 S 400,85 500,60"
              fill="none"
              stroke="#4a4a5a"
              strokeWidth="4"
              strokeLinecap="round"
              strokeDasharray="3 50"
            />

            {/* Flux d'énergie animé */}
            {isCharging && (
              <>
                {/* Glow principal */}
                <path
                  d="M 0,50
                     C 100,50 120,90 250,95
                     S 400,85 500,60"
                  fill="none"
                  stroke={colors.secondary}
                  strokeWidth="12"
                  strokeLinecap="round"
                  opacity="0.3"
                  filter="url(#energyGlowFilter)"
                />

                {/* Flux animé - particules principales */}
                <path
                  d="M 0,50
                     C 100,50 120,90 250,95
                     S 400,85 500,60"
                  fill="none"
                  stroke="url(#energyFlowGradient)"
                  strokeWidth="8"
                  strokeLinecap="round"
                  strokeDasharray="25 40"
                  style={{
                    animation: `energyFlowAnim ${animDuration} linear infinite`,
                  }}
                />

                {/* Flux animé secondaire (décalé) */}
                <path
                  d="M 0,50
                     C 100,50 120,90 250,95
                     S 400,85 500,60"
                  fill="none"
                  stroke={colors.main}
                  strokeWidth="4"
                  strokeLinecap="round"
                  strokeDasharray="10 60"
                  opacity="0.9"
                  style={{
                    animation: `energyFlowAnim ${animDuration} linear infinite`,
                    animationDelay: '-0.4s',
                  }}
                />

                {/* Particules d'énergie */}
                <path
                  d="M 0,50
                     C 100,50 120,90 250,95
                     S 400,85 500,60"
                  fill="none"
                  stroke={colors.secondary}
                  strokeWidth="5"
                  strokeLinecap="round"
                  strokeDasharray="5 70"
                  filter="url(#energyGlowFilter)"
                  style={{
                    animation: `energyFlowAnim ${animDuration} linear infinite`,
                    animationDelay: '-0.8s',
                  }}
                />
              </>
            )}
          </>
        ) : (
          /* Câble DÉCONNECTÉ - pendant, détendu */
          <>
            {/* Ombre */}
            <path
              d="M 0,50
                 C 60,50 100,100 160,115
                 S 220,110 250,100"
              fill="none"
              stroke="rgba(0,0,0,0.2)"
              strokeWidth="18"
              strokeLinecap="round"
              transform="translate(0, 6)"
            />

            {/* Câble détendu */}
            <path
              d="M 0,50
                 C 60,50 100,100 160,115
                 S 220,110 250,100"
              fill="none"
              stroke="url(#cableMainGradient)"
              strokeWidth="14"
              strokeLinecap="round"
            />

            {/* Highlight */}
            <path
              d="M 0,50
                 C 60,50 100,100 160,115
                 S 220,110 250,100"
              fill="none"
              stroke="url(#cableHighlight)"
              strokeWidth="14"
              strokeLinecap="round"
            />
          </>
        )}

        {/* ══════════════════════════════════════════════════════════════
            CONNECTEUR - Uniquement côté véhicule quand branché
        ══════════════════════════════════════════════════════════════ */}

        {isPlugged && (
          <g transform="translate(485, 48)">
            {/* Glow en charge - halo externe */}
            {isCharging && (
              <>
                <circle cx="18" cy="14" r="35" fill="none" stroke={colors.main} strokeWidth="3" opacity="0.2">
                  <animate attributeName="r" values="30;45;30" dur="2s" repeatCount="indefinite"/>
                  <animate attributeName="opacity" values="0.3;0.1;0.3" dur="2s" repeatCount="indefinite"/>
                </circle>
                <circle cx="18" cy="14" r="25" fill="none" stroke={colors.secondary} strokeWidth="2" opacity="0.4">
                  <animate attributeName="r" values="20;32;20" dur="2s" repeatCount="indefinite"/>
                  <animate attributeName="opacity" values="0.5;0.2;0.5" dur="2s" repeatCount="indefinite"/>
                </circle>
              </>
            )}

            {/* Corps du connecteur - fond */}
            <rect
              x="0" y="0"
              width="36" height="28"
              rx="5"
              fill="#1e1e2e"
              stroke="#3a3a4a"
              strokeWidth="2"
            />

            {/* Highlight du connecteur */}
            <rect
              x="2" y="2"
              width="32" height="10"
              rx="3"
              fill="rgba(255,255,255,0.1)"
            />

            {/* Pins du connecteur Type 2 */}
            <circle cx="9" cy="9" r="3" fill="#606070" stroke="#707080" strokeWidth="1"/>
            <circle cx="27" cy="9" r="3" fill="#606070" stroke="#707080" strokeWidth="1"/>
            <circle cx="9" cy="19" r="3" fill="#606070" stroke="#707080" strokeWidth="1"/>
            <circle cx="27" cy="19" r="3" fill="#606070" stroke="#707080" strokeWidth="1"/>

            {/* Pin central (PE) */}
            <rect x="14" y="15" width="8" height="6" rx="2" fill="#606070" stroke="#707080" strokeWidth="1"/>

            {/* LED de statut */}
            <circle
              cx="18"
              cy="5"
              r="2"
              fill={isCharging ? colors.secondary : '#4a5568'}
              style={isCharging ? { animation: 'ledBlink 1s ease-in-out infinite' } : {}}
            />
          </g>
        )}

        {/* Connecteur pendant quand déconnecté */}
        {!isPlugged && (
          <g transform="translate(238, 88) rotate(12)">
            {/* Corps du connecteur */}
            <rect
              x="0" y="0"
              width="30" height="24"
              rx="4"
              fill="#1e1e2e"
              stroke="#3a3a4a"
              strokeWidth="1.5"
            />

            {/* Highlight */}
            <rect
              x="2" y="2"
              width="26" height="8"
              rx="2"
              fill="rgba(255,255,255,0.08)"
            />

            {/* Pins simplifiés */}
            <circle cx="8" cy="8" r="2.5" fill="#505060"/>
            <circle cx="22" cy="8" r="2.5" fill="#505060"/>
            <circle cx="8" cy="16" r="2.5" fill="#505060"/>
            <circle cx="22" cy="16" r="2.5" fill="#505060"/>
            <rect x="12" y="13" width="6" height="5" rx="1.5" fill="#505060"/>
          </g>
        )}

        {/* Styles d'animation */}
        <style>
          {`
            @keyframes energyFlowAnim {
              from { stroke-dashoffset: 130; }
              to { stroke-dashoffset: 0; }
            }
            @keyframes ledBlink {
              0%, 100% { opacity: 1; }
              50% { opacity: 0.4; }
            }
          `}
        </style>
      </svg>
    </div>
  );
}

export default ChargingCable;
