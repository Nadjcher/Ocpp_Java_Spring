// frontend/src/store/chargingProfileStore.ts
// Store Zustand pour la gestion des profils de charge Smart Charging OCPP 1.6

import { create } from 'zustand';

// =========================================================================
// Types
// =========================================================================

export interface ChargingSchedulePeriod {
    startPeriod: number;  // Offset en secondes depuis le début
    limit: number;        // Limite en A ou W
    numberPhases?: number;
}

export interface ChargingSchedule {
    duration?: number;
    startSchedule?: string;
    chargingRateUnit: 'A' | 'W';
    minChargingRate?: number;
    chargingSchedulePeriod: ChargingSchedulePeriod[];
}

export interface ChargingProfile {
    chargingProfileId: number;
    transactionId?: number;
    stackLevel: number;
    chargingProfilePurpose: 'ChargePointMaxProfile' | 'TxDefaultProfile' | 'TxProfile';
    chargingProfileKind: 'Absolute' | 'Relative' | 'Recurring';
    recurrencyKind?: 'Daily' | 'Weekly';
    validFrom?: string;
    validTo?: string;
    chargingSchedule: ChargingSchedule;
    // Métadonnées
    sessionId?: string;
    connectorId?: number;
    appliedAt?: string;
    effectiveStartTime?: string;
}

export interface EffectiveLimit {
    limitW: number;
    limitA: number;
    limitKw: number;
    source: 'ChargePointMaxProfile' | 'TxDefaultProfile' | 'TxProfile' | 'physical' | null;
    profileId: number;
    stackLevel: number;
    currentPeriodStart: number;
    nextPeriod?: {
        startPeriod: number;
        limit: number;
        secondsUntilStart: number;
    };
}

export interface CompositeSchedulePeriod {
    startPeriod: number;
    limit: number;
}

export interface CompositeSchedule {
    connectorId: number;
    scheduleStart: string;
    duration: number;
    chargingRateUnit: 'A' | 'W';
    chargingSchedulePeriod: CompositeSchedulePeriod[];
}

// =========================================================================
// Store Interface
// =========================================================================

// Critères pour ClearChargingProfile selon OCPP 1.6
export interface ClearChargingProfileCriteria {
    id?: number;                    // ID spécifique du profil
    connectorId?: number;           // Filtrer par connecteur
    chargingProfilePurpose?: string; // Filtrer par purpose
    stackLevel?: number;            // Filtrer par stackLevel
}

interface ChargingProfileStore {
    // État
    profiles: Map<string, ChargingProfile[]>;  // sessionId -> profiles
    effectiveLimits: Map<string, EffectiveLimit>;  // sessionId -> limit
    compositeSchedules: Map<string, CompositeSchedule>;  // sessionId -> schedule

    // Actions
    addProfile: (sessionId: string, profile: ChargingProfile) => void;
    removeProfile: (sessionId: string, profileId: number) => void;
    clearProfiles: (sessionId: string, purpose?: string) => void;
    clearProfilesByCriteria: (sessionId: string, criteria: ClearChargingProfileCriteria) => number;
    clearAllProfiles: (sessionId: string) => void;
    cleanupExpiredProfiles: () => number;  // Nettoyage automatique

    updateEffectiveLimit: (sessionId: string, limit: EffectiveLimit) => void;
    updateCompositeSchedule: (sessionId: string, schedule: CompositeSchedule) => void;

    // Getters
    getProfiles: (sessionId: string) => ChargingProfile[];
    getActiveProfiles: (sessionId: string) => ChargingProfile[];  // Filtre les expirés
    getEffectiveLimit: (sessionId: string) => EffectiveLimit | null;
    getCompositeSchedule: (sessionId: string) => CompositeSchedule | null;
    getActivePeriod: (sessionId: string) => ChargingSchedulePeriod | null;
    isProfileActive: (profile: ChargingProfile) => boolean;

    // Calculs locaux
    calculateCurrentLimit: (profile: ChargingProfile, phaseType: string, voltageV: number) => number;
    convertToWatts: (limitA: number, phaseType: string, voltageV: number) => number;
    convertToAmps: (limitW: number, phaseType: string, voltageV: number) => number;
}

// =========================================================================
// Store Implementation
// =========================================================================

