// src/services/OCPPChargingProfilesManager.ts
// VERSION COMPLÈTE avec support Multi-Sessions, Recurring, validFrom/To, minChargingRate

export type ChargingProfilePurposeType = "ChargePointMaxProfile" | "TxDefaultProfile" | "TxProfile";
export type ChargingProfileKindType = "Absolute" | "Recurring" | "Relative";
export type RecurrencyKindType = "Daily" | "Weekly";
export type ChargingRateUnitType = "W" | "A";

export interface ChargingSchedulePeriod {
  startPeriod: number;
  limit: number;
  numberPhases?: number;
}

export interface ChargingSchedule {
  duration?: number;
  startSchedule?: string;
  chargingRateUnit: ChargingRateUnitType;
  chargingSchedulePeriod: ChargingSchedulePeriod[];
  minChargingRate?: number;
}

export interface ChargingProfile {
  chargingProfileId: number;
  transactionId?: number;
  stackLevel: number;
  chargingProfilePurpose: ChargingProfilePurposeType;
  chargingProfileKind: ChargingProfileKindType;
  chargingSchedule: ChargingSchedule;
  recurrencyKind?: RecurrencyKindType;
  validFrom?: string;
  validTo?: string;
  // Extension pour multi-sessions
  sessionId?: string;
  connectorId?: number;
  appliedAt?: number;
}

export interface ProfileApplication {
  profileId: number;
  purpose: ChargingProfilePurposeType;
  stackLevel: number;
  limitW: number;
  source: "profile" | "physical" | "default";
  timestamp: number;
  nextChangeIn?: number;
  profileDetails?: ChargingProfile;
  sessionId?: string;
}

export interface ConnectorConfig {
  voltage: number;
  phases: number;
}

// Interface pour la gestion des sessions
export interface SessionChargingState {
  sessionId: string;
  transactionId?: number;
  transactionStartTime?: number;
  profiles: Map<number, ChargingProfile>;
  effectiveLimit: ProfileApplication;
  connectorId: number;
}

const PURPOSE_PRIORITY: Record<ChargingProfilePurposeType, number> = {
  TxProfile: 3,
  TxDefaultProfile: 2,
  ChargePointMaxProfile: 1
};

function clamp(n: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, n));
}

function msOrSecToSec(value?: number): number | undefined {
  if (!value || !Number.isFinite(value)) return undefined;
  return value > 7 * 24 * 3600 ? Math.round(value / 1000) : Math.round(value);
}

export class OCPPChargingProfilesManager {
  private maxPowerW: number;
  private connectors: Map<number, ConnectorConfig>;
  private profiles: Map<number, Map<number, ChargingProfile>>;
  private effective: Map<number, ProfileApplication>;
  private timers: Map<number, NodeJS.Timeout[]>;
  private lastAppliedLimit: Map<number, number>;
  private transactionStartTimes: Map<number, number>;
  private cleanupTimer: NodeJS.Timeout | null = null;

  // Support multi-sessions
  private sessions: Map<string, SessionChargingState> = new Map();
  private sessionProfiles: Map<string, Map<number, ChargingProfile>> = new Map();
  private sessionTimers: Map<string, NodeJS.Timeout[]> = new Map();

  public onLimitChange?: (connectorId: number, limitW: number, source: ProfileApplication) => void;
  public onProfileChange?: (event: any) => void;
  public onProfileExpired?: (profileId: number, connectorId: number) => void;
  public onSessionLimitChange?: (sessionId: string, limitW: number, source: ProfileApplication) => void;

  constructor(init?: {
    maxPowerW?: number;
    defaultVoltage?: number;
    defaultPhases?: number;
    onLimitChange?: (connectorId: number, limitW: number, source: ProfileApplication) => void;
    onProfileChange?: (event: any) => void;
  }) {
    this.maxPowerW = init?.maxPowerW || 22000;
    this.connectors = new Map();
    this.profiles = new Map();
    this.effective = new Map();
    this.timers = new Map();
    this.lastAppliedLimit = new Map();
    this.transactionStartTimes = new Map();

    this.onLimitChange = init?.onLimitChange;
    this.onProfileChange = init?.onProfileChange;

    const defaultVoltage = init?.defaultVoltage ?? 230;
    const defaultPhases = init?.defaultPhases ?? 1;
    this.updateConnectorConfig(1, { voltage: defaultVoltage, phases: defaultPhases });

    // Démarrer le nettoyage automatique des profils expirés (toutes les 30s)
    this.startCleanupTimer();
  }

