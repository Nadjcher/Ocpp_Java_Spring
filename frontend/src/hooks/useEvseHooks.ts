// frontend/src/hooks/useEvseHooks.ts
// Hooks extraits de SimuEvseTab.tsx pour modularisation

import { useState, useCallback } from 'react';
import type { Toast } from '@/types/evse.types';
import { fetchJSON } from '@/utils/evse.utils';

// =========================================================================
// Types pour les hooks
// =========================================================================

export interface ToastHook {
  items: Toast[];
  push: (text: string) => void;
}

export interface TNRHook {
  isRecording: boolean;
  recName: string;
  recId: string;
  recDescription: string;
  recTags: string;
  recBaseline: boolean;
  recEvents: number;
  setRecName: (name: string) => void;
  setRecId: (id: string) => void;
  setRecDescription: (description: string) => void;
  setRecTags: (tags: string) => void;
  setRecBaseline: (baseline: boolean) => void;
  startRecording: () => Promise<boolean>;
  stopRecording: () => Promise<boolean>;
  tapEvent: (type: string, action: string, payload: any, sessionId?: string) => Promise<boolean>;
  checkRecordingStatus: () => Promise<{ isRecording: boolean } | null>;
}

// =========================================================================
// Hook Toasts - Gestion des notifications toast
// =========================================================================

/**
 * Hook pour gérer les notifications toast
 * @returns Object avec items (liste des toasts) et push (fonction pour ajouter)
 */
export function useToasts(): ToastHook {
  const [items, setItems] = useState<Toast[]>([]);

  const push = useCallback((text: string) => {
    const id = Date.now() + Math.random();
    setItems((x) => [...x, { id, text }]);
    setTimeout(() => {
      setItems((x) => x.filter((t) => t.id !== id));
    }, 5000);
  }, []);

  return { items, push };
}

// =========================================================================
// Hook TNR - Test & Replay avec API backend
// =========================================================================

/**
 * Hook pour la fonctionnalité TNR (Test & Replay)
 * Permet d'enregistrer et rejouer des scénarios OCPP
 * @param toasts - Hook toast pour les notifications
 * @param defaultUrl - URL OCPP par défaut
 */
