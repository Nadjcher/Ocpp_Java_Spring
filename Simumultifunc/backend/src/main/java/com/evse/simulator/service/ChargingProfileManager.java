package com.evse.simulator.service;

import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestionnaire centralisé des profils de charge OCPP 1.6.
 * <p>
 * Gère le stockage, la priorité et le calcul des limites effectives
 * selon la spécification OCPP 1.6 Smart Charging.
 * </p>
 */
@Service
@Slf4j
public class ChargingProfileManager {

    // Stockage: sessionId -> connectorId -> List<ChargingProfile>
    private final Map<String, Map<Integer, List<ChargingProfile>>> profiles = new ConcurrentHashMap<>();

    // Cache des limites effectives pour éviter les recalculs
    private final Map<String, EffectiveLimit> effectiveLimitsCache = new ConcurrentHashMap<>();

    // =========================================================================
    // CRUD Operations
    // =========================================================================

    /**
     * Ajoute ou met à jour un profil de charge.
     * Si un profil avec le même ID existe, il est remplacé.
     *
     * @param sessionId   ID de la session
     * @param connectorId ID du connecteur (0 = tous)
     * @param profile     Profil à ajouter
     */
    public void setChargingProfile(String sessionId, int connectorId, ChargingProfile profile) {
        profiles.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(connectorId, k -> new ArrayList<>());

        List<ChargingProfile> connectorProfiles = profiles.get(sessionId).get(connectorId);

        // Supprimer le profil existant avec le même ID
        connectorProfiles.removeIf(p -> p.getChargingProfileId() == profile.getChargingProfileId());

        // Ajouter le nouveau profil
        profile.setSessionId(sessionId);
        profile.setConnectorId(connectorId);
        profile.setAppliedAt(LocalDateTime.now());

        // Pour les profils Relative, définir le temps de début effectif
        if (profile.getChargingProfileKind() == ChargingProfileKind.RELATIVE) {
            profile.setEffectiveStartTime(LocalDateTime.now());
        }

        connectorProfiles.add(profile);

        // Invalider le cache
        effectiveLimitsCache.remove(sessionId + ":" + connectorId);

        log.info("[SCP] SetChargingProfile: session={}, connector={}, id={}, purpose={}, stackLevel={}, periods={}",
                sessionId, connectorId, profile.getChargingProfileId(),
                profile.getChargingProfilePurpose().getValue(),
                profile.getStackLevel(),
                profile.getChargingSchedule() != null && profile.getChargingSchedule().getChargingSchedulePeriod() != null
                        ? profile.getChargingSchedule().getChargingSchedulePeriod().size() : 0);

        // Log détaillé des périodes
        if (profile.getChargingSchedule() != null && profile.getChargingSchedule().getChargingSchedulePeriod() != null) {
            for (ChargingSchedulePeriod period : profile.getChargingSchedule().getChargingSchedulePeriod()) {
                log.info("[SCP]   Period: startPeriod={}s, limit={} {}, phases={}",
                        period.getStartPeriod(),
                        period.getLimit(),
                        profile.getChargingSchedule().getChargingRateUnit().getValue(),
                        period.getNumberPhases());
            }
        }
    }

