// frontend/src/components/session/SessionCard.tsx
// Carte resume d'une session dans la grille multi-session

import React from 'react';
import type { SessionData, MultiSessionStatus } from '@/store/multiSessionStore';
import {
  Plug, Zap, Battery, AlertCircle, CheckCircle,
  Loader, XCircle, Power, Trash2, Car
} from 'lucide-react';

interface SessionCardProps {
  session: SessionData;
  isSelected: boolean;
  onSelect: () => void;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onRemove?: () => void;
}

// Configuration visuelle par etat
const stateConfig: Record<MultiSessionStatus, {
  color: string;
  bgColor: string;
  borderColor: string;
  icon: React.FC<{ className?: string }>;
  label: string;
  animate?: boolean;
}> = {
  'NOT_CREATED': {
    color: 'text-gray-400',
    bgColor: 'bg-gray-700',
    borderColor: 'border-gray-600',
    icon: Plug,
    label: 'Non creee'
  },
  'CONFIGURED': {
    color: 'text-blue-400',
    bgColor: 'bg-blue-900/50',
    borderColor: 'border-blue-600',
    icon: Plug,
    label: 'Prete'
  },
  'CONNECTING': {
    color: 'text-yellow-400',
    bgColor: 'bg-yellow-900/50',
    borderColor: 'border-yellow-600',
    icon: Loader,
    label: 'Connexion...',
    animate: true
  },
  'CONNECTED': {
    color: 'text-green-400',
    bgColor: 'bg-green-900/50',
    borderColor: 'border-green-600',
    icon: CheckCircle,
    label: 'Connectee'
  },
  'PREPARING': {
    color: 'text-cyan-400',
    bgColor: 'bg-cyan-900/50',
    borderColor: 'border-cyan-600',
    icon: Loader,
    label: 'Preparation',
    animate: true
  },
  'CHARGING': {
    color: 'text-emerald-400',
    bgColor: 'bg-emerald-900/50',
    borderColor: 'border-emerald-500',
    icon: Zap,
    label: 'En charge'
  },
  'SUSPENDED': {
    color: 'text-orange-400',
    bgColor: 'bg-orange-900/50',
    borderColor: 'border-orange-600',
    icon: AlertCircle,
    label: 'Suspendue'
  },
  'FINISHING': {
    color: 'text-purple-400',
    bgColor: 'bg-purple-900/50',
    borderColor: 'border-purple-600',
    icon: Loader,
    label: 'Finalisation',
    animate: true
  },
  'DISCONNECTED': {
    color: 'text-gray-500',
    bgColor: 'bg-gray-800',
    borderColor: 'border-gray-700',
    icon: XCircle,
    label: 'Deconnectee'
  },
  'ERROR': {
    color: 'text-red-400',
    bgColor: 'bg-red-900/50',
    borderColor: 'border-red-600',
    icon: AlertCircle,
    label: 'Erreur'
  },
};

export function SessionCard({
  session,
  isSelected,
  onSelect,
  onConnect,
  onDisconnect,
  onRemove
}: SessionCardProps) {
  const config = stateConfig[session.state] || stateConfig['NOT_CREATED'];
  const Icon = config.icon;

  const handleConnect = (e: React.MouseEvent) => {
    e.stopPropagation();
    onConnect?.();
  };

  const handleDisconnect = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDisconnect?.();
  };

  const handleRemove = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm('Supprimer cette session ?')) {
      onRemove?.();
    }
  };

  const isActive = ['CONNECTING', 'CONNECTED', 'PREPARING', 'CHARGING', 'SUSPENDED', 'FINISHING'].includes(session.state);
  const canConnect = ['CONFIGURED', 'DISCONNECTED', 'ERROR'].includes(session.state);
  const canDisconnect = isActive;

  return (
    <div
      onClick={onSelect}
      className={`relative p-4 rounded-lg border-2 cursor-pointer transition-all ${
        isSelected
          ? `${config.borderColor} ${config.bgColor} ring-2 ring-offset-2 ring-offset-gray-900 ring-blue-500`
          : `border-gray-700 bg-gray-800 hover:border-gray-600 hover:bg-gray-750`
      }`}
    >
      {/* Badge d'etat */}
      <div className={`absolute top-2 right-2 flex items-center px-2 py-1 rounded text-xs font-medium ${config.bgColor} ${config.color} border ${config.borderColor}`}>
        <Icon className={`w-3 h-3 mr-1 ${config.animate ? 'animate-spin' : ''}`} />
        {config.label}
      </div>

      {/* Contenu */}
      <div className="mt-6">
        {/* CP-ID */}
        <h3 className="text-lg font-bold text-white mb-1 flex items-center">
          <Car className="w-4 h-4 mr-2 text-gray-400" />
          {session.config.chargePointId}
        </h3>

        {/* Vehicule & EVSE */}
        <p className="text-sm text-gray-400 mb-3">
          {session.config.vehicleId} | {session.config.evseType.replace('_', ' ')}
        </p>

        {/* Etat physique */}
        <div className="flex gap-2 mb-3">
          {session.isParked && (
            <span className="px-2 py-0.5 bg-indigo-900/50 text-indigo-300 rounded text-xs border border-indigo-700">
              Gare
            </span>
          )}
          {session.isPlugged && (
            <span className="px-2 py-0.5 bg-orange-900/50 text-orange-300 rounded text-xs border border-orange-700">
              Branche
            </span>
          )}
        </div>

        {/* Metriques si en charge ou connecte */}
        {['CHARGING', 'CONNECTED', 'PREPARING', 'SUSPENDED'].includes(session.state) && (
          <div className="grid grid-cols-2 gap-2 mb-3">
            <div className="flex items-center">
              <Battery className="w-4 h-4 mr-1 text-green-400" />
              <span className="text-sm text-white">{session.metrics.soc.toFixed(1)}%</span>
            </div>
            {session.state === 'CHARGING' && (
              <div className="flex items-center">
                <Zap className="w-4 h-4 mr-1 text-yellow-400" />
                <span className="text-sm text-white">{session.metrics.activePowerKw.toFixed(1)} kW</span>
              </div>
            )}
          </div>
        )}

        {/* Transaction ID si en charge */}
        {session.transactionId && (
          <p className="text-xs text-gray-500 mb-2">
            TX: {session.transactionId}
          </p>
        )}

        {/* Erreur */}
        {session.state === 'ERROR' && session.lastError && (
          <p className="text-xs text-red-400 mb-2 truncate" title={session.lastError}>
            {session.lastError}
          </p>
        )}

        {/* Actions */}
        <div className="flex space-x-2 mt-3 pt-3 border-t border-gray-700">
          {canConnect && (
            <button
              onClick={handleConnect}
              className="flex-1 px-3 py-1.5 bg-green-600 hover:bg-green-700 rounded text-sm font-medium text-white flex items-center justify-center"
            >
              <Power className="w-4 h-4 mr-1" />
              Connecter
            </button>
          )}

          {canDisconnect && (
            <button
              onClick={handleDisconnect}
              className="flex-1 px-3 py-1.5 bg-red-600 hover:bg-red-700 rounded text-sm font-medium text-white flex items-center justify-center"
            >
              <XCircle className="w-4 h-4 mr-1" />
              Deconnecter
            </button>
          )}

          {!isActive && (
            <button
              onClick={handleRemove}
              className="px-3 py-1.5 bg-gray-600 hover:bg-gray-500 rounded text-sm text-white"
              title="Supprimer"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default SessionCard;
