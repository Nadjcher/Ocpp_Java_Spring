// frontend/src/components/session/MultiSessionDashboard.tsx
// Dashboard principal multi-session avec grille et details
// VERSION 2.0 - Correction isolation contexte + handlers memorises

import React, { useCallback, useEffect, useMemo } from 'react';
import { useMultiSessionStore, useSession } from '@/store/multiSessionStore';
import { SessionGrid } from './SessionGrid';
import { useSessionController } from '@/hooks/useSessionController';
import wsPool from '@/services/WebSocketPool';
import {
  Battery, Zap, Activity, Clock, Settings,
  Power, Play, Square, Plug, Car, Info
} from 'lucide-react';

// Hook pour controller toutes les sessions - avec handlers MEMORISES
function useMultiSessionController() {
  // Handler de message memorise - IMPORTANT: utilise getState() pour eviter les closures
  const handleMessage = useCallback((sessionId: string, message: any) => {
    // Traitement des messages OCPP
    const [type, messageId, ...rest] = message;
    if (type === 2) {
      const [action, payload] = rest;
      useMultiSessionStore.getState().addLog(sessionId, 'info', `<-- ${action}`);
      // Repondre aux appels du serveur
      wsPool.send(sessionId, [3, messageId, { status: 'Accepted' }]);
    }
  }, []);

  // Handler d'etat memorise
  const handleStateChange = useCallback((sessionId: string, state: 'open' | 'close' | 'error', data?: any) => {
    const store = useMultiSessionStore.getState();

    switch (state) {
      case 'open':
        store.updateSessionState(sessionId, 'CONNECTED');
        store.addLog(sessionId, 'info', 'WebSocket connecte');
        // Envoyer BootNotification
        const bootMsg = [2, `boot-${Date.now()}`, 'BootNotification', {
          chargePointVendor: 'EVSE Simulator',
          chargePointModel: 'Virtual v2.0',
        }];
        wsPool.send(sessionId, bootMsg);
        break;
      case 'close':
        store.updateSessionState(sessionId, 'DISCONNECTED');
        store.addLog(sessionId, 'info', 'WebSocket ferme');
        break;
      case 'error':
        store.updateSessionState(sessionId, 'ERROR');
        store.addLog(sessionId, 'error', `Erreur: ${data?.message || 'Inconnue'}`);
        break;
    }
  }, []);

  const connectSession = useCallback((sessionId: string) => {
    const session = useMultiSessionStore.getState().getSession(sessionId);
    if (!session) {
      console.error(`[MultiSessionController] Session ${sessionId} non trouvee`);
      return;
    }

    useMultiSessionStore.getState().updateSessionState(sessionId, 'CONNECTING');
    useMultiSessionStore.getState().addLog(sessionId, 'info', `Connexion a ${session.config.url}...`);

    // IMPORTANT: Passer les handlers memorises
    wsPool.connect(
      sessionId,
      session.config.url,
      session.config.chargePointId,
      handleMessage,
      handleStateChange
    );
  }, [handleMessage, handleStateChange]);

  const disconnectSession = useCallback((sessionId: string) => {
    useMultiSessionStore.getState().addLog(sessionId, 'info', 'Deconnexion...');
    wsPool.disconnectSession(sessionId, true);
    useMultiSessionStore.getState().updateSessionState(sessionId, 'DISCONNECTED');
  }, []);

  return { connectSession, disconnectSession };
}

