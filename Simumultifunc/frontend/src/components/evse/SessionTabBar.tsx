// frontend/src/components/evse/SessionTabBar.tsx
// Barre d'onglets pour la navigation entre sessions

import React from 'react';
import type { SessionState } from '@/types/multiSession.types';
import { STATUS_COLORS, STATUS_LABELS, getStatusIcon } from '@/utils/statusMapping';
import type { FrontendStatus } from '@/utils/statusMapping';

interface SessionTabBarProps {
  sessions: SessionState[];
  activeSessionId: string | null;
  onSelectSession: (id: string) => void;
  onAddSession: () => void;
  onCloseSession: (id: string) => void;
}

export function SessionTabBar({
  sessions,
  activeSessionId,
  onSelectSession,
  onAddSession,
  onCloseSession
}: SessionTabBarProps) {

  return (
    <div className="bg-white border-b shadow-sm">
      <div className="flex items-center gap-1 p-2 overflow-x-auto">
        {/* Onglets des sessions */}
        {sessions.map((session) => {
          const isActive = session.id === activeSessionId;
          const status = session.status as FrontendStatus;
          const colors = STATUS_COLORS[status] || STATUS_COLORS.disconnected;

          return (
            <div
              key={session.id}
              onClick={() => onSelectSession(session.id)}
              className={`
                relative flex items-center gap-2 px-4 py-2 rounded-t-lg cursor-pointer
                transition-all duration-200 min-w-[160px] max-w-[200px]
                ${isActive
                  ? `${colors.bg} ${colors.text} border-2 ${colors.border} border-b-0 -mb-[2px] z-10`
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200 border border-transparent'
                }
              `}
            >
              {/* Icône de statut */}
              <span className={`text-lg ${session.isCharging ? 'animate-pulse' : ''}`}>
                {getStatusIcon(status, session.isCharging)}
              </span>

              {/* Info session */}
              <div className="flex-1 min-w-0">
                <div className="font-medium text-sm truncate">
                  {session.cpId}
                </div>
                <div className="text-xs opacity-75 truncate">
                  {session.isTemporary ? 'Non connecté' : STATUS_LABELS[status]}
                </div>
              </div>

              {/* Bouton fermer */}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onCloseSession(session.id);
                }}
                className="p-1 rounded hover:bg-black/10 text-current opacity-50 hover:opacity-100"
                title="Fermer cette session"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>

              {/* Badge transaction */}
              {session.txId && (
                <span className="absolute -top-1 -right-1 px-1.5 py-0.5 bg-emerald-500 text-white text-[10px] rounded-full">
                  #{session.txId}
                </span>
              )}
            </div>
          );
        })}

        {/* Bouton Nouvelle Session */}
        <button
          onClick={onAddSession}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-50 text-blue-600
                     hover:bg-blue-100 border-2 border-dashed border-blue-300
                     transition-all duration-200 min-w-[140px]"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          <span className="font-medium text-sm">Nouvelle Session</span>
        </button>

        {/* Compteur */}
        <div className="ml-auto flex items-center gap-3 px-3 text-sm text-gray-500">
          <span>
            {sessions.length} session{sessions.length > 1 ? 's' : ''}
          </span>
          {sessions.filter(s => s.isCharging).length > 0 && (
            <span className="flex items-center gap-1 text-emerald-600">
              <span className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse"></span>
              {sessions.filter(s => s.isCharging).length} en charge
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

export default SessionTabBar;