  /**
   * Démarre le timer de nettoyage automatique des profils expirés.
   */
  private startCleanupTimer() {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
    }
    this.cleanupTimer = setInterval(() => {
      this.cleanupExpiredProfiles();
    }, 30000); // Toutes les 30 secondes
  }

  /**
   * Nettoie les profils expirés (validTo < now) et recalcule les limites.
   */
  cleanupExpiredProfiles(): number {
    const now = Date.now();
    let cleanedCount = 0;
    const expiredProfiles: Array<{connectorId: number, profileId: number}> = [];

    for (const [connectorId, profilesMap] of this.profiles) {
      for (const [profileId, profile] of profilesMap) {
        // Vérifier validTo pour tous les types de profils
        if (profile.validTo) {
          const validToMs = Date.parse(profile.validTo);
          if (validToMs < now) {
            expiredProfiles.push({ connectorId, profileId });
          }
        }

        // Vérifier aussi les profils Absolute avec duration expirée
        if (profile.chargingProfileKind === "Absolute" && profile.chargingSchedule.duration) {
          const startMs = profile.chargingSchedule.startSchedule
            ? Date.parse(profile.chargingSchedule.startSchedule)
            : now;
          const endMs = startMs + profile.chargingSchedule.duration * 1000;
          if (endMs < now) {
            expiredProfiles.push({ connectorId, profileId });
          }
        }
      }
    }

    // Supprimer les profils expirés
    const connectorsToRecalc = new Set<number>();
    for (const { connectorId, profileId } of expiredProfiles) {
      const profilesMap = this.profiles.get(connectorId);
      if (profilesMap?.has(profileId)) {
        console.log(`[OCPPManager] 🗑️ Nettoyage profil expiré #${profileId} (connecteur ${connectorId})`);
        profilesMap.delete(profileId);
        this.clearTimersForProfile(connectorId, profileId);
        cleanedCount++;
        connectorsToRecalc.add(connectorId);
        this.onProfileExpired?.(profileId, connectorId);
      }
    }

    // Recalculer les limites pour les connecteurs affectés
    for (const connectorId of connectorsToRecalc) {
      this.recalculate(connectorId);
    }

    if (cleanedCount > 0) {
      console.log(`[OCPPManager] ✅ ${cleanedCount} profil(s) expiré(s) nettoyé(s)`);
      this.onProfileChange?.({ type: "CLEANUP", cleanedCount });
    }

    return cleanedCount;
  }

  updateConnectorConfig(connectorId: number, config: ConnectorConfig) {
    const newVoltage = config.voltage;
    const newPhases = Math.max(1, Math.round(config.phases));

    // Only update and recalculate if config has actually changed
    const existing = this.connectors.get(connectorId);
    if (existing && existing.voltage === newVoltage && existing.phases === newPhases) {
      return; // No change, skip logging and recalculation
    }

    console.log(`[OCPPManager] Config connecteur ${connectorId}: ${newVoltage}V, ${newPhases} phase(s)`);
    this.connectors.set(connectorId, {
      voltage: newVoltage,
      phases: newPhases
    });
    this.recalculate(connectorId);
  }

  /**
   * Met à jour la puissance maximale physique de la borne.
   * Doit être appelé quand le type EVSE change.
   *
   * @param maxPowerW Puissance max en Watts (calculée selon type: maxA * voltage * phases)
   */
  setMaxPowerW(maxPowerW: number) {
    if (this.maxPowerW === maxPowerW) return;

    console.log(`[OCPPManager] Limite physique mise à jour: ${this.maxPowerW / 1000}kW -> ${maxPowerW / 1000}kW`);
    this.maxPowerW = maxPowerW;

    // Recalculer toutes les limites
    for (const connectorId of this.connectors.keys()) {
      this.recalculate(connectorId);
    }

    // Recalculer aussi les sessions
    for (const sessionId of this.sessions.keys()) {
      this.recalculateSession(sessionId);
    }
  }

  /**
   * Obtient la puissance maximale physique actuelle en Watts.
   */
  getMaxPowerW(): number {
    return this.maxPowerW;
  }

  /**
   * Obtient la puissance maximale physique actuelle en kW.
   */
  getMaxPowerKw(): number {
    return this.maxPowerW / 1000;
  }

  markTransactionStart(connectorId: number) {
    this.transactionStartTimes.set(connectorId, Date.now());
    console.log(`[OCPPManager] Transaction démarrée sur connecteur ${connectorId}`);
  }

  markTransactionStop(connectorId: number) {
    this.transactionStartTimes.delete(connectorId);
    console.log(`[OCPPManager] Transaction arrêtée sur connecteur ${connectorId}`);
  }

  private toWatts(
      limit: number,
      unit: ChargingRateUnitType,
      periodPhases: number | undefined,
      connectorId: number
  ): number {
    if (unit === "W") return limit;

    const config = this.connectors.get(connectorId) || { voltage: 230, phases: 1 };
    const phases = periodPhases || config.phases || 1;
    const watts = limit * config.voltage * phases;

    console.log(`[OCPPManager] Conversion A->W: ${limit}A * ${config.voltage}V * ${phases}ph = ${watts}W`);
    return watts;
  }

  // Créer un profil depuis des paramètres UI
  createProfile(params: {
    connectorId?: number;
    chargingProfileId?: number;
    stackLevel?: number;
    purpose?: ChargingProfilePurposeType;
    kind?: ChargingProfileKindType;
    recurrencyKind?: RecurrencyKindType;
    validFrom?: string;
    validTo?: string;
    chargingRateUnit?: ChargingRateUnitType;
    minChargingRate?: number;
    periods?: Array<{ startPeriod: number; limit: number; numberPhases?: number }>;
  }): ChargingProfile {
    const now = new Date().toISOString();

    return {
      chargingProfileId: params.chargingProfileId || Date.now() % 10000,
      stackLevel: params.stackLevel ?? 0,
      chargingProfilePurpose: params.purpose || "TxProfile",
      chargingProfileKind: params.kind || "Absolute",
      recurrencyKind: params.kind === "Recurring" ? params.recurrencyKind : undefined,
      validFrom: params.kind === "Recurring" ? params.validFrom : undefined,
      validTo: params.kind === "Recurring" ? params.validTo : undefined,
      chargingSchedule: {
        startSchedule: params.kind === "Absolute" ? now : undefined,
        chargingRateUnit: params.chargingRateUnit || "W",
        minChargingRate: params.minChargingRate,
        chargingSchedulePeriod: params.periods || [{ startPeriod: 0, limit: 11000 }]
      }
    };
  }

  // Construire le payload OCPP pour SetChargingProfile
  buildSetChargingProfilePayload(connectorId: number, profile: ChargingProfile): any {
    return {
      connectorId,
      csChargingProfiles: {
        chargingProfileId: profile.chargingProfileId,
        transactionId: profile.transactionId,
        stackLevel: profile.stackLevel,
        chargingProfilePurpose: profile.chargingProfilePurpose,
        chargingProfileKind: profile.chargingProfileKind,
        recurrencyKind: profile.recurrencyKind,
        validFrom: profile.validFrom,
        validTo: profile.validTo,
        chargingSchedule: {
          duration: profile.chargingSchedule.duration,
          startSchedule: profile.chargingSchedule.startSchedule || new Date().toISOString(),
          chargingRateUnit: profile.chargingSchedule.chargingRateUnit,
          minChargingRate: profile.chargingSchedule.minChargingRate,
          chargingSchedulePeriod: profile.chargingSchedule.chargingSchedulePeriod
        }
      }
    };
  }

  // Construire le payload OCPP pour ClearChargingProfile
  buildClearChargingProfilePayload(criteria?: {
    id?: number;
    connectorId?: number;
    chargingProfilePurpose?: ChargingProfilePurposeType;
    stackLevel?: number;
  }): any {
    const payload: any = {};
    if (criteria?.id !== undefined) payload.id = criteria.id;
    if (criteria?.connectorId !== undefined) payload.connectorId = criteria.connectorId;
    if (criteria?.chargingProfilePurpose) payload.chargingProfilePurpose = criteria.chargingProfilePurpose;
    if (criteria?.stackLevel !== undefined) payload.stackLevel = criteria.stackLevel;
    return payload;
  }

  setChargingProfile(connectorId: number, profileOrPayload: ChargingProfile | any): { status: "Accepted" | "Rejected" } {
    let profile: ChargingProfile | null;

    if (profileOrPayload.chargingSchedule && profileOrPayload.chargingProfileId) {
      profile = profileOrPayload as ChargingProfile;
    } else {
      const parsed = this.parseChargingProfileMessage(profileOrPayload);
      connectorId = parsed.connectorId;
      profile = parsed.profile;
    }

    if (!profile) {
      console.log("[OCPPManager] Profil rejeté - parsing échoué");
      return { status: "Rejected" };
    }

    console.log(`[OCPPManager] Réception profil #${profile.chargingProfileId} pour connecteur ${connectorId}`, profile);

    // Pour un profil Relative, marquer le début si pas déjà fait
    if (profile.chargingProfileKind === "Relative" && !this.transactionStartTimes.has(connectorId)) {
      this.markTransactionStart(connectorId);
    }

    if (!this.profiles.has(connectorId)) {
      this.profiles.set(connectorId, new Map());
    }

    const connectorProfiles = this.profiles.get(connectorId)!;

    // Supprimer les profils de même purpose avec stackLevel <= au nouveau
    for (const [id, existingProfile] of connectorProfiles) {
      if (existingProfile.chargingProfilePurpose === profile.chargingProfilePurpose &&
          existingProfile.stackLevel <= profile.stackLevel) {
        console.log(`[OCPPManager] Suppression profil #${id} (remplacé par #${profile.chargingProfileId})`);
        connectorProfiles.delete(id);
        this.clearTimersForProfile(connectorId, id);
      }
    }

    connectorProfiles.set(profile.chargingProfileId, profile);
    this.scheduleRecalculation(connectorId, profile);

    this.onProfileChange?.({
      type: "SET",
      connectorId,
      profileId: profile.chargingProfileId,
      purpose: profile.chargingProfilePurpose,
      stackLevel: profile.stackLevel,
      profile
    });

    this.recalculate(connectorId);
    return { status: "Accepted" };
  }

  private parseChargingProfileMessage(payload: any): {
    connectorId: number;
    profile: ChargingProfile | null;
  } {
    if (!payload) {
      return { connectorId: 1, profile: null };
    }

    if (Array.isArray(payload) && payload.length >= 4) {
      payload = payload[3];
    }

    const connectorId = Number(payload.connectorId || 1);
    let profile: any = null;

    if (payload.csChargingProfiles) {
      profile = payload.csChargingProfiles;
    } else if (payload.chargingProfile) {
      profile = payload.chargingProfile;
    } else if (payload.chargingProfileId && payload.chargingSchedule) {
      profile = payload;
    }

    if (!profile || !profile.chargingSchedule) {
      console.log("[OCPPManager] Profil invalide - structure non reconnue");
      return { connectorId, profile: null };
    }

    const normalizedProfile: ChargingProfile = {
      chargingProfileId: Number(profile.chargingProfileId || 1),
      transactionId: profile.transactionId,
      stackLevel: Number(profile.stackLevel || 0),
      chargingProfilePurpose: profile.chargingProfilePurpose || "TxProfile",
      chargingProfileKind: profile.chargingProfileKind || "Absolute",
      chargingSchedule: {
        duration: msOrSecToSec(profile.chargingSchedule.duration),
        startSchedule: profile.chargingSchedule.startSchedule,
        chargingRateUnit: profile.chargingSchedule.chargingRateUnit || "W",
        chargingSchedulePeriod: (profile.chargingSchedule.chargingSchedulePeriod || []).map((p: any) => ({
          startPeriod: Number(p.startPeriod || 0),
          limit: Number(p.limit || 0),
          numberPhases: p.numberPhases ? Number(p.numberPhases) : undefined
        })),
        minChargingRate: profile.chargingSchedule.minChargingRate
      },
      recurrencyKind: profile.recurrencyKind as RecurrencyKindType,
      validFrom: profile.validFrom,
      validTo: profile.validTo
    };

    return { connectorId, profile: normalizedProfile };
  }

  private computeLimitForProfile(
      connectorId: number,
      profile: ChargingProfile,
      now = Date.now()
  ): number | null {
    const schedule = profile.chargingSchedule;

    // ═══════════════════════════════════════════════════════════════════
    // VÉRIFIER validFrom/validTo POUR TOUS LES TYPES DE PROFILS (OCPP 1.6)
    // ═══════════════════════════════════════════════════════════════════
    if (profile.validFrom) {
      const validFromMs = Date.parse(profile.validFrom);
      if (validFromMs > now) {
        console.log(`[OCPPManager] Profil #${profile.chargingProfileId}: pas encore valide (validFrom=${profile.validFrom})`);
        return null; // Pas encore valide
      }
    }
    if (profile.validTo) {
      const validToMs = Date.parse(profile.validTo);
      if (validToMs < now) {
        console.log(`[OCPPManager] Profil #${profile.chargingProfileId}: EXPIRÉ (validTo=${profile.validTo}, now=${new Date(now).toISOString()})`);
        return null; // Profil expiré
      }
    }

    let startMs: number;

    if (profile.chargingProfileKind === "Relative") {
      const txStart = this.transactionStartTimes.get(connectorId);
      if (!txStart) {
        console.log(`[OCPPManager] Profil Relative sans transaction active`);
        return null;
      }
      startMs = txStart;
    } else if (profile.chargingProfileKind === "Recurring") {
      // Pour Recurring, calculer le début de la période courante
      const scheduleStart = schedule.startSchedule ? Date.parse(schedule.startSchedule) : now;
      if (profile.recurrencyKind === "Daily") {
        // Aligner sur le début du jour courant
        const startOfDay = new Date(now);
        startOfDay.setHours(0, 0, 0, 0);
        const scheduleTime = new Date(scheduleStart);
        startOfDay.setHours(scheduleTime.getHours(), scheduleTime.getMinutes(), scheduleTime.getSeconds());
        startMs = startOfDay.getTime();

        // Si on est avant l'heure de début aujourd'hui, prendre hier
        if (startMs > now) {
          startMs -= 24 * 60 * 60 * 1000;
        }
      } else if (profile.recurrencyKind === "Weekly") {
        // Aligner sur le début de la semaine courante
        const startOfWeek = new Date(now);
        const day = startOfWeek.getDay();
        const diff = startOfWeek.getDate() - day + (day === 0 ? -6 : 1); // Lundi
        startOfWeek.setDate(diff);
        startOfWeek.setHours(0, 0, 0, 0);
        const scheduleTime = new Date(scheduleStart);
        startOfWeek.setHours(scheduleTime.getHours(), scheduleTime.getMinutes(), scheduleTime.getSeconds());
        startMs = startOfWeek.getTime();

        // Si on est avant le début cette semaine, prendre la semaine dernière
        if (startMs > now) {
          startMs -= 7 * 24 * 60 * 60 * 1000;
        }
      } else {
        startMs = schedule.startSchedule ? Date.parse(schedule.startSchedule) : now - 1000;
      }
    } else {
      startMs = schedule.startSchedule ? Date.parse(schedule.startSchedule) : now - 1000;
    }

    const elapsedSec = Math.max(0, Math.floor((now - startMs) / 1000));

    // Pour Recurring, utiliser modulo pour la répétition
    let effectiveElapsedSec = elapsedSec;
    if (profile.chargingProfileKind === "Recurring" && schedule.duration) {
      effectiveElapsedSec = elapsedSec % schedule.duration;
    } else if (schedule.duration && elapsedSec > schedule.duration) {
      return null; // Profil expiré
    }

    const periods = schedule.chargingSchedulePeriod || [];
    if (periods.length === 0) {
      return null;
    }

    // Trouver la période active
    let activePeriod = null;
    let nextPeriod = null;

    for (let i = 0; i < periods.length; i++) {
      const period = periods[i];
      const nextP = periods[i + 1];

      if (effectiveElapsedSec >= period.startPeriod) {
        if (nextP && effectiveElapsedSec < nextP.startPeriod) {
          activePeriod = period;
          nextPeriod = nextP;
          break;
        } else if (!nextP) {
          activePeriod = period;
          break;
        }
      }
    }

    if (!activePeriod) {
      return null;
    }

    // Ignorer les périodes courtes avec limit=0
    if (activePeriod.limit === 0 && nextPeriod) {
      const periodDuration = nextPeriod.startPeriod - activePeriod.startPeriod;
      if (periodDuration < 10 && effectiveElapsedSec >= activePeriod.startPeriod) {
        console.log(`[OCPPManager] Saut de la période 0A courte, application de ${nextPeriod.limit}${schedule.chargingRateUnit}`);
        activePeriod = nextPeriod;
      }
    }

    // Convertir la limite en Watts
    let limitW = this.toWatts(
        activePeriod.limit,
        schedule.chargingRateUnit,
        activePeriod.numberPhases,
        connectorId
    );

    // Appliquer minChargingRate si défini
    if (schedule.minChargingRate !== undefined) {
      const minW = this.toWatts(
          schedule.minChargingRate,
          schedule.chargingRateUnit,
          activePeriod.numberPhases,
          connectorId
      );
      limitW = Math.max(limitW, minW);
    }

    console.log(`[OCPPManager] Profil #${profile.chargingProfileId}: ${activePeriod.limit}${schedule.chargingRateUnit} = ${limitW}W (elapsed: ${effectiveElapsedSec}s)`);

    return clamp(limitW, 0, this.maxPowerW);
  }

  private pickEffectiveProfile(connectorId: number, now = Date.now()): ProfileApplication {
    const connectorProfiles = this.profiles.get(connectorId);

    if (!connectorProfiles || connectorProfiles.size === 0) {
      return {
        profileId: -1,
        purpose: "ChargePointMaxProfile",
        stackLevel: -1,
        limitW: this.maxPowerW,
        source: "default",
        timestamp: now
      };
    }

    // Trier les profils par priorité
    const sortedProfiles = Array.from(connectorProfiles.values()).sort((a, b) => {
      const purposeDiff = PURPOSE_PRIORITY[b.chargingProfilePurpose] - PURPOSE_PRIORITY[a.chargingProfilePurpose];
      if (purposeDiff !== 0) return purposeDiff;
      return b.stackLevel - a.stackLevel;
    });

    // Chercher le premier profil actif
    for (const profile of sortedProfiles) {
      const limitW = this.computeLimitForProfile(connectorId, profile, now);
      if (limitW != null) {
        // Calculer le temps avant le prochain changement
        let nextChangeIn: number | undefined;
        const schedule = profile.chargingSchedule;
        const periods = schedule.chargingSchedulePeriod || [];

        if (periods.length > 1) {
          let startMs: number;

          if (profile.chargingProfileKind === "Relative") {
            startMs = this.transactionStartTimes.get(connectorId) || now;
          } else {
            startMs = schedule.startSchedule ? Date.parse(schedule.startSchedule) : now;
          }

          const elapsedSec = Math.floor((now - startMs) / 1000);
          for (const period of periods) {
            if (period.startPeriod > elapsedSec) {
              nextChangeIn = period.startPeriod - elapsedSec;
              break;
            }
          }
        }

        console.log(`[OCPPManager] Profil actif: #${profile.chargingProfileId} (${profile.chargingProfilePurpose}) = ${limitW}W`);
        return {
          profileId: profile.chargingProfileId,
          purpose: profile.chargingProfilePurpose,
          stackLevel: profile.stackLevel,
          limitW,
          source: "profile",
          timestamp: now,
          nextChangeIn,
          profileDetails: profile
        };
      }
    }

    console.log(`[OCPPManager] Aucun profil actif, limite physique = ${this.maxPowerW}W`);
    return {
      profileId: -1,
      purpose: "ChargePointMaxProfile",
      stackLevel: -1,
      limitW: this.maxPowerW,
      source: "physical",
      timestamp: now
    };
  }

  private recalculate(connectorId: number) {
    const previous = this.lastAppliedLimit.get(connectorId);
    const current = this.pickEffectiveProfile(connectorId);

    this.effective.set(connectorId, current);

    // Treat undefined as maxPowerW (default state) to avoid logging initial setup
    const effectivePrevious = previous ?? this.maxPowerW;
    if (effectivePrevious !== current.limitW) {
      console.log(`[OCPPManager] Limite changée: ${effectivePrevious}W -> ${current.limitW}W`);
      this.lastAppliedLimit.set(connectorId, current.limitW);
      this.onLimitChange?.(connectorId, current.limitW, current);
    } else if (previous === undefined) {
      // First time initialization - just set the value without logging
      this.lastAppliedLimit.set(connectorId, current.limitW);
    }
  }

  private scheduleRecalculation(connectorId: number, profile: ChargingProfile) {
    const schedule = profile.chargingSchedule;
    const now = Date.now();

    this.clearTimersForProfile(connectorId, profile.chargingProfileId);

    const timers: NodeJS.Timeout[] = [];

    if (profile.chargingProfileKind === "Relative") {
      const txStart = this.transactionStartTimes.get(connectorId) || now;

      for (const period of schedule.chargingSchedulePeriod) {
        const changeTime = txStart + period.startPeriod * 1000;
        if (changeTime > now) {
          const delay = changeTime - now;
          console.log(`[OCPPManager] Timer période dans ${delay}ms pour limite ${period.limit}${schedule.chargingRateUnit}`);
          timers.push(
              setTimeout(() => {
                console.log(`[OCPPManager] Changement période: ${period.limit}${schedule.chargingRateUnit}`);
                this.recalculate(connectorId);
              }, delay)
          );
        }
      }
    } else if (profile.chargingProfileKind === "Recurring") {
      // Pour Recurring, recalculer périodiquement
      const recalcInterval = profile.recurrencyKind === "Daily" ? 60000 : 300000; // 1min ou 5min
      timers.push(
          setInterval(() => {
            this.recalculate(connectorId);
          }, recalcInterval) as any
      );
    }

    if (timers.length > 0) {
      if (!this.timers.has(connectorId)) {
        this.timers.set(connectorId, []);
      }
      this.timers.get(connectorId)!.push(...timers);
    }
  }

  private clearTimersForProfile(connectorId: number, profileId: number) {
    const connectorTimers = this.timers.get(connectorId);
    if (connectorTimers) {
      connectorTimers.forEach(timer => clearTimeout(timer));
      this.timers.set(connectorId, []);
    }
  }

  clearChargingProfile(criteria?: {
    id?: number;
    chargingProfilePurpose?: ChargingProfilePurposeType;
    stackLevel?: number;
    connectorId?: number;
  } | any): { status: "Accepted" | "Unknown"; cleared: number[] } {
    let parsedCriteria = criteria;
    if (criteria && typeof criteria === 'object') {
      parsedCriteria = {
        id: criteria.id || criteria.chargingProfileId,
        chargingProfilePurpose: criteria.chargingProfilePurpose,
        stackLevel: criteria.stackLevel,
        connectorId: criteria.connectorId
      };
    }

    console.log("[OCPPManager] Clear profils avec critères:", parsedCriteria);
    const cleared: number[] = [];

    const connectorsToProcess = parsedCriteria?.connectorId
        ? [parsedCriteria.connectorId]
        : Array.from(this.profiles.keys());

    for (const connectorId of connectorsToProcess) {
      const connectorProfiles = this.profiles.get(connectorId);
      if (!connectorProfiles) continue;

      for (const [profileId, profile] of connectorProfiles) {
        let shouldClear = !parsedCriteria;

        if (parsedCriteria) {
          shouldClear = true;

          if (parsedCriteria.id != null && profileId !== parsedCriteria.id) {
            shouldClear = false;
          }
          if (parsedCriteria.chargingProfilePurpose &&
              profile.chargingProfilePurpose !== parsedCriteria.chargingProfilePurpose) {
            shouldClear = false;
          }
          if (parsedCriteria.stackLevel != null &&
              profile.stackLevel !== parsedCriteria.stackLevel) {
            shouldClear = false;
          }
        }

        if (shouldClear) {
          connectorProfiles.delete(profileId);
          this.clearTimersForProfile(connectorId, profileId);
          cleared.push(profileId);
          console.log(`[OCPPManager] Profil #${profileId} supprimé`);
        }
      }

      this.recalculate(connectorId);
    }

    this.onProfileChange?.({ type: "CLEAR", cleared });

    return {
      status: cleared.length > 0 ? "Accepted" : "Unknown",
      cleared
    };
  }

  reset() {
    console.log("[OCPPManager] Reset complet");

    for (const timers of this.timers.values()) {
      timers.forEach(timer => clearTimeout(timer));
    }

    this.profiles.clear();
    this.effective.clear();
    this.timers.clear();
    this.lastAppliedLimit.clear();
    this.transactionStartTimes.clear();

    for (const connectorId of this.connectors.keys()) {
      this.recalculate(connectorId);
    }
  }

  /**
   * Arrête proprement le manager (cleanup timer, etc.)
   */
  destroy() {
    console.log("[OCPPManager] Destruction du manager");

    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }

    for (const timers of this.timers.values()) {
      timers.forEach(timer => clearTimeout(timer));
    }

    // Cleanup session timers
    for (const timers of this.sessionTimers.values()) {
      timers.forEach(timer => clearTimeout(timer));
    }

    this.profiles.clear();
    this.effective.clear();
    this.timers.clear();
    this.sessions.clear();
    this.sessionProfiles.clear();
    this.sessionTimers.clear();
  }

  // ==========================================================================
  // GESTION MULTI-SESSIONS
  // ==========================================================================

  /**
   * Enregistre une nouvelle session de charge.
   * À appeler quand une session SimuEVSE démarre.
   */
  registerSession(sessionId: string, config: {
    connectorId?: number;
    voltage?: number;
    phases?: number;
    transactionId?: number;
  } = {}) {
    const connectorId = config.connectorId ?? 1;
    const voltage = config.voltage ?? 230;
    const phases = config.phases ?? 1;

    const state: SessionChargingState = {
      sessionId,
      transactionId: config.transactionId,
      transactionStartTime: config.transactionId ? Date.now() : undefined,
      profiles: new Map(),
      effectiveLimit: {
        profileId: -1,
        purpose: "ChargePointMaxProfile",
        stackLevel: -1,
        limitW: this.maxPowerW,
        source: "default",
        timestamp: Date.now(),
        sessionId
      },
      connectorId
    };

    this.sessions.set(sessionId, state);
    this.sessionProfiles.set(sessionId, new Map());
    this.connectors.set(connectorId, { voltage, phases });

    console.log(`[OCPPManager] 📋 Session enregistrée: ${sessionId} (connector=${connectorId}, tx=${config.transactionId})`);
  }

  /**
   * Désenregistre une session et supprime tous ses profils TxProfile.
   * À appeler quand une session SimuEVSE se termine.
   */
  unregisterSession(sessionId: string, clearTxProfiles = true) {
    const session = this.sessions.get(sessionId);
    if (!session) return;

    // Supprimer les timers de la session
    const timers = this.sessionTimers.get(sessionId);
    if (timers) {
      timers.forEach(timer => clearTimeout(timer));
      this.sessionTimers.delete(sessionId);
    }

    // Supprimer les TxProfile liés à cette session (pas les TxDefaultProfile ni ChargePointMaxProfile)
    if (clearTxProfiles) {
      const profiles = this.sessionProfiles.get(sessionId);
      if (profiles) {
        for (const [profileId, profile] of profiles) {
          if (profile.chargingProfilePurpose === "TxProfile") {
            profiles.delete(profileId);
            console.log(`[OCPPManager] 🗑️ TxProfile #${profileId} supprimé (fin session ${sessionId})`);
          }
        }
      }
    }

    this.sessions.delete(sessionId);
    console.log(`[OCPPManager] 📋 Session désenregistrée: ${sessionId}`);

    this.onProfileChange?.({ type: "SESSION_END", sessionId });
  }

  /**
   * Marque le début d'une transaction pour une session.
   * Important pour les profils Relative.
   */
  startSessionTransaction(sessionId: string, transactionId: number) {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.transactionId = transactionId;
      session.transactionStartTime = Date.now();
      console.log(`[OCPPManager] 🔌 Transaction ${transactionId} démarrée pour session ${sessionId}`);
    }
    // Aussi mettre à jour le connecteur pour compatibilité
    this.markTransactionStart(session?.connectorId ?? 1);
  }

  /**
   * Marque la fin d'une transaction pour une session.
   * Supprime automatiquement les TxProfile liés.
   */
  stopSessionTransaction(sessionId: string) {
    const session = this.sessions.get(sessionId);
    if (!session) return;

    const transactionId = session.transactionId;
    session.transactionId = undefined;
    session.transactionStartTime = undefined;

    // Supprimer les TxProfile liés à cette transaction
    const profiles = this.sessionProfiles.get(sessionId);
    if (profiles) {
      for (const [profileId, profile] of profiles) {
        if (profile.chargingProfilePurpose === "TxProfile") {
          if (!profile.transactionId || profile.transactionId === transactionId) {
            profiles.delete(profileId);
            console.log(`[OCPPManager] 🗑️ TxProfile #${profileId} supprimé (fin tx ${transactionId})`);
          }
        }
      }
    }

    this.markTransactionStop(session.connectorId);
    this.recalculateSession(sessionId);

    console.log(`[OCPPManager] 🔌 Transaction ${transactionId} terminée pour session ${sessionId}`);
  }

  /**
   * Applique un profil de charge à une session spécifique.
   */
  setSessionChargingProfile(
    sessionId: string,
    profile: ChargingProfile
  ): { status: "Accepted" | "Rejected" } {
    const session = this.sessions.get(sessionId);
    if (!session) {
      // Auto-register session if not exists
      this.registerSession(sessionId);
    }

    const profiles = this.sessionProfiles.get(sessionId) ?? new Map();

    // Validation TxProfile: doit avoir une transaction active
    if (profile.chargingProfilePurpose === "TxProfile") {
      const currentSession = this.sessions.get(sessionId);
      if (!currentSession?.transactionId && !profile.transactionId) {
        console.warn(`[OCPPManager] ⚠️ TxProfile sans transaction active - session ${sessionId}`);
        // On accepte quand même mais on log un warning
      }
      // Associer le profil à la transaction courante si non spécifié
      if (!profile.transactionId && currentSession?.transactionId) {
        profile.transactionId = currentSession.transactionId;
      }
    }

    // Supprimer les profils de même purpose avec stackLevel <= au nouveau
    for (const [id, existingProfile] of profiles) {
      if (existingProfile.chargingProfilePurpose === profile.chargingProfilePurpose &&
          existingProfile.stackLevel <= profile.stackLevel) {
        profiles.delete(id);
        console.log(`[OCPPManager] Profil #${id} remplacé par #${profile.chargingProfileId}`);
      }
    }

    // Ajouter le profil avec métadonnées
    const enrichedProfile: ChargingProfile = {
      ...profile,
      sessionId,
      connectorId: session?.connectorId ?? 1,
      appliedAt: Date.now()
    };

    profiles.set(profile.chargingProfileId, enrichedProfile);
    this.sessionProfiles.set(sessionId, profiles);

    // Programmer les recalculs pour les périodes
    this.scheduleSessionRecalculation(sessionId, enrichedProfile);

    console.log(`[OCPPManager] ✅ Profil #${profile.chargingProfileId} appliqué à session ${sessionId}`,
      `(${profile.chargingProfilePurpose}, stack=${profile.stackLevel})`);

    this.recalculateSession(sessionId);

    this.onProfileChange?.({
      type: "SET",
      sessionId,
      profileId: profile.chargingProfileId,
      purpose: profile.chargingProfilePurpose,
      profile: enrichedProfile
    });

    return { status: "Accepted" };
  }

  /**
   * Supprime des profils d'une session selon les critères OCPP.
   */
  clearSessionChargingProfile(
    sessionId: string,
    criteria?: {
      id?: number;
      chargingProfilePurpose?: ChargingProfilePurposeType;
      stackLevel?: number;
    }
  ): { status: "Accepted" | "Unknown"; cleared: number[] } {
    const profiles = this.sessionProfiles.get(sessionId);
    if (!profiles) {
      return { status: "Unknown", cleared: [] };
    }

    const cleared: number[] = [];

    for (const [profileId, profile] of profiles) {
      let shouldClear = !criteria; // Si pas de critères, tout supprimer

      if (criteria) {
        shouldClear = true;

        if (criteria.id !== undefined && profileId !== criteria.id) {
          shouldClear = false;
        }
        if (criteria.chargingProfilePurpose &&
            profile.chargingProfilePurpose !== criteria.chargingProfilePurpose) {
          shouldClear = false;
        }
        if (criteria.stackLevel !== undefined &&
            profile.stackLevel !== criteria.stackLevel) {
          shouldClear = false;
        }
      }

      if (shouldClear) {
        profiles.delete(profileId);
        cleared.push(profileId);
        console.log(`[OCPPManager] 🗑️ Profil #${profileId} supprimé de session ${sessionId}`);
      }
    }

    if (cleared.length > 0) {
      this.recalculateSession(sessionId);
      this.onProfileChange?.({ type: "CLEAR", sessionId, cleared });
    }

    return {
      status: cleared.length > 0 ? "Accepted" : "Unknown",
      cleared
    };
  }

  /**
   * Recalcule la limite effective pour une session.
   */
  private recalculateSession(sessionId: string) {
    const session = this.sessions.get(sessionId);
    const profiles = this.sessionProfiles.get(sessionId);

    if (!session || !profiles) return;

    const now = Date.now();

    // Filtrer les profils actifs
    const activeProfiles = Array.from(profiles.values())
      .filter(p => this.isProfileActive(p, now));

    if (activeProfiles.length === 0) {
      session.effectiveLimit = {
        profileId: -1,
        purpose: "ChargePointMaxProfile",
        stackLevel: -1,
        limitW: this.maxPowerW,
        source: "default",
        timestamp: now,
        sessionId
      };
      this.onSessionLimitChange?.(sessionId, this.maxPowerW, session.effectiveLimit);
      return;
    }

    // Trier par priorité
    const sortedProfiles = activeProfiles.sort((a, b) => {
      const purposeDiff = PURPOSE_PRIORITY[b.chargingProfilePurpose] - PURPOSE_PRIORITY[a.chargingProfilePurpose];
      if (purposeDiff !== 0) return purposeDiff;
      return b.stackLevel - a.stackLevel;
    });

    // Prendre le profil le plus prioritaire avec une limite active
    const config = this.connectors.get(session.connectorId) || { voltage: 230, phases: 1 };

    for (const profile of sortedProfiles) {
      const limitW = this.computeSessionProfileLimit(sessionId, profile, now);
      if (limitW !== null) {
        const newLimit: ProfileApplication = {
          profileId: profile.chargingProfileId,
          purpose: profile.chargingProfilePurpose,
          stackLevel: profile.stackLevel,
          limitW,
          source: "profile",
          timestamp: now,
          profileDetails: profile,
          sessionId
        };

        // Notifier si changement
        if (session.effectiveLimit.limitW !== limitW) {
          console.log(`[OCPPManager] Session ${sessionId}: limite ${session.effectiveLimit.limitW}W -> ${limitW}W`);
          session.effectiveLimit = newLimit;
          this.onSessionLimitChange?.(sessionId, limitW, newLimit);
        } else {
          session.effectiveLimit = newLimit;
        }
        return;
      }
    }

    // Aucun profil actif
    session.effectiveLimit = {
      profileId: -1,
      purpose: "ChargePointMaxProfile",
      stackLevel: -1,
      limitW: this.maxPowerW,
      source: "default",
      timestamp: now,
      sessionId
    };
    this.onSessionLimitChange?.(sessionId, this.maxPowerW, session.effectiveLimit);
  }

  /**
   * Calcule la limite d'un profil pour une session.
   */
  private computeSessionProfileLimit(
    sessionId: string,
    profile: ChargingProfile,
    now: number
  ): number | null {
    const session = this.sessions.get(sessionId);
    if (!session) return null;

    const schedule = profile.chargingSchedule;
    const config = this.connectors.get(session.connectorId) || { voltage: 230, phases: 1 };

    // Déterminer le temps de début
    let startMs: number;

    if (profile.chargingProfileKind === "Relative") {
      // Pour Relative, utiliser le début de transaction
      if (!session.transactionStartTime) {
        return null; // Pas de transaction active
      }
      startMs = session.transactionStartTime;
    } else if (profile.chargingProfileKind === "Recurring") {
      // Pour Recurring, calculer le cycle courant
      const scheduleStart = schedule.startSchedule ? Date.parse(schedule.startSchedule) : now;
      if (profile.recurrencyKind === "Daily") {
        const startOfDay = new Date(now);
        startOfDay.setHours(0, 0, 0, 0);
        const scheduleTime = new Date(scheduleStart);
        startOfDay.setHours(scheduleTime.getHours(), scheduleTime.getMinutes(), scheduleTime.getSeconds());
        startMs = startOfDay.getTime();
        if (startMs > now) startMs -= 24 * 60 * 60 * 1000;
      } else if (profile.recurrencyKind === "Weekly") {
        const startOfWeek = new Date(now);
        const day = startOfWeek.getDay();
        startOfWeek.setDate(startOfWeek.getDate() - day + (day === 0 ? -6 : 1));
        startOfWeek.setHours(0, 0, 0, 0);
        startMs = startOfWeek.getTime();
        if (startMs > now) startMs -= 7 * 24 * 60 * 60 * 1000;
      } else {
        startMs = scheduleStart;
      }
    } else {
      // Absolute
      startMs = schedule.startSchedule ? Date.parse(schedule.startSchedule) : (profile.appliedAt ?? now);
    }

    const elapsedSec = Math.max(0, Math.floor((now - startMs) / 1000));

    // Pour Recurring avec duration, utiliser modulo
    let effectiveElapsedSec = elapsedSec;
    if (profile.chargingProfileKind === "Recurring" && schedule.duration) {
      effectiveElapsedSec = elapsedSec % schedule.duration;
    } else if (schedule.duration && elapsedSec > schedule.duration) {
      return null; // Profil expiré
    }

    // Trouver la période active
    const periods = schedule.chargingSchedulePeriod || [];
    if (periods.length === 0) return null;

    let activePeriod: ChargingSchedulePeriod | null = null;
    for (const period of periods) {
      if (period.startPeriod <= effectiveElapsedSec) {
        activePeriod = period;
      } else {
        break;
      }
    }

    if (!activePeriod) return null;

    // Convertir en Watts
    let limitW: number;
    if (schedule.chargingRateUnit === "W") {
      limitW = activePeriod.limit;
    } else {
      const phases = activePeriod.numberPhases || config.phases || 1;
      limitW = activePeriod.limit * config.voltage * phases;
    }

    // Appliquer minChargingRate
    if (schedule.minChargingRate !== undefined) {
      const minW = schedule.chargingRateUnit === "W"
        ? schedule.minChargingRate
        : schedule.minChargingRate * config.voltage * (config.phases || 1);
      limitW = Math.max(limitW, minW);
    }

    return clamp(limitW, 0, this.maxPowerW);
  }

  /**
   * Programme les recalculs pour les changements de période d'un profil de session.
   */
  private scheduleSessionRecalculation(sessionId: string, profile: ChargingProfile) {
    const session = this.sessions.get(sessionId);
    if (!session) return;

    const schedule = profile.chargingSchedule;
    const now = Date.now();

    // Nettoyer les anciens timers
    let timers = this.sessionTimers.get(sessionId) || [];
    this.sessionTimers.set(sessionId, []);

    if (profile.chargingProfileKind === "Relative" && session.transactionStartTime) {
      for (const period of schedule.chargingSchedulePeriod) {
        const changeTime = session.transactionStartTime + period.startPeriod * 1000;
        if (changeTime > now) {
          const delay = changeTime - now;
          const timer = setTimeout(() => {
            console.log(`[OCPPManager] ⏰ Changement période session ${sessionId}`);
            this.recalculateSession(sessionId);
          }, delay);
          timers.push(timer);
        }
      }
    }

    if (timers.length > 0) {
      this.sessionTimers.set(sessionId, timers);
    }
  }

  /**
   * Obtient l'état complet d'une session.
   */
  getSessionState(sessionId: string): {
    session: SessionChargingState | null;
    profiles: ChargingProfile[];
    effectiveLimit: ProfileApplication;
  } {
    const session = this.sessions.get(sessionId);
    const profiles = this.sessionProfiles.get(sessionId);
    const now = Date.now();

    if (!session) {
      return {
        session: null,
        profiles: [],
        effectiveLimit: {
          profileId: -1,
          purpose: "ChargePointMaxProfile",
          stackLevel: -1,
          limitW: this.maxPowerW,
          source: "default",
          timestamp: now
        }
      };
    }

    // Filtrer les profils expirés
    const activeProfiles = Array.from(profiles?.values() || [])
      .filter(p => this.isProfileActive(p, now));

    return {
      session,
      profiles: activeProfiles,
      effectiveLimit: session.effectiveLimit
    };
  }

  /**
   * Obtient la limite actuelle en Watts pour une session.
   */
  getSessionCurrentLimitW(sessionId: string): number {
    const session = this.sessions.get(sessionId);
    return session?.effectiveLimit.limitW ?? this.maxPowerW;
  }

  /**
   * Obtient la limite actuelle en kW pour une session.
   */
  getSessionCurrentLimitKw(sessionId: string): number {
    return this.getSessionCurrentLimitW(sessionId) / 1000;
  }

  /**
   * Liste toutes les sessions enregistrées.
   */
  getAllSessions(): string[] {
    return Array.from(this.sessions.keys());
  }

  /**
   * Vérifie si une session existe.
   */
  hasSession(sessionId: string): boolean {
    return this.sessions.has(sessionId);
  }

  /**
   * Vérifie si un profil est actif (non expiré).
   */
  private isProfileActive(profile: ChargingProfile, now = Date.now()): boolean {
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
    if (profile.chargingProfileKind === "Absolute" && profile.chargingSchedule.duration) {
      const startMs = profile.chargingSchedule.startSchedule
        ? Date.parse(profile.chargingSchedule.startSchedule)
        : now;
      const endMs = startMs + profile.chargingSchedule.duration * 1000;
      if (endMs < now) {
        return false; // Durée expirée
      }
    }

    return true;
  }

  getConnectorState(connectorId: number): {
    profiles: ChargingProfile[];
    effectiveLimit: ProfileApplication;
  } {
    const now = Date.now();
    const effectiveLimit = this.effective.get(connectorId) || this.pickEffectiveProfile(connectorId);

    // Filtrer les profils expirés pour l'affichage
    const allProfiles = Array.from(this.profiles.get(connectorId)?.values() || []);
    const activeProfiles = allProfiles.filter(p => this.isProfileActive(p, now));

    return {
      profiles: activeProfiles,
      effectiveLimit
    };
  }

  getCurrentLimitW(connectorId: number = 1): number {
    const state = this.getConnectorState(connectorId);
    return state.effectiveLimit.limitW;
  }

  getAllProfiles(includeExpired = false): ChargingProfile[] {
    const now = Date.now();
    const allProfiles: ChargingProfile[] = [];
    for (const profilesMap of this.profiles.values()) {
      allProfiles.push(...profilesMap.values());
    }

    if (includeExpired) {
      return allProfiles;
    }

    // Filtrer les profils expirés par défaut
    return allProfiles.filter(p => this.isProfileActive(p, now));
  }

  exportState() {
    const state: any = {
      maxPowerW: this.maxPowerW,
      connectors: {},
      profiles: {},
      effective: {},
      transactionStartTimes: {}
    };

    for (const [id, config] of this.connectors) {
      state.connectors[id] = config;
    }

    for (const [connectorId, profilesMap] of this.profiles) {
      state.profiles[connectorId] = Array.from(profilesMap.values());
    }

    for (const [connectorId, limit] of this.effective) {
      state.effective[connectorId] = limit;
    }

    for (const [connectorId, time] of this.transactionStartTimes) {
      state.transactionStartTimes[connectorId] = time;
    }

    return state;
  }
}