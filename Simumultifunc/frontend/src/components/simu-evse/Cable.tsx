// frontend/src/components/simu-evse/Cable.tsx
// Composant visuel amélioré pour le câble de charge avec états et animations

import React, { useState, useEffect, useRef } from 'react';
import { clamp } from '@/utils/evse.utils';

export type CableState = 'idle' | 'vehicle_parked' | 'plugged' | 'charging';

interface CableProps {
  containerRef: React.RefObject<HTMLDivElement>;
  startRef: React.RefObject<HTMLDivElement>;
  endRef: React.RefObject<HTMLDivElement>;
  show: boolean;
  charging: boolean;
  state?: CableState;
  sagFactor?: number;
  extraDropPx?: number;
  powerKW?: number;
  onAnglesChange?: (angles: { left: number; right: number }) => void;
}

export function Cable({
  containerRef,
  startRef,
  endRef,
  show,
  charging,
  state = charging ? 'charging' : show ? 'plugged' : 'idle',
  sagFactor = 0.35,
  extraDropPx = 8,
  powerKW = 0,
  onAnglesChange,
}: CableProps) {
  const [dims, setDims] = useState({ w: 0, h: 0, d: '' });
  const pathRef = useRef<SVGPathElement>(null);
  const [len, setLen] = useState(0);
  const [dashOffset, setDashOffset] = useState(0);
  const animationIdRef = useRef<number>(0);

  // Couleur du flux selon la puissance
  const getEnergyColor = () => {
    if (powerKW > 100) return { start: '#00d4ff', mid: '#00ff88', end: '#00d4ff' }; // DC rapide - cyan/vert
    if (powerKW > 22) return { start: '#3b82f6', mid: '#60a5fa', end: '#3b82f6' }; // DC moyen - bleu
    return { start: '#10b981', mid: '#34d399', end: '#10b981' }; // AC - vert
  };

  // Vitesse d'animation selon la puissance
  const getAnimationDuration = () => {
    if (powerKW > 100) return '0.8s';
    if (powerKW > 50) return '1s';
    if (powerKW > 22) return '1.2s';
    return '1.5s';
  };

  useEffect(() => {
    if (!show) return;

    const update = () => {
      const cont = containerRef.current;
      const a = startRef.current;
      const b = endRef.current;
      if (!cont || !a || !b) return;

      const r = cont.getBoundingClientRect();
      const ra = a.getBoundingClientRect();
      const rb = b.getBoundingClientRect();

      const x1 = ra.left + ra.width / 2 - r.left;
      const y1 = ra.top + ra.height / 2 - r.top;
      const x2 = rb.left + rb.width / 2 - r.left;
      const y2 = rb.top + rb.height / 2 - r.top;

      const dx = Math.abs(x2 - x1);
      const dist = Math.hypot(x2 - x1, y2 - y1);

      // Sag réduit pour une courbe légère et naturelle
      // La courbe descend légèrement au milieu puis remonte
      const effectiveSag = state === 'charging' || state === 'plugged'
        ? clamp(dist * sagFactor * 0.15, 20, 50)  // Courbe légère quand connecté
        : clamp(dist * sagFactor * 0.3, 30, 80);   // Un peu plus quand déconnecté

      // Points de contrôle pour une courbe naturelle
      const midY = Math.max(y1, y2) + effectiveSag;
      const cx1 = x1 + dx * 0.3;
      const cy1 = midY;
      const cx2 = x2 - dx * 0.3;
      const cy2 = midY;

      const leftAngle = (Math.atan2(cy1 - y1, cx1 - x1) * 180) / Math.PI;
      const rightAngle = (Math.atan2(y2 - cy2, x2 - cx2) * 180) / Math.PI;
      onAnglesChange?.({ left: leftAngle, right: rightAngle });

      const d = `M ${x1},${y1} C ${cx1},${cy1} ${cx2},${cy2} ${x2},${y2}`;
      setDims({ w: r.width, h: r.height, d });

      animationIdRef.current = requestAnimationFrame(() => {
        const p = pathRef.current;
        if (!p) return;
        const L = p.getTotalLength();
        setLen(L);
        setDashOffset(L);
        requestAnimationFrame(() => setDashOffset(0));
      });
    };

    update();
    const int = setInterval(update, 500);
    window.addEventListener('resize', update);

    return () => {
      clearInterval(int);
      window.removeEventListener('resize', update);
      if (animationIdRef.current) {
        cancelAnimationFrame(animationIdRef.current);
      }
    };
  }, [containerRef, startRef, endRef, show, state, sagFactor, extraDropPx, onAnglesChange]);

  if (!show) return null;

  const energyColors = getEnergyColor();
  const animDuration = getAnimationDuration();

  return (
    <svg
      width="100%"
      height="100%"
      viewBox={`0 0 ${Math.max(1, dims.w)} ${Math.max(1, dims.h)}`}
      style={{ position: 'absolute', inset: 0, zIndex: 40, pointerEvents: 'none' }}
    >
      <defs>
        {/* Ombre du câble */}
        <filter id="cable-shadow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur in="SourceAlpha" stdDeviation="4" />
          <feOffset dx="0" dy="6" result="offsetblur" />
          <feFlood floodColor="rgba(0,0,0,0.35)" />
          <feComposite in2="offsetblur" operator="in" />
          <feMerge>
            <feMergeNode />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>

        {/* Gradient du câble principal */}
        <linearGradient id="cable-gradient" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#2a2a3e" />
          <stop offset="30%" stopColor="#1a1a2a" />
          <stop offset="70%" stopColor="#1a1a2a" />
          <stop offset="100%" stopColor="#2a2a3e" />
        </linearGradient>

        {/* Texture du câble */}
        <pattern id="cable-texture" width="8" height="8" patternUnits="userSpaceOnUse">
          <circle cx="4" cy="4" r="1.5" fill="#3a3a4e" opacity="0.4" />
        </pattern>

        {/* Highlight du câble */}
        <linearGradient id="cable-highlight" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="rgba(255,255,255,0.15)" />
          <stop offset="50%" stopColor="rgba(255,255,255,0)" />
          <stop offset="100%" stopColor="rgba(0,0,0,0.1)" />
        </linearGradient>

        {/* Gradient du flux d'énergie animé */}
        {charging && (
          <>
            <linearGradient id="energy-flow-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor={energyColors.start} stopOpacity="0.2" />
              <stop offset="20%" stopColor={energyColors.start} stopOpacity="1" />
              <stop offset="50%" stopColor={energyColors.mid} stopOpacity="1" />
              <stop offset="80%" stopColor={energyColors.end} stopOpacity="1" />
              <stop offset="100%" stopColor={energyColors.end} stopOpacity="0.2" />
            </linearGradient>

            {/* Glow pour le flux d'énergie */}
            <filter id="energy-glow" x="-50%" y="-50%" width="200%" height="200%">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </>
        )}
      </defs>

      {/* Câble principal - couche d'ombre */}
      <path
        d={dims.d}
        stroke="#0a0a12"
        strokeWidth={22}
        strokeLinecap="round"
        fill="none"
        opacity="0.3"
        style={{ transform: 'translate(0, 4px)' }}
      />

      {/* Câble principal - corps */}
      <path
        ref={pathRef}
        d={dims.d}
        stroke="url(#cable-gradient)"
        strokeWidth={18}
        strokeLinecap="round"
        fill="none"
        filter="url(#cable-shadow)"
        style={{
          strokeDasharray: len,
          strokeDashoffset: dashOffset,
          transition: 'stroke-dashoffset 0.6s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      />

      {/* Câble - texture */}
      <path
        d={dims.d}
        stroke="url(#cable-texture)"
        strokeWidth={16}
        strokeLinecap="round"
        fill="none"
        opacity="0.5"
      />

      {/* Câble - highlight supérieur */}
      <path
        d={dims.d}
        stroke="url(#cable-highlight)"
        strokeWidth={18}
        strokeLinecap="round"
        fill="none"
        opacity="0.6"
      />

      {/* Bandes de renfort sur le câble */}
      <path
        d={dims.d}
        stroke="#3a3a4e"
        strokeWidth={3}
        strokeLinecap="round"
        strokeDasharray="2 40"
        fill="none"
        opacity="0.6"
      />

      {/* Flux d'énergie pendant la charge */}
      {charging && (
        <>
          {/* Glow principal */}
          <path
            d={dims.d}
            stroke={energyColors.mid}
            strokeWidth={10}
            strokeLinecap="round"
            fill="none"
            opacity="0.3"
            filter="url(#energy-glow)"
          />

          {/* Flux animé - particules */}
          <path
            d={dims.d}
            stroke="url(#energy-flow-gradient)"
            strokeWidth={6}
            strokeLinecap="round"
            fill="none"
            strokeDasharray="20 30"
            style={{
              animation: `cableEnergyFlow ${animDuration} linear infinite`,
            }}
          />

          {/* Flux animé secondaire (décalé) */}
          <path
            d={dims.d}
            stroke={energyColors.start}
            strokeWidth={3}
            strokeLinecap="round"
            fill="none"
            strokeDasharray="8 50"
            opacity="0.8"
            style={{
              animation: `cableEnergyFlow ${animDuration} linear infinite`,
              animationDelay: '-0.4s',
            }}
          />

          {/* Particules d'énergie */}
          <path
            d={dims.d}
            stroke={energyColors.mid}
            strokeWidth={4}
            strokeLinecap="round"
            fill="none"
            strokeDasharray="4 60"
            filter="url(#energy-glow)"
            style={{
              animation: `cableEnergyFlow ${animDuration} linear infinite`,
              animationDelay: '-0.8s',
            }}
          />
        </>
      )}

      {/* Indicateur de connexion aux extrémités */}
      {(state === 'plugged' || state === 'charging') && (
        <>
          {/* Glow côté borne */}
          <circle
            cx={dims.d ? parseFloat(dims.d.split(' ')[1]?.split(',')[0] || '0') : 0}
            cy={dims.d ? parseFloat(dims.d.split(' ')[1]?.split(',')[1] || '0') : 0}
            r={charging ? 12 : 8}
            fill={charging ? energyColors.mid : '#4ade80'}
            opacity={charging ? 0.6 : 0.4}
            style={charging ? { animation: 'connectorPulse 1.5s ease-in-out infinite' } : {}}
          />

          {/* Glow côté véhicule */}
          <circle
            cx={dims.d ? parseFloat(dims.d.split(' ').pop()?.split(',')[0] || '0') : 0}
            cy={dims.d ? parseFloat(dims.d.split(' ').pop()?.split(',')[1] || '0') : 0}
            r={charging ? 12 : 8}
            fill={charging ? energyColors.mid : '#4ade80'}
            opacity={charging ? 0.6 : 0.4}
            style={charging ? { animation: 'connectorPulse 1.5s ease-in-out infinite', animationDelay: '0.75s' } : {}}
          />
        </>
      )}

      {/* Styles d'animation inline */}
      <style>
        {`
          @keyframes cableEnergyFlow {
            from { stroke-dashoffset: 100; }
            to { stroke-dashoffset: 0; }
          }
          @keyframes connectorPulse {
            0%, 100% { opacity: 0.4; r: 10; }
            50% { opacity: 0.8; r: 14; }
          }
        `}
      </style>
    </svg>
  );
}

export default Cable;
