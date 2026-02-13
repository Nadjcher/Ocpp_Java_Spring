// frontend/src/constants/evse.constants.ts
// Constantes extraites de SimuEvseTab.tsx pour modularisation

import type { Vehicle, EvseType } from '@/types/evse.types';

/**
 * URL de base de l'API - détecte automatiquement l'environnement
 * En développement, utilise le proxy Vite configuré dans vite.config.ts
 */
export const API_BASE = (() => {
  if (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE) {
    return import.meta.env.VITE_API_BASE;
  }
  // En développement, utiliser une chaîne vide pour passer par le proxy Vite
  // Le proxy est configuré pour /api -> http://localhost:8887
  return "";
})();

/**
 * URLs des environnements OCPP
 */
export const ENV_URLS: Record<string, string> = {
  test: "wss://ocpp-test.example.com",
  pp: "wss://ocpp-pp.example.com"
};

/**
 * Tension par défaut en Volts
 */
export const DEFAULT_VOLTAGE = 230;

/**
 * Nombre de phases par type de borne
 */
export const DEFAULT_PHASES: Record<string, number> = {
  "ac-mono": 1,
  "ac-bi": 2,
  "ac-tri": 3,
  dc: 3,
};

/**
 * Chemins des assets images
 */
export const ASSETS = {
  stationAC: "/images/charger-ac.png",
  stationDC: "/images/charger-dc.png",
  genericEV: "/images/generic-ev.png",
  connectors: {
    left: "/images/connecteur vers la droite.png",
    right: "/images/connecteur vers la gauche.png",
  },
};

/**
 * Paramètres de simulation
 */
export const SIMULATION = {
  /** Rendement par défaut de la charge */
  EFFICIENCY_DEFAULT: 0.92,
  /** Rampe de puissance en kW/s */
  RAMP_KW_PER_S: 5 / 3,
  /** Bruit aléatoire sur la puissance */
  NOISE: 0.03,
  /** Nombre maximum de points sur les graphiques */
  MAX_POINTS: 900,
  /** Multiplicateur de charge SoC */
  SOC_CHARGE_MULTIPLIER: 3,
  /** Échelle du véhicule pour l'affichage */
  VEHICLE_SCALE: 1.5,
};

// Aliases pour compatibilité avec le code existant
export const EFFICIENCY_DEFAULT = SIMULATION.EFFICIENCY_DEFAULT;
export const RAMP_KW_PER_S = SIMULATION.RAMP_KW_PER_S;
export const NOISE = SIMULATION.NOISE;
export const MAX_POINTS = SIMULATION.MAX_POINTS;
export const SOC_CHARGE_MULTIPLIER = SIMULATION.SOC_CHARGE_MULTIPLIER;
export const VEHICLE_SCALE = SIMULATION.VEHICLE_SCALE;

/**
 * Liste des véhicules par défaut avec puissance de charge maximale (DC)
 * maxPowerKW: Puissance DC max acceptée par le véhicule
 * acMaxKW: Puissance AC max (chargeur embarqué)
 * acPhases: Nombre de phases AC supportées par le chargeur embarqué
 * acMaxA: Courant max par phase (calculé: acMaxKW * 1000 / (230 * acPhases))
 *
 * Règle: La puissance effective = min(EVSE, véhicule) pour phases ET courant
 */
