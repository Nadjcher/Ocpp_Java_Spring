// src/components/simu-evse/ConnectionPanel.tsx
// Bandeau de connexion OCPP compact et intuitif

import React, { useState } from 'react';

type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'booted' | 'authorized' | 'started' | 'stopped' | 'error' | 'closed';

interface ConnectionPanelProps {
  // Environnement
  environment: 'test' | 'pp';
  onEnvironmentChange: (env: 'test' | 'pp') => void;
  wsUrl: string;
  onWsUrlChange?: (url: string) => void;

  // Identite borne
  cpId: string;
  onCpIdChange: (cpId: string) => void;
  connectorId: number;
  onConnectorIdChange: (id: number) => void;

  // Connexion
  status: ConnectionStatus | string;
  bootAccepted?: boolean | null;
  onConnect: () => void;
  onDisconnect: () => void;
  disabled?: boolean;

  // ACK stats (optionnel)
  ackStats?: { ack: number; sent: number };
  txId?: number | null;
}

const STATUS_CONFIG: Record<string, { color: string; label: string; pulse?: boolean }> = {
  disconnected: { color: 'bg-slate-400', label: 'Deconnecte' },
  closed: { color: 'bg-slate-400', label: 'Ferme' },
  connecting: { color: 'bg-amber-400', label: 'Connexion...', pulse: true },
  connected: { color: 'bg-emerald-400', label: 'Connecte' },
  booted: { color: 'bg-emerald-500', label: 'Boot OK' },
  authorized: { color: 'bg-blue-500', label: 'Autorise' },
  started: { color: 'bg-emerald-500', label: 'En charge', pulse: true },
  stopped: { color: 'bg-orange-400', label: 'Arrete' },
  error: { color: 'bg-red-500', label: 'Erreur' },
};

export function ConnectionPanel({
  environment,
  onEnvironmentChange,
  wsUrl,
  onWsUrlChange,
  cpId,
  onCpIdChange,
  connectorId,
  onConnectorIdChange,
  status,
  bootAccepted,
  onConnect,
  onDisconnect,
  disabled,
  ackStats,
  txId,
}: ConnectionPanelProps) {
  const [showAdvanced, setShowAdvanced] = useState(false);

  const statusStr = String(status || 'disconnected');
  const statusConf = STATUS_CONFIG[statusStr] || STATUS_CONFIG.disconnected;
  const isConnected = !['disconnected', 'closed', 'error'].includes(statusStr);
  const isCharging = statusStr === 'started';

  return (
    <div className="rounded-lg border bg-white shadow-sm">
      <div className="flex items-center gap-4 px-4 py-3 flex-wrap">

        {/* 1. Environnement */}
        <div className="flex items-center gap-1">
          {(['test', 'pp'] as const).map((env) => (
            <button
              key={env}
              onClick={() => onEnvironmentChange(env)}
              disabled={isConnected}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold uppercase transition-all ${
                environment === env
                  ? env === 'test'
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'bg-orange-500 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
              } ${isConnected ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
            >
              {env}
            </button>
          ))}
          <button
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="ml-1 p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
            title="Mode avance - editer l'URL"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </button>
        </div>

        {/* Separateur */}
        <div className="h-8 w-px bg-slate-200" />

        {/* 2. Identite borne */}
        <div className="flex items-center gap-2 flex-1 min-w-0">
          <div className="flex-1 min-w-[180px]">
            <input
              className="w-full border border-slate-200 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              value={cpId}
              onChange={(e) => onCpIdChange(e.target.value)}
              placeholder="Identifiant borne — ex: E00000G9978"
              disabled={isConnected}
            />
          </div>
          <select
            className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm bg-white focus:ring-2 focus:ring-blue-500 w-20"
            value={connectorId}
            onChange={(e) => onConnectorIdChange(Number(e.target.value))}
            disabled={isConnected}
          >
            {[1, 2, 3, 4].map(n => (
              <option key={n} value={n}>Conn. {n}</option>
            ))}
          </select>
        </div>

        {/* Separateur */}
        <div className="h-8 w-px bg-slate-200" />

        {/* 3. Statut + Action */}
        <div className="flex items-center gap-3">
          {/* Indicateur de statut */}
          <div className="flex items-center gap-2">
            <span className="relative flex h-3 w-3">
              {statusConf.pulse && (
                <span className={`animate-ping absolute inline-flex h-full w-full rounded-full ${statusConf.color} opacity-75`} />
              )}
              <span className={`relative inline-flex rounded-full h-3 w-3 ${statusConf.color}`} />
            </span>
            <span className="text-sm font-medium text-slate-700">{statusConf.label}</span>
          </div>

          {/* ACK stats compact */}
          {isConnected && ackStats && ackStats.sent > 0 && (
            <span className="text-xs text-slate-400">
              ACK {ackStats.ack}/{ackStats.sent}
            </span>
          )}

          {/* TX ID compact */}
          {txId && (
            <span className="text-xs text-slate-400 font-mono">
              TX:{txId}
            </span>
          )}

          {/* Bouton Connect/Disconnect */}
          {!isConnected ? (
            <button
              onClick={onConnect}
              disabled={disabled || !cpId}
              className="px-4 py-1.5 rounded-lg bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center gap-1.5"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              Connecter
            </button>
          ) : (
            <button
              onClick={onDisconnect}
              className="px-4 py-1.5 rounded-lg bg-rose-600 text-white text-sm font-medium hover:bg-rose-700 transition-colors flex items-center gap-1.5"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
              </svg>
              Deconnecter
            </button>
          )}
        </div>
      </div>

      {/* Boot rejected alert */}
      {bootAccepted === false && (
        <div className="mx-4 mb-3 p-2.5 rounded-lg bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2">
          <svg className="w-4 h-4 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
          </svg>
          <div>
            <span className="font-semibold">BootNotification REJECTED</span> — L'OCPP ID "{cpId}" n'est pas reconnu. Deconnectez et verifiez l'ID.
          </div>
        </div>
      )}

      {/* URL avancee */}
      {showAdvanced && (
        <div className="px-4 pb-3 border-t border-slate-100 pt-2">
          <div className="text-xs text-slate-500 mb-1">URL WebSocket OCPP (avance)</div>
          <input
            className="w-full border border-slate-200 rounded-lg px-3 py-1.5 text-sm font-mono text-slate-600 bg-slate-50 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            value={wsUrl}
            onChange={(e) => onWsUrlChange?.(e.target.value)}
            disabled={isConnected}
            placeholder="wss://..."
          />
        </div>
      )}
    </div>
  );
}

export default ConnectionPanel;