    /**
     * Supprime des profils selon les critères OCPP 1.6.
     *
     * @param sessionId   ID de la session
     * @param id          ID du profil (optionnel)
     * @param connectorId ID du connecteur (optionnel)
     * @param purpose     Purpose du profil (optionnel)
     * @param stackLevel  Stack level (optionnel)
     * @return true si au moins un profil a été supprimé
     */
    public boolean clearChargingProfile(String sessionId, Integer id, Integer connectorId,
                                        String purpose, Integer stackLevel) {
        if (!profiles.containsKey(sessionId)) {
            return false;
        }

        boolean removed = false;
        ChargingProfilePurpose purposeEnum = purpose != null ?
                ChargingProfilePurpose.fromValue(purpose) : null;

        Map<Integer, List<ChargingProfile>> sessionProfiles = profiles.get(sessionId);

        for (Map.Entry<Integer, List<ChargingProfile>> entry : sessionProfiles.entrySet()) {
            int currentConnectorId = entry.getKey();

            // Filtrer par connectorId si spécifié
            if (connectorId != null && connectorId != currentConnectorId) {
                continue;
            }

            List<ChargingProfile> toRemove = entry.getValue().stream()
                    .filter(p -> {
                        if (id != null && p.getChargingProfileId() != id) return false;
                        if (purposeEnum != null && p.getChargingProfilePurpose() != purposeEnum) return false;
                        if (stackLevel != null && p.getStackLevel() != stackLevel) return false;
                        return true;
                    })
                    .collect(Collectors.toList());

            if (!toRemove.isEmpty()) {
                entry.getValue().removeAll(toRemove);
                removed = true;

                // Invalider le cache
                effectiveLimitsCache.remove(sessionId + ":" + currentConnectorId);

                log.info("[SCP] ClearChargingProfile: session={}, connector={}, removed {} profiles",
                        sessionId, currentConnectorId, toRemove.size());
            }
        }

        return removed;
    }

