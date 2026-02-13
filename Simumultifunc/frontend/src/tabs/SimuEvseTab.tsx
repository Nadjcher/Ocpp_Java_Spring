

import React, { useEffect, useMemo, useRef, useState, useCallback } from "react";
import { SmartChargingPanel } from "@/components/SmartChargingPanel";
import { OCPPChargingProfilesManager } from "@/services/OCPPChargingProfilesManager";
import PhasingSection from '@/components/PhasingSection';
import { config, fetchOcppUrls } from '@/config/env';
import { NumericInput } from "@/components/ui/NumericInput";

// Types extraits dans un fichier dédié
import type {
  SessionItem,
  LogEntry,
  MeterValuesMask,
  Serie,
  Toast,
  Vehicle,
  EvseType,
  ChartPoint
} from '@/types/evse.types';

// Constantes extraites dans un fichier dédié
import {
  API_BASE,
  DEFAULT_VOLTAGE,
  DEFAULT_PHASES,
  ASSETS,
  EFFICIENCY_DEFAULT,
  RAMP_KW_PER_S,
  NOISE,
  MAX_POINTS,
  SOC_CHARGE_MULTIPLIER,
  VEHICLE_SCALE,
  DEFAULT_VEHICLES,
  calculateEffectiveACPower,
  getCompatibleEvseTypes
} from '@/constants/evse.constants';

// Utilitaires extraits dans un fichier dédié
import {
  clamp,
  nowMs,
  ewma,
  formatHMS,
  fetchJSON,
  postAny,
  loadVehicleProfiles,
  getAllVehicles,
  calcVehPowerKW
} from '@/utils/evse.utils';

// Hooks extraits dans un fichier dédié
import { useToasts, useTNRWithAPI } from '@/hooks/useEvseHooks';

// Composants extraits dans un fichier dédié
import { TNRBandeau, ChargeDisplay, ChargingCable, GPMSetpointCard } from '@/components/simu-evse';

// Multi-sessions components et hooks
import { SessionTabBar } from '@/components/evse/SessionTabBar';
import type { SessionState, SessionConfig } from '@/types/multiSession.types';

// Anomaly Detection ML
import { useAnomalyAnalysis } from '@/hooks/useAnomalyAnalysis';
import { AnomalyDashboard } from '@/components/anomaly';

// ==========================================================================
// CACHE D'ÉTAT PER-SESSION (isolation des sessions) AVEC PERSISTANCE
// ==========================================================================
const STORAGE_KEY_SESSIONS = 'evse-simu-sessions-cache';
const STORAGE_KEY_SELECTED = 'evse-simu-selected-id';

interface PerSessionLocalState {
  logs: LogEntry[];
  series: {
    soc: Array<{ t: number; y: number }>;
    pActive: Array<{ t: number; y: number }>;
    expected: Array<{ t: number; y: number }>;
  };
  isParked: boolean;
  isPlugged: boolean;
  mvRunning: boolean;
  socStart: number;
  socTarget: number;
  vehicleId: string;
  chargeStartTime: number | null;
  energyStartKWh: number | null;
  energyNowKWh: number | null;
  energyFromPowerKWh: number;
  lastPowerMs: number | null;
  socFilt: number | null;
  pActiveFilt: number | null;
  lastRealMvMs: number;
  // === AJOUT: Config session-spécifique ===
  cpId: string;
  idTag: string;
  evseType: "ac-mono" | "ac-bi" | "ac-tri" | "dc";
  maxA: number;
  mvEvery: number;
  mvMask: MeterValuesMask;
  environment: "test" | "pp";
}

// Initialiser le cache depuis localStorage avec migration des anciennes données
function initSessionStateCache(): Map<string, PerSessionLocalState> {
  try {
    const stored = localStorage.getItem(STORAGE_KEY_SESSIONS);
    if (stored) {
      const parsed = JSON.parse(stored);
      const cache = new Map<string, PerSessionLocalState>();

      // Migrer chaque entrée en ajoutant les champs manquants
      Object.entries(parsed).forEach(([id, state]: [string, any], index) => {
        const migratedState: PerSessionLocalState = {
          logs: state.logs || [],
          series: state.series || { soc: [], pActive: [], expected: [] },
          isParked: state.isParked ?? false,
          isPlugged: state.isPlugged ?? false,
          mvRunning: state.mvRunning ?? false,
          socStart: state.socStart ?? 20,
          socTarget: state.socTarget ?? 80,
          vehicleId: state.vehicleId || '',
          chargeStartTime: state.chargeStartTime ?? null,
          energyStartKWh: state.energyStartKWh ?? null,
          energyNowKWh: state.energyNowKWh ?? null,
          energyFromPowerKWh: state.energyFromPowerKWh ?? 0,
          lastPowerMs: state.lastPowerMs ?? null,
          socFilt: state.socFilt ?? 20,
          pActiveFilt: state.pActiveFilt ?? null,
          lastRealMvMs: state.lastRealMvMs ?? 0,
          // Migrer les nouveaux champs avec valeurs par défaut uniques
          cpId: state.cpId || `CP-SESSION-${String(index + 1).padStart(3, '0')}`,
          idTag: state.idTag || '',
          evseType: state.evseType || 'ac-tri',
          maxA: state.maxA ?? 32,
          mvEvery: state.mvEvery ?? 10,
          mvMask: state.mvMask || {
            powerActive: true,
            energy: true,
            soc: true,
            powerOffered: true,
          },
          environment: state.environment || 'test'
        };
        cache.set(id, migratedState);
      });

      console.log('[SessionCache] Loaded and migrated', cache.size, 'sessions from localStorage');
      return cache;
    }
  } catch (e) {
    console.warn('[SessionCache] Failed to restore from localStorage:', e);
  }
  return new Map();
}

const sessionStateCache = initSessionStateCache();

// Sauvegarder le cache dans localStorage
function persistSessionCache() {
  try {
    const obj = Object.fromEntries(sessionStateCache.entries());
    localStorage.setItem(STORAGE_KEY_SESSIONS, JSON.stringify(obj));
  } catch (e) {
    console.warn('[SessionCache] Failed to persist to localStorage:', e);
  }
}

// Récupérer l'ID sélectionné depuis localStorage
function getStoredSelectedId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY_SELECTED);
  } catch {
    return null;
  }
}

// Sauvegarder l'ID sélectionné dans localStorage
function setStoredSelectedId(id: string | null) {
  try {
    if (id) {
      localStorage.setItem(STORAGE_KEY_SELECTED, id);
    } else {
      localStorage.removeItem(STORAGE_KEY_SELECTED);
    }
  } catch (e) {
    console.warn('[SessionCache] Failed to persist selected ID:', e);
  }
}

function getDefaultSessionState(sessionIndex?: number): PerSessionLocalState {
  // Générer un CP-ID unique basé sur l'index ou le timestamp
  const uniqueSuffix = sessionIndex !== undefined
    ? String(sessionIndex + 1).padStart(3, '0')
    : Date.now().toString().slice(-6);

  return {
    logs: [],
    series: { soc: [], pActive: [], expected: [] },
    isParked: false,
    isPlugged: false,
    mvRunning: false,
    socStart: 20,
    socTarget: 80,
    vehicleId: '',
    chargeStartTime: null,
    energyStartKWh: null,
    energyNowKWh: null,
    energyFromPowerKWh: 0,
    lastPowerMs: null,
    socFilt: 20,
    pActiveFilt: null,
    lastRealMvMs: 0,
    // === Config session-spécifique avec valeurs uniques ===
    cpId: `CP-SESSION-${uniqueSuffix}`,
    idTag: '',
    evseType: 'ac-tri',
    maxA: 32,
    mvEvery: 10,
    mvMask: {
      powerActive: true,
      energy: true,
      soc: true,
      powerOffered: true,
    },
    environment: 'test'
  };
}

// =========================================================================
// Types locaux (alias pour compatibilité)
// =========================================================================

// Types LogEntry, SessionItem, MeterValuesMask importés depuis @/types/evse.types

// Alias local pour compatibilité
type MvMask = MeterValuesMask;

// =========================================================================
// Styles
// =========================================================================

const extraCSS = `
@keyframes cableEnergy {
  from { stroke-dashoffset: 0 }
  to   { stroke-dashoffset: -80 }
}
@keyframes charging-pulse {
  0%, 100% { opacity: .6; transform: scale(1); }
  50%      { opacity: 1;  transform: scale(1.05); }
}
@keyframes connector-glow {
  0%, 100% { filter: drop-shadow(0 0 8px rgba(16,185,129,.9)); }
  50%      { filter: drop-shadow(0 0 16px rgba(16,185,129,1)); }
}
`;

// =========================================================================
// Composants auxiliaires (déclarés avant usage)
// =========================================================================

const ChargingProfileCard: React.FC<{
  profilesManager: OCPPChargingProfilesManager;
  connectorId: number;
}> = ({ profilesManager, connectorId }) => {
  const [state, setState] = useState({
    limitW: 0,
    source: 'default' as string,
    profiles: [] as any[]
  });

  useEffect(() => {
    const update = () => {
      const connectorState = profilesManager.getConnectorState(connectorId);
      setState({
        limitW: connectorState.effectiveLimit.limitW,
        source: connectorState.effectiveLimit.source === 'profile'
            ? connectorState.effectiveLimit.purpose
            : 'Limite Physique',
        profiles: connectorState.profiles
      });
    };

    update();
    const interval = setInterval(update, 500);
    return () => clearInterval(interval);
  }, [profilesManager, connectorId]);

  return (
      <div className="rounded-lg border border-emerald-500 bg-gradient-to-br from-emerald-50 to-white p-4 shadow-lg">
        <div className="text-sm font-bold text-emerald-700 mb-3">
          Charging Profiles OCPP 1.6
        </div>

        <div className="space-y-3">
          <div>
            <div className="text-xs text-gray-600 mb-1">Limite active:</div>
            <div className="flex items-center gap-3">
              <span className="text-2xl font-bold text-gray-900">
                {(state.limitW / 1000).toFixed(1)} kW
              </span>
            </div>
          </div>

          <div>
            <div className="text-xs text-gray-600 mb-1">Source:</div>
            <span className={`inline-block px-3 py-1 rounded-full text-sm font-medium ${
                state.source === 'Limite Physique'
                    ? 'bg-amber-100 text-amber-800'
                    : state.source === 'TxProfile'
                        ? 'bg-green-100 text-green-800'
                        : state.source === 'TxDefaultProfile'
                            ? 'bg-blue-100 text-blue-800'
                            : 'bg-gray-100 text-gray-800'
            }`}>
              {state.source}
            </span>
          </div>

          {state.profiles.length > 0 && (
              <div className="pt-2 border-t border-emerald-200">
                <div className="text-xs text-gray-600 mb-1">Profils actifs:</div>
                {state.profiles.map((p: any) => (
                    <div key={p.chargingProfileId} className="text-xs text-gray-700">
                      • #{p.chargingProfileId} - {p.chargingProfilePurpose} (Stack: {p.stackLevel})
                    </div>
                ))}
              </div>
          )}
        </div>
      </div>
  );
};

function Chart({
                 series,
                 yMax,
                 height = 300,
                 xLabel,
                 yLabel,
                 refLines,
               }: {
  series: Serie[];
  yMax: number;
  height?: number;
  xLabel?: string;
  yLabel?: string;
  refLines?: Array<{ value: number; label: string; color: string }>;
}) {
  const [hover, setHover] = useState<{
    x: number;
    vals: { label: string; y: number; color: string }[];
  } | null>(null);

  const boxRef = React.useRef<HTMLDivElement>(null);
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

function Connector({
                     side,
                     glowing = false,
                     size = 220,
                     rotation = 0,
                   }: {
  side: "left" | "right";
  glowing?: boolean;
  size?: number;
  rotation?: number;
}) {
  const src = side === "left" ? ASSETS.connectors.left : ASSETS.connectors.right;
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
                  width: "100%",
                  height: "100%",
                  objectFit: "contain",
                  filter: glowing
                      ? "drop-shadow(0 0 18px rgba(16,185,129,.85))"
                      : "drop-shadow(0 1px 6px rgba(0,0,0,.35))",
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
                  backgroundColor: glowing ? "#10b981" : "#6b7280",
                  opacity: glowing ? 0.9 : 0.6,
                  boxShadow: glowing
                      ? "0 0 20px rgba(16,185,129,0.8)"
                      : "0 2px 4px rgba(0,0,0,0.2)",
                }}
            >
              <span style={{
                color: "white",
                fontSize: size * 0.3,
                fontWeight: "bold"
              }}>
                {side === "left" ? "◀" : "▶"}
              </span>
            </div>
        )}
      </div>
  );
}

type CableState = 'idle' | 'vehicle_parked' | 'plugged' | 'charging';

