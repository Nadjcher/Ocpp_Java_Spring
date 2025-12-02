#!/usr/bin/env node

/**
 * Wrapper CommonJS pour lancer runner-http-api.js (ES Module)
 * Ce fichier est nécessaire pour pkg
 */

// Utiliser import() dynamique pour charger le module ES6
(async () => {
    try {
        // Charger le module ES6
        await import('./runner-http-api.js');
    } catch (error) {
        console.error('Erreur au démarrage:', error);
        process.exit(1);
    }
})();
