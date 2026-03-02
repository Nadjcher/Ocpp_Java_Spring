// frontend/src/types/lifecycle.types.ts
// Types pour les evenements du cycle de vie d'une session de charge

import type { MultiSessionStatus } from '@/store/multiSessionStore';

/**
 * Type d'evenement dans le cycle de vie d'une session
 */
export type LifecycleEventType =
  | 'status_change'    // Changement d'etat (DISCONNECTED -> CONNECTED, etc.)
  | 'ocpp_sent'        // Message OCPP envoye (CP -> CSMS)
  | 'ocpp_received'    // Message OCPP recu (CSMS -> CP)
  | 'error'            // Erreur
  | 'physical_action'  // Action physique (park, plug, unplug)
  | 'config_change'    // Changement de configuration (OCPP ID change, etc.)
  | 'metric_update';   // Mise a jour de metrique (SoC, energie, etc.)

/**
 * Severite d'un evenement
 */
export type EventSeverity = 'info' | 'warn' | 'error' | 'success';

/**
 * Evenement du cycle de vie d'une session
 */
export interface SessionLifecycleEvent {
  id: string;                           // ID unique de l'evenement
  timestamp: number;                    // Timestamp Unix ms
  eventType: LifecycleEventType;        // Type d'evenement
  severity: EventSeverity;              // Severite

  // Session
  sessionId: string;                    // ID de la session
  ocppId: string;                       // OCPP Charge Point ID au moment de l'evenement

  // Status change
  previousStatus?: MultiSessionStatus;  // Statut precedent
  newStatus?: MultiSessionStatus;       // Nouveau statut

  // OCPP message
  ocppAction?: string;                  // Action OCPP (BootNotification, StartTransaction, etc.)
  ocppPayload?: unknown;                // Payload brut JSON
  ocppMessageId?: string;               // Message ID OCPP

  // Details
  summary: string;                      // Resume lisible
  details?: Record<string, unknown>;    // Details additionnels (connectorId, energy, etc.)

  // Physical
  connectorId?: number;                 // Connecteur concerne
  transactionId?: string;               // ID de transaction
  energy?: number;                      // Energie en kWh
  soc?: number;                         // SoC en %
  power?: number;                       // Puissance en kW
  reason?: string;                      // Raison (stop, erreur, etc.)
}

/**
 * Filtres pour la timeline
 */
export interface LifecycleFilters {
  ocppId?: string;                      // Filtrer par OCPP ID
  eventTypes?: LifecycleEventType[];    // Filtrer par type d'evenement
  severities?: EventSeverity[];         // Filtrer par severite
  statuses?: MultiSessionStatus[];      // Filtrer par statut
  searchText?: string;                  // Recherche texte libre
  timeRange?: {                         // Plage de temps
    from: number;
    to: number;
  };
}

/**
 * Creer un evenement de cycle de vie
 */
