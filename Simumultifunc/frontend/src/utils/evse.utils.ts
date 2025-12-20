// frontend/src/utils/evse.utils.ts
// Utilitaires extraits de SimuEvseTab.tsx pour modularisation

import { API_BASE, DEFAULT_VEHICLES } from '@/constants/evse.constants';
import type { Vehicle } from '@/types/evse.types';

// =========================================================================
// Fonctions mathématiques
// =========================================================================

/**
 * Clamp une valeur entre min et max
 */
export const clamp = (n: number, a: number, b: number): number =>
  Math.max(a, Math.min(b, n));

/**
 * Retourne le timestamp actuel en millisecondes
 */
export const nowMs = (): number => Date.now();

/**
 * Calcule la moyenne mobile exponentielle (EWMA)
 * @param prev - Valeur précédente (null pour première valeur)
 * @param v - Nouvelle valeur
 * @param alpha - Facteur de lissage (défaut: 0.25)
 */
export const ewma = (prev: number | null, v: number, alpha = 0.25): number =>
  prev == null ? v : prev + alpha * (v - prev);

// =========================================================================
// Fonctions de formatage
// =========================================================================

/**
 * Formate un nombre de secondes en HH:MM:SS
 */
export const formatHMS = (s: number): string => {
  const h = Math.floor(s / 3600).toString().padStart(2, "0");
  const m = Math.floor((s % 3600) / 60).toString().padStart(2, "0");
  const ss = Math.floor(s % 60).toString().padStart(2, "0");
  return `${h}:${m}:${ss}`;
};

// =========================================================================
// Fonctions API
// =========================================================================

/**
 * Effectue une requête GET/POST avec parsing JSON automatique
 * @param path - Chemin relatif (ex: /api/simu)
 * @param init - Options fetch optionnelles
 */
export async function fetchJSON<T = any>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(`${API_BASE}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...(init || {}),
  });
  const txt = await r.text();
  try {
    return JSON.parse(txt) as T;
  } catch {
    return txt as any;
  }
}

/**
 * Tente de POST sur plusieurs chemins jusqu'à succès
 * @param paths - Liste de chemins à essayer
 * @returns true si au moins un a réussi
 */
export async function postAny(paths: string[]): Promise<boolean> {
  for (const p of paths) {
    try {
      const r = await fetch(`${API_BASE}${p}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      });
      if (r.ok) return true;
    } catch {
      // Continue avec le prochain chemin
    }
  }
  return false;
}

// =========================================================================
// Fonctions véhicules
// =========================================================================

/**
 * Charge les profils de véhicules depuis l'API
 * Note: Implémentation placeholder, retourne les véhicules par défaut
 */
export const loadVehicleProfiles = async (): Promise<Vehicle[]> => {
  // TODO: Implémenter le chargement depuis l'API backend
  // try {
  //   const vehicles = await fetchJSON<Vehicle[]>('/api/vehicles');
  //   return vehicles;
  // } catch {
  //   return DEFAULT_VEHICLES;
  // }
  return DEFAULT_VEHICLES;
};

/**
 * Retourne tous les véhicules disponibles
 */
export const getAllVehicles = (): Vehicle[] => DEFAULT_VEHICLES;

/**
 * Calcule la puissance de charge maximale d'un véhicule selon son SoC
 * Simule la courbe de charge typique des véhicules électriques
 * Utilise les caractéristiques réelles du véhicule sélectionné
 * @param vehicleId - ID du véhicule
 * @param soc - État de charge actuel (0-100)
 * @param isDC - True pour charge DC, False pour AC (défaut: true)
 * @returns Puissance maximale en kW
 */
export const calcVehPowerKW = (vehicleId: string, soc: number, isDC: boolean = true): number => {
  // Trouver le véhicule dans la liste
  const vehicle = DEFAULT_VEHICLES.find(v => v.id === vehicleId);

  // Puissance max du véhicule (DC ou AC selon le type de charge)
  const maxPowerKW = vehicle
    ? (isDC
        ? (vehicle.maxPowerKW ?? vehicle.maxDCPower ?? 50)
        : (vehicle.acMaxKW ?? vehicle.maxACPower ?? 11))
    : (isDC ? 50 : 11);

  // Courbe de charge réaliste selon le SoC
  // Les véhicules réduisent la puissance à mesure que la batterie se remplit
  let factor: number;
  if (soc < 10) {
    // Préchauffage batterie - 80% de la puissance max
    factor = 0.80;
  } else if (soc < 20) {
    // Montée en puissance - 95% de la puissance max
    factor = 0.95;
  } else if (soc < 50) {
    // Plateau optimal - 100% de la puissance max
    factor = 1.0;
  } else if (soc < 60) {
    // Début de réduction - 90%
    factor = 0.90;
  } else if (soc < 70) {
    // Réduction progressive - 75%
    factor = 0.75;
  } else if (soc < 80) {
    // Réduction plus marquée - 55%
    factor = 0.55;
  } else if (soc < 90) {
    // Charge lente pour préserver la batterie - 30%
    factor = 0.30;
  } else {
    // Fin de charge très lente - 15%
    factor = 0.15;
  }

  return Math.max(3, maxPowerKW * factor); // Minimum 3 kW
};

// =========================================================================
// Exports groupés pour faciliter l'import
// =========================================================================

export const mathUtils = {
  clamp,
  nowMs,
  ewma,
};

export const formatUtils = {
  formatHMS,
};

export const apiUtils = {
  fetchJSON,
  postAny,
};

export const vehicleUtils = {
  loadVehicleProfiles,
  getAllVehicles,
  calcVehPowerKW,
};
