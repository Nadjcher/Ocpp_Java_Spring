// frontend/src/components/session/SessionGrid.tsx
// Grille des sessions multi-session

import React from 'react';
import { Plus, RefreshCw, Trash2 } from 'lucide-react';
import { useMultiSessionStore } from '@/store/multiSessionStore';
import { SessionCard } from './SessionCard';

interface SessionGridProps {
  onConnect?: (sessionId: string) => void;
  onDisconnect?: (sessionId: string) => void;
}

export function SessionGrid({ onConnect, onDisconnect }: SessionGridProps) {
  const {
    sessions,
    activeSessionId,
    maxSessions,
    createSession,
    selectSession,
    removeSession,
    clearInactiveSessions,
    getConnectedSessions,
  } = useMultiSessionStore();

  const sessionList = Array.from(sessions.values()).sort((a, b) => a.index - b.index);
  const canAddMore = sessions.size < maxSessions;
  const connectedCount = getConnectedSessions().length;
  const inactiveCount = sessionList.filter(s =>
    ['NOT_CREATED', 'CONFIGURED', 'DISCONNECTED', 'ERROR'].includes(s.state)
  ).length;

  const handleCreateSession = () => {
    // Creer une nouvelle session SANS changer de page
    const newId = createSession();
    if (newId) {
      // Optionnel: selectionner la nouvelle session pour configuration
      selectSession(newId);
    }
  };

  const handleClearInactive = () => {
    if (window.confirm(`Supprimer ${inactiveCount} session(s) inactive(s) ?`)) {
      clearInactiveSessions();
    }
  };

  return (
    <div className="p-4 bg-gray-900 rounded-lg">
      {/* Header */}
      <div className="flex justify-between items-center mb-4">
        <div>
          <h2 className="text-xl font-bold text-white">
            Sessions
          </h2>
          <p className="text-sm text-gray-400">
            {connectedCount} connectee(s) / {sessions.size} totale(s)
          </p>
        </div>

        <div className="flex gap-2">
          {/* Bouton nettoyer */}
          {inactiveCount > 0 && (
            <button
              onClick={handleClearInactive}
              className="px-3 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg flex items-center gap-2 text-sm"
              title={`Supprimer ${inactiveCount} session(s) inactive(s)`}
            >
              <Trash2 className="w-4 h-4" />
              <span className="hidden sm:inline">Nettoyer ({inactiveCount})</span>
            </button>
          )}

          {/* Bouton nouvelle session */}
          <button
            onClick={handleCreateSession}
            disabled={!canAddMore}
            className={`px-4 py-2 rounded-lg font-medium flex items-center gap-2 ${
              canAddMore
                ? 'bg-blue-600 hover:bg-blue-700 text-white'
                : 'bg-gray-700 text-gray-500 cursor-not-allowed'
            }`}
          >
            <Plus className="w-5 h-5" />
            <span>Nouvelle Session</span>
            <span className="text-xs opacity-70">({sessions.size}/{maxSessions})</span>
          </button>
        </div>
      </div>

      {/* Grille des sessions */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {sessionList.map(session => (
          <SessionCard
            key={session.id}
            session={session}
            isSelected={session.id === activeSessionId}
            onSelect={() => selectSession(session.id)}
            onConnect={() => onConnect?.(session.id)}
            onDisconnect={() => onDisconnect?.(session.id)}
            onRemove={() => removeSession(session.id)}
          />
        ))}

        {/* Placeholder si aucune session */}
        {sessions.size === 0 && (
          <div className="col-span-full text-center py-12 bg-gray-800 rounded-lg border-2 border-dashed border-gray-700">
            <div className="text-gray-500 mb-4">
              <RefreshCw className="w-12 h-12 mx-auto opacity-50" />
            </div>
            <p className="text-lg text-gray-400 mb-4">Aucune session</p>
            <button
              onClick={handleCreateSession}
              className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium"
            >
              Creer ma premiere session
            </button>
          </div>
        )}

        {/* Placeholder pour ajouter */}
        {sessions.size > 0 && canAddMore && (
          <button
            onClick={handleCreateSession}
            className="flex flex-col items-center justify-center p-8 rounded-lg border-2 border-dashed border-gray-700 text-gray-500 hover:border-blue-600 hover:text-blue-400 hover:bg-gray-800/50 transition-all min-h-[200px]"
          >
            <Plus className="w-8 h-8 mb-2" />
            <span className="text-sm font-medium">Ajouter une session</span>
          </button>
        )}
      </div>

      {/* Info si limite atteinte */}
      {!canAddMore && (
        <p className="mt-4 text-center text-sm text-yellow-500">
          Limite de {maxSessions} sessions atteinte. Supprimez des sessions inactives pour en creer de nouvelles.
        </p>
      )}
    </div>
  );
}

export default SessionGrid;