export function createLifecycleEvent(
  sessionId: string,
  ocppId: string,
  eventType: LifecycleEventType,
  summary: string,
  overrides?: Partial<SessionLifecycleEvent>
): SessionLifecycleEvent {
  return {
    id: `evt-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
    timestamp: Date.now(),
    eventType,
    severity: eventType === 'error' ? 'error' : 'info',
    sessionId,
    ocppId,
    summary,
    ...overrides,
  };
}

/**
 * Configuration des couleurs par type d'evenement
 */
export const EVENT_TYPE_COLORS: Record<LifecycleEventType, {
  bg: string;
  text: string;
  border: string;
  dot: string;
  label: string;
}> = {
  status_change: {
    bg: 'bg-blue-50',
    text: 'text-blue-700',
    border: 'border-blue-200',
    dot: 'bg-blue-500',
    label: 'Statut',
  },
  ocpp_sent: {
    bg: 'bg-indigo-50',
    text: 'text-indigo-700',
    border: 'border-indigo-200',
    dot: 'bg-indigo-500',
    label: 'OCPP Sent',
  },
  ocpp_received: {
    bg: 'bg-purple-50',
    text: 'text-purple-700',
    border: 'border-purple-200',
    dot: 'bg-purple-500',
    label: 'OCPP Recv',
  },
  error: {
    bg: 'bg-red-50',
    text: 'text-red-700',
    border: 'border-red-200',
    dot: 'bg-red-500',
    label: 'Erreur',
  },
  physical_action: {
    bg: 'bg-orange-50',
    text: 'text-orange-700',
    border: 'border-orange-200',
    dot: 'bg-orange-500',
    label: 'Action',
  },
  config_change: {
    bg: 'bg-yellow-50',
    text: 'text-yellow-700',
    border: 'border-yellow-200',
    dot: 'bg-yellow-500',
    label: 'Config',
  },
  metric_update: {
    bg: 'bg-green-50',
    text: 'text-green-700',
    border: 'border-green-200',
    dot: 'bg-green-500',
    label: 'Metrique',
  },
};

/**
 * Configuration des couleurs par severite
 */
export const SEVERITY_COLORS: Record<EventSeverity, {
  bg: string;
  text: string;
  border: string;
}> = {
  info: { bg: 'bg-gray-50', text: 'text-gray-700', border: 'border-gray-200' },
  warn: { bg: 'bg-yellow-50', text: 'text-yellow-700', border: 'border-yellow-200' },
  error: { bg: 'bg-red-50', text: 'text-red-700', border: 'border-red-300' },
  success: { bg: 'bg-green-50', text: 'text-green-700', border: 'border-green-200' },
};

/**
 * Configuration des couleurs par statut de session (pour le badge)
 */
export const SESSION_STATUS_INDICATOR: Record<MultiSessionStatus, {
  color: string;
  pulse: boolean;
  label: string;
}> = {
  NOT_CREATED: { color: 'bg-gray-400', pulse: false, label: 'Non cree' },
  CONFIGURED: { color: 'bg-blue-400', pulse: false, label: 'Configure' },
  CONNECTING: { color: 'bg-yellow-400', pulse: true, label: 'Connexion...' },
  CONNECTED: { color: 'bg-green-400', pulse: false, label: 'Connecte' },
  PREPARING: { color: 'bg-cyan-400', pulse: true, label: 'Preparation' },
  CHARGING: { color: 'bg-emerald-500', pulse: true, label: 'En charge' },
  SUSPENDED: { color: 'bg-orange-400', pulse: false, label: 'Suspendu' },
  FINISHING: { color: 'bg-purple-400', pulse: true, label: 'Finalisation' },
  DISCONNECTED: { color: 'bg-gray-500', pulse: false, label: 'Deconnecte' },
  ERROR: { color: 'bg-red-500', pulse: false, label: 'Erreur' },
};

/**
 * Appliquer les filtres a une liste d'evenements
 */
export function filterLifecycleEvents(
  events: SessionLifecycleEvent[],
  filters: LifecycleFilters
): SessionLifecycleEvent[] {
  return events.filter(event => {
    // Filtre par OCPP ID
    if (filters.ocppId && event.ocppId !== filters.ocppId) {
      return false;
    }

    // Filtre par type d'evenement
    if (filters.eventTypes && filters.eventTypes.length > 0) {
      if (!filters.eventTypes.includes(event.eventType)) {
        return false;
      }
    }

    // Filtre par severite
    if (filters.severities && filters.severities.length > 0) {
      if (!filters.severities.includes(event.severity)) {
        return false;
      }
    }

    // Filtre par statut
    if (filters.statuses && filters.statuses.length > 0) {
      if (event.newStatus && !filters.statuses.includes(event.newStatus)) {
        return false;
      }
    }

    // Filtre par texte
    if (filters.searchText) {
      const search = filters.searchText.toLowerCase();
      const matchesSummary = event.summary.toLowerCase().includes(search);
      const matchesOcppAction = event.ocppAction?.toLowerCase().includes(search);
      const matchesOcppId = event.ocppId.toLowerCase().includes(search);
      const matchesReason = event.reason?.toLowerCase().includes(search);
      if (!matchesSummary && !matchesOcppAction && !matchesOcppId && !matchesReason) {
        return false;
      }
    }

    // Filtre par plage de temps
    if (filters.timeRange) {
      if (event.timestamp < filters.timeRange.from || event.timestamp > filters.timeRange.to) {
        return false;
      }
    }

    return true;
  });
}