function Cable({
                 containerRef,
                 startRef,
                 endRef,
                 show,
                 charging,
                 state,
                 powerKW = 0,
                 sagFactor = 0.35,
                 extraDropPx = 10,
                 onAnglesChange,
               }: {
  containerRef: React.RefObject<HTMLDivElement>;
  startRef: React.RefObject<HTMLDivElement>;
  endRef: React.RefObject<HTMLDivElement>;
  show: boolean;
  charging: boolean;
  state?: CableState;
  powerKW?: number;
  sagFactor?: number;
  extraDropPx?: number;
  onAnglesChange?: (angles: { left: number; right: number }) => void;
}) {
  const [dims, setDims] = useState({ w: 0, h: 0, d: "" });
  const pathRef = useRef<SVGPathElement>(null);
  const [len, setLen] = useState(0);
  const [dashOffset, setDashOffset] = useState(0);
  const animationIdRef = useRef<number>(0);

  // Couleur du flux selon la puissance
  const getEnergyColor = () => {
    if (powerKW > 100) return { start: '#00d4ff', mid: '#00ff88', end: '#00d4ff' }; // DC rapide
    if (powerKW > 22) return { start: '#3b82f6', mid: '#60a5fa', end: '#3b82f6' }; // DC moyen
    return { start: '#10b981', mid: '#34d399', end: '#10b981' }; // AC
  };

  // Vitesse d'animation selon la puissance
  const getAnimationDuration = () => {
    if (powerKW > 100) return '0.8s';
    if (powerKW > 50) return '1s';
    if (powerKW > 22) return '1.2s';
    return '1.5s';
  };

  const effectiveState = state || (charging ? 'charging' : show ? 'plugged' : 'idle');

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

      // Sag réduit quand connecté pour un câble plus tendu
      const effectiveSag = effectiveState === 'charging' || effectiveState === 'plugged'
        ? clamp(dist * sagFactor * 0.7, 40, 150)
        : clamp(dist * sagFactor, 60, 200);

      const cx1 = x1 + dx * 0.3, cy1 = y1 + effectiveSag + extraDropPx;
      const cx2 = x2 - dx * 0.3, cy2 = y2 + effectiveSag * 0.8 + extraDropPx;

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
    window.addEventListener("resize", update);
    return () => {
      clearInterval(int);
      window.removeEventListener("resize", update);
      if (animationIdRef.current) cancelAnimationFrame(animationIdRef.current);
    };
  }, [containerRef, startRef, endRef, show, effectiveState, sagFactor, extraDropPx, onAnglesChange]);

  if (!show) return null;

  const energyColors = getEnergyColor();
  const animDuration = getAnimationDuration();

  return (
      <svg
          width="100%"
          height="100%"
          viewBox={`0 0 ${Math.max(1, dims.w)} ${Math.max(1, dims.h)}`}
          style={{ position: "absolute", inset: 0, zIndex: 40, pointerEvents: 'none' }}
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
              transition: "stroke-dashoffset 0.6s cubic-bezier(0.4, 0, 0.2, 1)",
            }}
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
              style={{ animation: `cableEnergyFlow ${animDuration} linear infinite` }}
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
              style={{ animation: `cableEnergyFlow ${animDuration} linear infinite`, animationDelay: '-0.4s' }}
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
              style={{ animation: `cableEnergyFlow ${animDuration} linear infinite`, animationDelay: '-0.8s' }}
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
          `}
        </style>
      </svg>
  );
}

// =========================================================================
// Composant principal SimuEvseTab
// =========================================================================

export default function SimuEvseTab() {
  // ========= 1. HOOKS DE BASE =========
  const toasts = useToasts();

  // ========= 2. ÉTATS NÉCESSAIRES POUR TNR =========
  const [environment, setEnvironment] = useState<"test" | "pp">(config.defaultEnvironment as "test" | "pp");
  const [envUrls, setEnvUrls] = useState<Record<string, string>>(config.ocppUrls);

  // Charger les URLs OCPP depuis l'API au démarrage
  useEffect(() => {
    fetchOcppUrls().then(urls => {
      if (urls && Object.keys(urls).length > 0) {
        setEnvUrls(urls);
      }
    });
  }, []);

  const cpUrl = envUrls[environment] || config.ocppUrls[environment];

  // ========= 3. HOOK TNR (après toasts et cpUrl) =========
  const tnr = useTNRWithAPI(toasts, cpUrl);

  // ========= 4. AUTRES ÉTATS =========
  const [sessions, setSessions] = useState<SessionItem[]>([]);
  const [selId, setSelId] = useState<string | null>(() => getStoredSelectedId());
  const [logs, setLogs] = useState<LogEntry[]>([]);

  // Helper pour ajouter un log côté frontend
  const addLog = useCallback((level: string, category: string, message: string, data?: any) => {
    const ts = new Date().toISOString();
    const dataStr = data ? ` | ${JSON.stringify(data)}` : '';
    const line = `[${level}] [${category}] ${message}${dataStr}`;
    setLogs(prev => [...prev.slice(-799), { ts, line }]);
  }, []);

  const [cpId, setCpId] = useState("POP-REGULATION-TEST-TOMY2");
  const [idTag, setIdTag] = useState<string>("");

  const [evseType, setEvseType] = useState<"ac-mono" | "ac-bi" | "ac-tri" | "dc">("ac-tri");
  const [maxA, setMaxA] = useState<number>(32);
  // Mode test: ignorer les limites véhicule pour la puissance physique
  const [bypassVehicleLimits, setBypassVehicleLimits] = useState<boolean>(false);

  // GPM Setpoint states
  const [gpmRootNodeId, setGpmRootNodeId] = useState<string>("");
  const [gpmSetpointKw, setGpmSetpointKw] = useState<number | null>(null);
  const [gpmEnabled, setGpmEnabled] = useState<boolean>(false);

  // Anomaly Detection ML
  const anomalyAnalysis = useAnomalyAnalysis({
    enabled: true,
    autoAnalyzeInterval: 5000, // Refresh every 5s
    onCritical: (anomaly) => {
      toasts.push(`Anomalie critique: ${anomaly.message}`);
    },
  });

  const [vehicles, setVehicles] = useState<Array<{
    id: string;
    name?: string;
    manufacturer?: string;
    model?: string;
    variant?: string;
    capacityKWh: number;
    efficiency?: number;
    imageUrl?: string;
    maxPowerKW?: number;
    acMaxKW?: number;
    acPhases?: number;    // Nombre de phases AC supportées
    acMaxA?: number;      // Courant max par phase en AC
    maxDCPower?: number;
    maxACPower?: number;
    connectorTypes?: string[];  // Types de connecteurs supportés
  }>>([]);
  const [vehicleId, setVehicleId] = useState<string>("");

  // Connecteurs compatibles selon le véhicule sélectionné
  const selectedVehicle = useMemo(
    () => vehicles.find(v => v.id === vehicleId),
    [vehicles, vehicleId]
  );
  const compatibleEvseTypes = useMemo(
    () => getCompatibleEvseTypes(selectedVehicle),
    [selectedVehicle]
  );

  // Auto-sélection d'un type EVSE compatible quand le véhicule change
  useEffect(() => {
    if (compatibleEvseTypes.length > 0) {
      const isCurrentCompatible = compatibleEvseTypes.some(t => t.value === evseType);
      if (!isCurrentCompatible) {
        setEvseType(compatibleEvseTypes[0].value);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [compatibleEvseTypes]);

  const [mvEvery, setMvEvery] = useState<number>(10);
  const [mvMask, setMvMask] = useState<MvMask>({
    powerActive: true,
    energy: true,
    soc: true,
    powerOffered: true,
  });
  const [socStart, setSocStart] = useState<number>(20);
  const [socTarget, setSocTarget] = useState<number>(80);
  const [mvRunning, setMvRunning] = useState(false);

  const [priceApiUrl, setPriceApiUrl] = useState<string>("https://evplatform.evcharge-pp.totalenergies.com/evportal/api/tx");
  const [priceData, setPriceData] = useState<any | null>(null);
  const [fetchingPrice, setFetchingPrice] = useState(false);

  const [series, setSeries] = useState<{
    soc: Array<{ t: number; y: number }>;
    pActive: Array<{ t: number; y: number }>;
    expected: Array<{ t: number; y: number }>;
  }>({ soc: [], pActive: [], expected: [] });

  const [ackStats, setAckStats] = useState<{
    sent: number;
    ack: number;
    rate: number;
  }>({ sent: 0, ack: 0, rate: 1 });

  const [showCable, setShowCable] = useState(true);
  const [isParked, setIsParked] = useState(false);
  const [isPlugged, setIsPlugged] = useState(false);
  const [bootAccepted, setBootAccepted] = useState<boolean | null>(null); // null = unknown, true = accepted, false = rejected
  const [connAngles, setConnAngles] = useState<{ left: number; right: number }>({
    left: 0,
    right: 0,
  });

  // ========= 5. REFS =========
  const lastMvTapRef = useRef<number>(0);
  const energyStartKWhRef = useRef<number | null>(null);
  const energyNowKWhRef = useRef<number | null>(null);
  const energyFromPowerKWhRef = useRef<number>(0);
  const lastPowerMsRef = useRef<number | null>(null);
  const chargeStartTimeRef = useRef<number | null>(null);
  const pActiveFiltRef = useRef<number | null>(null);
  const socFiltRef = useRef<number | null>(20);
  const lastRealMvMsRef = useRef<number>(0);
  const skipNextSyncRef = useRef(false); // Pour éviter que le useEffect écrase l'état local après action utilisateur

  const containerRef = useRef<HTMLDivElement>(null);
  const stationRef = useRef<HTMLDivElement>(null);
  const stationPortRef = useRef<HTMLDivElement>(null);
  const carRef = useRef<HTMLDivElement>(null);
  const carPortRef = useRef<HTMLDivElement>(null);

  // Ref pour gérer le changement de session
  const previousSessionIdRef = useRef<string | null>(null);

  // ========= 5.5 SESSION STATE ISOLATION =========
  // Sauvegarder l'état dans le cache quand la session change
  const saveCurrentSessionState = useCallback(() => {
    const currentId = previousSessionIdRef.current;
    if (currentId) {
      const state: PerSessionLocalState = {
        logs,
        series,
        isParked,
        isPlugged,
        mvRunning,
        socStart,
        socTarget,
        vehicleId,
        chargeStartTime: chargeStartTimeRef.current,
        energyStartKWh: energyStartKWhRef.current,
        energyNowKWh: energyNowKWhRef.current,
        energyFromPowerKWh: energyFromPowerKWhRef.current,
        lastPowerMs: lastPowerMsRef.current,
        socFilt: socFiltRef.current,
        pActiveFilt: pActiveFiltRef.current,
        lastRealMvMs: lastRealMvMsRef.current,
        // === AJOUT: Config session-spécifique ===
        cpId,
        idTag,
        evseType,
        maxA,
        mvEvery,
        mvMask,
        environment
      };
      sessionStateCache.set(currentId, state);
      persistSessionCache(); // Persister dans localStorage
      console.log('[SessionIsolation] Saved state for session:', currentId, 'cpId:', cpId);
    }
  }, [logs, series, isParked, isPlugged, mvRunning, socStart, socTarget, vehicleId, cpId, idTag, evseType, maxA, mvEvery, mvMask, environment]);

  // Charger l'état depuis le cache ou créer un nouvel état
  const loadSessionState = useCallback((newId: string | null) => {
    if (!newId) return;

    const cached = sessionStateCache.get(newId);
    if (cached) {
      console.log('[SessionIsolation] Loading cached state for session:', newId, 'cpId:', cached.cpId);
      setLogs(cached.logs);
      setSeries(cached.series);
      setIsParked(cached.isParked);
      setIsPlugged(cached.isPlugged);
      setMvRunning(cached.mvRunning);
      setSocStart(cached.socStart);
      setSocTarget(cached.socTarget);
      setVehicleId(cached.vehicleId);
      chargeStartTimeRef.current = cached.chargeStartTime;
      energyStartKWhRef.current = cached.energyStartKWh;
      energyNowKWhRef.current = cached.energyNowKWh;
      energyFromPowerKWhRef.current = cached.energyFromPowerKWh;
      lastPowerMsRef.current = cached.lastPowerMs;
      socFiltRef.current = cached.socFilt;
      pActiveFiltRef.current = cached.pActiveFilt;
      lastRealMvMsRef.current = cached.lastRealMvMs;
      // === AJOUT: Restaurer la config session-spécifique ===
      setCpId(cached.cpId);
      setIdTag(cached.idTag);
      setEvseType(cached.evseType);
      setMaxA(cached.maxA);
      setMvEvery(cached.mvEvery);
      setMvMask(cached.mvMask);
      setEnvironment(cached.environment);
    } else {
      console.log('[SessionIsolation] Creating new state for session:', newId);
      // Créer un état par défaut avec un CP-ID unique
      const sessionIndex = sessions.length;
      const defaultState = getDefaultSessionState(sessionIndex);

      // Réinitialiser à l'état par défaut pour une nouvelle session
      setLogs([]);
      setSeries({ soc: [], pActive: [], expected: [] });
      setMvRunning(false);
      chargeStartTimeRef.current = null;
      energyStartKWhRef.current = null;
      energyNowKWhRef.current = null;
      energyFromPowerKWhRef.current = 0;
      lastPowerMsRef.current = null;
      socFiltRef.current = defaultState.socStart;
      pActiveFiltRef.current = null;
      lastRealMvMsRef.current = 0;
      // === AJOUT: Initialiser avec un CP-ID unique ===
      setCpId(defaultState.cpId);
      setIdTag(defaultState.idTag);
      setEvseType(defaultState.evseType);
      setMaxA(defaultState.maxA);
      setMvEvery(defaultState.mvEvery);
      setMvMask(defaultState.mvMask);
      setEnvironment(defaultState.environment);
      setSocStart(defaultState.socStart);
      setSocTarget(defaultState.socTarget);

      // Sauvegarder immédiatement le nouvel état dans le cache
      sessionStateCache.set(newId, defaultState);
      persistSessionCache();
    }
  }, [sessions.length]);

  // useEffect pour gérer le changement de session
  useEffect(() => {
    if (previousSessionIdRef.current !== selId) {
      // Sauvegarder l'état de l'ancienne session
      saveCurrentSessionState();

      // Charger l'état de la nouvelle session
      loadSessionState(selId);

      // Mettre à jour la référence
      previousSessionIdRef.current = selId;
    }
  }, [selId, saveCurrentSessionState, loadSessionState]);

  // Nettoyer le cache quand une session est supprimée
  const cleanupSessionCache = useCallback((sessionId: string) => {
    sessionStateCache.delete(sessionId);
    persistSessionCache(); // Persister dans localStorage
    console.log('[SessionIsolation] Cleaned cache for session:', sessionId);
  }, []);

  // ========= 6. VARIABLES DÉRIVÉES (qui dépendent des états) =========
  const selSession = sessions.find((s) => s.id === selId) || null;
  const isCharging = selSession?.status === "charging" || selSession?.status === "started";
  const voltage = selSession?.metrics?.voltage || DEFAULT_VOLTAGE;
  const phases = selSession?.metrics?.phases || DEFAULT_PHASES[evseType] || 1;
  const selectedVehicle = vehicles.find((v) => v.id === vehicleId) || undefined;
  const vehicleCapacityKWh = selectedVehicle?.capacityKWh ?? 60;
  const vehicleEfficiency = selectedVehicle?.efficiency ?? EFFICIENCY_DEFAULT;
  const vehicleImage = selectedVehicle?.imageUrl || ASSETS.genericEV;
  const currentSoc = socFiltRef.current ?? socStart;
  const currentPowerKw = pActiveFiltRef.current ?? 0;

  // ========= 7. FONCTIONS CALLBACK (utilisées dans le gestionnaire) =========
  // Déterminer si c'est une charge DC ou AC selon le type de borne
  const isDCCharging = evseType === 'dc';

  const vehMaxKwAtSoc = useCallback((soc: number): number => {
    if (!vehicleId) return isDCCharging ? 50 : 11;
    const kw = calcVehPowerKW(vehicleId, soc, isDCCharging);
    return Number.isFinite(kw) ? Math.max(0, kw) : (isDCCharging ? 50 : 11);
  }, [vehicleId, isDCCharging]);

  const clampRamp = useCallback((prev: number, target: number, dtSec: number) => {
    const maxStep = RAMP_KW_PER_S * dtSec;
    return prev + Math.max(-maxStep, Math.min(maxStep, target - prev));
  }, []);

  // ========= 8. GESTIONNAIRE OCPP (qui peut maintenant utiliser toasts) =========
  // Calculer la limite physique initiale selon le type EVSE par défaut
  const initialPhases = DEFAULT_PHASES[evseType] || 1;
  const initialMaxPowerW = maxA * DEFAULT_VOLTAGE * initialPhases;

  const [profilesManager] = useState(
      () =>
          new OCPPChargingProfilesManager({
            maxPowerW: initialMaxPowerW,
            defaultVoltage: DEFAULT_VOLTAGE,
            defaultPhases: initialPhases,
            onLimitChange: (_connectorId: number, limitW: number, source: any) => {
              const limitKw = limitW / 1000;
              // Utilisation différée pour éviter les problèmes de closure
              setTimeout(() => {
                toasts.push(`SCP • ${source.purpose} → ${limitKw.toFixed(1)} kW`);
                setSeries(s => ({ ...s }));
              }, 0);
            },
            onProfileChange: (_event: any) => {
              console.log("Profile change:", _event);
              setSeries(s => ({ ...s }));
            },
          })
  );

  const profileState = profilesManager.getConnectorState(1);

  // ========= 9. CALCULS USEMEMO (peuvent maintenant utiliser vehMaxKwAtSoc) =========
  // Puissance max de la borne DC (configurable, typiquement 50-350 kW)
  const [dcMaxPowerKw, setDcMaxPowerKw] = useState<number>(350);

  // Calcul des phases et courant effectifs (min EVSE, véhicule) - sauf en mode bypass
  const effectiveACConfig = useMemo(() => {
    if (isDCCharging) return null;

    // En mode bypass, ignorer les limites véhicule
    if (bypassVehicleLimits) {
      return {
        effectiveKw: (maxA * voltage * phases) / 1000,
        effectivePhases: phases,
        effectiveA: maxA
      };
    }

    // Phases et courant du véhicule (ou valeurs par défaut si non défini)
    const vehicleAcPhases = selectedVehicle?.acPhases ?? 3;
    const vehicleAcMaxA = selectedVehicle?.acMaxA ?? 32;

    return calculateEffectiveACPower(
      phases,      // EVSE phases
      maxA,        // EVSE courant max par phase
      vehicleAcPhases,
      vehicleAcMaxA,
      voltage
    );
  }, [phases, maxA, voltage, selectedVehicle, isDCCharging, bypassVehicleLimits]);

  const physicalLimitKw = useMemo(() => {
    if (isDCCharging) {
      // En DC, la puissance est directement en kW (pas de calcul V*A*phases)
      // On utilise le min entre la borne DC et le courant max configuré
      return dcMaxPowerKw;
    }

    // En AC: utiliser le calcul effectif qui prend en compte min(EVSE, véhicule)
    if (effectiveACConfig) {
      return effectiveACConfig.effectiveKw;
    }

    // Fallback si pas de config
    return (maxA * voltage * phases) / 1000;
  }, [maxA, voltage, phases, isDCCharging, dcMaxPowerKw, effectiveACConfig]);

  const appliedLimitKw = useMemo(() => {
    const profileLimitKw = profileState.effectiveLimit.limitW / 1000;
    if (profileState.effectiveLimit.source === "profile") {
      return Math.min(profileLimitKw, physicalLimitKw);
    }
    return physicalLimitKw;
  }, [physicalLimitKw, profileState]);

  const effectivePowerLimit = useMemo(() => {
    const currentSoc = socFiltRef.current ?? socStart;
    const vehKw = vehMaxKwAtSoc(currentSoc);
    const stationKw = selSession?.metrics?.stationKwMax ?? Infinity;
    const backendKw = selSession?.metrics?.backendKwMax ?? Infinity;
    return Math.min(appliedLimitKw, vehKw, stationKw, backendKw);
  }, [appliedLimitKw, vehMaxKwAtSoc, socStart, selSession]);

  const totalEnergyKWh = useMemo(() => {
    // Priorité 1: Valeur du backend (source de vérité)
    if (selSession?.energy != null && selSession.energy > 0) {
      return selSession.energy;
    }
    // Priorité 2: Valeur parsée des MeterValues (énergie relative dans la session)
    if (energyNowKWhRef.current != null && energyStartKWhRef.current != null) {
      return Math.max(0, energyNowKWhRef.current - energyStartKWhRef.current);
    }
    // Fallback: Calcul local depuis la puissance
    return energyFromPowerKWhRef.current;
  }, [selSession?.energy, series.pActive, series.soc]);

  const elapsedTime = useMemo(() => {
    const isChargingStatus = selSession?.status === "charging" || selSession?.status === "started";
    if (!isChargingStatus)
      return "00:00:00";
    // Priorité: utiliser chargeStartTime du backend si disponible, sinon la ref locale
    const startTime = (selSession as any)?.chargeStartTime || chargeStartTimeRef.current;
    if (!startTime) {
      // Fallback: utiliser chargingDurationSec du backend si disponible
      const durationSec = (selSession as any)?.chargingDurationSec;
      if (durationSec && durationSec > 0) {
        return formatHMS(durationSec);
      }
      return "00:00:00";
    }
    const elapsed = Math.floor((Date.now() - startTime) / 1000);
    return formatHMS(elapsed);
  }, [selSession?.status, selSession, series.pActive]);

  // Ref pour éviter que refreshSessions écrase la sélection utilisateur
  const userSelectedIdRef = useRef<string | null>(getStoredSelectedId());

  // Mettre à jour la limite physique du profilesManager quand la config EVSE change
  useEffect(() => {
    const maxPowerW = physicalLimitKw * 1000;
    profilesManager.setMaxPowerW(maxPowerW);
    profilesManager.updateConnectorConfig(1, { voltage, phases });
  }, [physicalLimitKw, voltage, phases, profilesManager]);

  // ========= 10. FONCTIONS ACTIONS =========
  async function refreshSessions() {
    try {
      const allSessions: SessionItem[] = await fetchJSON(`/api/simu`);
      // Filtrer les sessions de performance (ID commençant par "perf-")
      const list = allSessions.filter(s => !s.id?.startsWith('perf-'));
      setSessions(list);

      // Ne changer la sélection que si:
      // 1. Aucune session n'est sélectionnée ET il y a des sessions
      // 2. La session sélectionnée n'existe plus dans la liste
      const currentSelId = userSelectedIdRef.current || selId;

      if (!currentSelId && list.length) {
        setSelId(list[0].id);
        userSelectedIdRef.current = list[0].id;
        setStoredSelectedId(list[0].id); // Persister dans localStorage
      } else if (currentSelId && !list.some((s) => s.id === currentSelId)) {
        // La session sélectionnée n'existe plus, sélectionner la première
        const newId = list[0]?.id || null;
        setSelId(newId);
        userSelectedIdRef.current = newId;
        setStoredSelectedId(newId); // Persister dans localStorage
      }
      // SINON: garder la sélection actuelle, ne rien changer
    } catch {}
  }

  // Fonction pour sélectionner une session (mise à jour de la ref + localStorage)
  const selectSession = useCallback((id: string | null) => {
    userSelectedIdRef.current = id;
    setSelId(id);
    setStoredSelectedId(id); // Persister dans localStorage
  }, []);

  async function fetchPrice() {
    if (!selId) {
      toasts.push("Aucune session sélectionnée");
      return;
    }

    setFetchingPrice(true);
    try {
      // Token is now handled automatically by the backend via CognitoTokenService
      const data = await fetchJSON<typeof priceData>(`/api/simu/${selId}/price`);

      // Validate response has required fields
      if (data && typeof data.totalPrice === 'number') {
        setPriceData(data);

        if (data.source === "api" || data.source === "tte") {
          toasts.push(`Prix récupéré depuis l'API TTE`);
        } else if (data.source === "not_configured") {
          toasts.push(`TTE non configuré - prix calculé localement`);
        } else if (data.source === "not_found") {
          toasts.push(`Transaction non trouvée - prix à 0`);
        } else {
          toasts.push(`Prix calculé avec fallback`);
        }
      } else {
        // Invalid response structure
        toasts.push(`Réponse invalide du serveur`);
        setPriceData(null);
      }
    } catch (error: any) {
      toasts.push(`Erreur récupération prix: ${error.message}`);
      setPriceData(null);
    } finally {
      setFetchingPrice(false);
    }
  }

  async function callEither(
      paths: string[],
      tap: { type: string; action: string; payload?: any }
  ) {
    if (!selId) return;
    const ok = await postAny(paths);
    if (ok) await tnr.tapEvent(tap.type, tap.action, tap.payload || {}, selId);
    refreshSessions();
  }

  async function onConnect() {
    const fullUrl = cpUrl.endsWith(`/${cpId}`) ? cpUrl : `${cpUrl}/${cpId}`;
    const tagToUse = idTag.trim() || "TAG-001";

    // Reset boot status
    setBootAccepted(null);

    addLog('INFO', 'SESSION', `>> Creating session`, { cpId, url: fullUrl, evseType, maxA });
    try {
      const res = await fetchJSON<any>("/api/simu/session", {
        method: "POST",
        body: JSON.stringify({
          url: fullUrl,
          cpId,
          idTag: tagToUse,
          auto: false,
          evseType,
          maxA,
          // Pour DC, envoyer la puissance max configurée
          ...(evseType === 'dc' ? { maxPowerKw: dcMaxPowerKw } : {}),
          // Phases véhicule pour que le backend génère les MeterValues correctement
          ...(selectedVehicle?.acPhases ? { vehicleAcPhases: selectedVehicle.acPhases } : {}),
        }),
      });

      if (res?.id) {
        addLog('INFO', 'SESSION', `<< Session created`, { id: res.id });

        // === IMPORTANT: Initialiser le cache pour cette session ===
        // Utiliser les valeurs actuelles du formulaire (cpId, idTag, etc.)
        const sessionState: PerSessionLocalState = {
          logs: [],
          series: { soc: [], pActive: [], expected: [] },
          isParked: false,
          isPlugged: false,
          mvRunning: false,
          socStart,
          socTarget,
          vehicleId,
          chargeStartTime: null,
          energyStartKWh: null,
          energyNowKWh: null,
          energyFromPowerKWh: 0,
          lastPowerMs: null,
          socFilt: socStart,
          pActiveFilt: null,
          lastRealMvMs: 0,
          // Config spécifique pour cette session (valeurs du formulaire)
          cpId,
          idTag: tagToUse,
          evseType,
          maxA,
          mvEvery,
          mvMask,
          environment
        };
        sessionStateCache.set(res.id, sessionState);
        persistSessionCache();

        selectSession(res.id);
        await postAny([
          `/api/simu/${res.id}/mv-mask`,
          `/api/simu/${res.id}/status/mv-mask`,
        ]);
        await fetchJSON(`/api/simu/${res.id}/status/mv-mask`, {
          method: "POST",
          body: JSON.stringify({
            mvMask,
            mvEverySec: mvEvery,
            socStart,
            socTarget,
            evseType,
            maxA,
          }),
        }).catch(() => {});
        addLog('INFO', 'OCPP', `>> WebSocket connecting to CSMS...`);
        addLog('INFO', 'OCPP', `>> Sending BootNotification`);

        // Attendre et vérifier la réponse du BootNotification
        await new Promise(resolve => setTimeout(resolve, 2000)); // Attendre 2s pour la réponse
        await refreshSessions();

        // Vérifier le status de la session pour le boot
        // Utiliser une requête séparée pour récupérer les données fraîches
        const updatedSessions = await fetchJSON<any[]>('/api/sessions');
        const updatedSession = updatedSessions?.find((s: any) => s.id === res.id);

        // Vérifier le bootStatus si disponible dans la réponse API
        const sessionBootStatus = (updatedSession as any)?.bootStatus || (updatedSession as any)?.boot?.status;

        if (sessionBootStatus === 'Rejected' || updatedSession?.status === 'error') {
          setBootAccepted(false);
          addLog('ERROR', 'OCPP', `<< BootNotification REJECTED - OCPP ID "${cpId}" non reconnu par le CSMS`);
          toasts.push(`[WARN] BootNotification rejeté - L'OCPP ID "${cpId}" n'est pas enregistré sur le CSMS`);
        } else if (sessionBootStatus === 'Accepted' ||
                   updatedSession?.status === 'booted' ||
                   updatedSession?.status === 'parked' ||
                   updatedSession?.status === 'plugged') {
          setBootAccepted(true);
          addLog('SUCCESS', 'OCPP', `<< BootNotification ACCEPTED`);
        } else if (updatedSession?.status === 'connected') {
          // Encore en attente - on attend un peu plus ou on considère comme potentiellement rejeté
          addLog('WARNING', 'OCPP', `<< BootNotification en attente de réponse...`);
        }

        await tnr.tapEvent(
            "session",
            "CONNECT",
            { url: fullUrl, cpId, idTag: tagToUse, evseType, maxA },
            res.id
        );
      } else {
        addLog('ERROR', 'SESSION', `<< Session creation failed`);
        alert("Erreur CONNECT");
      }
    } catch (e: any) {
      addLog('ERROR', 'SESSION', `<< Error: ${e?.message || e}`);
      alert(e?.message || "Erreur CONNECT");
    } finally {
      refreshSessions();
    }
  }

  async function onNewSession() {
    // Générer automatiquement un nouveau CP-ID unique (incrémental basé sur le timestamp)
    const sessionNum = sessions.length + 1;
    const timestamp = Date.now().toString().slice(-6);
    const newCpId = `CP-SESSION-${sessionNum}-${timestamp}`;
    const newIdTag = `TAG-${sessionNum}`;

    const fullUrl = cpUrl.endsWith(`/${newCpId}`) ? cpUrl : `${cpUrl}/${newCpId}`;
    try {
      const res = await fetchJSON<any>("/api/simu/session", {
        method: "POST",
        body: JSON.stringify({
          url: fullUrl,
          cpId: newCpId,
          idTag: newIdTag,
          auto: false,
          evseType: 'ac-tri', // Valeur par défaut pour nouvelle session
          maxA: 32,
        }),
      });
      if (res?.id) {
        // === IMPORTANT: Initialiser le cache pour cette nouvelle session AVANT de la sélectionner ===
        const newSessionState: PerSessionLocalState = {
          logs: [],
          series: { soc: [], pActive: [], expected: [] },
          isParked: false,
          isPlugged: false,
          mvRunning: false,
          socStart: 20,
          socTarget: 80,
          vehicleId: '',
          chargeStartTime: null,
          energyStartKWh: null,
          energyNowKWh: null,
          energyFromPowerKWh: 0,
          lastPowerMs: null,
          socFilt: 20,
          pActiveFilt: null,
          lastRealMvMs: 0,
          // Config spécifique pour cette nouvelle session
          cpId: newCpId,
          idTag: newIdTag,
          evseType: 'ac-tri',
          maxA: 32,
          mvEvery: 10,
          mvMask: {
            powerActive: true,
            energy: true,
            soc: true,
            powerOffered: true,
          },
          environment: 'test'
        };
        sessionStateCache.set(res.id, newSessionState);
        persistSessionCache();

        // Maintenant sélectionner la session (loadSessionState trouvera les données dans le cache)
        selectSession(res.id);
        toasts.push(`Nouvelle session créée: ${newCpId}`);
        await tnr.tapEvent(
            "session",
            "NEW_SESSION",
            { url: fullUrl, cpId: newCpId, idTag: newIdTag, evseType: 'ac-mono', maxA: 32 },
            res.id
        );
      }
    } catch (e: any) {
      toasts.push(`Erreur: ${e?.message || "Erreur création session"}`);
    } finally {
      refreshSessions();
    }
  }

  async function onDisconnect() {
    if (!selId) return;
    const sessionIdToCleanup = selId; // Sauvegarder l'ID avant de le mettre à null
    try {
      // Utiliser voluntary-stop pour marquer la déconnexion comme volontaire
      // Ceci empêche la reconnexion automatique et ferme proprement la session
      await fetchJSON(`/api/sessions/${selId}/voluntary-stop?reason=User%20requested%20disconnect`, { method: "POST" });
      await tnr.tapEvent("session", "DISCONNECT", { id: selId }, selId);
      console.log('[SimuEvseTab] Session disconnected voluntarily:', selId);
    } catch (e) {
      // Fallback sur l'ancien endpoint si le nouveau n'existe pas
      console.warn('[SimuEvseTab] voluntary-stop failed, falling back to DELETE:', e);
      await fetchJSON(`/api/simu/${selId}`, { method: "DELETE" });
    } finally {
      // Nettoyer le cache de la session
      cleanupSessionCache(sessionIdToCleanup);
      selectSession(null);
      setLogs([]);
      setSeries({ soc: [], pActive: [], expected: [] });
      setMvRunning(false);
      setIsParked(false);
      setIsPlugged(false);
      setBootAccepted(null); // Reset boot status on disconnect
      profilesManager.reset();
      energyStartKWhRef.current = null;
      energyNowKWhRef.current = null;
      energyFromPowerKWhRef.current = 0;
      lastPowerMsRef.current = null;
      socFiltRef.current = socStart;
      pActiveFiltRef.current = null;
      lastRealMvMsRef.current = 0;
    }
  }

  const onPark = async () => {
    addLog('INFO', 'EVENT', `>> Vehicle arriving (Park)`);
    await callEither(
        [`/api/simu/${selId}/park`, `/api/simu/${selId}/status/park`],
        { type: "session", action: "PARK" }
    );
    addLog('INFO', 'EVENT', `<< Vehicle parked`);
    setIsParked(true);
  };

  const onPlug = async () => {
    addLog('INFO', 'EVENT', `>> Plugging cable`);
    addLog('INFO', 'OCPP', `>> Sending StatusNotification`, { status: 'Preparing' });
    await callEither(
        [`/api/simu/${selId}/plug`, `/api/simu/${selId}/status/plug`],
        { type: "session", action: "PLUG" }
    );
    addLog('INFO', 'EVENT', `<< Cable plugged`);
    setIsPlugged(true);
  };

  const onUnplug = async () => {
    if (!selId) return;
    if (isCharging) {
      alert("Arrête la charge avant de débrancher le câble.");
      return;
    }
    addLog('INFO', 'EVENT', `>> Unplugging cable`);
    await callEither(
        [`/api/simu/${selId}/unplug`, `/api/simu/${selId}/status/unplug`],
        { type: "session", action: "UNPLUG" }
    );
    addLog('INFO', 'EVENT', `<< Cable unplugged`);
    // Empêcher le useEffect de synchro d'écraser notre état local
    skipNextSyncRef.current = true;
    setIsPlugged(false);
    // La voiture reste garée (isParked ne change pas)
  };

  const onLeave = async () => {
    if (!selId) return;
    if (isCharging) {
      alert("Impossible de partir : charge en cours. Arrête d'abord la charge.");
      return;
    }
    if (isPlugged) {
      alert("Impossible de partir : câble branché. Débranche d'abord.");
      return;
    }
    addLog('INFO', 'EVENT', `>> Vehicle leaving`);
    addLog('INFO', 'OCPP', `>> Sending StatusNotification`, { status: 'Available' });
    await callEither(
        [`/api/simu/${selId}/leave`, `/api/simu/${selId}/status/unpark`],
        { type: "session", action: "LEAVE" }
    );
    addLog('INFO', 'EVENT', `<< Vehicle left`);
    setIsParked(false);
    setIsPlugged(false);
  };

  const onAuth = async () => {
    if (!selId) return;
    const tagToUse = idTag.trim() || "TAG-001";
    addLog('INFO', 'OCPP', `>> Sending Authorize`, { idTag: tagToUse });
    try {
      const res = await fetchJSON<any>(`/api/simu/${selId}/authorize`, {
        method: "POST",
        body: JSON.stringify({ idTag: tagToUse }),
      });
      addLog('INFO', 'OCPP', `<< Authorize Response`, res);
    } catch (e: any) {
      addLog('ERROR', 'OCPP', `<< Authorize Error: ${e?.message || e}`);
    }
    await tnr.tapEvent("session", "Authorize", { idTag: tagToUse }, selId);
  };

  const onStart = async () => {
    if (!selId) return;
    profilesManager.markTransactionStart(1);
    addLog('INFO', 'OCPP', `>> Sending StartTransaction`, { connectorId: 1 });
    try {
      const res = await fetchJSON<any>(`/api/simu/${selId}/startTx`, {
        method: "POST",
        body: JSON.stringify({ connectorId: 1 }),
      });
      addLog('INFO', 'OCPP', `<< StartTransaction Response`, res);
    } catch (e: any) {
      addLog('ERROR', 'OCPP', `<< StartTransaction Error: ${e?.message || e}`);
    }
    addLog('INFO', 'MV', `>> Starting MeterValues`, { periodSec: mvEvery });
    await fetchJSON(`/api/simu/${selId}/mv/start`, {
      method: "POST",
      body: JSON.stringify({ periodSec: mvEvery }),
    }).catch(() => {});
    setMvRunning(true);
    chargeStartTimeRef.current = Date.now();
    energyStartKWhRef.current = null;
    energyNowKWhRef.current = null;
    energyFromPowerKWhRef.current = 0;
    lastPowerMsRef.current = null;
    await tnr.tapEvent(
        "session",
        "StartTransaction",
        { connectorId: 1, mvEvery },
        selId
    );
  };

  const onStop = async () => {
    if (!selId) return;

    // 1. Marquer fin de transaction dans le profiles manager
    profilesManager.markTransactionStop(1);

    // 2. Arreter les MeterValues periodiques
    addLog('INFO', 'MV', `>> Stopping MeterValues`);
    await fetchJSON(`/api/simu/${selId}/mv/stop`, { method: "POST" }).catch(() => {});

    // 3. Envoyer StopTransaction
    addLog('INFO', 'OCPP', `>> Sending StopTransaction`);
    try {
      const res = await fetchJSON<any>(`/api/simu/${selId}/stopTx`, { method: "POST" });
      addLog('INFO', 'OCPP', `<< StopTransaction Response`, res);
    } catch (e: any) {
      addLog('ERROR', 'OCPP', `<< StopTransaction Error: ${e?.message || e}`);
    }
    setMvRunning(false);

    // 4. TNR event
    await tnr.tapEvent("session", "StopTransaction", {}, selId);

    // 5. StatusNotification -> Finishing
    addLog('INFO', 'OCPP', `>> Sending StatusNotification`, { status: 'Finishing' });
    await fetchJSON(`/api/simu/${selId}/status`, {
      method: "POST",
      body: JSON.stringify({
        connectorId: 1,
        status: "Finishing",
        errorCode: "NoError",
        timestamp: new Date().toISOString()
      })
    }).catch(() => {});

    // 6. Apres un court delai, StatusNotification -> Available
    setTimeout(async () => {
      if (!selId) return;
      addLog('INFO', 'OCPP', `>> Sending StatusNotification`, { status: 'Available' });
      await fetchJSON(`/api/simu/${selId}/status`, {
        method: "POST",
        body: JSON.stringify({
          connectorId: 1,
          status: "Available",
          errorCode: "NoError",
          timestamp: new Date().toISOString()
        })
      }).catch(() => {});

      toasts.push("Charge terminee - Borne disponible");

      // Reset des refs de charge
      pActiveFiltRef.current = 0;
      energyFromPowerKWhRef.current = 0;
      chargeStartTimeRef.current = null;

      // Rafraichir les sessions
      refreshSessions();

      // 7. Recuperer automatiquement le prix apres la fin de session
      addLog('INFO', 'PRICE', `>> Recuperation automatique du prix...`);
      try {
        const priceResult = await fetchJSON<any>(`/api/simu/${selId}/price`);
        if (priceResult && typeof priceResult.totalPrice === 'number') {
          setPriceData(priceResult);
          addLog('INFO', 'PRICE', `<< Prix recupere: ${priceResult.totalPrice?.toFixed(2)} ${priceResult.currency || 'EUR'}`);
          toasts.push(`Prix: ${priceResult.totalPrice?.toFixed(2)} ${priceResult.currency || 'EUR'}`);
        } else {
          addLog('WARN', 'PRICE', `<< Reponse prix invalide`, priceResult);
        }
      } catch (priceError: any) {
        addLog('ERROR', 'PRICE', `<< Erreur recuperation prix: ${priceError?.message || priceError}`);
      }
    }, 1500);
  };

  // ═══════════════════════════════════════════════════════════════════════════
  // Mode Idle: Active le mode sans consommation pour tester idle fee
  // ═══════════════════════════════════════════════════════════════════════════
  const [isIdleMode, setIsIdleMode] = useState(false);

  const onIdle = async () => {
    if (!selId) return;

    if (isIdleMode) {
      // Désactiver le mode idle
      addLog('INFO', 'IDLE', `>> Désactivation du mode idle...`);
      try {
        const res = await fetchJSON<any>(`/api/sessions/${selId}/idle`, { method: "DELETE" });
        addLog('INFO', 'IDLE', `<< Mode idle désactivé`, res);
        setIsIdleMode(false);
        toasts.push("Mode idle désactivé - La charge reprend");
      } catch (e: any) {
        addLog('ERROR', 'IDLE', `<< Erreur: ${e?.message || e}`);
      }
    } else {
      // Activer le mode idle
      addLog('INFO', 'IDLE', `>> Activation du mode idle (power=0, session reste en vie)...`);
      try {
        const res = await fetchJSON<any>(`/api/sessions/${selId}/idle`, { method: "POST" });
        addLog('INFO', 'IDLE', `<< Mode idle activé - Pas de consommation`, res);
        setIsIdleMode(true);
        toasts.push("Mode idle activé - Consommation: 0 kW");
      } catch (e: any) {
        addLog('ERROR', 'IDLE', `<< Erreur: ${e?.message || e}`);
      }
    }
  };

  const onApplyMv = async () => {
    if (!selId) return;
    await fetchJSON(`/api/simu/${selId}/status/mv-mask`, {
      method: "POST",
      body: JSON.stringify({
        mvMask,
        mvEverySec: mvEvery,
        socStart,
        socTarget,
        evseType,
        maxA,
      }),
    }).catch(() => {});
    if (mvRunning) {
      await fetchJSON(`/api/simu/${selId}/mv/restart`, {
        method: "POST",
        body: JSON.stringify({ periodSec: mvEvery }),
      }).catch(() => {});
    }
    await tnr.tapEvent(
        "session",
        "APPLY_MV_MASK",
        { mvMask, mvEvery, socStart, socTarget, evseType, maxA },
        selId || undefined
    );
  };

  // ========= 11. USE EFFECTS =========

  // MAJ config connecteur quand type EVSE change ou session change
  useEffect(() => {
    const phasesFromType = evseType === "ac-tri" ? 3 : evseType === "ac-bi" ? 2 : 1;
    const actualVoltage = selSession?.metrics?.voltage || DEFAULT_VOLTAGE;
    const actualPhases = selSession?.metrics?.phases || phasesFromType;

    profilesManager.updateConnectorConfig(1, {
      voltage: actualVoltage,
      phases: actualPhases
    });
    // Logging is now handled by profilesManager only when config actually changes
  }, [evseType, profilesManager, selSession]);

  // Charger config au montage
  useEffect(() => {
    fetchJSON<{ hasToken: boolean; url: string }>("/api/config/price-token")
        .then((config) => {
          if (config.url) {
            setPriceApiUrl(config.url);
          }
        })
        .catch(() => {});
  }, []);

  useEffect(() => {
    setPriceData(null);
  }, [selId]);

  // Charger véhicules
  useEffect(() => {
    loadVehicleProfiles().then(() => {
      const arr = getAllVehicles();
      setVehicles(arr);
      if (!vehicleId && arr.length) setVehicleId(arr[0].id);
    });
  }, [vehicleId]);

  // Sessions polling
  useEffect(() => {
    refreshSessions();
    const t = setInterval(refreshSessions, 1500);
    return () => clearInterval(t);
  }, []);

  // Broadcast sessions vers SimuGPM via localStorage (plus stable que Context)
  useEffect(() => {
    const STORAGE_KEY = 'evse-sessions-broadcast';

    // Convertir les sessions en format broadcast
    // IMPORTANT: Utiliser les données du cache par session pour l'isolation
    const broadcastData = sessions.map(s => {
      // Récupérer l'état caché de cette session spécifique (pas les refs globaux!)
      const cachedState = sessionStateCache.get(s.id);

      // Si c'est la session actuellement sélectionnée, utiliser les refs courantes
      // Sinon utiliser les valeurs du cache de cette session
      const isCurrentSession = s.id === selId;
      const sessionSoc = isCurrentSession
        ? (s.soc ?? socFiltRef.current ?? socStart)
        : (s.soc ?? cachedState?.socFilt ?? cachedState?.socStart ?? 20);
      const sessionPower = isCurrentSession
        ? (s.metrics?.txpKw ?? pActiveFiltRef.current ?? 0)
        : (s.metrics?.txpKw ?? cachedState?.pActiveFilt ?? 0);
      const sessionEnergy = isCurrentSession
        ? (s.energy ?? energyFromPowerKWhRef.current ?? 0)
        : (s.energy ?? cachedState?.energyFromPowerKWh ?? 0);
      const sessionVoltage = s.metrics?.voltage ?? DEFAULT_VOLTAGE;

      return {
        sessionId: s.id,
        cpId: s.cpId,
        status: s.status,
        isConnected: s.isConnected ?? s.status !== 'disconnected',
        isCharging: s.status === 'charging' || s.status === 'started',
        soc: sessionSoc,
        offeredPower: (s.metrics?.txdpKw ?? 0) * 1000,
        activePower: sessionPower * 1000,
        setPoint: (s.metrics?.backendKwMax ?? 0) * 1000,
        energy: sessionEnergy * 1000,
        voltage: sessionVoltage,
        current: (sessionPower * 1000) / sessionVoltage,
        transactionId: s.txId,
        config: {
          cpId: s.cpId,
          environment: cachedState ? environment : environment, // TODO: isolate per session if needed
          evseType: evseType,
          maxA: maxA,
          idTag: s.config?.idTag ?? idTag,
          vehicleId: isCurrentSession ? vehicleId : (cachedState?.vehicleId ?? vehicleId),
          socStart: isCurrentSession ? socStart : (cachedState?.socStart ?? 20),
          socTarget: isCurrentSession ? socTarget : (cachedState?.socTarget ?? 80),
          mvEvery: mvEvery,
          mvMask: mvMask
        }
      };
    });

    // Sauvegarder dans localStorage
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        sessions: broadcastData,
        timestamp: Date.now()
      }));
    } catch (e) {
      // Ignore localStorage errors
    }
  }, [sessions, selId, environment, evseType, maxA, idTag, vehicleId, socStart, socTarget, mvEvery, mvMask]);

  // Synchroniser isParked et isPlugged avec les données du backend
  useEffect(() => {
    if (selSession) {
      // Si on vient de faire une action utilisateur, on ignore cette synchro
      if (skipNextSyncRef.current) {
        skipNextSyncRef.current = false;
        return;
      }

      // Statuts qui impliquent que la voiture est garée (inclut "booted" pour après unplug)
      const parkedStatuses = ["booted", "parked", "plugged", "preparing", "authorizing", "authorized", "starting", "started", "charging", "finishing", "stopping", "stopped"];
      // Statuts qui impliquent que le câble est branché
      const pluggedStatuses = ["plugged", "preparing", "authorizing", "authorized", "starting", "started", "charging", "finishing", "stopping", "stopped"];

      // Déduire depuis le status en priorité (plus fiable)
      const statusImpliesParked = parkedStatuses.includes(selSession.status);
      const statusImpliesPlugged = pluggedStatuses.includes(selSession.status);

      // Utiliser le flag backend OU le statut (pour éviter les incohérences)
      const shouldBeParked = selSession.isParked === true || statusImpliesParked;
      const shouldBePlugged = selSession.isPlugged === true || statusImpliesPlugged;

      setIsParked(shouldBeParked);
      setIsPlugged(shouldBePlugged);
    }
  }, [selSession?.id, selSession?.status, selSession?.isParked, selSession?.isPlugged]);

  // Logs polling
  useEffect(() => {
    let t: any;
    async function poll() {
      if (!selId) return;
      try {
        const rawData = await fetchJSON<any[]>(`/api/simu/${selId}/logs`);
        if (Array.isArray(rawData)) {
          // Transform backend format to frontend format
          const transformed: LogEntry[] = rawData.map((log: any) => ({
            ts: log.timestamp || log.ts || new Date().toISOString(),
            line: log.line || `[${log.level || 'INFO'}] [${log.category || 'LOG'}] ${log.message || ''}`
          }));
          setLogs(transformed.slice(-800));
        }
      } catch {}
    }
    poll();
    t = setInterval(poll, 1000);
    return () => clearInterval(t);
  }, [selId]);

  // Fiabilité OCPP
  useEffect(() => {
    const slice = logs.slice(-200);
    const sent = slice.filter((l) => l?.line?.includes(">> Sent")).length;
    const ack = slice.filter((l) => l?.line?.includes("<< RESULT")).length;
    setAckStats({ sent, ack, rate: sent ? ack / sent : 1 });
  }, [logs])

  // Ajoutez un ref pour tracker les logs déjà traités
  const processedLogsRef = useRef<Set<string>>(new Set());

  // Parsing des logs (avec SCP robuste)
  useEffect(() => {
    const recent = logs.slice(-80);
    if (!recent.length) return;

    const sSoc = [...series.soc];
    const sPA = [...series.pActive];
    const sExp = [...series.expected];
    let mvTapPayload: any | null = null;
    let hasNewData = false;

    for (const l of recent) {
      const t = nowMs();
      const logKey = `${l.ts}_${l.line}`;

      // Ignorer si déjà traité
      if (processedLogsRef.current.has(logKey)) continue;

      // Marquer comme traité APRÈS vérification
      processedLogsRef.current.add(logKey);

      // Nettoyer les anciens logs traités (garder seulement les 200 derniers)
      if (processedLogsRef.current.size > 200) {
        const keys = Array.from(processedLogsRef.current);
        keys.slice(0, keys.length - 200).forEach(k =>
            processedLogsRef.current.delete(k)
        );
      }

      // MeterValues
      if (/MeterValues/i.test(l.line)) {
        let paKw: number | undefined;
        let socPct: number | undefined;
        let energyKWh: number | undefined;

        const i1 = l.line.indexOf("{");
        const i2 = l.line.lastIndexOf("}");
        if (i1 !== -1 && i2 !== -1 && i2 > i1) {
          try {
            const payload = JSON.parse(l.line.slice(i1, i2 + 1));
            const mvArr = payload?.meterValue || [];
            for (const mv of mvArr) {
              for (const it of mv?.sampledValue || []) {
                const meas = String(it?.measurand || "");
                const val = Number(it?.value);
                const unit = String(it?.unit || "");
                if (!Number.isFinite(val)) continue;
                if (meas === "Power.Active.Import") {
                  paKw = unit === "W" ? val / 1000 : val;
                  hasNewData = true;
                } else if (meas === "SoC") {
                  socPct = val;
                  hasNewData = true;
                } else if (meas === "Energy.Active.Import.Register") {
                  energyKWh = unit.toLowerCase() === "wh" ? val / 1000 : val;
                  hasNewData = true;
                }
              }
            }
          } catch {}
        }

        if (energyKWh != null) {
          if (energyStartKWhRef.current == null)
            energyStartKWhRef.current = energyKWh;
          energyNowKWhRef.current = energyKWh;
        }
        if (paKw != null) {
          lastRealMvMsRef.current = t;
          const lastT = lastPowerMsRef.current;
          lastPowerMsRef.current = t;
          if (lastT != null) {
            const dtH = (t - lastT) / 3600000;
            energyFromPowerKWhRef.current += Math.max(0, paKw) * dtH;
          }
          const smooth = ewma(pActiveFiltRef.current, Math.max(0, paKw));
          pActiveFiltRef.current = smooth;
          sPA.push({ t, y: smooth });
          if (sPA.length > MAX_POINTS) sPA.shift();
        }
        if (socPct != null) {
          const smoothSoc = ewma(
              socFiltRef.current,
              clamp(socPct, 0, 100)
          );
          socFiltRef.current = smoothSoc;
          const lastY = sSoc.length ? sSoc[sSoc.length - 1].y : socStart;
          sSoc.push({ t, y: clamp(Math.max(lastY, smoothSoc), 0, 100) });
          if (sSoc.length > MAX_POINTS) sSoc.shift();

          const expKw = vehMaxKwAtSoc(smoothSoc);
          sExp.push({ t, y: expKw });
          if (sExp.length > MAX_POINTS) sExp.shift();
        }
        mvTapPayload = { powerActiveKw: paKw, socPercent: socPct };
      }

      // SetChargingProfile (parser robuste avec conversion A→W)
      if (/SetChargingProfile/i.test(l.line)) {
        const i1 = l.line.indexOf("{");
        const i2 = l.line.lastIndexOf("}");
        if (i1 !== -1 && i2 !== -1 && i2 > i1) {
          try {
            const parsed = JSON.parse(l.line.slice(i1, i2 + 1));

            const payload =
                Array.isArray(parsed) ? parsed[3] :
                    (parsed && typeof parsed === "object" && "payload" in parsed) ? (parsed as any).payload :
                        parsed;

            const connectorId = Number(payload?.connectorId || 1);
            const profile = payload?.csChargingProfiles || payload?.chargingProfile;

            if (profile) {
              // Normaliser duration si nécessaire
              if (profile?.chargingSchedule?.duration != null) {
                const d = profile.chargingSchedule.duration;
                profile.chargingSchedule.duration = d > 7 * 24 * 3600 ? Math.round(d / 1000) : d;
              }

              // CONVERSION A → W SI NÉCESSAIRE
              if (profile?.chargingSchedule?.chargingRateUnit === "A") {
                profile.chargingSchedule.chargingSchedulePeriod?.forEach((p: any) => {
                  // Convertir ampères en watts
                  p.limit = p.limit * voltage * phases;
                });
                profile.chargingSchedule.chargingRateUnit = "W";
                toasts.push(`SCP converti: A → W (${voltage}V × ${phases} phases)`);
              }

              const res = profilesManager.setChargingProfile(connectorId, profile);
              if (res.status === "Accepted") {
                toasts.push(`SCP appliqué • ${profile.chargingProfilePurpose} (stack ${profile.stackLevel})`);
              }
            }
          } catch (e) {
            console.error("Parse SCP failed", e);
          }
        }
      }

      // ClearChargingProfile
      if (/ClearChargingProfile/i.test(l.line)) {
        const i1 = l.line.indexOf("{");
        const i2 = l.line.lastIndexOf("}");

        let criteria = {};
        if (i1 !== -1 && i2 !== -1 && i2 > i1) {
          try {
            criteria = JSON.parse(l.line.slice(i1, i2 + 1));
          } catch {}
        }

        const result = profilesManager.clearChargingProfile(criteria as any);
        toasts.push(`Clear: ${result.cleared.length} profil(s) supprimé(s)`);
      }
    }

    if (hasNewData) setSeries({ soc: sSoc, pActive: sPA, expected: sExp });

    const now = nowMs();
    if (
        tnr.isRecording &&
        mvTapPayload &&
        now - (lastMvTapRef.current || 0) > 1000
    ) {
      lastMvTapRef.current = now;
      tnr.tapEvent(
          "meter",
          "MeterValues",
          mvTapPayload,
          selId || undefined
      );
    }
  }, [
    logs,
    tnr.isRecording,
    selId,
    socStart,
    voltage,
    phases,
    profilesManager,
    toasts,
    vehMaxKwAtSoc,
    tnr
  ]);

  useEffect(() => {
    if (!socFiltRef.current) socFiltRef.current = socStart;
  }, [socStart]);

  const [, forceTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => forceTick((t) => t + 1), 1000);
    return () => clearInterval(id);
  }, []);

  // Simulation 1 Hz
  useEffect(() => {
    const isChargingStatus = selSession?.status === "charging" || selSession?.status === "started";
    if (!isChargingStatus) return;
    const id = setInterval(() => {
      const vehKw = vehMaxKwAtSoc(socFiltRef.current ?? socStart);
      // Utiliser la limite basée sur les phases du backend si disponibles
      const backendPhases = selSession?.metrics?.phases;
      const effectiveLimitKw = backendPhases && backendPhases > 1
        ? Math.min(appliedLimitKw, (maxA * voltage * backendPhases) / 1000)
        : appliedLimitKw;
      const targetKw = Math.min(vehKw, effectiveLimitKw);
      const prev = pActiveFiltRef.current ?? 0;

      // Ne pas écraser la puissance si on a reçu des MeterValues récemment (< 3s)
      // pour respecter la valeur réelle du backend
      const hasRecentMv = Date.now() - lastRealMvMsRef.current < 3000;

      let newPower: number;
      if (hasRecentMv && prev > 0) {
        // Garder la valeur des MeterValues, juste ajouter un léger bruit
        newPower = prev * (1 + (Math.random() * 2 - 1) * NOISE * 0.1);
      } else {
        // Simulation locale si pas de MeterValues récents
        const ramped = clampRamp(prev, targetKw, 1);
        const noisy = ramped * (1 + (Math.random() * 2 - 1) * NOISE);
        newPower = ewma(prev, Math.max(0, noisy));
      }

      pActiveFiltRef.current = newPower;
      setSeries((s) => ({
        ...s,
        pActive: [
          ...s.pActive,
          { t: Date.now(), y: pActiveFiltRef.current! },
        ].slice(-MAX_POINTS),
        expected: [...s.expected, { t: Date.now(), y: vehKw }].slice(
            -MAX_POINTS
        ),
      }));

      const dtH = 1 / 3600;
      if (!hasRecentMv) {
        energyFromPowerKWhRef.current += Math.max(
            0,
            pActiveFiltRef.current!
        ) * dtH;
      }

      const cap = vehicleCapacityKWh;
      const eff = vehicleEfficiency;
      const dSoc = ((pActiveFiltRef.current! * dtH * eff) / cap) * 100 * SOC_CHARGE_MULTIPLIER;
      socFiltRef.current = clamp(
          (socFiltRef.current ?? socStart) + dSoc,
          0,
          100
      );

      setSeries((s) => ({
        ...s,
        soc: [...s.soc, { t: Date.now(), y: socFiltRef.current! }].slice(
            -MAX_POINTS
        ),
      }));

      // Feed data to anomaly analyzer
      anomalyAnalysis.analyzeAll({
        power: {
          timestamp: Date.now(),
          powerW: (pActiveFiltRef.current ?? 0) * 1000,
          setpointW: appliedLimitKw * 1000,
        },
        soc: {
          timestamp: Date.now(),
          soc: socFiltRef.current ?? socStart,
          energyWh: energyFromPowerKWhRef.current * 1000,
          powerW: (pActiveFiltRef.current ?? 0) * 1000,
        },
        scp: gpmSetpointKw ? {
          timestamp: Date.now(),
          setpointW: gpmSetpointKw * 1000,
          actualPowerW: (pActiveFiltRef.current ?? 0) * 1000,
          accepted: true,
        } : undefined,
      });
    }, 1000);
    return () => clearInterval(id);
  }, [selSession?.status, selSession?.metrics?.phases, appliedLimitKw, maxA, voltage, vehMaxKwAtSoc, socStart, vehicleCapacityKWh, vehicleEfficiency, clampRamp, anomalyAnalysis, gpmSetpointKw]);

  // ========= 12. RENDER =========
  // Si bootAccepted === false, les boutons d'action OCPP sont désactivés
  // car le CSMS a rejeté le BootNotification (OCPP ID non reconnu)
  const canAuth = !!selSession && isPlugged && bootAccepted !== false;
  const canStart = selSession?.status === "authorized" && isPlugged && bootAccepted !== false;
  const canStop = (selSession?.status === "charging" || selSession?.status === "started") && bootAccepted !== false;

  // Convertir les sessions en format SessionState pour le SessionTabBar
  const sessionsForTabBar: SessionState[] = sessions.map(s => ({
    id: s.id,
    cpId: s.cpId,
    isTemporary: false,
    status: s.status as any,
    wsUrl: s.wsUrl,
    txId: s.txId ?? null,
    transactionStartTime: null,
    isParked: s.isParked ?? false,
    isPlugged: s.isPlugged ?? false,
    isConnected: s.isConnected ?? false,
    isCharging: s.status === 'charging' || s.status === 'started',
    soc: s.soc ?? socFiltRef.current ?? socStart,
    activePower: pActiveFiltRef.current ?? 0,
    offeredPower: 0,
    energy: s.energy ?? 0,
    voltage: s.voltage ?? DEFAULT_VOLTAGE,
    current: 0,
    config: {
      cpId: s.cpId,
      environment: environment,
      evseType: evseType,
      maxA: maxA,
      idTag: s.config?.idTag ?? idTag,
      vehicleId: vehicleId,
      socStart: socStart,
      socTarget: socTarget,
      mvEvery: mvEvery,
      mvMask: mvMask
    },
    metrics: {
      stationKwMax: s.metrics?.stationKwMax ?? 0,
      backendKwMax: s.metrics?.backendKwMax ?? 0,
      txpKw: s.metrics?.txpKw ?? 0,
      txdpKw: s.metrics?.txdpKw ?? 0,
      voltage: s.metrics?.voltage ?? DEFAULT_VOLTAGE,
      phases: s.metrics?.phases ?? 1,
      energyKWh: s.energy ?? 0
    },
    logs: [],
    chartData: { soc: [], power: [], expected: [] }
  }));

  // Handler pour fermer une session depuis le tab bar
  const handleCloseSession = async (sessionId: string) => {
    const session = sessions.find(s => s.id === sessionId);
    if (!session) return;

    if (session.status === 'charging' || session.status === 'started') {
      toasts.push("Arretez d'abord la charge");
      return;
    }

    try {
      await fetchJSON(`/api/simu/${sessionId}`, { method: 'DELETE' });
      // Nettoyer le cache de la session
      cleanupSessionCache(sessionId);
      toasts.push(`Session ${session.cpId} fermee`);
      await refreshSessions();
    } catch (e) {
      toasts.push("Erreur lors de la fermeture");
    }
  };

  return (
      <div className="flex flex-col gap-4">
        <style dangerouslySetInnerHTML={{ __html: extraCSS }} />

        {/* Barre d'onglets multi-sessions */}
        <SessionTabBar
          sessions={sessionsForTabBar}
          activeSessionId={selId}
          onSelectSession={selectSession}
          onAddSession={onNewSession}
          onCloseSession={handleCloseSession}
        />

        {/* Utilisation du composant TNRBandeau AMELIORE */}
        <TNRBandeau tnr={tnr} onOpenTNR={() => window.open("/tnr", "_blank")} />

        {/* Statistiques OCPP et bouton Deconnexion */}
        <div className="flex items-center justify-between px-2">
          <div className="flex gap-4 items-center">
            <div className="text-xs">
              <div className="opacity-60">Fiabilite OCPP</div>
              <div
                  className={`font-semibold ${
                      ackStats.rate >= 0.98
                          ? "text-emerald-600"
                          : ackStats.rate >= 0.9
                              ? "text-amber-600"
                              : "text-rose-600"
                  }`}
              >
                ACK: {Math.round(ackStats.rate * 100)}%
                <span className="opacity-60 ml-1">
                  ({ackStats.ack}/{ackStats.sent})
                </span>
              </div>
            </div>
          </div>
          <button
              onClick={onDisconnect}
              className="px-3 py-2 rounded bg-rose-600 text-white hover:bg-rose-500 disabled:opacity-50"
              disabled={!selId}
          >
            Fermer (DISCONNECT)
          </button>
        </div>

        {/* Connexion + Contrôle */}
        <div className="grid grid-cols-12 gap-4">
          {/* Panneau gauche */}
          <div className="col-span-4 rounded border bg-white p-4 shadow-sm">
            <div className="font-semibold mb-2">Connexion OCPP</div>

            {/* Dropdown environnement - AJOUT ICI */}
            <div className="text-xs mb-1">Environnement</div>
            <select
                className="w-full border rounded px-2 py-1 mb-2 bg-white cursor-pointer"
                value={environment}
                onChange={(e) => setEnvironment(e.target.value as "test" | "pp")}
            >
              <option value="test">Test</option>
              <option value="pp">PP</option>
            </select>

            {/* URL non-éditable - REMPLACE L'INPUT */}
            <div className="text-xs mb-1 text-gray-500">OCPP WebSocket URL</div>
            <div className="w-full border rounded px-2 py-1 mb-2 bg-gray-100 text-gray-700 text-sm">
              {cpUrl}
            </div>

            {/* CP-ID reste éditable */}
            <div className="text-xs mb-1">CP-ID</div>
            <input
                className="w-full border rounded px-2 py-1 mb-3"
                value={cpId}
                onChange={(e) => setCpId(e.target.value)}
            />

            {/* Boutons de connexion */}
            <div className="flex gap-2">
              <button
                  onClick={onConnect}
                  className="px-3 py-2 rounded bg-emerald-600 text-white hover:bg-emerald-500"
              >
                CONNECT
              </button>
              <button
                  onClick={onDisconnect}
                  className="px-3 py-2 rounded bg-slate-200 hover:bg-slate-100"
                  disabled={!selId}
              >
                DISCONNECT
              </button>
            </div>

            {/* Status et Transaction */}
            <div className="mt-4 grid grid-cols-2 gap-2">
              <div className="rounded border p-2 bg-slate-50">
                <div className="text-xs">Status</div>
                <div className="text-lg">{selSession?.status ?? "—"}</div>
              </div>
              <div className="rounded border p-2 bg-slate-50">
                <div className="text-xs">Transaction</div>
                <div className="text-lg">{selSession?.txId ?? "—"}</div>
              </div>
            </div>

            {/* Alerte BootNotification Rejected */}
            {bootAccepted === false && (
              <div className="mt-3 p-3 rounded bg-rose-100 border border-rose-300 text-rose-800 text-sm">
                <div className="font-semibold flex items-center gap-2">
                  <span>BootNotification REJECTED</span>
                </div>
                <div className="mt-1 text-xs">
                  L'OCPP ID "{cpId}" n'est pas reconnu par le CSMS.
                  Les opérations OCPP sont désactivées.
                  Vérifiez que l'OCPP ID est enregistré sur le CSMS, puis déconnectez et reconnectez.
                </div>
              </div>
            )}

            {/* État Charging Profiles avec ChargingProfileCard */}
            <div className="mt-4">
              <ChargingProfileCard
                  profilesManager={profilesManager}
                  connectorId={1}
              />
            </div>

            {/* Prix de la session - Déplacé ici depuis la section bas */}
            <div className="mt-4 rounded border-l-4 border-l-blue-500 bg-white p-3 shadow-sm">
              <div className="flex items-center justify-between mb-2">
                <div className="font-semibold text-sm text-blue-800">Prix de la session</div>
                <button
                    onClick={fetchPrice}
                    disabled={!selId || fetchingPrice}
                    className={`px-2 py-1 rounded text-xs font-medium ${
                        selSession?.status === "finishing" || selSession?.status === "closed" || selSession?.status === "available"
                            ? "bg-blue-600 text-white hover:bg-blue-700"
                            : "bg-slate-300 text-slate-500"
                    } disabled:opacity-50`}
                >
                  {fetchingPrice ? "..." : "Récupérer"}
                </button>
              </div>

              {priceData && typeof priceData.totalPrice === 'number' ? (
                  <div className="space-y-2">
                    {/* Prix principal */}
                    <div className="rounded-lg bg-gradient-to-r from-emerald-50 to-blue-50 p-2">
                      <div className="flex items-baseline justify-between">
                        <div>
                          <div className="text-[10px] text-slate-600">Prix total</div>
                          <div className="text-xl font-bold text-emerald-700">
                            {priceData.totalPrice.toFixed(2)} {priceData.currency || 'EUR'}
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="text-[10px] text-slate-600">Énergie</div>
                          <div className="text-sm font-semibold text-slate-700">
                            {(priceData.energyKWh ?? 0).toFixed(2)} kWh
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Détails compacts */}
                    <div className="grid grid-cols-2 gap-1 text-xs">
                      <div className="rounded border p-1.5 bg-slate-50">
                        <div className="text-[10px] text-slate-500">Prix/kWh</div>
                        <div className="font-semibold">
                          {(priceData.pricePerKWh ?? 0) > 0 ? priceData.pricePerKWh.toFixed(3) : "0.000"} {priceData.currency || 'EUR'}
                        </div>
                      </div>
                      <div className="rounded border p-1.5 bg-slate-50">
                        <div className="text-[10px] text-slate-500">Source</div>
                        <div className="font-semibold">
                          {priceData.source === "api" || priceData.source === "tte" ? "API TTE" :
                              priceData.source === "not_configured" ? "Non config." :
                                  priceData.source === "not_found" ? "Non trouvé" :
                                      priceData.source === "fallback" ? "Local" : "Erreur"}
                        </div>
                      </div>
                    </div>

                    {/* Transaction ID */}
                    {priceData.transactionId && (
                        <div className="text-[10px] text-slate-500 border-t pt-1">
                          TX: {priceData.transactionId}
                        </div>
                    )}

                    {/* Message */}
                    {priceData.message && (
                        <div className={`text-[10px] p-1.5 rounded ${
                            priceData.source === "api" || priceData.source === "tte" ? "bg-green-100 text-green-700" :
                                priceData.source === "not_configured" ? "bg-gray-100 text-gray-700" :
                                    priceData.source === "not_found" ? "bg-yellow-100 text-yellow-700" :
                                        "bg-orange-100 text-orange-700"
                        }`}>
                          {priceData.message}
                        </div>
                    )}
                  </div>
              ) : (
                  <div className="text-center py-3 text-slate-500 text-xs">
                    {selSession?.status === "finishing" || selSession?.status === "closed" || selSession?.status === "available"
                        ? "Cliquez pour récupérer le prix"
                        : "Terminez la session"}
                  </div>
              )}
            </div>
          </div>

          {/* Panneau droit */}
          <div className="col-span-8 rounded border bg-white p-4 shadow-sm">
            <div className="font-semibold mb-2">Contrôle de charge</div>

            <div className="grid grid-cols-12 gap-2">
              <div className="col-span-3">
                <div className="text-xs mb-1">Type EVSE</div>
                <select
                    className="w-full border rounded px-2 py-1"
                    value={evseType}
                    onChange={(e) =>
                        setEvseType(e.target.value as any)
                    }
                >
                  {compatibleEvseTypes.map((t) => (
                    <option key={t.value} value={t.value}>{t.label}</option>
                  ))}
                </select>
                {selectedVehicle?.connectorTypes && (
                  <div className="text-[10px] text-slate-400 mt-0.5">
                    {selectedVehicle.connectorTypes.join(', ')}
                  </div>
                )}
              </div>
              <div className="col-span-2">
                <div className="text-xs mb-1">{isDCCharging ? 'Max (kW)' : 'Max (A)'}</div>
                <NumericInput
                    className={`w-full border rounded px-2 py-1 ${bypassVehicleLimits ? 'border-orange-400 bg-orange-50' : ''}`}
                    value={isDCCharging ? dcMaxPowerKw : maxA}
                    onChange={(val) => {
                      if (isDCCharging) {
                        setDcMaxPowerKw(val);
                      } else {
                        setMaxA(val);
                      }
                    }}
                    min={1}
                    max={isDCCharging ? 500 : 500}
                />
                {!isDCCharging && (
                  <label className="flex items-center gap-1 mt-1 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={bypassVehicleLimits}
                      onChange={(e) => setBypassVehicleLimits(e.target.checked)}
                      className="w-3 h-3 accent-orange-500"
                    />
                    <span className={`text-[10px] ${bypassVehicleLimits ? 'text-orange-600 font-medium' : 'text-slate-400'}`}>
                      Mode test (ignorer VE)
                    </span>
                  </label>
                )}
              </div>

              <div className="col-span-4">
                <div className="text-xs mb-1">Véhicule</div>
                <select
                    className="w-full border rounded px-2 py-1"
                    value={vehicleId}
                    onChange={(e) => setVehicleId(e.target.value)}
                >
                  {vehicles.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.name ||
                            `${v.manufacturer ?? ""} ${v.model ?? ""} ${v.variant ?? ""}`.trim()}
                      </option>
                  ))}
                </select>
              </div>

              <div className="col-span-3">
                <div className="text-xs mb-1">idTag</div>
                <input
                    className="w-full border rounded px-2 py-1"
                    value={idTag}
                    onChange={(e) => setIdTag(e.target.value)}
                    placeholder="Entrez l'idTag..."
                />
              </div>

              <div className="col-span-12 grid grid-cols-12 gap-2 mt-2">
                <div className="col-span-2">
                  <div className="text-xs mb-1">Période MV (s)</div>
                  <NumericInput
                      className="w-full border rounded px-2 py-1"
                      value={mvEvery}
                      onChange={setMvEvery}
                      min={1}
                      max={3600}
                  />
                </div>
                <div className="col-span-4">
                  <div className="text-xs mb-1">Mesures envoyées</div>
                  <div className="flex items-center gap-4 text-xs">
                    <label className="inline-flex items-center gap-1">
                      <input
                          type="checkbox"
                          checked={mvMask.powerActive}
                          onChange={(e) =>
                              setMvMask((m) => ({
                                ...m,
                                powerActive: e.target.checked,
                              }))
                          }
                      />
                      Power.Active.Import
                    </label>
                    <label className="inline-flex items-center gap-1">
                      <input
                          type="checkbox"
                          checked={mvMask.energy}
                          onChange={(e) =>
                              setMvMask((m) => ({
                                ...m,
                                energy: e.target.checked,
                              }))
                          }
                      />
                      Energy.Active.Import.Register
                    </label>
                  </div>
                  <div className="flex items-center gap-4 text-xs mt-1">
                    <label className="inline-flex items-center gap-1">
                      <input
                          type="checkbox"
                          checked={mvMask.soc}
                          onChange={(e) =>
                              setMvMask((m) => ({
                                ...m,
                                soc: e.target.checked,
                              }))
                          }
                      />
                      SoC
                    </label>
                    <label className="inline-flex items-center gap-1">
                      <input
                          type="checkbox"
                          checked={mvMask.powerOffered}
                          onChange={(e) =>
                              setMvMask((m) => ({
                                ...m,
                                powerOffered: e.target.checked,
                              }))
                          }
                      />
                      Power.Offered
                    </label>
                  </div>
                </div>

                <div className="col-span-3">
                  <div className="text-xs mb-1">SoC (%)</div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-500">Départ</span>
                    <NumericInput
                        className="w-16 border rounded px-2 py-1"
                        value={socStart}
                        onChange={setSocStart}
                        min={0}
                        max={100}
                    />
                    <span className="text-xs text-slate-500">Cible</span>
                    <NumericInput
                        className="w-16 border rounded px-2 py-1"
                        value={socTarget}
                        onChange={setSocTarget}
                        min={0}
                        max={100}
                    />
                  </div>
                </div>

                <div className="col-span-3 flex items-end justify-end gap-2">
                  <button
                      className="px-3 py-2 bg-indigo-600 text-white rounded hover:bg-indigo-700 w-full"
                      onClick={onApplyMv}
                  >
                    Appliquer MV
                  </button>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="text-slate-500">Câble</span>
                    <label className="inline-flex items-center gap-2">
                      <input
                          type="checkbox"
                          checked={showCable}
                          onChange={(e) => setShowCable(e.target.checked)}
                      />
                      Afficher
                    </label>
                  </div>
                </div>
              </div>

              {/* NOUVEAU: Section Phasage */}
              <div className="col-span-12">
                <PhasingSection
                    sessionId={selId}
                    disabled={!selSession || selSession.status === "error"}
                    apiBase={API_BASE}
                    evseConfig={{
                      phases: phases,
                      maxA: maxA
                    }}
                    vehicleLimits={selectedVehicle ? {
                      acPhases: selectedVehicle.acPhases ?? 3,
                      acMaxA: selectedVehicle.acMaxA ?? 32
                    } : undefined}
                    externalTestMode={bypassVehicleLimits}
                />
              </div>

              {/* Section GPM Setpoint */}
              <div className="col-span-12 mt-2">
                <div className="rounded border p-3 bg-violet-50 border-violet-200">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <span className="text-violet-600">⚡</span>
                      <span className="text-xs font-semibold text-violet-800">GPM Setpoint Monitoring</span>
                    </div>
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                          type="checkbox"
                          checked={gpmEnabled}
                          onChange={(e) => setGpmEnabled(e.target.checked)}
                          className="w-3 h-3 accent-violet-500"
                      />
                      <span className={`text-xs ${gpmEnabled ? 'text-violet-700 font-medium' : 'text-slate-400'}`}>
                        Activer
                      </span>
                    </label>
                  </div>
                  {gpmEnabled && (
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-violet-600 whitespace-nowrap">Root Node ID:</span>
                      <input
                          className="flex-1 border border-violet-200 rounded px-2 py-1 text-sm bg-white"
                          value={gpmRootNodeId}
                          onChange={(e) => setGpmRootNodeId(e.target.value)}
                          placeholder="Ex: SITE-001 ou 5e7f..."
                      />
                    </div>
                  )}
                </div>
              </div>

              {/* Boutons organisés */}
              <div className="col-span-12 grid grid-cols-2 gap-4 mt-3">
                {/* Colonne gauche – Contrôles BORNE */}
                <div className="rounded border p-3 bg-slate-50">
                  <div className="text-xs text-slate-500 font-semibold mb-2">
                    Borne
                  </div>
                  <div className="grid grid-cols-5 gap-2">
                    <button
                        className={`px-3 py-2 rounded text-sm ${
                            isParked && !isPlugged
                                ? "bg-blue-600 text-white hover:bg-blue-700"
                                : "bg-slate-200 text-slate-500"
                        }`}
                        onClick={onPlug}
                        disabled={!selId || !isParked || isPlugged}
                    >
                      Plug
                    </button>
                    <button
                        className={`px-3 py-2 rounded text-sm ${
                            isPlugged && !isCharging
                                ? "bg-slate-700 text-white hover:bg-slate-600"
                                : "bg-slate-200 text-slate-500"
                        }`}
                        onClick={onUnplug}
                        disabled={!selId || !isPlugged || isCharging}
                    >
                      Unplug
                    </button>
                    <button
                        className={`px-3 py-2 rounded text-sm ${
                            !!selSession &&
                            isPlugged &&
                            selSession?.status !== "authorized" &&
                            selSession?.status !== "charging" &&
                            selSession?.status !== "started"
                                ? "bg-sky-600 text-white hover:bg-sky-700"
                                : "bg-sky-200 text-sky-600"
                        }`}
                        disabled={
                            !selId ||
                            !isPlugged ||
                            selSession?.status === "authorized" ||
                            selSession?.status === "charging" ||
                            selSession?.status === "started"
                        }
                        onClick={onAuth}
                    >
                      Auth
                    </button>
                    <button
                        className={`px-3 py-2 rounded text-sm ${
                            selSession?.status === "authorized" && isPlugged
                                ? "bg-emerald-600 text-white hover:bg-emerald-700"
                                : "bg-emerald-200 text-emerald-700"
                        }`}
                        disabled={!selId || !(selSession?.status === "authorized" && isPlugged)}
                        onClick={onStart}
                    >
                      Start
                    </button>
                    <button
                        className={`px-3 py-2 rounded text-sm ${
                            selSession?.status === "charging" || selSession?.status === "started"
                                ? "bg-rose-600 text-white hover:bg-rose-700"
                                : "bg-rose-200 text-rose-700"
                        }`}
                        disabled={!selId || !(selSession?.status === "charging" || selSession?.status === "started")}
                        onClick={onStop}
                    >
                      Stop
                    </button>
                    <button
                        className={`px-3 py-2 rounded text-sm ${
                            (selSession?.status === "charging" || selSession?.status === "started")
                                ? isIdleMode
                                    ? "bg-amber-600 text-white hover:bg-amber-700"
                                    : "bg-amber-500 text-white hover:bg-amber-600"
                                : "bg-amber-200 text-amber-700"
                        }`}
                        disabled={!selId || !(selSession?.status === "charging" || selSession?.status === "started")}
                        onClick={onIdle}
                        title="Active/désactive le mode idle (puissance=0, session reste en vie)"
                    >
                      {isIdleMode ? "⏸️ Idle ON" : "⏸️ Idle"}
                    </button>
                  </div>
                </div>

                {/* Colonne droite – Contrôles VÉHICULE */}
                <div className="rounded border p-3 bg-slate-50">
                  <div className="text-xs text-slate-500 font-semibold mb-2">
                    Véhicule
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                        className={`px-3 py-2 rounded text-sm ${
                            !isParked && !isPlugged
                                ? "bg-blue-600 text-white hover:bg-blue-700"
                                : "bg-slate-200 text-slate-500"
                        }`}
                        onClick={onPark}
                        disabled={!selId || isParked || isPlugged}
                    >
                      Park
                    </button>
                    <button
                        className={`px-3 py-2 rounded text-sm ${
                            isParked && !isPlugged && !isCharging
                                ? "bg-orange-600 text-white hover:bg-orange-700"
                                : "bg-slate-200 text-slate-500"
                        }`}
                        onClick={onLeave}
                        disabled={!selId || !isParked || isPlugged || isCharging}
                    >
                      Leave
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Scène visuelle - Disposition horizontale comme la référence */}
            <div
                ref={containerRef}
                className="mt-4 rounded-xl bg-gradient-to-br from-slate-100 via-white to-slate-50 p-6 relative overflow-hidden"
                style={{ minHeight: 400 }}
            >
              {/* Container flex pour aligner horizontalement */}
              <div className="flex items-center justify-between w-full h-full relative">

                {/* BORNE à gauche */}
                <div
                    ref={stationRef}
                    className="relative select-none flex-shrink-0"
                    style={{ width: 200, height: 280 }}
                >
                  <img
                      src={evseType === "dc" ? ASSETS.stationDC : ASSETS.stationAC}
                      alt="Borne de charge"
                      className="w-full h-full object-contain"
                      style={{
                        filter: "drop-shadow(0 4px 6px rgba(0, 0, 0, 0.1))",
                      }}
                      onError={(e) => {
                        const target = e.target as HTMLImageElement;
                        target.style.display = 'none';
                        const parent = target.parentElement;
                        if (parent) {
                          parent.style.background = 'linear-gradient(135deg, #374151 0%, #1f2937 100%)';
                          parent.style.borderRadius = '8px';
                          parent.style.display = 'flex';
                          parent.style.alignItems = 'center';
                          parent.style.justifyContent = 'center';
                          parent.innerHTML = '<div style="color: white; text-align: center; font-weight: bold;">EVSE</div>';
                        }
                      }}
                  />
                  {/* Port de la borne - ref déplacé vers le connecteur quand plugged */}
                  {!isPlugged && (
                    <div
                        ref={stationPortRef}
                        className="absolute w-3 h-3"
                        style={{ right: 20, top: "50%", transform: "translateY(-50%)" }}
                    />
                  )}
                </div>

                {/* Zone centrale pour le câble et connecteurs */}
                <div className="flex-1 relative" style={{ minHeight: 300 }}>

                  {/* CONNECTEURS - avec rotation selon l'angle du câble */}
                  {isPlugged && (
                      <>
                        {/* Connecteur côté borne - positionné près du bord gauche (côté station) */}
                        <div
                            ref={stationPortRef}
                            style={{
                              position: "absolute",
                              left: "5%",
                              top: "40%",
                              transform: "translate(-50%, -50%)",
                              zIndex: 80,
                            }}
                        >
                          <Connector
                              side="right"
                              rotation={connAngles.left - 50}
                              glowing={selSession?.status === "charging" || selSession?.status === "started"}
                              size={180}
                          />
                        </div>

                        {/* Connecteur côté voiture - positionné près du bord droit (côté véhicule) */}
                        {isParked && (
                            <div
                                ref={carPortRef}
                                style={{
                                  position: "absolute",
                                  right: "5%",
                                  top: "40%",
                                  transform: "translate(50%, -50%)",
                                  zIndex: 80,
                                }}
                            >
                              <Connector
                                  side="left"
                                  rotation={connAngles.right + 50}
                                  glowing={selSession?.status === "charging" || selSession?.status === "started"}
                                  size={180}
                              />
                            </div>
                        )}
                      </>
                  )}

                </div>

                {/* VOITURE à droite avec ChargeDisplay en dessous */}
                {isParked && (
                    <div className="flex flex-col items-center flex-shrink-0">
                      {/* Image de la voiture */}
                      <div
                          ref={carRef}
                          className="relative select-none"
                          style={{
                            width: 380 * VEHICLE_SCALE,
                            height: 200 * VEHICLE_SCALE,
                          }}
                      >
                        <img
                            src={vehicleImage}
                            alt="Véhicule électrique"
                            className="w-full h-full object-contain"
                            style={{
                              filter: "drop-shadow(0 4px 6px rgba(0, 0, 0, 0.1))",
                            }}
                            onError={(e) => {
                              const target = e.target as HTMLImageElement;
                              target.style.display = 'none';
                              const parent = target.parentElement;
                              if (parent) {
                                parent.style.background = 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)';
                                parent.style.borderRadius = '8px';
                                parent.style.display = 'flex';
                                parent.style.alignItems = 'center';
                                parent.style.justifyContent = 'center';
                                parent.innerHTML = '<div style="color: white; text-align: center; font-weight: bold;">EV</div>';
                              }
                            }}
                        />
                        {/* Port de la voiture - ref déplacé vers le connecteur quand plugged */}
                        {!isPlugged && (
                          <div
                              ref={carPortRef}
                              className="absolute w-3 h-3"
                              style={{
                                left: 20 * VEHICLE_SCALE,
                                top: "50%",
                                transform: "translateY(-50%)"
                              }}
                          />
                        )}
                      </div>

                      {/* ChargeDisplay en dessous de la voiture */}
                      {(selSession?.status === "charging" || selSession?.status === "started") && (
                          <div className="mt-3">
                            <ChargeDisplay
                                soc={currentSoc}
                                powerKw={currentPowerKw}
                                energyKWh={totalEnergyKWh}
                                elapsedTime={elapsedTime}
                                isCharging={true}
                            />
                          </div>
                      )}
                    </div>
                )}
              </div>

              {/* CÂBLE - placé au niveau du containerRef pour couvrir borne et voiture */}
              {showCable && isParked && isPlugged && (
                  <Cable
                      containerRef={containerRef}
                      startRef={stationPortRef}
                      endRef={carPortRef}
                      show={isParked && isPlugged}
                      charging={selSession?.status === "charging" || selSession?.status === "started"}
                      state={
                        selSession?.status === "charging" || selSession?.status === "started"
                          ? 'charging'
                          : isPlugged
                            ? 'plugged'
                            : isParked
                              ? 'vehicle_parked'
                              : 'idle'
                      }
                      powerKW={currentPowerKw}
                      sagFactor={0.35}
                      extraDropPx={60}
                      onAnglesChange={setConnAngles}
                  />
              )}
            </div>
          </div>
        </div>

        {/* Métriques + Graph + Logs - Layout amélioré */}
        <div className="grid grid-cols-12 gap-4">
          {/* Métriques temps réel - Plus compact */}
          <div className="col-span-3 rounded border bg-white p-4 shadow-sm">
            <div className="font-semibold mb-2">Métriques temps réel</div>

            {/* Carte Charging Profiles */}
            <div className="mb-3">
              <ChargingProfileCard
                  profilesManager={profilesManager}
                  connectorId={1}
              />
            </div>

            {/* GPM Setpoint Card */}
            <div className="mb-3">
              <GPMSetpointCard
                  cpId={cpId}
                  rootNodeId={gpmRootNodeId}
                  enabled={gpmEnabled}
                  onSetpointReceived={setGpmSetpointKw}
              />
            </div>

            {/* Anomaly Detection ML - Compact */}
            <div className="mb-3">
              <AnomalyDashboard
                  result={anomalyAnalysis.analysisResult}
                  enabled={anomalyAnalysis.enabled}
                  onToggle={anomalyAnalysis.setEnabled}
                  onResolve={anomalyAnalysis.resolveAnomaly}
                  onRefresh={anomalyAnalysis.refreshAnalysis}
                  onClear={anomalyAnalysis.clearHistory}
                  compact
              />
            </div>

            <div className="grid grid-cols-2 gap-2 mb-2">
              <div className="rounded border p-2 bg-emerald-50">
                <div className="text-[10px] text-emerald-700 mb-0.5">Énergie</div>
                <div className="text-lg font-bold">{totalEnergyKWh.toFixed(2)} kWh</div>
              </div>
              <div className="rounded border p-2 bg-blue-50">
                <div className="text-[10px] text-blue-700 mb-0.5">Durée</div>
                <div className="text-lg font-mono">{elapsedTime}</div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2 mb-2">
              <div className="rounded border p-2 bg-slate-50">
                <div className="text-[10px] text-slate-500 mb-0.5">Limite EVSE</div>
                <div className="text-lg font-bold">{physicalLimitKw.toFixed(1)} kW</div>
                <div className="text-[9px] text-slate-400">{maxA}A×{voltage}V×{phases}φ</div>
              </div>
              <div className="rounded border p-2 bg-indigo-50">
                <div className="text-[10px] text-indigo-600 mb-0.5">Limite SCP</div>
                <div className="text-lg font-bold text-indigo-900">{appliedLimitKw.toFixed(1)} kW</div>
              </div>
            </div>

            {/* Puissance véhicule */}
            <div className="rounded border p-2 bg-amber-50 mb-2">
              <div className="text-[10px] text-amber-700 mb-0.5">Max véhicule @ {Math.round(currentSoc)}%</div>
              <div className="text-lg font-bold text-amber-900">{vehMaxKwAtSoc(currentSoc).toFixed(1)} kW</div>
              <div className="text-[9px] text-amber-600">{selectedVehicle?.name || 'Générique'}</div>
            </div>

            {(selSession?.status === "charging" || selSession?.status === "started") && (
                <div className="rounded border p-2 bg-yellow-50">
                  <div className="text-[10px] text-yellow-700 mb-0.5">Coût (0.40 €/kWh)</div>
                  <div className="text-lg font-bold text-yellow-900">
                    {(totalEnergyKWh * 0.40).toFixed(2)} €
                  </div>
                </div>
            )}
          </div>

          {/* Graphique - Plus grand */}
          <div className="col-span-5 rounded border bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <div className="font-semibold">Courbe de charge</div>
              <div className="text-xs text-slate-500">
                {selectedVehicle?.name || 'Véhicule'} • {isDCCharging ? 'DC' : 'AC'} • Max {(isDCCharging ? selectedVehicle?.maxPowerKW : selectedVehicle?.acMaxKW) || '?'} kW
              </div>
            </div>
            <Chart
                height={380}
                yMax={Math.max(
                    20,
                    Math.ceil(Math.max(
                      physicalLimitKw,
                      appliedLimitKw,
                      vehMaxKwAtSoc(20)
                    ) * 1.2)
                )}
                xLabel="Temps"
                yLabel="Puissance (kW)"
                refLines={[
                  {
                    value: appliedLimitKw,
                    label: `Limite EVSE: ${appliedLimitKw.toFixed(1)} kW`,
                    color: "#dc2626",
                  },
                  {
                    value: vehMaxKwAtSoc(currentSoc),
                    label: `Max véhicule: ${vehMaxKwAtSoc(currentSoc).toFixed(1)} kW`,
                    color: "#f59e0b",
                  },
                  ...(profileState.effectiveLimit.source === "profile"
                      ? [
                        {
                          value: profileState.effectiveLimit.limitW / 1000,
                          label: `SCP: ${(profileState.effectiveLimit.limitW / 1000).toFixed(1)} kW`,
                          color: "#22c55e",
                        },
                      ]
                      : []),
                  ...(gpmSetpointKw !== null
                      ? [
                        {
                          value: gpmSetpointKw,
                          label: `GPM: ${gpmSetpointKw.toFixed(1)} kW`,
                          color: "#8b5cf6",
                        },
                      ]
                      : []),
                ]}
                series={[
                  {
                    label: "Puissance réelle",
                    color: "#10b981",
                    points: series.pActive,
                    width: 3,
                  },
                  {
                    label: "Courbe véhicule (max)",
                    color: "#f59e0b",
                    dash: "6 3",
                    points: series.expected,
                    width: 2,
                    opacity: 0.6,
                  },
                ]}
            />
          </div>

          {/* Logs - Plus grand */}
          <div className="col-span-4 rounded border bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between mb-2">
              <div className="font-semibold">Logs de session</div>
              <div className="flex gap-2">
                <button
                    className="px-2 py-1 bg-slate-200 hover:bg-slate-100 rounded text-xs"
                    onClick={() => {
                      const txt = logs
                          .map((l) => `[${l.ts}] ${l.line}`)
                          .join("\n");
                      navigator.clipboard.writeText(txt);
                    }}
                    disabled={!logs.length}
                >
                  Copier
                </button>
                <button
                    className="px-2 py-1 bg-rose-600 text-white hover:bg-rose-500 rounded text-xs"
                    onClick={() => setLogs([])}
                >
                  Effacer
                </button>
              </div>
            </div>
            <div
                className="bg-[#0b1220] text-[#cde3ff] font-mono text-[11px] p-3 overflow-y-auto rounded"
                style={{ height: 380 }}
            >
              {logs.map((l, i) => (
                  <div key={i} className="whitespace-pre-wrap break-all py-0.5 border-b border-slate-800/30">
                    <span className="text-slate-500">[{l.ts?.split('T')[1]?.substring(0,8) || l.ts}]</span> {l.line}
                  </div>
              ))}
            </div>
          </div>
        </div>

        {/* Toasts */}
        {toasts.items.length > 0 && (
            <div className="fixed right-4 bottom-4 z-[1000] flex flex-col gap-2">
              {toasts.items.map((t) => (
                  <div
                      key={t.id}
                      className="px-4 py-3 rounded-lg bg-slate-900 text-white shadow-2xl text-sm animate-[pulse_0.5s_ease-in-out]"
                  >
                    {t.text}
                  </div>
              ))}
            </div>
        )}
      </div>
  );
}