// Composant de detail de session - utilise le selector reactif
function SessionDetail({ sessionId }: { sessionId: string }) {
  // IMPORTANT: Utiliser le selector reactif au lieu de getSession
  const session = useSession(sessionId);
  const controller = useSessionController(sessionId);

  if (!session) {
    return (
      <div className="flex items-center justify-center h-full text-gray-500">
        Session non trouvee
      </div>
    );
  }

  return (
    <div className="p-4 space-y-4">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h3 className="text-xl font-bold text-white flex items-center">
            <Car className="w-5 h-5 mr-2" />
            {session.config.chargePointId}
          </h3>
          <p className="text-sm text-gray-400">
            {session.config.vehicleId} | {session.config.evseType.replace('_', ' ')} | {session.config.maxPowerKw} kW max
          </p>
        </div>
        <div className="flex gap-2">
          {session.state === 'DISCONNECTED' || session.state === 'CONFIGURED' ? (
            <button
              onClick={controller.connect}
              className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg flex items-center"
            >
              <Power className="w-4 h-4 mr-2" />
              Connecter
            </button>
          ) : (
            <button
              onClick={controller.disconnect}
              className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg flex items-center"
            >
              <Power className="w-4 h-4 mr-2" />
              Deconnecter
            </button>
          )}
        </div>
      </div>

      {/* Metriques */}
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-gray-800 rounded-lg p-4">
          <div className="flex items-center text-gray-400 mb-1">
            <Battery className="w-4 h-4 mr-1" />
            <span className="text-sm">SoC</span>
          </div>
          <div className="text-2xl font-bold text-white">{session.metrics.soc.toFixed(1)}%</div>
          <div className="text-xs text-gray-500">Cible: {session.metrics.socTarget}%</div>
        </div>
        <div className="bg-gray-800 rounded-lg p-4">
          <div className="flex items-center text-gray-400 mb-1">
            <Zap className="w-4 h-4 mr-1" />
            <span className="text-sm">Puissance</span>
          </div>
          <div className="text-2xl font-bold text-white">{session.metrics.activePowerKw.toFixed(1)} kW</div>
          <div className="text-xs text-gray-500">Offert: {session.metrics.offeredPowerKw.toFixed(1)} kW</div>
        </div>
        <div className="bg-gray-800 rounded-lg p-4">
          <div className="flex items-center text-gray-400 mb-1">
            <Activity className="w-4 h-4 mr-1" />
            <span className="text-sm">Energie</span>
          </div>
          <div className="text-2xl font-bold text-white">{session.metrics.energyKwh.toFixed(2)} kWh</div>
        </div>
        <div className="bg-gray-800 rounded-lg p-4">
          <div className="flex items-center text-gray-400 mb-1">
            <Clock className="w-4 h-4 mr-1" />
            <span className="text-sm">Duree</span>
          </div>
          <div className="text-2xl font-bold text-white">
            {session.chargingStartedAt
              ? `${Math.floor((Date.now() - session.chargingStartedAt) / 60000)} min`
              : '--'}
          </div>
        </div>
      </div>

      {/* Actions physiques */}
      <div className="bg-gray-800 rounded-lg p-4">
        <h4 className="text-sm font-medium text-gray-400 mb-3">Actions physiques</h4>
        <div className="flex gap-3">
          <button
            onClick={controller.park}
            disabled={session.isParked || session.state === 'DISCONNECTED'}
            className={`px-4 py-2 rounded-lg flex items-center ${
              session.isParked
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-700 hover:bg-gray-600 text-gray-300'
            } ${session.state === 'DISCONNECTED' ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            <Car className="w-4 h-4 mr-2" />
            {session.isParked ? 'Gare' : 'Garer'}
          </button>
          <button
            onClick={controller.plug}
            disabled={!session.isParked || session.isPlugged || session.state === 'DISCONNECTED'}
            className={`px-4 py-2 rounded-lg flex items-center ${
              session.isPlugged
                ? 'bg-orange-600 text-white'
                : 'bg-gray-700 hover:bg-gray-600 text-gray-300'
            } ${(!session.isParked || session.state === 'DISCONNECTED') ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            <Plug className="w-4 h-4 mr-2" />
            {session.isPlugged ? 'Branche' : 'Brancher'}
          </button>
          <button
            onClick={controller.unplug}
            disabled={!session.isPlugged || session.state === 'CHARGING'}
            className={`px-4 py-2 rounded-lg flex items-center bg-gray-700 hover:bg-gray-600 text-gray-300 ${
              !session.isPlugged || session.state === 'CHARGING' ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            <Plug className="w-4 h-4 mr-2" />
            Debrancher
          </button>
          <button
            onClick={controller.unpark}
            disabled={session.isPlugged || !session.isParked}
            className={`px-4 py-2 rounded-lg flex items-center bg-gray-700 hover:bg-gray-600 text-gray-300 ${
              session.isPlugged || !session.isParked ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            <Car className="w-4 h-4 mr-2" />
            Partir
          </button>
        </div>
      </div>

      {/* Actions OCPP */}
      <div className="bg-gray-800 rounded-lg p-4">
        <h4 className="text-sm font-medium text-gray-400 mb-3">Actions OCPP</h4>
        <div className="flex gap-3">
          <button
            onClick={controller.authorize}
            disabled={session.state !== 'CONNECTED' || session.authorized}
            className={`px-4 py-2 rounded-lg flex items-center ${
              session.authorized
                ? 'bg-cyan-600 text-white'
                : 'bg-gray-700 hover:bg-gray-600 text-gray-300'
            } ${session.state !== 'CONNECTED' || session.authorized ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            <Info className="w-4 h-4 mr-2" />
            {session.authorized ? 'Autorise' : 'Authorize'}
          </button>
          <button
            onClick={controller.startTransaction}
            disabled={!session.authorized || session.state === 'CHARGING'}
            className={`px-4 py-2 rounded-lg flex items-center bg-green-600 hover:bg-green-700 text-white ${
              !session.authorized || session.state === 'CHARGING' ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            <Play className="w-4 h-4 mr-2" />
            Start Transaction
          </button>
          <button
            onClick={controller.stopTransaction}
            disabled={session.state !== 'CHARGING'}
            className={`px-4 py-2 rounded-lg flex items-center bg-red-600 hover:bg-red-700 text-white ${
              session.state !== 'CHARGING' ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          >
            <Square className="w-4 h-4 mr-2" />
            Stop Transaction
          </button>
        </div>
      </div>

      {/* Logs */}
      <div className="bg-gray-800 rounded-lg p-4">
        <h4 className="text-sm font-medium text-gray-400 mb-3">
          Logs ({session.logs.length})
        </h4>
        <div className="h-40 overflow-y-auto bg-gray-900 rounded p-2 font-mono text-xs">
          {session.logs.length === 0 ? (
            <p className="text-gray-500">Aucun log</p>
          ) : (
            session.logs.slice(-50).reverse().map((log, i) => (
              <div key={i} className={`${
                log.level === 'error' ? 'text-red-400' :
                log.level === 'warn' ? 'text-yellow-400' :
                'text-gray-300'
              }`}>
                <span className="text-gray-500">{new Date(log.timestamp).toLocaleTimeString()}</span>
                {' '}
                {log.message}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export function MultiSessionDashboard() {
  // IMPORTANT: Utiliser des selectors reactifs au lieu de getActiveSession()
  const activeSessionId = useMultiSessionStore(state => state.activeSessionId);
  const activeSession = useSession(activeSessionId);
  const { connectSession, disconnectSession } = useMultiSessionController();

  // Charger les sessions au montage
  useEffect(() => {
    useMultiSessionStore.getState().loadFromBackend();
  }, []);

  // Debug: afficher les connexions actives (en developpement)
  useEffect(() => {
    if (process.env.NODE_ENV === 'development') {
      const interval = setInterval(() => {
        const debugInfo = wsPool.getDebugInfo();
        if (debugInfo.length > 0) {
          console.log('[MultiSessionDashboard] Connexions actives:', debugInfo);
        }
      }, 30000);
      return () => clearInterval(interval);
    }
  }, []);

  return (
    <div className="h-full flex flex-col bg-gray-900">
      {/* Grille des sessions (partie superieure) */}
      <div className="flex-shrink-0 border-b border-gray-700 max-h-[50%] overflow-y-auto">
        <SessionGrid
          onConnect={connectSession}
          onDisconnect={disconnectSession}
        />
      </div>

      {/* Detail de la session selectionnee (partie inferieure) */}
      <div className="flex-1 overflow-y-auto">
        {activeSessionId && activeSession ? (
          <SessionDetail sessionId={activeSessionId} />
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-gray-500">
            <Settings className="w-12 h-12 mb-4 opacity-50" />
            <p className="text-lg">Selectionnez une session pour voir les details</p>
            <p className="text-sm mt-2">ou creez-en une nouvelle avec le bouton ci-dessus</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default MultiSessionDashboard;
