// frontend/src/components/simu-evse/Connector.tsx
// Composant visuel pour le connecteur de charge

import React, { useState } from 'react';
import { ASSETS } from '@/constants/evse.constants';

interface ConnectorProps {
  side: 'left' | 'right';
  glowing?: boolean;
  size?: number;
  rotation?: number;
}

export function Connector({
  side,
  glowing = false,
  size = 220,
  rotation = 0,
}: ConnectorProps) {
  const src = side === 'left' ? ASSETS.connectors.left : ASSETS.connectors.right;
  const [imageError, setImageError] = useState(false);

  return (
    <div
      className="absolute -translate-x-1/2 -translate-y-1/2"
      style={{
        width: size,
        height: size,
        transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
        zIndex: 60,
      }}
    >
      {!imageError ? (
        <img
          src={src}
          alt={`Connecteur ${side}`}
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'contain',
            filter: glowing
              ? 'drop-shadow(0 0 18px rgba(16,185,129,.85))'
              : 'drop-shadow(0 1px 6px rgba(0,0,0,.35))',
          }}
          onError={() => {
            console.error(`Image non trouvée: ${src}`);
            setImageError(true);
          }}
        />
      ) : (
        <div
          className="w-full h-full rounded-full flex items-center justify-center"
          style={{
            backgroundColor: glowing ? '#10b981' : '#6b7280',
            opacity: glowing ? 0.9 : 0.6,
            boxShadow: glowing
              ? '0 0 20px rgba(16,185,129,0.8)'
              : '0 2px 4px rgba(0,0,0,0.2)',
          }}
        >
          <span
            style={{
              color: 'white',
              fontSize: size * 0.3,
              fontWeight: 'bold',
            }}
          >
            {side === 'left' ? '◀' : '▶'}
          </span>
        </div>
      )}
    </div>
  );
}

export default Connector;
