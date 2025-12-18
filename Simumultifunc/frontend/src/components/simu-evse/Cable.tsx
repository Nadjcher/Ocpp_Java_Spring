// frontend/src/components/simu-evse/Cable.tsx
// Composant visuel pour le câble de charge animé

import React, { useState, useEffect, useRef } from 'react';
import { clamp } from '@/utils/evse.utils';

interface CableProps {
  containerRef: React.RefObject<HTMLDivElement>;
  startRef: React.RefObject<HTMLDivElement>;
  endRef: React.RefObject<HTMLDivElement>;
  show: boolean;
  charging: boolean;
  sagFactor?: number;
  extraDropPx?: number;
  onAnglesChange?: (angles: { left: number; right: number }) => void;
}

export function Cable({
  containerRef,
  startRef,
  endRef,
  show,
  charging,
  sagFactor = 0.48,
  extraDropPx = 10,
  onAnglesChange,
}: CableProps) {
  const [dims, setDims] = useState({ w: 0, h: 0, d: '' });
  const pathRef = useRef<SVGPathElement>(null);
  const [len, setLen] = useState(0);
  const [dashOffset, setDashOffset] = useState(0);

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
      const sag = clamp(dist * sagFactor, 60, 220);
      const cx1 = x1 + dx * 0.28;
      const cy1 = y1 + sag + extraDropPx;
      const cx2 = x2 - dx * 0.28;
      const cy2 = y2 + sag * 0.85 + extraDropPx;

      const leftAngle = (Math.atan2(cy1 - y1, cx1 - x1) * 180) / Math.PI;
      const rightAngle = (Math.atan2(y2 - cy2, x2 - cx2) * 180) / Math.PI;
      onAnglesChange?.({ left: leftAngle, right: rightAngle });

      const d = `M ${x1},${y1} C ${cx1},${cy1} ${cx2},${cy2} ${x2},${y2}`;
      setDims({ w: r.width, h: r.height, d });

      requestAnimationFrame(() => {
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
    };
  }, [containerRef, startRef, endRef, show, sagFactor, extraDropPx, onAnglesChange]);

  if (!show) return null;

  return (
    <svg
      width="100%"
      height="100%"
      viewBox={`0 0 ${Math.max(1, dims.w)} ${Math.max(1, dims.h)}`}
      style={{ position: 'absolute', inset: 0, zIndex: 40 }}
    >
      <defs>
        <filter id="cable-shadow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur in="SourceAlpha" stdDeviation="3" />
          <feOffset dx="0" dy="4" result="offsetblur" />
          <feFlood floodColor="rgba(0,0,0,0.25)" />
          <feComposite in2="offsetblur" operator="in" />
          <feMerge>
            <feMergeNode />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        {charging && (
          <linearGradient id="energy-flow" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#10b981" />
            <stop offset="50%" stopColor="#34d399" />
            <stop offset="100%" stopColor="#10b981" />
          </linearGradient>
        )}
      </defs>

      <path
        ref={pathRef}
        d={dims.d}
        stroke="#0f172a"
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

      {charging && (
        <path
          d={dims.d}
          stroke="url(#energy-flow)"
          strokeWidth={6}
          strokeLinecap="round"
          fill="none"
          strokeDasharray="12 20"
          style={{ animation: 'cableEnergy 1.5s linear infinite' }}
        />
      )}
    </svg>
  );
}

export default Cable;
