// frontend/src/hooks/useSessionController.ts
// Hook pour controler une session EVSE individuelle (OCPP 1.6)
// VERSION 2.0 - Isolation complete du contexte par session

import { useCallback, useRef, useEffect, useMemo } from 'react';
import { useMultiSessionStore, useSession, type MultiSessionStatus } from '@/store/multiSessionStore';
import wsPool from '@/services/WebSocketPool';
import { createLifecycleEvent } from '@/types/lifecycle.types';
import type { LifecycleEventType, EventSeverity } from '@/types/lifecycle.types';

interface UseSessionControllerOptions {
  autoReconnect?: boolean;
  onStateChange?: (state: MultiSessionStatus) => void;
  onMessage?: (action: string, payload: any) => void;
}

// Map globale pour les intervalles de meter values par session
// IMPORTANT: En dehors du hook pour persister entre les re-renders
const meterIntervalsMap = new Map<string, ReturnType<typeof setInterval>>();

// Map globale pour les pending requests par session
const pendingRequestsMap = new Map<string, Map<string, {
  resolve: (value: any) => void;
  reject: (error: Error) => void;
  action: string;
}>>();

// Compteur de message ID par session
const messageIdCounters = new Map<string, number>();

export function useSessionController(sessionId: string, options: UseSessionControllerOptions = {}) {
  // IMPORTANT: Utiliser le selector reactif pour obtenir la session
  const session = useSession(sessionId);

  // Helper pour emettre un evenement lifecycle
  const emitLifecycleEvent = useCallback((
    eventType: LifecycleEventType,
    summary: string,
    overrides?: Partial<import('@/types/lifecycle.types').SessionLifecycleEvent>
  ) => {
    const store = useMultiSessionStore.getState();
    const currentSession = store.getSession(sessionId);
    const ocppId = currentSession?.config.chargePointId || sessionId;
    const event = createLifecycleEvent(sessionId, ocppId, eventType, summary, overrides);
    store.addLifecycleEvent(sessionId, event);
  }, [sessionId]);

  // Initialiser les structures pour cette session
  useEffect(() => {
    if (!pendingRequestsMap.has(sessionId)) {
      pendingRequestsMap.set(sessionId, new Map());
    }
    if (!messageIdCounters.has(sessionId)) {
      messageIdCounters.set(sessionId, 1);
    }

    return () => {
      // Cleanup au demontage
      const interval = meterIntervalsMap.get(sessionId);
      if (interval) {
        clearInterval(interval);
        meterIntervalsMap.delete(sessionId);
      }
    };
  }, [sessionId]);

  // Obtenir le message ID pour cette session specifique
  const getNextMessageId = useCallback(() => {
    const current = messageIdCounters.get(sessionId) || 1;
    messageIdCounters.set(sessionId, current + 1);
    return String(current);
  }, [sessionId]);

  // === HANDLERS WEBSOCKET ===

  const handleMessage = useCallback((sid: string, message: any) => {
    // IMPORTANT: Verifier que c'est bien pour cette session
    if (sid !== sessionId) return;

    const store = useMultiSessionStore.getState();
    const [type, messageId, ...rest] = message;
    const pendingRequests = pendingRequestsMap.get(sessionId);

    if (type === 3) {
      // CALLRESULT
      const [payload] = rest;
      const pending = pendingRequests?.get(messageId);
      if (pending) {
        store.addLog(sessionId, 'info', `<-- ${pending.action} Response`);
        emitLifecycleEvent('ocpp_received', `${pending.action} Response`, {
          ocppAction: pending.action,
          ocppPayload: payload,
          ocppMessageId: messageId,
          severity: 'info',
        });
        pending.resolve(payload);
        pendingRequests?.delete(messageId);
        handleCallResult(pending.action, payload);
      }
    } else if (type === 2) {
      // CALL from server
      const [action, payload] = rest;
      store.addLog(sessionId, 'info', `<-- CALL ${action}`);
      emitLifecycleEvent('ocpp_received', `CALL ${action} recu du serveur`, {
        ocppAction: action,
        ocppPayload: payload,
        ocppMessageId: messageId,
        severity: 'info',
      });
      handleServerCall(messageId, action, payload);
      options.onMessage?.(action, payload);
    } else if (type === 4) {
      // CALLERROR
      const [errorCode, errorDescription] = rest;
      store.addLog(sessionId, 'error', `<-- ERROR: ${errorCode} - ${errorDescription}`);
      emitLifecycleEvent('error', `Erreur OCPP: ${errorCode} - ${errorDescription}`, {
        ocppMessageId: messageId,
        reason: `${errorCode}: ${errorDescription}`,
        severity: 'error',
      });
      const pending = pendingRequests?.get(messageId);
      if (pending) {
        pending.reject(new Error(`${errorCode}: ${errorDescription}`));
        pendingRequests?.delete(messageId);
      }
    }
  }, [sessionId, options]);

  const handleStateChange = useCallback((sid: string, state: 'open' | 'close' | 'error', data?: any) => {
    // IMPORTANT: Verifier que c'est bien pour cette session
    if (sid !== sessionId) return;

    const store = useMultiSessionStore.getState();

    switch (state) {
      case 'open':
        store.updateSessionState(sessionId, 'CONNECTED');
        store.addLog(sessionId, 'info', 'WebSocket connecte');
        emitLifecycleEvent('status_change', 'WebSocket connecte', {
          severity: 'success',
        });
        options.onStateChange?.('CONNECTED');
        // Envoyer BootNotification automatiquement
        sendBootNotification();
        break;
      case 'close':
        store.updateSessionState(sessionId, 'DISCONNECTED');
        store.addLog(sessionId, 'info', `WebSocket ferme (${data?.code || 'unknown'})`);
        emitLifecycleEvent('status_change', `WebSocket ferme (code: ${data?.code || 'unknown'})`, {
          severity: 'warn',
          reason: `Close code: ${data?.code || 'unknown'}`,
        });
        stopMeterValues();
        options.onStateChange?.('DISCONNECTED');
        break;
      case 'error':
        store.updateSessionState(sessionId, 'ERROR');
        store.updateSession(sessionId, { lastError: data?.message || 'Erreur WebSocket' });
        store.addLog(sessionId, 'error', `Erreur: ${data?.message || 'Inconnue'}`);
        emitLifecycleEvent('error', `Erreur WebSocket: ${data?.message || 'Inconnue'}`, {
          severity: 'error',
          reason: data?.message || 'Erreur WebSocket inconnue',
        });
        options.onStateChange?.('ERROR');
        break;
    }
  }, [sessionId, options]);

  // === ACTIONS PUBLIQUES ===

  const connect = useCallback(() => {
    const store = useMultiSessionStore.getState();
    const currentSession = store.getSession(sessionId);
    if (!currentSession) {
      console.error(`[SessionController] Session ${sessionId} non trouvee`);
      return;
    }

    store.updateSessionState(sessionId, 'CONNECTING');
    store.addLog(sessionId, 'info', `Connexion a ${currentSession.config.url}...`);
    emitLifecycleEvent('status_change', `Connexion a ${currentSession.config.url}`, {
      details: { url: currentSession.config.url, chargePointId: currentSession.config.chargePointId },
    });

    wsPool.connect(
      sessionId,
      currentSession.config.url,
      currentSession.config.chargePointId,
      handleMessage,
      handleStateChange
    );
  }, [sessionId, handleMessage, handleStateChange, emitLifecycleEvent]);

  const disconnect = useCallback(() => {
    const store = useMultiSessionStore.getState();
    store.addLog(sessionId, 'info', 'Deconnexion...');
    emitLifecycleEvent('status_change', 'Deconnexion manuelle', {
      severity: 'warn',
      reason: 'Deconnexion utilisateur',
    });
    stopMeterValues();
    wsPool.disconnectSession(sessionId, true);
    store.updateSessionState(sessionId, 'DISCONNECTED');
  }, [sessionId, emitLifecycleEvent]);

  const remove = useCallback(() => {
    disconnect();
    useMultiSessionStore.getState().removeSession(sessionId);
  }, [sessionId, disconnect]);

  // === MESSAGES OCPP ===

  const sendOCPP = useCallback((action: string, payload: any): Promise<any> => {
    return new Promise((resolve, reject) => {
      const store = useMultiSessionStore.getState();
      const msgId = getNextMessageId();
      const message = [2, msgId, action, payload];

      // Obtenir la map des pending requests pour cette session
      let pendingRequests = pendingRequestsMap.get(sessionId);
      if (!pendingRequests) {
        pendingRequests = new Map();
        pendingRequestsMap.set(sessionId, pendingRequests);
      }

      pendingRequests.set(msgId, { resolve, reject, action });
      store.addLog(sessionId, 'info', `--> ${action}`);

      // Emettre un evenement lifecycle pour le message envoye
      // (sauf MeterValues et Heartbeat pour eviter le bruit)
      if (action !== 'MeterValues' && action !== 'Heartbeat') {
        emitLifecycleEvent('ocpp_sent', `${action} envoye`, {
          ocppAction: action,
          ocppPayload: payload,
          ocppMessageId: msgId,
          severity: 'info',
        });
      }

      if (!wsPool.send(sessionId, message)) {
        pendingRequests.delete(msgId);
        reject(new Error('WebSocket non connecte'));
        return;
      }

      // Timeout
      setTimeout(() => {
        const pr = pendingRequestsMap.get(sessionId);
        if (pr?.has(msgId)) {
          pr.delete(msgId);
          reject(new Error('Timeout'));
        }
      }, 30000);
    });
  }, [sessionId, getNextMessageId, emitLifecycleEvent]);

  const sendBootNotification = useCallback(async () => {
    const store = useMultiSessionStore.getState();
    try {
      const response = await sendOCPP('BootNotification', {
        chargePointVendor: 'EVSE Simulator',
        chargePointModel: 'Virtual v2.0',
        chargePointSerialNumber: sessionId.substring(0, 10),
        firmwareVersion: '2.0.0',
      });

      if (response.status === 'Accepted') {
        store.updateSession(sessionId, { bootAccepted: true });
        store.addLog(sessionId, 'info', 'BootNotification accepte');
        emitLifecycleEvent('ocpp_received', `BootNotification ${response.status}`, {
          ocppAction: 'BootNotification',
          severity: 'success',
          details: { interval: response.interval, currentTime: response.currentTime },
        });

        // Envoyer StatusNotification
        await sendOCPP('StatusNotification', {
          connectorId: 0,
          errorCode: 'NoError',
          status: 'Available',
        });
      } else {
        emitLifecycleEvent('ocpp_received', `BootNotification ${response.status}`, {
          ocppAction: 'BootNotification',
          severity: response.status === 'Rejected' ? 'error' : 'warn',
        });
      }
    } catch (error) {
      store.addLog(sessionId, 'error', `BootNotification echoue: ${error}`);
      emitLifecycleEvent('error', `BootNotification echoue: ${error}`, {
        ocppAction: 'BootNotification',
        severity: 'error',
        reason: String(error),
      });
    }
  }, [sessionId, sendOCPP, emitLifecycleEvent]);

  const authorize = useCallback(async () => {
    const store = useMultiSessionStore.getState();
    const currentSession = store.getSession(sessionId);
    if (!currentSession) return false;

    store.updateSessionState(sessionId, 'PREPARING');

    try {
      const response = await sendOCPP('Authorize', {
        idTag: currentSession.config.idTag,
      });

      if (response.idTagInfo?.status === 'Accepted') {
        store.updateSession(sessionId, { authorized: true });
        store.addLog(sessionId, 'info', 'Authorize accepte');
        emitLifecycleEvent('ocpp_received', `Authorize Accepted (idTag: ${currentSession.config.idTag})`, {
          ocppAction: 'Authorize',
          severity: 'success',
          details: { idTag: currentSession.config.idTag, idTagInfo: response.idTagInfo },
        });
        return true;
      } else {
        store.addLog(sessionId, 'warn', `Authorize refuse: ${response.idTagInfo?.status}`);
        emitLifecycleEvent('ocpp_received', `Authorize refuse: ${response.idTagInfo?.status}`, {
          ocppAction: 'Authorize',
          severity: 'error',
          reason: response.idTagInfo?.status,
        });
        return false;
      }
    } catch (error) {
      store.addLog(sessionId, 'error', `Authorize echoue: ${error}`);
      emitLifecycleEvent('error', `Authorize echoue: ${error}`, {
        ocppAction: 'Authorize',
        severity: 'error',
        reason: String(error),
      });
      return false;
    }
  }, [sessionId, sendOCPP, emitLifecycleEvent]);

  const startTransaction = useCallback(async () => {
    const store = useMultiSessionStore.getState();
    const currentSession = store.getSession(sessionId);
    if (!currentSession) return false;

    // Autoriser d'abord si necessaire
    if (!currentSession.authorized) {
      const authOk = await authorize();
      if (!authOk) return false;
    }

    // Re-obtenir la session apres authorize
    const updatedSession = store.getSession(sessionId);
    if (!updatedSession) return false;

    try {
      const response = await sendOCPP('StartTransaction', {
        connectorId: updatedSession.config.connectorId,
        idTag: updatedSession.config.idTag,
        meterStart: Math.round(updatedSession.metrics.energyKwh * 1000),
        timestamp: new Date().toISOString(),
      });

      if (response.idTagInfo?.status === 'Accepted' && response.transactionId) {
        store.updateSession(sessionId, { transactionId: String(response.transactionId) });
        store.updateSessionState(sessionId, 'CHARGING');
        store.addLog(sessionId, 'info', `Transaction demarree (ID: ${response.transactionId})`);
        emitLifecycleEvent('ocpp_received', `Transaction demarree (ID: ${response.transactionId})`, {
          ocppAction: 'StartTransaction',
          severity: 'success',
          transactionId: String(response.transactionId),
          connectorId: updatedSession.config.connectorId,
          details: { idTag: updatedSession.config.idTag, meterStart: updatedSession.metrics.energyKwh },
        });
        startMeterValues();
        return true;
      }
      emitLifecycleEvent('ocpp_received', `StartTransaction refuse: ${response.idTagInfo?.status}`, {
        ocppAction: 'StartTransaction',
        severity: 'error',
        reason: response.idTagInfo?.status,
      });
      return false;
    } catch (error) {
      store.addLog(sessionId, 'error', `StartTransaction echoue: ${error}`);
      emitLifecycleEvent('error', `StartTransaction echoue: ${error}`, {
        ocppAction: 'StartTransaction',
        severity: 'error',
        reason: String(error),
      });
      return false;
    }
  }, [sessionId, sendOCPP, authorize, emitLifecycleEvent]);

  const stopTransaction = useCallback(async () => {
    const store = useMultiSessionStore.getState();
    const currentSession = store.getSession(sessionId);
    if (!currentSession?.transactionId) return false;

    store.updateSessionState(sessionId, 'FINISHING');
    stopMeterValues();

    try {
      await sendOCPP('StopTransaction', {
        transactionId: parseInt(currentSession.transactionId),
        meterStop: Math.round(currentSession.metrics.energyKwh * 1000),
        timestamp: new Date().toISOString(),
        reason: 'Local',
      });

      store.updateSession(sessionId, { transactionId: null, authorized: false });
      store.updateSessionState(sessionId, 'CONNECTED');
      store.addLog(sessionId, 'info', 'Transaction arretee');
      emitLifecycleEvent('ocpp_received', `Transaction arretee (ID: ${currentSession.transactionId})`, {
        ocppAction: 'StopTransaction',
        severity: 'info',
        transactionId: currentSession.transactionId,
        energy: currentSession.metrics.energyKwh,
        soc: currentSession.metrics.soc,
        reason: 'Local',
      });
      return true;
    } catch (error) {
      store.addLog(sessionId, 'error', `StopTransaction echoue: ${error}`);
      emitLifecycleEvent('error', `StopTransaction echoue: ${error}`, {
        ocppAction: 'StopTransaction',
        severity: 'error',
        reason: String(error),
      });
      return false;
    }
  }, [sessionId, sendOCPP, emitLifecycleEvent]);

  // === METER VALUES ===

  const stopMeterValues = useCallback(() => {
    const interval = meterIntervalsMap.get(sessionId);
    if (interval) {
      clearInterval(interval);
      meterIntervalsMap.delete(sessionId);
    }
  }, [sessionId]);

  const startMeterValues = useCallback(() => {
    // Verifier si deja en cours pour cette session
    if (meterIntervalsMap.has(sessionId)) return;

    const intervalId = setInterval(() => {
      const store = useMultiSessionStore.getState();
      const currentSession = store.getSession(sessionId);

      if (!currentSession || currentSession.state !== 'CHARGING') {
        stopMeterValues();
        return;
      }

      // Simuler progression
      const newSoc = Math.min(currentSession.metrics.socTarget, currentSession.metrics.soc + 0.2);
      const power = currentSession.config.maxPowerKw * 0.8 + Math.random() * currentSession.config.maxPowerKw * 0.2;
      const energy = currentSession.metrics.energyKwh + (power / 360); // Wh par 10s

      store.updateSessionMetrics(sessionId, {
        soc: newSoc,
        activePowerKw: power,
        energyKwh: energy,
      });

      store.addChartPoint(sessionId, { soc: newSoc, power, energy });

      // Envoyer MeterValues OCPP
      sendOCPP('MeterValues', {
        connectorId: currentSession.config.connectorId,
        transactionId: parseInt(currentSession.transactionId || '0'),
        meterValue: [{
          timestamp: new Date().toISOString(),
          sampledValue: [
            { value: String(newSoc), context: 'Sample.Periodic', measurand: 'SoC', unit: 'Percent' },
            { value: String(Math.round(power * 1000)), context: 'Sample.Periodic', measurand: 'Power.Active.Import', unit: 'W' },
            { value: String(Math.round(energy * 1000)), context: 'Sample.Periodic', measurand: 'Energy.Active.Import.Register', unit: 'Wh' },
          ],
        }],
      }).catch(() => {});

      // Arreter si SoC cible atteint
      if (newSoc >= currentSession.metrics.socTarget) {
        stopTransaction();
      }
    }, 10000); // Toutes les 10 secondes

    meterIntervalsMap.set(sessionId, intervalId);
  }, [sessionId, sendOCPP, stopMeterValues, stopTransaction]);

  // === HANDLE SERVER CALLS ===

  const handleServerCall = useCallback((messageId: string, action: string, payload: any) => {
    const store = useMultiSessionStore.getState();
    let response: any = { status: 'Accepted' };

    switch (action) {
      case 'SetChargingProfile':
        const limit = payload.csChargingProfiles?.chargingSchedule?.chargingSchedulePeriod?.[0]?.limit || 0;
        store.addLog(sessionId, 'info', `SCP recu: ${limit} ${payload.csChargingProfiles?.chargingSchedule?.chargingRateUnit || 'W'}`);
        emitLifecycleEvent('ocpp_received', `SetChargingProfile: ${limit} ${payload.csChargingProfiles?.chargingSchedule?.chargingRateUnit || 'W'}`, {
          ocppAction: 'SetChargingProfile',
          ocppPayload: payload,
          severity: 'info',
          power: limit / 1000,
        });
        // Mettre a jour offeredPower
        const currentSession = store.getSession(sessionId);
        if (currentSession) {
          const powerKw = payload.csChargingProfiles?.chargingSchedule?.chargingRateUnit === 'A'
            ? (limit * currentSession.metrics.voltage * Math.sqrt(3)) / 1000
            : limit / 1000;
          store.updateSessionMetrics(sessionId, { offeredPowerKw: powerKw });
        }
        break;

      case 'RemoteStartTransaction':
        emitLifecycleEvent('ocpp_received', 'RemoteStartTransaction recu', {
          ocppAction: 'RemoteStartTransaction',
          ocppPayload: payload,
          severity: 'info',
        });
        // Demarrer une transaction a distance
        authorize().then((authOk) => {
          if (authOk) startTransaction();
        });
        break;

      case 'RemoteStopTransaction':
        emitLifecycleEvent('ocpp_received', 'RemoteStopTransaction recu', {
          ocppAction: 'RemoteStopTransaction',
          ocppPayload: payload,
          severity: 'warn',
        });
        stopTransaction();
        break;

      case 'GetConfiguration':
        response = {
          configurationKey: [
            { key: 'HeartbeatInterval', readonly: false, value: '60' },
            { key: 'MeterValueSampleInterval', readonly: false, value: '10' },
          ],
        };
        break;

      case 'ChangeConfiguration':
        emitLifecycleEvent('config_change', `ChangeConfiguration: ${payload.key} = ${payload.value}`, {
          ocppAction: 'ChangeConfiguration',
          ocppPayload: payload,
          severity: 'info',
        });
        response = { status: 'Accepted' };
        break;

      case 'ClearChargingProfile':
        emitLifecycleEvent('ocpp_received', 'ClearChargingProfile recu', {
          ocppAction: 'ClearChargingProfile',
          ocppPayload: payload,
          severity: 'info',
        });
        response = { status: 'Accepted' };
        break;

      default:
        response = { status: 'NotSupported' };
        store.addLog(sessionId, 'warn', `Action non supportee: ${action}`);
        emitLifecycleEvent('ocpp_received', `Action non supportee: ${action}`, {
          ocppAction: action,
          severity: 'warn',
        });
    }

    // Envoyer la reponse
    wsPool.send(sessionId, [3, messageId, response]);
  }, [sessionId, authorize, startTransaction, stopTransaction, emitLifecycleEvent]);

  const handleCallResult = useCallback((action: string, payload: any) => {
    // Traitement specifique par action si necessaire
    options.onMessage?.(action, payload);
  }, [options]);

  // Actions physiques (park, plug)
  const park = useCallback(() => {
    const store = useMultiSessionStore.getState();
    store.updateSession(sessionId, { isParked: true });
    store.addLog(sessionId, 'info', 'Vehicule gare');
    emitLifecycleEvent('physical_action', 'Vehicule gare', { severity: 'info' });
  }, [sessionId, emitLifecycleEvent]);

  const unpark = useCallback(() => {
    const store = useMultiSessionStore.getState();
    store.updateSession(sessionId, { isParked: false, isPlugged: false });
    store.addLog(sessionId, 'info', 'Vehicule parti');
    emitLifecycleEvent('physical_action', 'Vehicule parti', { severity: 'info' });
  }, [sessionId, emitLifecycleEvent]);

  const plug = useCallback(() => {
    const store = useMultiSessionStore.getState();
    const currentSession = store.sessions.get(sessionId);
    store.updateSession(sessionId, { isPlugged: true });
    store.addLog(sessionId, 'info', 'Cable branche');
    emitLifecycleEvent('physical_action', 'Cable branche', {
      severity: 'info',
      connectorId: currentSession?.config?.connectorId || 1,
    });
    // Envoyer StatusNotification avec le connectorId de la session
    sendOCPP('StatusNotification', {
      connectorId: currentSession?.config?.connectorId || 1,
      errorCode: 'NoError',
      status: 'Preparing',
    }).catch(() => {});
  }, [sessionId, sendOCPP, emitLifecycleEvent]);

  const unplug = useCallback(() => {
    const store = useMultiSessionStore.getState();
    const currentSession = store.sessions.get(sessionId);
    store.updateSession(sessionId, { isPlugged: false });
    store.addLog(sessionId, 'info', 'Cable debranche');
    emitLifecycleEvent('physical_action', 'Cable debranche', {
      severity: 'info',
      connectorId: currentSession?.config?.connectorId || 1,
    });
    // Envoyer StatusNotification avec le connectorId de la session
    sendOCPP('StatusNotification', {
      connectorId: currentSession?.config?.connectorId || 1,
      errorCode: 'NoError',
      status: 'Available',
    }).catch(() => {});
  }, [sessionId, sendOCPP, emitLifecycleEvent]);

  // Cleanup au demontage du hook
  useEffect(() => {
    return () => {
      stopMeterValues();
      // Nettoyer les pending requests pour cette session
      pendingRequestsMap.delete(sessionId);
      messageIdCounters.delete(sessionId);
    };
  }, [sessionId, stopMeterValues]);

  return {
    // Session courante (reactive)
    session,

    // Connexion
    connect,
    disconnect,
    remove,
    isConnected: () => wsPool.isConnected(sessionId),

    // OCPP
    sendOCPP,
    authorize,
    startTransaction,
    stopTransaction,

    // Actions physiques
    park,
    unpark,
    plug,
    unplug,
  };
}

export default useSessionController;