    /**
     * Récupère tous les profils actifs pour un connecteur.
     */
    public List<ChargingProfile> getActiveProfiles(String sessionId, int connectorId) {
        List<ChargingProfile> result = new ArrayList<>();

        if (!profiles.containsKey(sessionId)) {
            return result;
        }

        // Profils du connecteur spécifique
        if (profiles.get(sessionId).containsKey(connectorId)) {
            result.addAll(profiles.get(sessionId).get(connectorId));
        }

        // Profils du connecteur 0 (s'appliquent à tous)
        if (connectorId != 0 && profiles.get(sessionId).containsKey(0)) {
            result.addAll(profiles.get(sessionId).get(0));
        }

        // Filtrer par validité
        LocalDateTime now = LocalDateTime.now();
        return result.stream()
                .filter(p -> isProfileValid(p, now))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Calcul de la limite effective
    // =========================================================================

    /**
     * Calcule la limite de puissance effective en Watts.
     *
     * @param sessionId   ID de la session
     * @param connectorId ID du connecteur
     * @param phaseType   Type de phase ("AC_MONO", "AC_TRI", "DC")
     * @param voltageV    Tension en Volts
     * @return Limite effective en Watts
     */
    public EffectiveLimit getEffectiveLimit(String sessionId, int connectorId,
                                            String phaseType, double voltageV) {
        String cacheKey = sessionId + ":" + connectorId;

        // Vérifier le cache (invalidé à chaque mise à jour de profil)
        // Note: Pour la production, ajouter une TTL au cache

        List<ChargingProfile> activeProfiles = getActiveProfiles(sessionId, connectorId);

        if (activeProfiles.isEmpty()) {
            return EffectiveLimit.noLimit();
        }

        LocalDateTime now = LocalDateTime.now();

        // Grouper par purpose et prendre le stackLevel le plus élevé pour chaque
        Map<ChargingProfilePurpose, ChargingProfile> highestByPurpose = new EnumMap<>(ChargingProfilePurpose.class);

        for (ChargingProfile profile : activeProfiles) {
            ChargingProfilePurpose purpose = profile.getChargingProfilePurpose();
            ChargingProfile existing = highestByPurpose.get(purpose);

            if (existing == null || profile.getStackLevel() > existing.getStackLevel()) {
                highestByPurpose.put(purpose, profile);
            }
        }

        // Calculer la limite pour chaque profil actif
        double minLimitW = Double.MAX_VALUE;
        ChargingProfile limitingProfile = null;
        ChargingSchedulePeriod activePeriod = null;

        for (ChargingProfile profile : highestByPurpose.values()) {
            ChargingSchedulePeriod period = getActivePeriod(profile, now);
            if (period == null) continue;

            double limitW = convertToWatts(period.getLimit(),
                    profile.getChargingSchedule().getChargingRateUnit(),
                    period.getNumberPhases(),
                    phaseType,
                    voltageV);

            if (limitW < minLimitW) {
                minLimitW = limitW;
                limitingProfile = profile;
                activePeriod = period;
            }
        }

        if (limitingProfile == null) {
            return EffectiveLimit.noLimit();
        }

        EffectiveLimit result = new EffectiveLimit(
                minLimitW,
                activePeriod.getLimit(),
                limitingProfile.getChargingSchedule().getChargingRateUnit(),
                limitingProfile.getChargingProfilePurpose(),
                limitingProfile.getChargingProfileId(),
                limitingProfile.getStackLevel(),
                activePeriod.getStartPeriod(),
                getNextPeriodInfo(limitingProfile, now)
        );

        effectiveLimitsCache.put(cacheKey, result);

        log.debug("[SCP] EffectiveLimit: session={}, connector={}, limit={} W ({} {}), source={}",
                sessionId, connectorId, minLimitW,
                activePeriod.getLimit(),
                limitingProfile.getChargingSchedule().getChargingRateUnit().getValue(),
                limitingProfile.getChargingProfilePurpose().getValue());

        return result;
    }

    /**
     * Récupère la période active d'un profil.
     */
    private ChargingSchedulePeriod getActivePeriod(ChargingProfile profile, LocalDateTime now) {
        if (profile.getChargingSchedule() == null ||
                profile.getChargingSchedule().getChargingSchedulePeriod() == null ||
                profile.getChargingSchedule().getChargingSchedulePeriod().isEmpty()) {
            return null;
        }

        LocalDateTime scheduleStart = getScheduleStartTime(profile);
        long elapsedSeconds = Duration.between(scheduleStart, now).getSeconds();

        if (elapsedSeconds < 0) {
            return null; // Schedule pas encore démarré
        }

        // Vérifier la durée du schedule
        Integer duration = profile.getChargingSchedule().getDuration();
        if (duration != null && elapsedSeconds > duration) {
            return null; // Schedule terminé
        }

        // Trouver la période active (la dernière dont startPeriod <= elapsedSeconds)
        ChargingSchedulePeriod activePeriod = null;
        for (ChargingSchedulePeriod period : profile.getChargingSchedule().getChargingSchedulePeriod()) {
            if (period.getStartPeriod() <= elapsedSeconds) {
                activePeriod = period;
            } else {
                break;
            }
        }

        return activePeriod;
    }

    /**
     * Détermine le temps de début du schedule.
     */
    private LocalDateTime getScheduleStartTime(ChargingProfile profile) {
        // Pour les profils Relative, utiliser le temps effectif
        if (profile.getChargingProfileKind() == ChargingProfileKind.RELATIVE) {
            return profile.getEffectiveStartTime() != null ?
                    profile.getEffectiveStartTime() : profile.getAppliedAt();
        }

        // Pour les profils Absolute, utiliser startSchedule
        if (profile.getChargingSchedule().getStartSchedule() != null) {
            return profile.getChargingSchedule().getStartSchedule();
        }

        // Par défaut, utiliser le moment d'application
        return profile.getAppliedAt();
    }

    /**
     * Convertit une limite en Watts selon le type de phase.
     */
    private double convertToWatts(double limit, ChargingRateUnit unit,
                                  Integer numberPhases, String phaseType, double voltageV) {
        if (unit == ChargingRateUnit.W) {
            return limit;
        }

        // Conversion A -> W
        int phases = numberPhases != null ? numberPhases : getDefaultPhases(phaseType);

        switch (phaseType.toUpperCase()) {
            case "AC_MONO":
            case "AC_1":
                // P = V × I × cos(φ), cos(φ) ≈ 1
                return voltageV * limit;

            case "AC_TRI":
            case "AC_3":
                // P = √3 × V × I × cos(φ), V = tension phase-phase (400V)
                // Si voltageV = 230V (phase-neutre), utiliser 400V pour triphasé
                double vPhasePhase = voltageV < 300 ? voltageV * Math.sqrt(3) : voltageV;
                return Math.sqrt(3) * vPhasePhase * limit;

            case "DC":
                // P = V × I (tension variable selon SoC)
                return voltageV * limit;

            default:
                // Par défaut, triphasé
                return Math.sqrt(3) * 400 * limit;
        }
    }

    private int getDefaultPhases(String phaseType) {
        if (phaseType == null) return 3;
        return switch (phaseType.toUpperCase()) {
            case "AC_MONO", "AC_1" -> 1;
            case "DC" -> 1;
            default -> 3;
        };
    }

    /**
     * Vérifie si un profil est valide (dans sa période de validité).
     */
    private boolean isProfileValid(ChargingProfile profile, LocalDateTime now) {
        if (profile.getValidFrom() != null && now.isBefore(profile.getValidFrom())) {
            return false;
        }
        if (profile.getValidTo() != null && now.isAfter(profile.getValidTo())) {
            return false;
        }
        return true;
    }

    /**
     * Récupère les infos sur la prochaine période.
     */
    private NextPeriodInfo getNextPeriodInfo(ChargingProfile profile, LocalDateTime now) {
        if (profile.getChargingSchedule() == null ||
                profile.getChargingSchedule().getChargingSchedulePeriod() == null) {
            return null;
        }

        LocalDateTime scheduleStart = getScheduleStartTime(profile);
        long elapsedSeconds = Duration.between(scheduleStart, now).getSeconds();

        List<ChargingSchedulePeriod> periods = profile.getChargingSchedule().getChargingSchedulePeriod();

        for (ChargingSchedulePeriod period : periods) {
            if (period.getStartPeriod() > elapsedSeconds) {
                return new NextPeriodInfo(
                        period.getStartPeriod(),
                        period.getLimit(),
                        (int) (period.getStartPeriod() - elapsedSeconds)
                );
            }
        }

        return null;
    }

    // =========================================================================
    // GetCompositeSchedule
    // =========================================================================

    /**
     * Calcule le schedule composite (fusionné) pour un connecteur.
     *
     * @param sessionId        ID de la session
     * @param connectorId      ID du connecteur
     * @param duration         Durée demandée en secondes
     * @param chargingRateUnit Unité de sortie (A ou W)
     * @param phaseType        Type de phase
     * @param voltageV         Tension
     * @return Schedule composite
     */
    public CompositeSchedule getCompositeSchedule(String sessionId, int connectorId,
                                                   int duration, String chargingRateUnit,
                                                   String phaseType, double voltageV) {
        List<ChargingProfile> activeProfiles = getActiveProfiles(sessionId, connectorId);

        if (activeProfiles.isEmpty()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        ChargingRateUnit outputUnit = ChargingRateUnit.fromValue(chargingRateUnit);

        // Calculer les points de changement de limite
        Set<Integer> changePoints = new TreeSet<>();
        changePoints.add(0);

        for (ChargingProfile profile : activeProfiles) {
            if (profile.getChargingSchedule() == null) continue;

            LocalDateTime scheduleStart = getScheduleStartTime(profile);
            long offsetSeconds = Duration.between(scheduleStart, now).getSeconds();

            for (ChargingSchedulePeriod period : profile.getChargingSchedule().getChargingSchedulePeriod()) {
                int effectiveStart = (int) (period.getStartPeriod() - offsetSeconds);
                if (effectiveStart >= 0 && effectiveStart <= duration) {
                    changePoints.add(effectiveStart);
                }
            }
        }

        // Construire le schedule composite
        List<CompositeSchedulePeriod> compositePeriods = new ArrayList<>();

        for (int startOffset : changePoints) {
            if (startOffset > duration) break;

            // Calculer la limite effective à ce point
            LocalDateTime pointTime = now.plusSeconds(startOffset);

            double minLimitW = Double.MAX_VALUE;
            for (ChargingProfile profile : activeProfiles) {
                ChargingSchedulePeriod period = getActivePeriodAtTime(profile, pointTime);
                if (period == null) continue;

                double limitW = convertToWatts(period.getLimit(),
                        profile.getChargingSchedule().getChargingRateUnit(),
                        period.getNumberPhases(),
                        phaseType,
                        voltageV);

                minLimitW = Math.min(minLimitW, limitW);
            }

            if (minLimitW < Double.MAX_VALUE) {
                double outputLimit = outputUnit == ChargingRateUnit.W ?
                        minLimitW :
                        convertWattsToAmps(minLimitW, phaseType, voltageV);

                compositePeriods.add(new CompositeSchedulePeriod(startOffset, outputLimit));
            }
        }

        if (compositePeriods.isEmpty()) {
            return null;
        }

        return new CompositeSchedule(
                connectorId,
                now,
                duration,
                outputUnit,
                compositePeriods
        );
    }

    private ChargingSchedulePeriod getActivePeriodAtTime(ChargingProfile profile, LocalDateTime time) {
        if (profile.getChargingSchedule() == null ||
                profile.getChargingSchedule().getChargingSchedulePeriod() == null) {
            return null;
        }

        LocalDateTime scheduleStart = getScheduleStartTime(profile);
        long elapsedSeconds = Duration.between(scheduleStart, time).getSeconds();

        if (elapsedSeconds < 0) return null;

        ChargingSchedulePeriod activePeriod = null;
        for (ChargingSchedulePeriod period : profile.getChargingSchedule().getChargingSchedulePeriod()) {
            if (period.getStartPeriod() <= elapsedSeconds) {
                activePeriod = period;
            } else {
                break;
            }
        }

        return activePeriod;
    }

    private double convertWattsToAmps(double watts, String phaseType, double voltageV) {
        return switch (phaseType.toUpperCase()) {
            case "AC_MONO", "AC_1" -> watts / voltageV;
            case "AC_TRI", "AC_3" -> watts / (Math.sqrt(3) * (voltageV < 300 ? voltageV * Math.sqrt(3) : voltageV));
            case "DC" -> watts / voltageV;
            default -> watts / (Math.sqrt(3) * 400);
        };
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Supprime tous les profils d'une session.
     */
    public void clearAllProfiles(String sessionId) {
        profiles.remove(sessionId);
        effectiveLimitsCache.entrySet().removeIf(e -> e.getKey().startsWith(sessionId + ":"));
        log.info("[SCP] Cleared all profiles for session: {}", sessionId);
    }

    /**
     * Réinitialise le gestionnaire.
     */
    public void reset() {
        profiles.clear();
        effectiveLimitsCache.clear();
        log.info("[SCP] ChargingProfileManager reset");
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    /**
     * Représente la limite effective calculée.
     */
    public record EffectiveLimit(
            double limitW,
            double limitRaw,
            ChargingRateUnit unit,
            ChargingProfilePurpose source,
            int profileId,
            int stackLevel,
            int currentPeriodStart,
            NextPeriodInfo nextPeriod
    ) {
        public static EffectiveLimit noLimit() {
            return new EffectiveLimit(Double.MAX_VALUE, 0, ChargingRateUnit.W, null, -1, -1, 0, null);
        }

        public boolean hasLimit() {
            return limitW < Double.MAX_VALUE;
        }

        public double getLimitKw() {
            return limitW / 1000.0;
        }
    }

    /**
     * Info sur la prochaine période.
     */
    public record NextPeriodInfo(
            int startPeriod,
            double limit,
            int secondsUntilStart
    ) {}

    /**
     * Schedule composite.
     */
    public record CompositeSchedule(
            int connectorId,
            LocalDateTime scheduleStart,
            int duration,
            ChargingRateUnit chargingRateUnit,
            List<CompositeSchedulePeriod> chargingSchedulePeriod
    ) {}

    /**
     * Période dans le schedule composite.
     */
    public record CompositeSchedulePeriod(
            int startPeriod,
            double limit
    ) {}
}