export const useChargingProfileStore = create<ChargingProfileStore>((set, get) => ({
    profiles: new Map(),
    effectiveLimits: new Map(),
    compositeSchedules: new Map(),

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    addProfile: (sessionId: string, profile: ChargingProfile) => {
        set(state => {
            const newProfiles = new Map(state.profiles);
            const sessionProfiles = newProfiles.get(sessionId) || [];

            // Supprimer le profil existant avec le même ID
            const filtered = sessionProfiles.filter(p => p.chargingProfileId !== profile.chargingProfileId);
            filtered.push({ ...profile, sessionId, appliedAt: new Date().toISOString() });

            newProfiles.set(sessionId, filtered);

            console.log('[SCP Store] Added profile:', profile.chargingProfileId, 'for session:', sessionId);

            return { profiles: newProfiles };
        });
    },

    removeProfile: (sessionId: string, profileId: number) => {
        set(state => {
            const newProfiles = new Map(state.profiles);
            const sessionProfiles = newProfiles.get(sessionId) || [];
            const filtered = sessionProfiles.filter(p => p.chargingProfileId !== profileId);
            newProfiles.set(sessionId, filtered);

            console.log('[SCP Store] Removed profile:', profileId, 'from session:', sessionId);

            return { profiles: newProfiles };
        });
    },

    clearProfiles: (sessionId: string, purpose?: string) => {
        set(state => {
            const newProfiles = new Map(state.profiles);
            const sessionProfiles = newProfiles.get(sessionId) || [];

            const filtered = purpose
                ? sessionProfiles.filter(p => p.chargingProfilePurpose !== purpose)
                : [];

            newProfiles.set(sessionId, filtered);

            console.log('[SCP Store] Cleared profiles for session:', sessionId, 'purpose:', purpose || 'all');

            return { profiles: newProfiles };
        });
    },

    clearAllProfiles: (sessionId: string) => {
        set(state => {
            const newProfiles = new Map(state.profiles);
            const newLimits = new Map(state.effectiveLimits);
            const newSchedules = new Map(state.compositeSchedules);

            newProfiles.delete(sessionId);
            newLimits.delete(sessionId);
            newSchedules.delete(sessionId);

            console.log('[SCP Store] Cleared all profiles for session:', sessionId);

            return {
                profiles: newProfiles,
                effectiveLimits: newLimits,
                compositeSchedules: newSchedules
            };
        });
    },

    /**
     * Supprime les profils selon les critères OCPP 1.6 ClearChargingProfile.
     * Tous les critères sont optionnels et combinés en AND.
     * Si aucun critère n'est fourni, TOUS les profils sont supprimés.
     * @returns Le nombre de profils supprimés
     */
    clearProfilesByCriteria: (sessionId: string, criteria: ClearChargingProfileCriteria): number => {
        let removedCount = 0;

        set(state => {
            const newProfiles = new Map(state.profiles);
            const sessionProfiles = newProfiles.get(sessionId) || [];

            // Si aucun critère, supprimer tous les profils (clear-all)
            const hasCriteria = criteria.id !== undefined ||
                               criteria.connectorId !== undefined ||
                               criteria.chargingProfilePurpose !== undefined ||
                               criteria.stackLevel !== undefined;

            if (!hasCriteria) {
                removedCount = sessionProfiles.length;
                newProfiles.set(sessionId, []);
                console.log('[SCP Store] Clear ALL profiles (no criteria):', removedCount, 'removed');
                return { profiles: newProfiles };
            }

            const filtered = sessionProfiles.filter(profile => {
                // Critère ID: correspondance exacte
                if (criteria.id !== undefined && profile.chargingProfileId === criteria.id) {
                    return false; // Remove this profile
                }

                // Si ID est spécifié et ne correspond pas, garder le profil
                if (criteria.id !== undefined) {
                    return true;
                }

                // Critères multiples combinés en AND
                let shouldRemove = true;

                // connectorId: undefined ou égal (connectorId=0 signifie global/tous connecteurs)
                if (criteria.connectorId !== undefined && criteria.connectorId !== 0) {
                    if (profile.connectorId !== undefined && profile.connectorId !== criteria.connectorId) {
                        shouldRemove = false;
                    }
                }

                // chargingProfilePurpose: correspondance exacte
                if (criteria.chargingProfilePurpose !== undefined) {
                    if (profile.chargingProfilePurpose !== criteria.chargingProfilePurpose) {
                        shouldRemove = false;
                    }
                }

                // stackLevel: correspondance exacte
                if (criteria.stackLevel !== undefined) {
                    if (profile.stackLevel !== criteria.stackLevel) {
                        shouldRemove = false;
                    }
                }

                return !shouldRemove; // Keep if NOT shouldRemove
            });

            removedCount = sessionProfiles.length - filtered.length;
            newProfiles.set(sessionId, filtered);

            console.log('[SCP Store] ClearProfiles by criteria:', JSON.stringify(criteria),
                '- Removed:', removedCount, 'profiles');

            return { profiles: newProfiles };
        });

        return removedCount;
    },

    updateEffectiveLimit: (sessionId: string, limit: EffectiveLimit) => {
        set(state => {
            const newLimits = new Map(state.effectiveLimits);
            newLimits.set(sessionId, limit);

            console.log('[SCP Store] Updated effective limit for session:', sessionId,
                'limit:', limit.limitKw, 'kW', 'source:', limit.source);

            return { effectiveLimits: newLimits };
        });
    },

    updateCompositeSchedule: (sessionId: string, schedule: CompositeSchedule) => {
        set(state => {
            const newSchedules = new Map(state.compositeSchedules);
            newSchedules.set(sessionId, schedule);

            console.log('[SCP Store] Updated composite schedule for session:', sessionId,
                'periods:', schedule.chargingSchedulePeriod.length);

            return { compositeSchedules: newSchedules };
        });
    },

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    getProfiles: (sessionId: string) => {
        return get().profiles.get(sessionId) || [];
    },

    /**
     * Vérifie si un profil est actif (non expiré).
     */
    isProfileActive: (profile: ChargingProfile) => {
        const now = Date.now();

        // Vérifier validFrom
        if (profile.validFrom) {
            const validFromMs = Date.parse(profile.validFrom);
            if (validFromMs > now) {
                return false; // Pas encore valide
            }
        }

        // Vérifier validTo
        if (profile.validTo) {
            const validToMs = Date.parse(profile.validTo);
            if (validToMs < now) {
                return false; // Expiré
            }
        }

        // Vérifier duration pour les profils Absolute
        if (profile.chargingProfileKind === 'Absolute' && profile.chargingSchedule.duration) {
            const startMs = profile.chargingSchedule.startSchedule
                ? Date.parse(profile.chargingSchedule.startSchedule)
                : profile.appliedAt
                    ? Date.parse(profile.appliedAt)
                    : now;
            const endMs = startMs + profile.chargingSchedule.duration * 1000;
            if (endMs < now) {
                return false; // Durée expirée
            }
        }

        return true;
    },

    /**
     * Récupère les profils actifs (filtre les expirés).
     */
    getActiveProfiles: (sessionId: string) => {
        const profiles = get().profiles.get(sessionId) || [];
        return profiles.filter(p => get().isProfileActive(p));
    },

    /**
     * Nettoie les profils expirés de toutes les sessions.
     * @returns Nombre de profils nettoyés
     */
    cleanupExpiredProfiles: () => {
        let cleanedCount = 0;

        const newProfiles = new Map(get().profiles);

        for (const [sessionId, sessionProfiles] of newProfiles) {
            const activeProfiles = sessionProfiles.filter(p => {
                const isActive = get().isProfileActive(p);
                if (!isActive) {
                    cleanedCount++;
                    console.log(`[SCP Store] 🗑️ Profil #${p.chargingProfileId} expiré (session ${sessionId})`);
                }
                return isActive;
            });

            if (activeProfiles.length !== sessionProfiles.length) {
                newProfiles.set(sessionId, activeProfiles);
            }
        }

        if (cleanedCount > 0) {
            console.log(`[SCP Store] ✅ ${cleanedCount} profil(s) expiré(s) nettoyé(s)`);
            // Note: On ne peut pas appeler set() ici car on est dans un getter
            // Le nettoyage sera fait par un timer externe
        }

        return cleanedCount;
    },

    getEffectiveLimit: (sessionId: string) => {
        return get().effectiveLimits.get(sessionId) || null;
    },

    getCompositeSchedule: (sessionId: string) => {
        return get().compositeSchedules.get(sessionId) || null;
    },

    getActivePeriod: (sessionId: string) => {
        const limit = get().effectiveLimits.get(sessionId);
        if (!limit) return null;

        const profiles = get().profiles.get(sessionId) || [];
        const activeProfile = profiles.find(p => p.chargingProfileId === limit.profileId);

        if (!activeProfile?.chargingSchedule?.chargingSchedulePeriod) return null;

        const now = Date.now();
        const startTime = activeProfile.effectiveStartTime
            ? new Date(activeProfile.effectiveStartTime).getTime()
            : activeProfile.appliedAt
                ? new Date(activeProfile.appliedAt).getTime()
                : now;

        const elapsedSeconds = Math.floor((now - startTime) / 1000);

        let activePeriod: ChargingSchedulePeriod | null = null;
        for (const period of activeProfile.chargingSchedule.chargingSchedulePeriod) {
            if (period.startPeriod <= elapsedSeconds) {
                activePeriod = period;
            } else {
                break;
            }
        }

        return activePeriod;
    },

    // -------------------------------------------------------------------------
    // Calculs locaux
    // -------------------------------------------------------------------------

    calculateCurrentLimit: (profile: ChargingProfile, phaseType: string, voltageV: number) => {
        if (!profile.chargingSchedule?.chargingSchedulePeriod?.length) {
            return Infinity;
        }

        const now = Date.now();
        const startTime = profile.effectiveStartTime
            ? new Date(profile.effectiveStartTime).getTime()
            : profile.appliedAt
                ? new Date(profile.appliedAt).getTime()
                : now;

        const elapsedSeconds = Math.floor((now - startTime) / 1000);

        // Trouver la période active
        let activePeriod: ChargingSchedulePeriod | null = null;
        for (const period of profile.chargingSchedule.chargingSchedulePeriod) {
            if (period.startPeriod <= elapsedSeconds) {
                activePeriod = period;
            } else {
                break;
            }
        }

        if (!activePeriod) return Infinity;

        // Convertir en Watts si nécessaire
        if (profile.chargingSchedule.chargingRateUnit === 'W') {
            return activePeriod.limit;
        }

        return get().convertToWatts(activePeriod.limit, phaseType, voltageV);
    },

    convertToWatts: (limitA: number, phaseType: string, voltageV: number) => {
        switch (phaseType.toUpperCase()) {
            case 'AC_MONO':
            case 'AC_1':
                return voltageV * limitA;

            case 'AC_TRI':
            case 'AC_3':
                // P = sqrt(3) * V_phase-phase * I
                const vPP = voltageV < 300 ? voltageV * Math.sqrt(3) : voltageV;
                return Math.sqrt(3) * vPP * limitA;

            case 'DC':
                return voltageV * limitA;

            default:
                return Math.sqrt(3) * 400 * limitA;
        }
    },

    convertToAmps: (limitW: number, phaseType: string, voltageV: number) => {
        switch (phaseType.toUpperCase()) {
            case 'AC_MONO':
            case 'AC_1':
                return limitW / voltageV;

            case 'AC_TRI':
            case 'AC_3':
                const vPP = voltageV < 300 ? voltageV * Math.sqrt(3) : voltageV;
                return limitW / (Math.sqrt(3) * vPP);

            case 'DC':
                return limitW / voltageV;

            default:
                return limitW / (Math.sqrt(3) * 400);
        }
    }
}));

// =========================================================================
// Helpers
// =========================================================================

/**
 * Formatte une limite pour l'affichage.
 */
export function formatLimit(limit: EffectiveLimit | null): string {
    if (!limit || limit.limitW === Infinity || limit.limitW === 0) {
        return 'Aucune limite';
    }
    return `${limit.limitKw.toFixed(1)} kW (${limit.limitA.toFixed(1)} A)`;
}

/**
 * Formatte le temps restant avant la prochaine période.
 */
export function formatTimeUntilNextPeriod(seconds: number): string {
    if (seconds <= 0) return 'maintenant';
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}min ${seconds % 60}s`;
    const hours = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    return `${hours}h ${mins}min`;
}

/**
 * Obtient la couleur selon le purpose.
 */
export function getPurposeColor(purpose: string | null): string {
    switch (purpose) {
        case 'ChargePointMaxProfile':
            return 'text-red-600 bg-red-100';
        case 'TxDefaultProfile':
            return 'text-orange-600 bg-orange-100';
        case 'TxProfile':
            return 'text-blue-600 bg-blue-100';
        default:
            return 'text-gray-600 bg-gray-100';
    }
}

export default useChargingProfileStore;