export const DEFAULT_VEHICLES: Vehicle[] = [
  { id: "1", name: "Tesla Model 3", capacityKWh: 75, efficiency: 0.95, maxPowerKW: 250, acMaxKW: 11, acPhases: 3, acMaxA: 16, connectorTypes: ["TYPE2", "CCS"], imageUrl: "/images/tesla-model3 white.png" },
  { id: "2", name: "Renault ZOE", capacityKWh: 52, efficiency: 0.90, maxPowerKW: 50, acMaxKW: 22, acPhases: 3, acMaxA: 32, connectorTypes: ["TYPE2"], imageUrl: "/images/renault-zoe-silver.jpg" },
  { id: "3", name: "Nissan Leaf", capacityKWh: 62, efficiency: 0.88, maxPowerKW: 100, acMaxKW: 6.6, acPhases: 1, acMaxA: 29, connectorTypes: ["TYPE2", "CHADEMO"], imageUrl: "/images/generic-ev.png" },
  { id: "4", name: "Hyundai Kona", capacityKWh: 64, efficiency: 0.92, maxPowerKW: 77, acMaxKW: 11, acPhases: 3, acMaxA: 16, connectorTypes: ["TYPE2", "CCS"], imageUrl: "/images/generic-ev.png" },
  { id: "5", name: "BMW i3", capacityKWh: 42, efficiency: 0.93, maxPowerKW: 50, acMaxKW: 11, acPhases: 3, acMaxA: 16, connectorTypes: ["TYPE2", "CCS"], imageUrl: "/images/generic-ev.png" },
  { id: "6", name: "Audi e-tron", capacityKWh: 95, efficiency: 0.91, maxPowerKW: 150, acMaxKW: 22, acPhases: 3, acMaxA: 32, connectorTypes: ["TYPE2", "CCS"], imageUrl: "/images/generic-ev.png" },
  { id: "7", name: "Volkswagen ID.3", capacityKWh: 58, efficiency: 0.90, maxPowerKW: 120, acMaxKW: 11, acPhases: 3, acMaxA: 16, connectorTypes: ["TYPE2", "CCS"], imageUrl: "/images/generic-ev.png" },
  { id: "8", name: "Peugeot e-208", capacityKWh: 50, efficiency: 0.89, maxPowerKW: 100, acMaxKW: 11, acPhases: 3, acMaxA: 16, connectorTypes: ["TYPE2", "CCS"], imageUrl: "/images/generic-ev.png" },
  { id: "9", name: "Mercedes EQC", capacityKWh: 80, efficiency: 0.91, maxPowerKW: 110, acMaxKW: 11, acPhases: 3, acMaxA: 16, connectorTypes: ["TYPE2", "CCS"], imageUrl: "/images/generic-ev.png" },
  { id: "10", name: "Porsche Taycan", capacityKWh: 93, efficiency: 0.94, maxPowerKW: 270, acMaxKW: 22, acPhases: 3, acMaxA: 32, connectorTypes: ["TYPE2", "CCS"], imageUrl: "/images/generic-ev.png" }
];

/**
 * Retourne la liste des types EVSE compatibles avec un véhicule donné.
 *
 * Règles de compatibilité:
 * - TYPE2 → AC (mono/bi/tri selon acPhases du véhicule)
 * - CCS → DC (tous niveaux) + AC via TYPE2
 * - CHADEMO → DC uniquement
 * - Si aucun connectorTypes défini → tous les types sont disponibles (fallback)
 *
 * @param vehicle Le profil du véhicule sélectionné
 * @returns Liste ordonnée des EvseType compatibles
 */
export function getCompatibleEvseTypes(vehicle: { connectorTypes?: string[]; acPhases?: number } | null | undefined): { value: EvseType; label: string }[] {
  const allTypes: { value: EvseType; label: string }[] = [
    { value: 'ac-mono', label: 'AC Mono (1 phase)' },
    { value: 'ac-bi', label: 'AC Bi (2 phases)' },
    { value: 'ac-tri', label: 'AC Tri (3 phases)' },
    { value: 'dc', label: 'DC' },
  ];

  if (!vehicle || !vehicle.connectorTypes || vehicle.connectorTypes.length === 0) {
    return allTypes;
  }

  const connectors = vehicle.connectorTypes.map(c => c.toUpperCase());
  const acPhases = vehicle.acPhases ?? 3;
  const result: { value: EvseType; label: string }[] = [];

  // AC: le véhicule doit avoir un connecteur TYPE2 (ou CCS qui inclut TYPE2)
  const supportsAC = connectors.includes('TYPE2') || connectors.includes('CCS');
  if (supportsAC) {
    result.push({ value: 'ac-mono', label: 'AC Mono (1 phase)' });
    if (acPhases >= 2) {
      result.push({ value: 'ac-bi', label: 'AC Bi (2 phases)' });
    }
    if (acPhases >= 3) {
      result.push({ value: 'ac-tri', label: 'AC Tri (3 phases)' });
    }
  }

  // DC: le véhicule doit avoir CCS ou CHAdeMO
  const supportsDC = connectors.includes('CCS') || connectors.includes('CHADEMO');
  if (supportsDC) {
    result.push({ value: 'dc', label: 'DC' });
  }

  return result.length > 0 ? result : allTypes;
}

