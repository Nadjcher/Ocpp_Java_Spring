package com.evse.simulator.service;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.*;
import com.evse.simulator.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion du Smart Charging (OCPP 1.6).
 * <p>
 * Gère les profils de charge, les limites de puissance,
 * et le calcul du planning composite.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmartChargingService implements com.evse.simulator.domain.service.SmartChargingService {

    private final SessionService sessionService;
    private final OCPPService ocppService;

    // Profils par session (sessionId -> list de profils)
    private final Map<String, List<ChargingProfile>> sessionProfiles = new ConcurrentHashMap<>();

    // Profil global ChargePointMaxProfile
    private ChargingProfile chargePointMaxProfile;

    // =========================================================================
    // Set Charging Profile
    // =========================================================================

    /**
     * Définit un profil de charge pour une session.
     *
     * @param sessionId ID de la session
     * @param profile profil de charge
     * @return statut de l'opération
     */
    public String setChargingProfile(String sessionId, ChargingProfile profile) {
        Session session = sessionService.getSession(sessionId);

        // Validation du profil
        String validationError = validateProfile(profile, session);
        if (validationError != null) {
            log.warn("Invalid charging profile for session {}: {}", sessionId, validationError);
            return "Rejected";
        }

        // Gestion selon le purpose
        switch (profile.getChargingProfilePurpose()) {
            case CHARGE_POINT_MAX_PROFILE -> {
                chargePointMaxProfile = profile;
                log.info("Set ChargePointMaxProfile: {}", profile.getChargingProfileId());
            }
            case TX_DEFAULT_PROFILE, TX_PROFILE -> {
                List<ChargingProfile> profiles = sessionProfiles
                        .computeIfAbsent(sessionId, k -> new ArrayList<>());

                // Supprimer les profils existants au même stack level et purpose
                profiles.removeIf(p ->
                        p.getStackLevel() == profile.getStackLevel() &&
                                p.getChargingProfilePurpose() == profile.getChargingProfilePurpose());

                profiles.add(profile);
                profiles.sort(Comparator.comparingInt(ChargingProfile::getStackLevel));

                // Appliquer le profil à la session
                session.setActiveChargingProfile(profile);
                applyChargingLimit(session, profile);

                log.info("Set ChargingProfile {} for session {} (purpose={}, stackLevel={})",
                        profile.getChargingProfileId(), sessionId,
                        profile.getChargingProfilePurpose(), profile.getStackLevel());
            }
        }

        return "Accepted";
    }

    /**
     * Valide un profil de charge.
     */
    private String validateProfile(ChargingProfile profile, Session session) {
        if (profile.getChargingProfileId() < 0) {
            return "Invalid chargingProfileId";
        }

        if (profile.getChargingProfilePurpose() == ChargingProfilePurpose.TX_PROFILE) {
            if (profile.getTransactionId() != null &&
                    !profile.getTransactionId().toString().equals(session.getTransactionId())) {
                return "TransactionId mismatch";
            }
        }

        if (profile.getChargingSchedule() == null) {
            return "Missing chargingSchedule";
        }

        if (profile.getChargingSchedule().getChargingSchedulePeriod() == null ||
                profile.getChargingSchedule().getChargingSchedulePeriod().isEmpty()) {
            return "Missing chargingSchedulePeriod";
        }

        return null;
    }

    /**
     * Applique la limite de charge d'un profil.
     */
    private void applyChargingLimit(Session session, ChargingProfile profile) {
        double limitKw = profile.getCurrentLimitKw(
                session.getVoltage(),
                session.getChargerType().getPhases());

        if (limitKw < Double.MAX_VALUE) {
            double newPower = Math.min(session.getMaxPowerKw(), limitKw);
            session.setCurrentPowerKw(newPower);
            log.debug("Applied charging limit {}kW to session {}", newPower, session.getId());
        }
    }

    // =========================================================================
    // Clear Charging Profile
    // =========================================================================

    /**
     * Supprime un ou plusieurs profils de charge.
     *
     * @param sessionId ID de la session (optionnel)
     * @param chargingProfileId ID du profil (optionnel)
     * @param stackLevel niveau de stack (optionnel)
     * @param purpose but du profil (optionnel)
     * @return statut de l'opération
     */
    public String clearChargingProfile(String sessionId, Integer chargingProfileId,
                                       Integer stackLevel, ChargingProfilePurpose purpose) {
        boolean cleared = false;

        // Suppression du profil global si applicable
        if (chargePointMaxProfile != null) {
            if (purpose == null || purpose == ChargingProfilePurpose.CHARGE_POINT_MAX_PROFILE) {
                if (chargingProfileId == null ||
                        chargingProfileId == chargePointMaxProfile.getChargingProfileId()) {
                    chargePointMaxProfile = null;
                    cleared = true;
                    log.info("Cleared ChargePointMaxProfile");
                }
            }
        }

        // Suppression des profils de session
        if (sessionId != null) {
            List<ChargingProfile> profiles = sessionProfiles.get(sessionId);
            if (profiles != null) {
                int beforeCount = profiles.size();
                profiles.removeIf(p -> matchesCriteria(p, chargingProfileId, stackLevel, purpose));
                if (profiles.size() < beforeCount) {
                    cleared = true;
                    log.info("Cleared {} profile(s) from session {}",
                            beforeCount - profiles.size(), sessionId);

                    // Mettre à jour la session
                    Session session = sessionService.getSession(sessionId);
                    if (profiles.isEmpty()) {
                        session.setActiveChargingProfile(null);
                    } else {
                        session.setActiveChargingProfile(profiles.get(profiles.size() - 1));
                    }
                }
            }
        } else {
            // Suppression sur toutes les sessions
            for (Map.Entry<String, List<ChargingProfile>> entry : sessionProfiles.entrySet()) {
                int beforeCount = entry.getValue().size();
                entry.getValue().removeIf(p ->
                        matchesCriteria(p, chargingProfileId, stackLevel, purpose));
                if (entry.getValue().size() < beforeCount) {
                    cleared = true;
                }
            }
        }

        return cleared ? "Accepted" : "Unknown";
    }

    private boolean matchesCriteria(ChargingProfile profile, Integer chargingProfileId,
                                    Integer stackLevel, ChargingProfilePurpose purpose) {
        if (chargingProfileId != null && profile.getChargingProfileId() != chargingProfileId) {
            return false;
        }
        if (stackLevel != null && profile.getStackLevel() != stackLevel) {
            return false;
        }
        if (purpose != null && profile.getChargingProfilePurpose() != purpose) {
            return false;
        }
        return true;
    }

    // =========================================================================
    // Get Composite Schedule
    // =========================================================================

    /**
     * Calcule le planning de charge composite.
     *
     * @param sessionId ID de la session
     * @param duration durée en secondes
     * @param chargingRateUnit unité de mesure
     * @return planning composite
     */
    public ChargingSchedule getCompositeSchedule(String sessionId, int duration,
                                                  ChargingRateUnit chargingRateUnit) {
        Session session = sessionService.getSession(sessionId);
        List<ChargingProfile> profiles = sessionProfiles.getOrDefault(sessionId, new ArrayList<>());

        // Trier par priorité (stack level descendant)
        profiles.sort((a, b) -> b.getStackLevel() - a.getStackLevel());

        // Ajouter le profil global si présent
        if (chargePointMaxProfile != null) {
            profiles.add(0, chargePointMaxProfile);
        }

        if (profiles.isEmpty()) {
            // Pas de profil, retourner la limite max de la borne
            return createDefaultSchedule(session, duration, chargingRateUnit);
        }

        // Calculer le composite
        return calculateComposite(profiles, session, duration, chargingRateUnit);
    }

    /**
     * Crée un planning par défaut (pas de limitation).
     */
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

    /**
     * Calcule le planning composite à partir des profils.
     */
    private ChargingSchedule calculateComposite(List<ChargingProfile> profiles,
                                                 Session session, int duration,
                                                 ChargingRateUnit targetUnit) {
        List<ChargingSchedulePeriod> compositePeriods = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Créer des intervalles de temps
        Set<Integer> breakpoints = new TreeSet<>();
        breakpoints.add(0);
        breakpoints.add(duration);

        for (ChargingProfile profile : profiles) {
            ChargingSchedule schedule = profile.getChargingSchedule();
            if (schedule != null && schedule.getChargingSchedulePeriod() != null) {
                for (ChargingSchedulePeriod period : schedule.getChargingSchedulePeriod()) {
                    int adjustedStart = adjustStartPeriod(profile, period.getStartPeriod(), now);
                    if (adjustedStart >= 0 && adjustedStart < duration) {
                        breakpoints.add(adjustedStart);
                    }
                }
            }
        }

        // Pour chaque intervalle, trouver la limite la plus restrictive
        List<Integer> sortedBreakpoints = new ArrayList<>(breakpoints);
        for (int i = 0; i < sortedBreakpoints.size() - 1; i++) {
            int start = sortedBreakpoints.get(i);
            int end = sortedBreakpoints.get(i + 1);

            double minLimit = Double.MAX_VALUE;
            Integer numPhases = null;

            for (ChargingProfile profile : profiles) {
                ChargingSchedule schedule = profile.getChargingSchedule();
                if (schedule == null) continue;

                double limit = getLimitAtTime(profile, start, now, session, targetUnit);
                if (limit < minLimit) {
                    minLimit = limit;
                    // Trouver le nombre de phases pour cette période
                    ChargingSchedulePeriod activePeriod = getActivePeriod(schedule, start, now, profile);
                    if (activePeriod != null && activePeriod.getNumberPhases() != null) {
                        numPhases = activePeriod.getNumberPhases();
                    }
                }
            }

            if (minLimit == Double.MAX_VALUE) {
                minLimit = targetUnit == ChargingRateUnit.A ?
                        session.getMaxCurrentA() :
                        session.getMaxPowerKw() * 1000;
            }

            compositePeriods.add(ChargingSchedulePeriod.builder()
                    .startPeriod(start)
                    .limit(minLimit)
                    .numberPhases(numPhases != null ? numPhases : session.getChargerType().getPhases())
                    .build());
        }

        return ChargingSchedule.builder()
                .duration(duration)
                .startSchedule(now)
                .chargingRateUnit(targetUnit)
                .chargingSchedulePeriod(compositePeriods)
                .build();
    }

    private int adjustStartPeriod(ChargingProfile profile, int startPeriod, LocalDateTime now) {
        if (profile.getChargingProfileKind() == ChargingProfileKind.ABSOLUTE) {
            ChargingSchedule schedule = profile.getChargingSchedule();
            if (schedule.getStartSchedule() != null) {
                long offsetSeconds = java.time.Duration.between(schedule.getStartSchedule(), now).getSeconds();
                return (int) (startPeriod - offsetSeconds);
            }
        }
        return startPeriod;
    }

    private double getLimitAtTime(ChargingProfile profile, int timeOffset,
                                   LocalDateTime now, Session session, ChargingRateUnit targetUnit) {
        ChargingSchedulePeriod period = getActivePeriod(
                profile.getChargingSchedule(), timeOffset, now, profile);

        if (period == null) {
            return Double.MAX_VALUE;
        }

        double limit = period.getLimit();
        ChargingRateUnit sourceUnit = profile.getChargingSchedule().getChargingRateUnit();

        // Conversion si nécessaire
        if (sourceUnit != targetUnit) {
            int phases = period.getNumberPhases() != null ?
                    period.getNumberPhases() : session.getChargerType().getPhases();

            if (sourceUnit == ChargingRateUnit.A && targetUnit == ChargingRateUnit.W) {
                // A → W
                double factor = phases == 1 ? 1.0 : (phases == 2 ? 2.0 : Math.sqrt(3));
                limit = session.getVoltage() * limit * factor;
            } else if (sourceUnit == ChargingRateUnit.W && targetUnit == ChargingRateUnit.A) {
                // W → A
                double factor = phases == 1 ? 1.0 : (phases == 2 ? 2.0 : Math.sqrt(3));
                limit = limit / (session.getVoltage() * factor);
            }
        }

        return limit;
    }

    private ChargingSchedulePeriod getActivePeriod(ChargingSchedule schedule, int timeOffset,
                                                    LocalDateTime now, ChargingProfile profile) {
        if (schedule == null || schedule.getChargingSchedulePeriod() == null) {
            return null;
        }

        ChargingSchedulePeriod activePeriod = null;
        for (ChargingSchedulePeriod period : schedule.getChargingSchedulePeriod()) {
            int adjustedStart = adjustStartPeriod(profile, period.getStartPeriod(), now);
            if (adjustedStart <= timeOffset) {
                activePeriod = period;
            } else {
                break;
            }
        }

        return activePeriod;
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    /**
     * Récupère les profils actifs pour une session.
     *
     * @param sessionId ID de la session
     * @return liste des profils
     */
    public List<ChargingProfile> getActiveProfiles(String sessionId) {
        return sessionProfiles.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * Récupère le profil ChargePointMaxProfile global.
     *
     * @return le profil ou null
     */
    public ChargingProfile getChargePointMaxProfile() {
        return chargePointMaxProfile;
    }

    /**
     * Récupère la limite de puissance actuelle pour une session.
     *
     * @param sessionId ID de la session
     * @return limite en kW
     */
    public double getCurrentLimit(String sessionId) {
        Session session = sessionService.getSession(sessionId);
        ChargingProfile profile = session.getActiveChargingProfile();

        if (profile == null) {
            return session.getMaxPowerKw();
        }

        return profile.getCurrentLimitKw(
                session.getVoltage(),
                session.getChargerType().getPhases());
    }
}