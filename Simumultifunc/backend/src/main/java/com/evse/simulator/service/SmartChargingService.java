package com.evse.simulator.service;

import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.*;
import com.evse.simulator.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service de gestion du Smart Charging conforme OCPP 1.6.
 *
 * Hiérarchie des profils:
 * 1. ChargePointMaxProfile (connectorId=0) - Limite GLOBALE, priorité la plus haute
 * 2. TxDefaultProfile (connectorId=0 ou spécifique) - Limite par défaut pour transactions
 * 3. TxProfile (connectorId spécifique) - Limite pour UNE transaction spécifique
 *
 * LIMITE EFFECTIVE = MIN(ChargePointMaxProfile, TxDefaultProfile, TxProfile)
 *
 * Stack Levels: Plus le stackLevel est ÉLEVÉ, plus le profil est PRIORITAIRE
 * au sein d'un même ChargingProfilePurpose.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmartChargingService implements com.evse.simulator.domain.service.SmartChargingService {

    private final SessionService sessionService;

    // ═══════════════════════════════════════════════════════════════════
    // STOCKAGE DES PROFILS PAR TYPE
    // ═══════════════════════════════════════════════════════════════════

    // ChargePointMaxProfile: stackLevel -> Profile (un seul par stackLevel)
    private final Map<Integer, ChargingProfile> chargePointMaxProfiles = new ConcurrentHashMap<>();

    // TxDefaultProfile: connectorId -> (stackLevel -> Profile)
    // connectorId = 0 pour global
    private final Map<Integer, Map<Integer, ChargingProfile>> txDefaultProfiles = new ConcurrentHashMap<>();

    // TxProfile: sessionId -> (stackLevel -> Profile)
    private final Map<String, Map<Integer, ChargingProfile>> txProfiles = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // SET CHARGING PROFILE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applique un profil de charge selon OCPP 1.6.
     *
     * @param sessionId ID de la session (pour TxProfile)
     * @param profile Le profil à appliquer
     * @return "Accepted", "Rejected", ou "NotSupported"
     */
    public String setChargingProfile(String sessionId, ChargingProfile profile) {
        Session session = sessionService.getSession(sessionId);
        int connectorId = session != null ? session.getConnectorId() : 0;

        return setChargingProfile(connectorId, sessionId, profile);
    }

    /**
     * Applique un profil de charge avec connectorId explicite.
     */
    public String setChargingProfile(int connectorId, String sessionId, ChargingProfile profile) {
        log.info("[SCP] SetChargingProfile: connectorId={}, purpose={}, stackLevel={}, profileId={}",
            connectorId, profile.getChargingProfilePurpose(),
            profile.getStackLevel(), profile.getChargingProfileId());

        // ─── VALIDATION ───────────────────────────────────────────────
        String validationError = validateProfile(connectorId, profile);
        if (validationError != null) {
            log.warn("[SCP] Validation failed: {}", validationError);
            return "Rejected";
        }

        // ─── APPLICATION SELON LE PURPOSE ─────────────────────────────
        profile.setConnectorId(connectorId);
        profile.setSessionId(sessionId);
        profile.setAppliedAt(LocalDateTime.now());

        switch (profile.getChargingProfilePurpose()) {
            case CHARGE_POINT_MAX_PROFILE -> {
                applyChargePointMaxProfile(profile);
            }
            case TX_DEFAULT_PROFILE -> {
                applyTxDefaultProfile(connectorId, profile);
            }
            case TX_PROFILE -> {
                applyTxProfile(sessionId, profile);
            }
        }

        // ─── METTRE À JOUR LA SESSION ─────────────────────────────────
        if (sessionId != null) {
            Session session = sessionService.getSession(sessionId);
            if (session != null) {
                updateSessionWithEffectiveLimit(session);
            }
        }

        logProfileSummary();
        return "Accepted";
    }

    /**
     * Validation complète du profil selon OCPP 1.6.
     */
    private String validateProfile(int connectorId, ChargingProfile profile) {
        // ID obligatoire et positif
        if (profile.getChargingProfileId() < 0) {
            return "Invalid chargingProfileId";
        }

        // StackLevel obligatoire et >= 0
        if (profile.getStackLevel() < 0) {
            return "Invalid stackLevel";
        }

        // Purpose obligatoire
        if (profile.getChargingProfilePurpose() == null) {
            return "Missing chargingProfilePurpose";
        }

        // Kind obligatoire
        if (profile.getChargingProfileKind() == null) {
            return "Missing chargingProfileKind";
        }

        // Schedule obligatoire
        if (profile.getChargingSchedule() == null) {
            return "Missing chargingSchedule";
        }

        // Périodes obligatoires
        if (profile.getChargingSchedule().getChargingSchedulePeriod() == null ||
            profile.getChargingSchedule().getChargingSchedulePeriod().isEmpty()) {
            return "Missing chargingSchedulePeriod";
        }

        // ChargingRateUnit obligatoire
        if (profile.getChargingSchedule().getChargingRateUnit() == null) {
            return "Missing chargingRateUnit";
        }

        // Règles spécifiques par purpose
        switch (profile.getChargingProfilePurpose()) {
            case CHARGE_POINT_MAX_PROFILE -> {
                if (connectorId != 0) {
                    return "ChargePointMaxProfile must have connectorId=0";
                }
            }
            case TX_PROFILE -> {
                if (connectorId == 0) {
                    return "TxProfile cannot have connectorId=0";
                }
            }
            case TX_DEFAULT_PROFILE -> {
                // Peut être global (0) ou spécifique
            }
        }

        // Recurring doit avoir recurrencyKind
        if (profile.getChargingProfileKind() == ChargingProfileKind.RECURRING) {
            if (profile.getRecurrencyKind() == null) {
                return "Recurring profile requires recurrencyKind";
            }
        }

        return null; // Valid
    }

    private void applyChargePointMaxProfile(ChargingProfile profile) {
        // Remplace le profil existant au même stackLevel
        chargePointMaxProfiles.put(profile.getStackLevel(), profile);
        log.info("[SCP] ChargePointMaxProfile applied: stackLevel={}, limit={}",
            profile.getStackLevel(), getFirstPeriodLimit(profile));
    }

    private void applyTxDefaultProfile(int connectorId, ChargingProfile profile) {
        txDefaultProfiles
            .computeIfAbsent(connectorId, k -> new ConcurrentHashMap<>())
            .put(profile.getStackLevel(), profile);
        log.info("[SCP] TxDefaultProfile applied: connectorId={}, stackLevel={}, limit={}",
            connectorId, profile.getStackLevel(), getFirstPeriodLimit(profile));
    }

    private void applyTxProfile(String sessionId, ChargingProfile profile) {
        if (sessionId == null) {
            log.warn("[SCP] Cannot apply TxProfile without sessionId");
            return;
        }
        txProfiles
            .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .put(profile.getStackLevel(), profile);
        log.info("[SCP] TxProfile applied: sessionId={}, stackLevel={}, limit={}",
            sessionId, profile.getStackLevel(), getFirstPeriodLimit(profile));
    }

    private Double getFirstPeriodLimit(ChargingProfile profile) {
        if (profile.getChargingSchedule() != null &&
            profile.getChargingSchedule().getChargingSchedulePeriod() != null &&
            !profile.getChargingSchedule().getChargingSchedulePeriod().isEmpty()) {
            return profile.getChargingSchedule().getChargingSchedulePeriod().get(0).getLimit();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEAR CHARGING PROFILE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Supprime les profils selon les critères OCPP 1.6.
     * Tous les critères sont optionnels et combinés en AND.
     *
     * @param sessionId ID de la session (pour TxProfile)
     * @param connectorId ID du connecteur (pour TxDefaultProfile, 0 = global)
     * @param chargingProfileId ID spécifique du profil à supprimer
     * @param stackLevel Niveau de pile pour filtrer
     * @param purpose Type de profil à supprimer
     * @return "Accepted" si profils supprimés, "Unknown" sinon
     */
    public String clearChargingProfile(String sessionId, Integer connectorId,
                                       Integer chargingProfileId, Integer stackLevel,
                                       ChargingProfilePurpose purpose) {
        log.info("[SCP] ClearChargingProfile: sessionId={}, connectorId={}, id={}, stackLevel={}, purpose={}",
            sessionId, connectorId, chargingProfileId, stackLevel, purpose);

        int removedCount = 0;

        // ─── Cas 1: ID spécifique ─────────────────────────────────────
        if (chargingProfileId != null) {
            removedCount = removeProfileById(chargingProfileId);
            log.info("[SCP] Removed {} profile(s) by ID", removedCount);
            return removedCount > 0 ? "Accepted" : "Unknown";
        }

        // ─── Cas 2: Critères multiples ────────────────────────────────

        // Clear ChargePointMaxProfile (connectorId doit être 0 ou null)
        if (purpose == null || purpose == ChargingProfilePurpose.CHARGE_POINT_MAX_PROFILE) {
            if (connectorId == null || connectorId == 0) {
                removedCount += clearChargePointMaxProfiles(stackLevel);
            }
        }

        // Clear TxDefaultProfile - AVEC filtrage par connectorId
        if (purpose == null || purpose == ChargingProfilePurpose.TX_DEFAULT_PROFILE) {
            removedCount += clearTxDefaultProfiles(connectorId, stackLevel);
        }

        // Clear TxProfile
        if (purpose == null || purpose == ChargingProfilePurpose.TX_PROFILE) {
            if (sessionId != null) {
                removedCount += clearTxProfilesForSession(sessionId, stackLevel);
            } else {
                removedCount += clearAllTxProfiles(stackLevel);
            }
        }

        log.info("[SCP] Total cleared: {} profiles", removedCount);
        return removedCount > 0 ? "Accepted" : "Unknown";
    }

    /**
     * Version legacy pour compatibilité arrière (sans connectorId).
     * @deprecated Utiliser clearChargingProfile avec connectorId
     */
    @Deprecated
    public String clearChargingProfile(String sessionId, Integer chargingProfileId,
                                       Integer stackLevel, ChargingProfilePurpose purpose) {
        return clearChargingProfile(sessionId, null, chargingProfileId, stackLevel, purpose);
    }

    private int removeProfileById(int profileId) {
        int count = 0;

        // ChargePointMaxProfiles
        var cpIt = chargePointMaxProfiles.entrySet().iterator();
        while (cpIt.hasNext()) {
            if (cpIt.next().getValue().getChargingProfileId() == profileId) {
                cpIt.remove();
                count++;
            }
        }

        // TxDefaultProfiles
        for (var connectorProfiles : txDefaultProfiles.values()) {
            var it = connectorProfiles.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().getChargingProfileId() == profileId) {
                    it.remove();
                    count++;
                }
            }
        }

        // TxProfiles
        for (var sessionProfiles : txProfiles.values()) {
            var it = sessionProfiles.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().getChargingProfileId() == profileId) {
                    it.remove();
                    count++;
                }
            }
        }

        return count;
    }

    private int clearChargePointMaxProfiles(Integer stackLevel) {
        if (stackLevel != null) {
            return chargePointMaxProfiles.remove(stackLevel) != null ? 1 : 0;
        } else {
            int count = chargePointMaxProfiles.size();
            chargePointMaxProfiles.clear();
            return count;
        }
    }

    /**
     * Supprime les TxDefaultProfiles selon les critères.
     *
     * @param connectorId Si null ou 0: tous les connecteurs. Sinon: uniquement ce connecteur.
     * @param stackLevel Si null: tous les stackLevels. Sinon: uniquement ce niveau.
     * @return Le nombre de profils supprimés.
     */
    private int clearTxDefaultProfiles(Integer connectorId, Integer stackLevel) {
        int count = 0;

        // Si connectorId est spécifié (et != 0), ne traiter que ce connecteur
        if (connectorId != null && connectorId != 0) {
            var connectorProfiles = txDefaultProfiles.get(connectorId);
            if (connectorProfiles != null) {
                if (stackLevel != null) {
                    if (connectorProfiles.remove(stackLevel) != null) count++;
                } else {
                    count += connectorProfiles.size();
                    connectorProfiles.clear();
                }
            }
            log.debug("[SCP] Cleared TxDefaultProfiles for connector {}: {} removed", connectorId, count);
        } else {
            // connectorId null ou 0: traiter TOUS les connecteurs
            for (var entry : txDefaultProfiles.entrySet()) {
                var connectorProfiles = entry.getValue();
                if (stackLevel != null) {
                    if (connectorProfiles.remove(stackLevel) != null) count++;
                } else {
                    count += connectorProfiles.size();
                    connectorProfiles.clear();
                }
            }
            log.debug("[SCP] Cleared TxDefaultProfiles for ALL connectors: {} removed", count);
        }

        return count;
    }

    private int clearTxProfilesForSession(String sessionId, Integer stackLevel) {
        var sessionProfiles = txProfiles.get(sessionId);
        if (sessionProfiles == null) return 0;

        if (stackLevel != null) {
            return sessionProfiles.remove(stackLevel) != null ? 1 : 0;
        } else {
            int count = sessionProfiles.size();
            sessionProfiles.clear();
            return count;
        }
    }

    private int clearAllTxProfiles(Integer stackLevel) {
        int count = 0;
        for (var sessionProfiles : txProfiles.values()) {
            if (stackLevel != null) {
                if (sessionProfiles.remove(stackLevel) != null) count++;
            } else {
                count += sessionProfiles.size();
                sessionProfiles.clear();
            }
        }
        return count;
    }

    /**
     * Supprime tous les TxProfiles d'une session (appelé à StopTransaction).
     */
    public void clearTxProfilesOnStopTransaction(String sessionId) {
        var removed = txProfiles.remove(sessionId);
        if (removed != null && !removed.isEmpty()) {
            log.info("[SCP] Cleared {} TxProfiles for ended session {}", removed.size(), sessionId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CALCUL DE LA LIMITE EFFECTIVE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calcule la limite de puissance effective pour une session.
     * LIMITE = MIN(ChargePointMaxProfile, TxDefaultProfile, TxProfile)
     *
     * @return Limite en kW, ou session.maxPowerKw si aucun profil
     */
    @Override
    public double getCurrentLimit(String sessionId) {
        Session session = sessionService.getSession(sessionId);
        if (session == null) {
            log.warn("[SCP] getCurrentLimit: session {} not found", sessionId);
            return Double.MAX_VALUE;
        }

        // Log l'état des profils pour debug (niveau INFO pour visibilité)
        log.info("[SCP] getCurrentLimit({}): TxProfiles count={}, hasSession={}, profiles={}",
            sessionId, txProfiles.size(), txProfiles.containsKey(sessionId),
            txProfiles.containsKey(sessionId) ? txProfiles.get(sessionId).keySet() : "none");

        EffectiveLimit limit = getEffectiveLimit(sessionId, session.getConnectorId(),
            session.getVoltage(), session.getChargerType().getPhases());

        if (limit.effectiveLimitKw != null) {
            log.info("[SCP] getCurrentLimit({}): returning {} kW from {}",
                sessionId, limit.effectiveLimitKw, limit.appliedPurpose);
            return limit.effectiveLimitKw;
        }
        log.info("[SCP] getCurrentLimit({}): no limit, returning maxPower {} kW",
            sessionId, session.getMaxPowerKw());
        return session.getMaxPowerKw();
    }

    /**
     * Calcule la limite effective avec tous les détails.
     */
    public EffectiveLimit getEffectiveLimit(String sessionId, int connectorId,
                                            double voltage, int phases) {
        LocalDateTime now = LocalDateTime.now();

        Double chargePointMaxLimitKw = null;
        Double txDefaultLimitKw = null;
        Double txLimitKw = null;
        String appliedPurpose = null;
        Integer appliedProfileId = null;
        Integer appliedStackLevel = null;

        // ─── 1. ChargePointMaxProfile ─────────────────────────────────
        // Prendre le profil valide avec le stackLevel le plus élevé
        ChargingProfile cpMaxProfile = getHighestValidProfile(chargePointMaxProfiles.values(), now);
        if (cpMaxProfile != null) {
            chargePointMaxLimitKw = calculateLimitKw(cpMaxProfile, now, voltage, phases);
            log.debug("[SCP] ChargePointMaxProfile limit: {} kW", chargePointMaxLimitKw);
        }

        // ─── 2. TxDefaultProfile ──────────────────────────────────────
        // D'abord global (connectorId=0), puis spécifique
        ChargingProfile txDefaultProfile = null;

        // Global
        var globalTxDefault = txDefaultProfiles.get(0);
        if (globalTxDefault != null) {
            txDefaultProfile = getHighestValidProfile(globalTxDefault.values(), now);
        }

        // Spécifique (override si stackLevel plus élevé)
        if (connectorId > 0) {
            var connectorTxDefault = txDefaultProfiles.get(connectorId);
            if (connectorTxDefault != null) {
                var connectorProfile = getHighestValidProfile(connectorTxDefault.values(), now);
                if (connectorProfile != null) {
                    if (txDefaultProfile == null ||
                        connectorProfile.getStackLevel() > txDefaultProfile.getStackLevel()) {
                        txDefaultProfile = connectorProfile;
                    }
                }
            }
        }

        if (txDefaultProfile != null) {
            txDefaultLimitKw = calculateLimitKw(txDefaultProfile, now, voltage, phases);
            log.debug("[SCP] TxDefaultProfile limit: {} kW (stackLevel={})",
                txDefaultLimitKw, txDefaultProfile.getStackLevel());
        }

        // ─── 3. TxProfile ─────────────────────────────────────────────
        if (sessionId != null) {
            var sessionProfiles = txProfiles.get(sessionId);
            log.info("[SCP] TxProfile lookup for session {}: found={}, profiles={}",
                sessionId, sessionProfiles != null, sessionProfiles != null ? sessionProfiles.size() : 0);
            if (sessionProfiles != null) {
                // Log chaque profil pour debug
                for (var entry : sessionProfiles.entrySet()) {
                    ChargingProfile p = entry.getValue();
                    boolean isValid = isProfileValid(p, now);
                    log.info("[SCP] TxProfile stackLevel={}: id={}, purpose={}, validFrom={}, validTo={}, now={}, isValid={}",
                        entry.getKey(), p.getChargingProfileId(), p.getChargingProfilePurpose(),
                        p.getValidFrom(), p.getValidTo(), now, isValid);
                }
                ChargingProfile txProfile = getHighestValidProfile(sessionProfiles.values(), now);
                if (txProfile != null) {
                    txLimitKw = calculateLimitKw(txProfile, now, voltage, phases);
                    log.info("[SCP] TxProfile limit: {} kW (stackLevel={}, profileId={})",
                        txLimitKw, txProfile.getStackLevel(), txProfile.getChargingProfileId());
                } else {
                    log.info("[SCP] No valid TxProfile found for session {}", sessionId);
                }
            }
        }

        // ─── CALCULER LE MIN ──────────────────────────────────────────
        Double effectiveLimitKw = null;

        if (chargePointMaxLimitKw != null) {
            effectiveLimitKw = chargePointMaxLimitKw;
            appliedPurpose = "ChargePointMaxProfile";
        }

        if (txDefaultLimitKw != null) {
            if (effectiveLimitKw == null || txDefaultLimitKw < effectiveLimitKw) {
                effectiveLimitKw = txDefaultLimitKw;
                appliedPurpose = "TxDefaultProfile";
            }
        }

        if (txLimitKw != null) {
            if (effectiveLimitKw == null || txLimitKw < effectiveLimitKw) {
                effectiveLimitKw = txLimitKw;
                appliedPurpose = "TxProfile";
            }
        }

        log.debug("[SCP] Effective limit: {} kW from {}", effectiveLimitKw, appliedPurpose);

        return new EffectiveLimit(
            effectiveLimitKw,
            chargePointMaxLimitKw,
            txDefaultLimitKw,
            txLimitKw,
            appliedPurpose
        );
    }

    /**
     * Trouve le profil valide avec le stackLevel le plus élevé.
     */
    private ChargingProfile getHighestValidProfile(Collection<ChargingProfile> profiles,
                                                    LocalDateTime now) {
        return profiles.stream()
            .filter(p -> isProfileValid(p, now))
            .max(Comparator.comparingInt(ChargingProfile::getStackLevel))
            .orElse(null);
    }

    /**
     * Vérifie si un profil est valide (dans sa période de validité).
     */
    private boolean isProfileValid(ChargingProfile profile, LocalDateTime now) {
        if (profile.getValidFrom() != null && now.isBefore(profile.getValidFrom())) {
            return false; // Pas encore valide
        }
        if (profile.getValidTo() != null && now.isAfter(profile.getValidTo())) {
            return false; // Expiré
        }
        return true;
    }

    /**
     * Calcule la limite en kW pour un profil à un instant donné.
     */
    private Double calculateLimitKw(ChargingProfile profile, LocalDateTime now,
                                    double voltage, int phases) {
        ChargingSchedule schedule = profile.getChargingSchedule();
        if (schedule == null || schedule.getChargingSchedulePeriod() == null) {
            log.info("[SCP] calculateLimitKw: no schedule or periods for profile {}", profile.getChargingProfileId());
            return null;
        }

        // Trouver la période active
        LocalDateTime scheduleStart = getScheduleStartTime(profile, now);
        long elapsedSeconds = Duration.between(scheduleStart, now).getSeconds();

        log.info("[SCP] calculateLimitKw: profile={}, kind={}, validFrom={}, startSchedule={}, scheduleStart={}, now={}, elapsed={}s, duration={}",
            profile.getChargingProfileId(), profile.getChargingProfileKind(), profile.getValidFrom(),
            schedule.getStartSchedule(), scheduleStart, now, elapsedSeconds, schedule.getDuration());

        if (elapsedSeconds < 0) {
            log.info("[SCP] calculateLimitKw: schedule not started yet (elapsed={}<0)", elapsedSeconds);
            return null; // Schedule pas encore démarré
        }

        // Vérifier la durée
        if (schedule.getDuration() != null && elapsedSeconds > schedule.getDuration()) {
            log.info("[SCP] calculateLimitKw: schedule EXPIRED (elapsed={}s > duration={}s)",
                elapsedSeconds, schedule.getDuration());
            return null; // Schedule terminé
        }

        // Trouver la période applicable
        ChargingSchedulePeriod activePeriod = null;
        for (ChargingSchedulePeriod period : schedule.getChargingSchedulePeriod()) {
            if (period.getStartPeriod() <= elapsedSeconds) {
                activePeriod = period;
            } else {
                break;
            }
        }

        if (activePeriod == null) {
            log.info("[SCP] calculateLimitKw: no active period found");
            return null;
        }

        // Convertir en kW
        double limit = activePeriod.getLimit();
        int numPhases = activePeriod.getNumberPhases() != null ?
            activePeriod.getNumberPhases() : phases;

        Double resultKw;
        if (schedule.getChargingRateUnit() == ChargingRateUnit.A) {
            // A → kW: P = U × I × √3 (triphasé) ou P = U × I (monophasé)
            double factor = numPhases == 1 ? 1.0 : Math.sqrt(3);
            resultKw = (voltage * limit * factor) / 1000.0;
        } else {
            // Déjà en W, convertir en kW
            resultKw = limit / 1000.0;
        }

        log.info("[SCP] calculateLimitKw: profile={}, limit={}(unit={}), result={} kW",
            profile.getChargingProfileId(), limit, schedule.getChargingRateUnit(), resultKw);
        return resultKw;
    }

    /**
     * Détermine le temps de début du schedule selon le type de profil.
     *
     * Pour les profils ABSOLUTE avec un startSchedule périmé (startSchedule + duration < now),
     * on utilise appliedAt comme référence pour éviter que des profils fraîchement reçus
     * soient considérés comme expirés à cause d'un startSchedule ancien.
     */
    private LocalDateTime getScheduleStartTime(ChargingProfile profile, LocalDateTime now) {
        switch (profile.getChargingProfileKind()) {
            case ABSOLUTE -> {
                LocalDateTime startSchedule = profile.getChargingSchedule().getStartSchedule();
                Integer duration = profile.getChargingSchedule().getDuration();
                LocalDateTime appliedAt = profile.getAppliedAt();

                if (startSchedule != null) {
                    // Vérifier si le schedule original est déjà complètement expiré
                    // Si startSchedule + duration < appliedAt, le profile a été reçu après son expiration théorique
                    // Dans ce cas, utiliser appliedAt comme référence (le CSMS envoie un refresh)
                    if (duration != null && appliedAt != null) {
                        LocalDateTime scheduleEnd = startSchedule.plusSeconds(duration);
                        if (scheduleEnd.isBefore(appliedAt)) {
                            log.info("[SCP] ABSOLUTE profile {}: startSchedule {} + duration {}s = {} is before appliedAt {}, using appliedAt as reference",
                                profile.getChargingProfileId(), startSchedule, duration, scheduleEnd, appliedAt);
                            return appliedAt;
                        }
                    }
                    return startSchedule;
                }
                // For profiles with future validFrom but no startSchedule,
                // the schedule should start when the profile becomes valid
                if (profile.getValidFrom() != null) {
                    return profile.getValidFrom();
                }
                return appliedAt != null ? appliedAt : now;
            }
            case RELATIVE -> {
                // Relatif au début de la transaction (moment d'application)
                return profile.getEffectiveStartTime() != null ?
                    profile.getEffectiveStartTime() :
                    (profile.getAppliedAt() != null ? profile.getAppliedAt() : now);
            }
            case RECURRING -> {
                return calculateRecurringStart(profile, now);
            }
            default -> {
                return now;
            }
        }
    }

    /**
     * Calcule le début de la période courante pour un profil récurrent.
     */
    private LocalDateTime calculateRecurringStart(ChargingProfile profile, LocalDateTime now) {
        LocalDateTime startSchedule = profile.getChargingSchedule().getStartSchedule();
        if (startSchedule == null) {
            return now;
        }

        Duration period = profile.getRecurrencyKind() == RecurrencyKind.DAILY ?
            Duration.ofDays(1) : Duration.ofDays(7);

        Duration sinceStart = Duration.between(startSchedule, now);
        if (sinceStart.isNegative()) {
            return startSchedule;
        }

        long periodsElapsed = sinceStart.toSeconds() / period.toSeconds();
        return startSchedule.plus(period.multipliedBy(periodsElapsed));
    }

    /**
     * Met à jour la session avec la limite effective actuelle.
     */
    private void updateSessionWithEffectiveLimit(Session session) {
        EffectiveLimit limit = getEffectiveLimit(
            session.getId(),
            session.getConnectorId(),
            session.getVoltage(),
            session.getChargerType().getPhases()
        );

        if (limit.effectiveLimitKw != null) {
            session.setScpLimitKw(limit.effectiveLimitKw);
            session.setScpPurpose(limit.appliedPurpose);
            log.info("[SCP] Session {} limit updated: {} kW from {}",
                session.getId(), limit.effectiveLimitKw, limit.appliedPurpose);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET COMPOSITE SCHEDULE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calcule le planning composite selon OCPP 1.6.
     */
    public ChargingSchedule getCompositeSchedule(String sessionId, int duration,
                                                  ChargingRateUnit targetUnit) {
        Session session = sessionService.getSession(sessionId);
        if (session == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusSeconds(duration);
        double voltage = session.getVoltage();
        int phases = session.getChargerType().getPhases();

        // Collecter tous les profils applicables
        List<ChargingProfile> allProfiles = new ArrayList<>();
        allProfiles.addAll(chargePointMaxProfiles.values());

        txDefaultProfiles.getOrDefault(0, new HashMap<>()).values()
            .forEach(allProfiles::add);
        txDefaultProfiles.getOrDefault(session.getConnectorId(), new HashMap<>()).values()
            .forEach(allProfiles::add);
        txProfiles.getOrDefault(sessionId, new HashMap<>()).values()
            .forEach(allProfiles::add);

        // Filtrer les profils valides
        List<ChargingProfile> validProfiles = allProfiles.stream()
            .filter(p -> isProfileValid(p, now))
            .collect(Collectors.toList());

        if (validProfiles.isEmpty()) {
            return createDefaultSchedule(session, duration, targetUnit);
        }

        // Créer les points de temps
        Set<Integer> timePoints = new TreeSet<>();
        timePoints.add(0);

        for (ChargingProfile profile : validProfiles) {
            LocalDateTime scheduleStart = getScheduleStartTime(profile, now);
            for (ChargingSchedulePeriod period : profile.getChargingSchedule().getChargingSchedulePeriod()) {
                long offset = Duration.between(now, scheduleStart).getSeconds() + period.getStartPeriod();
                if (offset >= 0 && offset < duration) {
                    timePoints.add((int) offset);
                }
            }
        }

        // Calculer la limite pour chaque point
        List<ChargingSchedulePeriod> compositePeriods = new ArrayList<>();
        Double lastLimit = null;

        for (int timeOffset : timePoints) {
            LocalDateTime pointTime = now.plusSeconds(timeOffset);

            // Trouver le MIN de tous les profils
            Double minLimit = null;
            Integer numPhases = phases;

            for (ChargingProfile profile : validProfiles) {
                Double limit = calculateLimitAtTime(profile, pointTime, now, voltage, phases, targetUnit);
                if (limit != null && (minLimit == null || limit < minLimit)) {
                    minLimit = limit;
                }
            }

            // Ajouter si différent du précédent
            if (minLimit != null && !minLimit.equals(lastLimit)) {
                compositePeriods.add(ChargingSchedulePeriod.builder()
                    .startPeriod(timeOffset)
                    .limit(minLimit)
                    .numberPhases(numPhases)
                    .build());
                lastLimit = minLimit;
            }
        }

        if (compositePeriods.isEmpty()) {
            return createDefaultSchedule(session, duration, targetUnit);
        }

        return ChargingSchedule.builder()
            .duration(duration)
            .startSchedule(now)
            .chargingRateUnit(targetUnit)
            .chargingSchedulePeriod(compositePeriods)
            .build();
    }

    private Double calculateLimitAtTime(ChargingProfile profile, LocalDateTime time,
                                        LocalDateTime referenceStart, double voltage,
                                        int phases, ChargingRateUnit targetUnit) {
        Double limitKw = calculateLimitKw(profile, time, voltage, phases);
        if (limitKw == null) return null;

        // Convertir vers l'unité cible
        if (targetUnit == ChargingRateUnit.W) {
            return limitKw * 1000.0;
        } else {
            // kW → A
            double factor = phases == 1 ? 1.0 : Math.sqrt(3);
            return (limitKw * 1000.0) / (voltage * factor);
        }
    }

    private ChargingSchedule createDefaultSchedule(Session session, int duration,
                                                    ChargingRateUnit unit) {
        double limit = unit == ChargingRateUnit.A ?
            session.getMaxCurrentA() :
            session.getMaxPowerKw() * 1000;

        return ChargingSchedule.builder()
            .duration(duration)
            .startSchedule(LocalDateTime.now())
            .chargingRateUnit(unit)
            .chargingSchedulePeriod(List.of(
                ChargingSchedulePeriod.builder()
                    .startPeriod(0)
                    .limit(limit)
                    .numberPhases(session.getChargerType().getPhases())
                    .build()
            ))
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Récupère tous les profils actifs pour une session.
     */
    public List<ChargingProfile> getActiveProfiles(String sessionId) {
        List<ChargingProfile> result = new ArrayList<>();

        // ChargePointMaxProfiles
        result.addAll(chargePointMaxProfiles.values());

        // TxDefaultProfiles (global)
        if (txDefaultProfiles.containsKey(0)) {
            result.addAll(txDefaultProfiles.get(0).values());
        }

        // TxProfiles pour cette session
        if (sessionId != null && txProfiles.containsKey(sessionId)) {
            result.addAll(txProfiles.get(sessionId).values());
        }

        return result;
    }

    /**
     * Récupère le ChargePointMaxProfile global.
     */
    public ChargingProfile getChargePointMaxProfile() {
        if (chargePointMaxProfiles.isEmpty()) {
            return null;
        }
        // Retourner celui avec le stackLevel le plus élevé
        return chargePointMaxProfiles.values().stream()
            .max(Comparator.comparingInt(ChargingProfile::getStackLevel))
            .orElse(null);
    }

    /**
     * Log un résumé des profils actuels.
     */
    private void logProfileSummary() {
        log.info("[SCP] Profile Summary: ChargePointMax={}, TxDefault={}, TxProfile={}",
            chargePointMaxProfiles.size(),
            txDefaultProfiles.values().stream().mapToInt(Map::size).sum(),
            txProfiles.values().stream().mapToInt(Map::size).sum());
    }

    // ═══════════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════════

    public record EffectiveLimit(
        Double effectiveLimitKw,
        Double chargePointMaxLimitKw,
        Double txDefaultLimitKw,
        Double txLimitKw,
        String appliedPurpose
    ) {}
}
