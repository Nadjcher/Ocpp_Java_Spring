// src/components/simu-evse/ActionBar.tsx
// Barre d'actions contextuelles avec workflow visuel

import React from 'react';

type SessionStatus = string; // 'connected' | 'booted' | 'authorized' | 'started' | 'stopped' | 'closed' | 'error' | etc.

interface ActionBarProps {
  status: SessionStatus;
  isParked: boolean;
  isPlugged: boolean;
  onPark: () => void;
  onLeave: () => void;
  onPlug: () => void;
  onUnplug: () => void;
  onAuth: () => void;
  onStart: () => void;
  onStop: () => void;
  disabled?: boolean;
}

interface Step {
  id: string;
  label: string;
  icon: React.ReactNode;
  active: boolean;
  done: boolean;
  enabled: boolean;
  action?: () => void;
  reverseAction?: () => void;
  reverseLabel?: string;
}

export function ActionBar({
  status,
  isParked,
  isPlugged,
  onPark,
  onLeave,
  onPlug,
  onUnplug,
  onAuth,
  onStart,
  onStop,
  disabled,
}: ActionBarProps) {
  const isAuthorized = status === 'authorized';
  const isCharging = status === 'started';
  const isConnected = !['disconnected', 'closed', 'error', ''].includes(status || '');

  // Definir les etapes du workflow
  const steps: Step[] = [
    {
      id: 'park',
      label: 'Park',
      icon: <CarIcon />,
      active: isParked && !isPlugged,
      done: isParked,
      enabled: isConnected && !isParked && !isPlugged,
      action: onPark,
      reverseAction: onLeave,
      reverseLabel: 'Leave',
    },
    {
      id: 'plug',
      label: 'Plug',
      icon: <PlugIcon />,
      active: isPlugged && !isAuthorized && !isCharging,
      done: isPlugged,
      enabled: isParked && !isPlugged,
      action: onPlug,
      reverseAction: onUnplug,
      reverseLabel: 'Unplug',
    },
    {
      id: 'auth',
      label: 'Auth',
      icon: <ShieldIcon />,
      active: isAuthorized && !isCharging,
      done: isAuthorized || isCharging,
      enabled: isPlugged && !isAuthorized && !isCharging,
      action: onAuth,
    },
    {
      id: 'start',
      label: 'Start',
      icon: <PlayIcon />,
      active: isCharging,
      done: false,
      enabled: isPlugged && isAuthorized && !isCharging,
      action: onStart,
    },
    {
      id: 'charging',
      label: 'Charging',
      icon: <BoltIcon />,
      active: isCharging,
      done: false,
      enabled: false,
    },
    {
      id: 'stop',
      label: 'Stop',
      icon: <StopIcon />,
      active: false,
      done: false,
      enabled: isCharging,
      action: onStop,
    },
  ];

  // Trouver l'etape active pour le highlight
  const activeIdx = steps.findIndex(s => s.active);

  return (
    <div className="rounded-lg border border-slate-200 bg-white shadow-sm p-3">
      {/* Workflow visuel */}
      <div className="flex items-center gap-1 overflow-x-auto pb-2">
        {steps.map((step, idx) => (
          <React.Fragment key={step.id}>
            {idx > 0 && (
              <div className={`flex-shrink-0 h-0.5 w-6 ${
                idx <= activeIdx ? 'bg-emerald-400' : 'bg-slate-200'
              }`} />
            )}
            <button
              onClick={step.enabled ? step.action : (step.done && step.reverseAction ? step.reverseAction : undefined)}
              disabled={disabled || (!step.enabled && !step.done)}
              className={`flex-shrink-0 flex flex-col items-center gap-1 px-3 py-2 rounded-lg transition-all min-w-[60px] ${
                step.active
                  ? step.id === 'charging'
                    ? 'bg-emerald-100 border-2 border-emerald-400 text-emerald-700 shadow-sm'
                    : 'bg-blue-100 border-2 border-blue-400 text-blue-700 shadow-sm'
                  : step.done
                  ? 'bg-emerald-50 border border-emerald-200 text-emerald-600 cursor-pointer hover:bg-emerald-100'
                  : step.enabled
                  ? 'bg-white border-2 border-blue-300 text-blue-600 cursor-pointer hover:bg-blue-50 hover:shadow-sm'
                  : 'bg-slate-50 border border-slate-200 text-slate-300 cursor-not-allowed'
              }`}
              title={step.done && step.reverseAction ? step.reverseLabel : step.label}
            >
              <span className="w-5 h-5">{step.icon}</span>
              <span className="text-[10px] font-medium whitespace-nowrap">
                {step.done && step.reverseAction && !step.active ? step.reverseLabel : step.label}
              </span>
              {/* Dot indicateur */}
              {step.active && step.id === 'charging' && (
                <span className="absolute -top-1 -right-1 flex h-2.5 w-2.5">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                  <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500" />
                </span>
              )}
            </button>
          </React.Fragment>
        ))}
      </div>

      {/* Actions rapides en dessous (boutons classiques pour les utilisateurs habitues) */}
      <div className="flex gap-2 mt-2 pt-2 border-t border-slate-100">
        <div className="flex gap-1.5 flex-1">
          <ActionBtn label="Park" onClick={onPark} disabled={disabled || !isConnected || isParked} active={isParked} color="indigo" />
          <ActionBtn label="Plug" onClick={onPlug} disabled={disabled || !isParked || isPlugged} active={isPlugged} color="blue" />
          <ActionBtn label="Auth" onClick={onAuth} disabled={disabled || !isPlugged || isAuthorized || isCharging} active={isAuthorized} color="cyan" />
          <ActionBtn label="Start" onClick={onStart} disabled={disabled || !isPlugged || !isAuthorized || isCharging} color="emerald" />
        </div>
        <div className="h-6 w-px bg-slate-200 self-center" />
        <div className="flex gap-1.5">
          <ActionBtn label="Stop" onClick={onStop} disabled={disabled || !isCharging} color="rose" />
          <ActionBtn label="Unplug" onClick={onUnplug} disabled={disabled || !isPlugged || isCharging} color="slate" />
          <ActionBtn label="Leave" onClick={onLeave} disabled={disabled || !isParked || isPlugged || isCharging} color="slate" />
        </div>
      </div>
    </div>
  );
}