/**
 * Connecteur physique d'un véhicule avec label et type EVSE associé.
 */
export interface VehicleConnector {
  index: number;       // 0-based
  label: string;       // ex: "Connecteur 1 — TYPE2 (AC Tri)"
  type: string;        // ex: "TYPE2", "CCS", "CHADEMO"
  evseType: EvseType;  // type EVSE auto-déterminé
}

/**
 * Retourne les connecteurs physiques du véhicule sous forme de liste numérotée.
 * Chaque connecteur est associé automatiquement au bon type EVSE.
 *
 * Mapping:
 * - TYPE2 → AC (mono/bi/tri selon acPhases du véhicule)
 * - CCS   → DC
 * - CHADEMO → DC
 */
export function getVehicleConnectors(vehicle: { connectorTypes?: string[]; acPhases?: number } | null | undefined): VehicleConnector[] {
  if (!vehicle?.connectorTypes?.length) {
    // Fallback: connecteur AC générique + DC
    return [
      { index: 0, label: 'Connecteur 1 — TYPE2 (AC Tri)', type: 'TYPE2', evseType: 'ac-tri' },
      { index: 1, label: 'Connecteur 2 — CCS (DC)', type: 'CCS', evseType: 'dc' },
    ];
  }

  const acPhases = vehicle.acPhases ?? 3;

  return vehicle.connectorTypes.map((raw, i) => {
    const type = raw.toUpperCase();
    let evseType: EvseType;
    let detail: string;

    if (type === 'TYPE2') {
      if (acPhases <= 1) { evseType = 'ac-mono'; detail = 'AC Mono'; }
      else if (acPhases === 2) { evseType = 'ac-bi'; detail = 'AC Bi'; }
      else { evseType = 'ac-tri'; detail = 'AC Tri'; }
    } else {
      evseType = 'dc';
      detail = 'DC';
    }

    return {
      index: i,
      label: `Connecteur ${i + 1} — ${type} (${detail})`,
      type,
      evseType,
    };
  });
}

/**
 * Calcule la puissance effective AC en prenant en compte les limites EVSE et véhicule.
 *
 * @param evsePhases Nombre de phases de l'EVSE (1, 2 ou 3)
 * @param evseMaxA Courant max par phase de l'EVSE
 * @param vehicleAcPhases Nombre de phases du chargeur embarqué véhicule
 * @param vehicleAcMaxA Courant max par phase du véhicule
 * @param voltage Tension (défaut 230V)
 * @returns Puissance effective en kW
 */
export function calculateEffectiveACPower(
  evsePhases: number,
  evseMaxA: number,
  vehicleAcPhases: number,
  vehicleAcMaxA: number,
  voltage: number = DEFAULT_VOLTAGE
): { effectiveKw: number; effectivePhases: number; effectiveA: number } {
  // Le nombre de phases effectif est le minimum entre EVSE et véhicule
  const effectivePhases = Math.min(evsePhases, vehicleAcPhases);

  // Le courant effectif par phase est le minimum entre EVSE et véhicule
  const effectiveA = Math.min(evseMaxA, vehicleAcMaxA);

  // Puissance = V × I × phases
  const effectiveKw = (voltage * effectiveA * effectivePhases) / 1000;

  return { effectiveKw, effectivePhases, effectiveA };
}

/**
 * Styles CSS supplémentaires pour les animations
 */
export const EXTRA_CSS = `
@keyframes cableEnergy {
  from { stroke-dashoffset: 0 }
  to   { stroke-dashoffset: -80 }
}
@keyframes charging-pulse {
  0%, 100% { opacity: .6; transform: scale(1); }
  50%      { opacity: 1;  transform: scale(1.05); }
}
@keyframes connector-glow {
  0%, 100% { filter: drop-shadow(0 0 8px rgba(16,185,129,.9)); }
  50%      { filter: drop-shadow(0 0 16px rgba(16,185,129,1)); }
}
`;
