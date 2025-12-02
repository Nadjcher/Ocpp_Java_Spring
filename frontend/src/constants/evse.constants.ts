// frontend/src/constants/evse.constants.ts
// Constantes extraites de SimuEvseTab.tsx pour modularisation

import type { Vehicle } from '@/types/evse.types';

/**
 * URL de base de l'API - détecte automatiquement l'environnement
 */
export const API_BASE = (() => {
  if (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE) {
    return import.meta.env.VITE_API_BASE;
  }
  if (typeof window !== 'undefined' && window.location.hostname === 'localhost') {
    return "http://localhost:8887";
  }
  return "/api";
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
  VEHICLE_SCALE: 1.3,
};

// Aliases pour compatibilité avec le code existant
export const EFFICIENCY_DEFAULT = SIMULATION.EFFICIENCY_DEFAULT;
export const RAMP_KW_PER_S = SIMULATION.RAMP_KW_PER_S;
export const NOISE = SIMULATION.NOISE;
export const MAX_POINTS = SIMULATION.MAX_POINTS;
export const SOC_CHARGE_MULTIPLIER = SIMULATION.SOC_CHARGE_MULTIPLIER;
export const VEHICLE_SCALE = SIMULATION.VEHICLE_SCALE;

/**
 * Liste des véhicules par défaut
 */
export const DEFAULT_VEHICLES: Vehicle[] = [
  { id: "1", name: "Tesla Model 3", capacityKWh: 75, efficiency: 0.95, imageUrl: "/images/tesla-model3 white.png" },
  { id: "2", name: "Renault ZOE", capacityKWh: 52, efficiency: 0.90, imageUrl: "/images/renault-zoe-silver.jpg" },
  { id: "3", name: "Nissan Leaf", capacityKWh: 62, efficiency: 0.88, imageUrl: "/images/generic-ev.png" },
  { id: "4", name: "Hyundai Kona", capacityKWh: 64, efficiency: 0.92, imageUrl: "/images/generic-ev.png" },
  { id: "5", name: "BMW i3", capacityKWh: 42, efficiency: 0.93, imageUrl: "/images/generic-ev.png" },
  { id: "6", name: "Audi e-tron", capacityKWh: 95, efficiency: 0.91, imageUrl: "/images/generic-ev.png" },
  { id: "7", name: "Volkswagen ID.3", capacityKWh: 58, efficiency: 0.90, imageUrl: "/images/generic-ev.png" },
  { id: "8", name: "Peugeot e-208", capacityKWh: 50, efficiency: 0.89, imageUrl: "/images/generic-ev.png" },
  { id: "9", name: "Mercedes EQC", capacityKWh: 80, efficiency: 0.91, imageUrl: "/images/generic-ev.png" },
  { id: "10", name: "Porsche Taycan", capacityKWh: 93, efficiency: 0.94, imageUrl: "/images/generic-ev.png" }
];

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