// Bouton d'action compact
function ActionBtn({
  label,
  onClick,
  disabled,
  active,
  color,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  active?: boolean;
  color: string;
}) {
  const colorMap: Record<string, { bg: string; text: string; activeBg: string }> = {
    indigo: { bg: 'bg-indigo-50 hover:bg-indigo-100 border-indigo-200', text: 'text-indigo-700', activeBg: 'bg-indigo-600 text-white border-indigo-600' },
    blue: { bg: 'bg-blue-50 hover:bg-blue-100 border-blue-200', text: 'text-blue-700', activeBg: 'bg-blue-600 text-white border-blue-600' },
    cyan: { bg: 'bg-cyan-50 hover:bg-cyan-100 border-cyan-200', text: 'text-cyan-700', activeBg: 'bg-cyan-600 text-white border-cyan-600' },
    emerald: { bg: 'bg-emerald-50 hover:bg-emerald-100 border-emerald-200', text: 'text-emerald-700', activeBg: 'bg-emerald-600 text-white border-emerald-600' },
    rose: { bg: 'bg-rose-50 hover:bg-rose-100 border-rose-200', text: 'text-rose-700', activeBg: 'bg-rose-600 text-white border-rose-600' },
    slate: { bg: 'bg-slate-50 hover:bg-slate-100 border-slate-200', text: 'text-slate-700', activeBg: 'bg-slate-600 text-white border-slate-600' },
  };
  const c = colorMap[color] || colorMap.slate;

  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`px-3 py-1 rounded border text-xs font-medium transition-all ${
        active ? c.activeBg : `${c.bg} ${c.text}`
      } ${disabled ? 'opacity-40 cursor-not-allowed' : ''}`}
    >
      {label}
    </button>
  );
}

// Mini icons (inline SVG)
function CarIcon() {
  return (
    <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
    </svg>
  );
}
function PlugIcon() {
  return (
    <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
    </svg>
  );
}
function ShieldIcon() {
  return (
    <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}
function PlayIcon() {
  return (
    <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}
function BoltIcon() {
  return (
    <svg className="w-full h-full" fill="currentColor" viewBox="0 0 24 24">
      <path d="M13 10V3L4 14h7v7l9-11h-7z" />
    </svg>
  );
}
function StopIcon() {
  return (
    <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 10a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1v-4z" />
    </svg>
  );
}

export default ActionBar;
