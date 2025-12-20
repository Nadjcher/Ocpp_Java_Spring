// frontend/src/components/simu-evse/Chart.tsx
import React, { useState, useEffect, useRef } from "react";
import type { Serie } from "@/types/evse.types";
import { clamp } from "@/utils/evse.utils";

interface ChartProps {
  series: Serie[];
  yMax: number;
  height?: number;
  xLabel?: string;
  yLabel?: string;
  refLines?: Array<{ value: number; label: string; color: string }>;
}

export function Chart({
  series,
  yMax,
  height = 300,
  xLabel,
  yLabel,
  refLines,
}: ChartProps) {
  const [hover, setHover] = useState<{
    x: number;
    vals: { label: string; y: number; color: string }[];
  } | null>(null);

  const boxRef = useRef<HTMLDivElement>(null);
  const [W, setW] = useState(560);
  const H = height;
  const pad = 48;

  useEffect(() => {
    const update = () => {
      const w = boxRef.current?.clientWidth || 560;
      setW(Math.max(480, Math.floor(w)));
    };
    update();
    const ro = new ResizeObserver(update);
    if (boxRef.current) ro.observe(boxRef.current);
    window.addEventListener("resize", update);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", update);
    };
  }, []);

  const all = series.flatMap((s) => s.points);
  const tmin = all.length ? Math.min(...all.map((p) => p.t)) : 0;
  const tmax = all.length ? Math.max(...all.map((p) => p.t)) : 1;

  const xOf = (t: number) =>
    pad + ((t - tmin) / Math.max(1, tmax - tmin)) * (W - pad * 2);
  const yOf = (y: number) =>
    H - pad - (clamp(y, 0, yMax) / Math.max(1, yMax)) * (H - pad * 2);

  function onMove(e: React.MouseEvent<SVGSVGElement>) {
    const rect = (e.target as SVGElement)
      .closest("svg")!
      .getBoundingClientRect();
    const x = e.clientX - rect.left;
    const tGuess =
      tmin +
      ((x - pad) / Math.max(1, W - pad * 2)) * Math.max(1, tmax - tmin);

    const vals = series.map((s) => {
      let best = s.points[0];
      let d = Infinity;
      for (const p of s.points) {
        const dd = Math.abs(p.t - tGuess);
        if (dd < d) {
          d = dd;
          best = p;
        }
      }
      return { label: s.label, y: best?.y ?? 0, color: s.color };
    });
    setHover({ x: clamp(x, pad, W - pad), vals });
  }

  const gridLines = [];
  const numLines = 6;
  for (let i = 0; i <= numLines; i++) {
    const yPos = H - pad - (i / numLines) * (H - pad * 2);
    const value = (i / numLines) * yMax;
    gridLines.push(
      <g key={`grid-${i}`}>
        <line
          x1={pad}
          y1={yPos}
          x2={W - pad}
          y2={yPos}
          stroke={i === 0 ? "#374151" : "#e5e7eb"}
          strokeWidth={i === 0 ? 2 : 1}
          strokeDasharray={i === 0 ? "0" : "2 2"}
        />
        <text
          x={pad - 8}
          y={yPos + 4}
          fill="#64748b"
          fontSize="11"
          textAnchor="end"
          fontWeight={i === 0 ? "bold" : "normal"}
        >
          {value.toFixed(0)}
        </text>
      </g>
    );
  }

  return (
    <div ref={boxRef} className="relative w-full">
      <svg
        width={W}
        height={H}
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
        className="bg-gradient-to-br from-white to-slate-50 rounded-lg border border-slate-200"
      >
        <defs>
          {series.map((s, i) => (
            <linearGradient
              key={`grad-${i}`}
              id={`grad-${i}`}
              x1="0"
              y1="0"
              x2="0"
              y2="1"
            >
              <stop offset="0%" stopColor={s.color} stopOpacity="0.3" />
              <stop offset="100%" stopColor={s.color} stopOpacity="0.0" />
            </linearGradient>
          ))}
        </defs>

        {refLines?.map((ref, i) => (
          <g key={`ref-${i}`}>
            <line
              x1={pad}
              y1={yOf(ref.value)}
              x2={W - pad}
              y2={yOf(ref.value)}
              stroke={ref.color}
              strokeWidth={2}
              strokeDasharray="8 4"
              opacity={0.6}
            />
            <rect
              x={W - pad - 64}
              y={yOf(ref.value) - 10}
              width={60}
              height={18}
              fill="white"
              opacity={0.9}
              rx={3}
            />
            <text
              x={W - pad - 34}
              y={yOf(ref.value) + 2}
              fill={ref.color}
              fontSize="10"
              textAnchor="middle"
              fontWeight="bold"
            >
              {ref.label}
            </text>
          </g>
        ))}

        {gridLines}

        <line
          x1={pad}
          y1={pad}
          x2={pad}
          y2={H - pad}
          stroke="#374151"
          strokeWidth={2}
        />
        <line
          x1={pad}
          y1={H - pad}
          x2={W - pad}
          y2={H - pad}
          stroke="#374151"
          strokeWidth={2}
        />

        {yLabel && (
          <text
            x={12}
            y={H / 2}
            fill="#374151"
            fontSize="12"
            fontWeight="bold"
            transform={`rotate(-90, 12, ${H / 2})`}
            textAnchor="middle"
          >
            {yLabel}
          </text>
        )}
        {xLabel && (
          <text
            x={W / 2}
            y={H - 8}
            fill="#374151"
            fontSize="12"
            fontWeight="bold"
            textAnchor="middle"
          >
            {xLabel}
          </text>
        )}

        {series.map((s, i) => {
          const pathD = s.points
            .map((p) => `${xOf(p.t)},${yOf(p.y)}`)
            .join(" ");
          const areaD =
            s.points.length > 1
              ? `M ${xOf(s.points[0].t)},${yOf(0)} L ${pathD} L ${xOf(
                  s.points[s.points.length - 1].t
                )},${yOf(0)} Z`
              : "";

          return (
            <g key={i}>
              {!s.dash && areaD && (
                <path d={areaD} fill={`url(#grad-${i})`} opacity={0.2} />
              )}
              <polyline
                fill="none"
                stroke={s.color}
                strokeWidth={s.width ?? 2.5}
                strokeDasharray={s.dash}
                strokeLinecap="round"
                strokeLinejoin="round"
                opacity={s.opacity ?? 1}
                points={pathD}
              />
            </g>
          );
        })}

        {hover && (
          <>
            <line
              x1={hover.x}
              y1={pad}
              x2={hover.x}
              y2={H - pad}
              stroke="#6b7280"
              strokeWidth={1}
              strokeDasharray="4 2"
            />
            {hover.vals.map((v, i) => (
              <circle
                key={i}
                cx={hover.x}
                cy={yOf(v.y)}
                r={4}
                fill={v.color}
                stroke="white"
                strokeWidth={2}
              />
            ))}
          </>
        )}
      </svg>

      {hover && (
        <div
          className="absolute bg-white border border-slate-200 rounded-lg shadow-xl px-3 py-2"
          style={{ left: Math.min(hover.x + 10, W - 130), top: 10 }}
        >
          {hover.vals.map((v) => (
            <div key={v.label} className="flex items-center gap-2">
              <div
                className="w-3 h-3 rounded-full"
                style={{ backgroundColor: v.color }}
              />
              <span className="text-xs text-slate-600">{v.label}:</span>
              <span className="text-sm font-bold">
                {v.y.toFixed(2)} {v.label.includes("SoC") ? "%" : "kW"}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
