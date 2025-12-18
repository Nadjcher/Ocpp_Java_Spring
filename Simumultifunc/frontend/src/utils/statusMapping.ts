// frontend/src/utils/statusMapping.ts
// Mapping des statuts OCPP backend <-> frontend

/**
 * Statuts OCPP 1.6 du backend (StatusNotification)
 */
export type BackendStatus =
  | 'Available' | 'Preparing' | 'Charging' | 'SuspendedEV'
  | 'SuspendedEVSE' | 'Finishing' | 'Reserved' | 'Unavailable' | 'Faulted';

/**
 * Statuts frontend pour l'affichage UI
 * Inclut les états du backend SessionState (parked, plugged, started, etc.)
 */
export type FrontendStatus =
  | 'disconnected' | 'connecting' | 'connected' | 'available'
  | 'preparing' | 'authorized' | 'charging' | 'finishing'
  | 'reserved' | 'error' | 'closed'
  | 'booted' | 'parked' | 'plugged' | 'authorizing' | 'starting' | 'started' | 'stopping' | 'stopped';

/**
 * Convertit un statut backend vers un statut frontend
 */
export function mapBackendToFrontendStatus(
  backendStatus: string,
  isConnected: boolean,
  hasTransaction: boolean
): FrontendStatus {
  if (!isConnected) return 'disconnected';

  switch (backendStatus) {
    case 'Available':
      return 'available';
    case 'Preparing':
      return hasTransaction ? 'authorized' : 'preparing';
    case 'Charging':
      return 'charging';
    case 'Finishing':
      return 'finishing';
    case 'Reserved':
      return 'reserved';
    case 'SuspendedEV':
    case 'SuspendedEVSE':
      return 'charging'; // Toujours en session
    case 'Unavailable':
    case 'Faulted':
      return 'error';
    default:
      return isConnected ? 'connected' : 'disconnected';
  }
}

/**
 * Convertit un statut frontend vers un statut OCPP
 */
export function mapFrontendToOCPPStatus(frontendStatus: FrontendStatus): string {
  switch (frontendStatus) {
    case 'available': return 'Available';
    case 'preparing': return 'Preparing';
    case 'authorized': return 'Preparing';
    case 'charging': return 'Charging';
    case 'finishing': return 'Finishing';
    case 'reserved': return 'Reserved';
    case 'error': return 'Faulted';
    default: return 'Available';
  }
}

/**
 * Labels pour l'affichage UI
 */
export const STATUS_LABELS: Record<FrontendStatus, string> = {
  disconnected: 'Déconnecté',
  connecting: 'Connexion...',
  connected: 'Connecté',
  available: 'Disponible',
  preparing: 'Préparation',
  authorized: 'Autorisé',
  charging: 'En charge',
  finishing: 'Fin de charge',
  reserved: 'Réservé',
  error: 'Erreur',
  closed: 'Fermé',
  booted: 'Boot accepté',
  parked: 'Véhicule garé',
  plugged: 'Câble branché',
  authorizing: 'Autorisation...',
  starting: 'Démarrage...',
  started: 'En charge',
  stopping: 'Arrêt...',
  stopped: 'Arrêté'
};

/**
 * Couleurs pour l'affichage UI (Tailwind classes)
 */
export const STATUS_COLORS: Record<FrontendStatus, { bg: string; text: string; border: string }> = {
  disconnected: { bg: 'bg-gray-100', text: 'text-gray-700', border: 'border-gray-300' },
  connecting: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-300' },
  connected: { bg: 'bg-blue-100', text: 'text-blue-700', border: 'border-blue-300' },
  available: { bg: 'bg-green-100', text: 'text-green-700', border: 'border-green-300' },
  preparing: { bg: 'bg-orange-100', text: 'text-orange-700', border: 'border-orange-300' },
  authorized: { bg: 'bg-cyan-100', text: 'text-cyan-700', border: 'border-cyan-300' },
  charging: { bg: 'bg-emerald-100', text: 'text-emerald-700', border: 'border-emerald-300' },
  finishing: { bg: 'bg-purple-100', text: 'text-purple-700', border: 'border-purple-300' },
  reserved: { bg: 'bg-pink-100', text: 'text-pink-700', border: 'border-pink-300' },
  error: { bg: 'bg-red-100', text: 'text-red-700', border: 'border-red-300' },
  closed: { bg: 'bg-gray-100', text: 'text-gray-500', border: 'border-gray-200' },
  booted: { bg: 'bg-blue-100', text: 'text-blue-700', border: 'border-blue-300' },
  parked: { bg: 'bg-indigo-100', text: 'text-indigo-700', border: 'border-indigo-300' },
  plugged: { bg: 'bg-orange-100', text: 'text-orange-700', border: 'border-orange-300' },
  authorizing: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-300' },
  starting: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-300' },
  started: { bg: 'bg-emerald-100', text: 'text-emerald-700', border: 'border-emerald-300' },
  stopping: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-300' },
  stopped: { bg: 'bg-purple-100', text: 'text-purple-700', border: 'border-purple-300' }
};

/**
 * Icônes par statut
 */
export function getStatusIcon(status: FrontendStatus, isCharging: boolean = false): string {
  if (isCharging) return '⚡';
  switch (status) {
    case 'available': return '✓';
    case 'preparing': return '🔌';
    case 'authorized': return '🔑';
    case 'charging': return '⚡';
    case 'finishing': return '🏁';
    case 'reserved': return '🎫';
    case 'error': return '⚠️';
    case 'disconnected': return '○';
    case 'connecting': return '◐';
    case 'booted': return '✓';
    case 'parked': return '🅿️';
    case 'plugged': return '🔌';
    case 'authorizing': return '🔑';
    case 'starting': return '▶️';
    case 'started': return '⚡';
    case 'stopping': return '⏸️';
    case 'stopped': return '⏹️';
    default: return '●';
  }
}
