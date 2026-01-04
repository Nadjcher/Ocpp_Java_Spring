/**
 * Configuration de l'environnement pour l'application EVSE Simulator.
 *
 * Ce fichier centralise toutes les configurations d'URL et de valeurs par défaut
 * pour le frontend.
 */

// URLs OCPP par environnement
export interface OcppUrls {
    test: string;
    prod: string;
    local: string;
    [key: string]: string;
}

// Configuration par défaut
export interface Defaults {
    cpId: string;
    idTag: string;
    connectorId: number;
    meterValuesInterval: number;
}

// Configuration globale
export interface Config {
    apiUrl: string;
    ocppUrls: OcppUrls;
    defaults: Defaults;
}

/**
 * Configuration par défaut de l'application.
 * Les valeurs peuvent être surchargées par les variables d'environnement Vite.
 */
export const config: Config = {
    // URL de l'API backend (runner)
    apiUrl: import.meta.env.VITE_API_URL || "http://localhost:8887",

    // URLs WebSocket OCPP par environnement
    ocppUrls: {
        test: import.meta.env.VITE_OCPP_URL_TEST || "ws://localhost:8080/ocpp",
        prod: import.meta.env.VITE_OCPP_URL_PROD || "wss://csms.example.com/ocpp",
        local: import.meta.env.VITE_OCPP_URL_LOCAL || "ws://localhost:8080/ocpp",
    },

    // Valeurs par défaut pour les sessions
    defaults: {
        cpId: import.meta.env.VITE_DEFAULT_CP_ID || "CP001",
        idTag: import.meta.env.VITE_DEFAULT_ID_TAG || "TEST-TAG",
        connectorId: 1,
        meterValuesInterval: 10,
    },
};

/**
 * Récupère les URLs OCPP depuis le backend.
 * Permet de charger dynamiquement les URLs configurées côté serveur.
 *
 * @returns Promise avec les URLs OCPP ou les URLs par défaut en cas d'erreur
 */
export async function fetchOcppUrls(): Promise<OcppUrls> {
    try {
        const baseUrl = import.meta.env.DEV ? "" : config.apiUrl;
        const response = await fetch(`${baseUrl}/api/config/ocpp-urls`);

        if (!response.ok) {
            console.warn("Failed to fetch OCPP URLs from server, using defaults");
            return config.ocppUrls;
        }

        const data = await response.json();

        // Fusionner avec les valeurs par défaut
        return {
            ...config.ocppUrls,
            ...data,
        };
    } catch (error) {
        console.warn("Error fetching OCPP URLs:", error);
        return config.ocppUrls;
    }
}

/**
 * Récupère l'URL de base de l'API en fonction de l'environnement.
 * En développement, utilise une chaîne vide pour passer par le proxy Vite.
 */
export function getApiBase(): string {
    if (import.meta.env.DEV) {
        return "";
    }

    // Vérifier localStorage pour URL personnalisée
    if (typeof window !== 'undefined') {
        const stored = window.localStorage.getItem("runner_api");
        if (stored) return stored;
    }

    return config.apiUrl;
}

/**
 * Vérifie si l'application est en mode développement.
 */
export function isDev(): boolean {
    return import.meta.env.DEV === true;
}

export default config;
