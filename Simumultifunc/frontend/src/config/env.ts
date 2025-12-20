/**
 * Configuration centralisée basée sur les variables d'environnement.
 * Toutes les valeurs sensibles doivent être définies via des variables d'environnement.
 */

// URL de base de l'API backend
const getApiUrl = (): string => {
    const isDev = typeof import.meta !== "undefined" && import.meta.env?.DEV;
    if (isDev) {
        return ""; // Utiliser le proxy Vite en développement
    }
    return import.meta.env.VITE_API_URL ?? "http://localhost:8887";
};

// URLs OCPP par environnement (fallback si l'API ne répond pas)
const getOcppUrls = (): Record<string, string> => ({
    test: import.meta.env.VITE_OCPP_TEST_URL ?? "wss://evse-test.total-ev-charge.com/ocpp/WebSocket",
    pp: import.meta.env.VITE_OCPP_PP_URL ?? "wss://evse-pp.total-ev-charge.com/ocpp/WebSocket",
    prod: import.meta.env.VITE_OCPP_PROD_URL ?? "wss://evse.total-ev-charge.com/ocpp/WebSocket",
});

// Configuration exportée
export const config = {
    apiUrl: getApiUrl(),

    // URLs OCPP (fallback, préférer l'API /api/config/ocpp-urls)
    ocppUrls: getOcppUrls(),

    // Environnement par défaut
    defaultEnvironment: import.meta.env.VITE_DEFAULT_ENV ?? "test",

    // Feature flags
    features: {
        smartCharging: import.meta.env.VITE_ENABLE_SMART_CHARGING !== "false",
        tnr: import.meta.env.VITE_ENABLE_TNR !== "false",
        performance: import.meta.env.VITE_ENABLE_PERFORMANCE !== "false",
        ml: import.meta.env.VITE_ENABLE_ML !== "false",
    },

    // Valeurs par défaut pour les sessions (fallback, préférer l'API /api/config/defaults)
    defaults: {
        cpId: import.meta.env.VITE_DEFAULT_CP_ID ?? "SIMU-CP-001",
        idTag: import.meta.env.VITE_DEFAULT_ID_TAG ?? "TAG-001",
    }
};

/**
 * Récupère les URLs OCPP depuis l'API backend.
 * Retourne les fallback si l'API n'est pas disponible.
 */
export async function fetchOcppUrls(): Promise<Record<string, string>> {
    try {
        const response = await fetch(`${config.apiUrl}/api/config/ocpp-urls`);
        if (response.ok) {
            return await response.json();
        }
    } catch (e) {
        console.warn("Could not fetch OCPP URLs from API, using defaults:", e);
    }
    return config.ocppUrls;
}

/**
 * Récupère les valeurs par défaut depuis l'API backend.
 */
export async function fetchDefaults(): Promise<{
    cpId: string;
    idTag: string;
    vehicle: string;
    connectorId: number;
    maxPowerKw: number;
}> {
    try {
        const response = await fetch(`${config.apiUrl}/api/config/defaults`);
        if (response.ok) {
            return await response.json();
        }
    } catch (e) {
        console.warn("Could not fetch defaults from API, using env config:", e);
    }
    return {
        cpId: config.defaults.cpId,
        idTag: config.defaults.idTag,
        vehicle: "GENERIC",
        connectorId: 1,
        maxPowerKw: 22.0,
    };
}

export default config;