export const useTNRWithAPI = (toasts: ToastHook | null, defaultUrl: string): TNRHook => {
  const [isRecording, setIsRecording] = useState(false);
  const [recName, setRecName] = useState(`record-${Date.now()}`);
  const [recId, setRecId] = useState(`tnr_${Date.now()}`);
  const [recDescription, setRecDescription] = useState("");
  const [recTags, setRecTags] = useState("");
  const [recBaseline, setRecBaseline] = useState(false);
  const [recEvents, setRecEvents] = useState(0);

  const startRecording = async (): Promise<boolean> => {
    try {
      const tagsArray = recTags
        .split(',')
        .map(t => t.trim())
        .filter(Boolean);

      const response = await fetchJSON("/api/tnr/recorder/start", {
        method: "POST",
        body: JSON.stringify({
          name: recName,
          description: recDescription || `Enregistré depuis Simu EVSE le ${new Date().toLocaleString()}`,
          tags: ["simu-evse", ...tagsArray],
          folder: "simu-evse",
          config: {
            url: defaultUrl
          }
        })
      });

      if (response && response.ok !== false && !response.error) {
        setIsRecording(true);
        setRecEvents(0);
        console.log("TNR Recording started:", response);
        toasts?.push("Enregistrement TNR démarré");
        return true;
      } else {
        throw new Error(response?.error || "Erreur démarrage enregistrement");
      }
    } catch (error: any) {
      console.error("Failed to start TNR recording:", error);
      toasts?.push(`Erreur démarrage: ${error.message}`);
      setIsRecording(false);
      return false;
    }
  };

  const checkRecordingStatus = async (): Promise<{ isRecording: boolean } | null> => {
    try {
      const status = await fetchJSON("/api/tnr/status");
      if (status.isRecording !== isRecording) {
        console.warn(`TNR: État désynchronisé - local:${isRecording}, serveur:${status.isRecording}`);
        setIsRecording(status.isRecording);
      }
      return status;
    } catch (error) {
      console.error("Failed to check TNR status:", error);
      return null;
    }
  };

  const stopRecording = async (): Promise<boolean> => {
    const serverStatus = await checkRecordingStatus();

    if (!serverStatus?.isRecording && !isRecording) {
      console.warn("TNR: Aucun enregistrement actif");
      toasts?.push("Aucun enregistrement en cours");
      return false;
    }

    try {
      const tagsArray = recTags
        .split(',')
        .map(t => t.trim())
        .filter(Boolean);

      const timestamp = Date.now();
      const uniqueId = `tnr_${timestamp}_${Math.random().toString(36).slice(2, 8)}`;
      const uniqueName = recName || `Scenario_${new Date(timestamp).toISOString().replace(/[:.]/g, '-')}`;

      console.log(`[TNR] Stopping recording with ID: ${uniqueId}, Name: ${uniqueName}`);

      const response = await fetchJSON("/api/tnr/recorder/stop", {
        method: "POST",
        body: JSON.stringify({
          id: uniqueId,
          name: uniqueName,
          description: recDescription || `Enregistré depuis Simu EVSE le ${new Date().toLocaleString()}`,
          tags: ["simu-evse", ...tagsArray],
          folder: "simu-evse",
          baseline: recBaseline
        })
      });

      console.log(`[TNR] Stop response:`, response);

      if (response && response.ok !== false && !response.error) {
        setIsRecording(false);
        const savedId = response.id || uniqueId;
        console.log("TNR Recording saved:", savedId);
        toasts?.push(`Scénario sauvegardé: ${savedId}`);

        // Réinitialiser pour le prochain enregistrement
        const nextTimestamp = Date.now();
        setRecName(`record-${nextTimestamp}`);
        setRecId(`tnr_${nextTimestamp}`);
        setRecDescription("");
        setRecEvents(0);
        setRecTags("");
        setRecBaseline(false);

        // Notifier l'onglet TNR
        window.dispatchEvent(new CustomEvent('tnr-scenario-saved', {
          detail: {
            id: savedId,
            name: uniqueName,
            description: recDescription
          }
        }));

        return true;
      } else {
        throw new Error(response?.error || response?.message || "Erreur arrêt enregistrement");
      }
    } catch (error: any) {
      console.error("Failed to stop TNR recording:", error);
      setIsRecording(false);

      if (error.message?.includes("not recording")) {
        toasts?.push("Pas d'enregistrement actif côté serveur");
      } else {
        toasts?.push(`Erreur sauvegarde: ${error.message}`);
      }

      return false;
    }
  };

  const tapEvent = async (type: string, action: string, payload: any, sessionId?: string): Promise<boolean> => {
    if (!isRecording) {
      console.debug("TNR: tapEvent ignoré car pas d'enregistrement actif");
      return false;
    }

    try {
      const response = await fetchJSON("/api/tnr/tap", {
        method: "POST",
        body: JSON.stringify({
          type,
          action,
          sessionId: sessionId || "unknown",
          payload: payload || {},
          timestamp: Date.now()
        })
      });

      if (response && response.ok !== false) {
        setRecEvents(e => e + 1);
        console.debug(`TNR: Event tapped - ${type}/${action}`, payload);
        return true;
      } else {
        console.warn("TNR: tap event failed", response);
        return false;
      }
    } catch (error) {
      console.error("Failed to tap event:", error);
      return false;
    }
  };

  return {
    isRecording,
    recName,
    recId,
    recDescription,
    recTags,
    recBaseline,
    recEvents,
    setRecName,
    setRecId,
    setRecDescription,
    setRecTags,
    setRecBaseline,
    startRecording,
    stopRecording,
    tapEvent,
    checkRecordingStatus
  };
};
