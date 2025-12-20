package com.evse.simulator.ocpp.v16.handlers.smartcharging;

import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.ChargingProfileStatus;
import com.evse.simulator.service.SmartChargingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handler pour SetChargingProfile (CS → CP).
 */
@Slf4j
@Component
public class SetChargingProfileHandler extends AbstractOcpp16IncomingHandler {

    private final SmartChargingService smartChargingService;

    public SetChargingProfileHandler(SmartChargingService smartChargingService) {
        this.smartChargingService = smartChargingService;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.SET_CHARGING_PROFILE;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "connectorId");
        requireField(payload, "csChargingProfiles");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        try {
            Integer connectorId = getInteger(payload, "connectorId", true);
            Map<String, Object> csChargingProfiles = getObject(payload, "csChargingProfiles", true);

            // Parser le profil
            ChargingProfile profile = parseChargingProfile(csChargingProfiles);
            if (profile == null) {
                log.warn("[{}] SetChargingProfile rejected: failed to parse profile", session.getId());
                logToSession(session, "SetChargingProfile REJECTED - invalid profile");
                return createResponse(ChargingProfileStatus.REJECTED);
            }

            // Log complet du profil parsé
            log.info("[{}] SetChargingProfile parsed: id={}, purpose={}, kind={}, stackLevel={}, validFrom={}, validTo={}",
                session.getId(), profile.getChargingProfileId(), profile.getChargingProfilePurpose(),
                profile.getChargingProfileKind(), profile.getStackLevel(),
                profile.getValidFrom(), profile.getValidTo());
            if (profile.getChargingSchedule() != null) {
                log.info("[{}] SetChargingProfile schedule: duration={}, startSchedule={}, unit={}, periods={}",
                    session.getId(), profile.getChargingSchedule().getDuration(),
                    profile.getChargingSchedule().getStartSchedule(),
                    profile.getChargingSchedule().getChargingRateUnit(),
                    profile.getChargingSchedule().getChargingSchedulePeriod() != null ?
                        profile.getChargingSchedule().getChargingSchedulePeriod().size() : 0);
            }

            // Appliquer le profil via SmartChargingService (gère stockage, hiérarchie, limites)
            String status = smartChargingService.setChargingProfile(
                    connectorId,
                    session.getId(),
                    profile
            );

            if (!"Accepted".equals(status)) {
                log.warn("[{}] SetChargingProfile rejected by SmartChargingService: {}",
                        session.getId(), status);
                logToSession(session, "SetChargingProfile REJECTED - " + status);
                return createResponse(ChargingProfileStatus.REJECTED);
            }

            // Stocker aussi le profil actif dans la session pour référence rapide
            session.setActiveChargingProfile(profile);

            // Calculer et afficher la limite effective
            double effectiveLimitKw = smartChargingService.getCurrentLimit(session.getId());

            // Mettre à jour les champs SCP de la session pour l'affichage frontend
            session.setScpLimitKw(effectiveLimitKw);
            session.setScpProfileId(profile.getChargingProfileId());
            session.setScpPurpose(profile.getChargingProfilePurpose() != null
                ? profile.getChargingProfilePurpose().getValue() : null);
            session.setScpStackLevel(profile.getStackLevel());

            if (effectiveLimitKw < session.getMaxPowerKw()) {
                session.addLog(LogEntry.info("SCP",
                        String.format("Profile #%d applied: effective limit = %.1f kW",
                                profile.getChargingProfileId(),
                                effectiveLimitKw)));

                log.info("[{}] SCP limit applied: {} kW from profile #{} purpose={}",
                        session.getId(), effectiveLimitKw, profile.getChargingProfileId(),
                        session.getScpPurpose());
            }

            log.info("[{}] SetChargingProfile accepted: connector={}, profile={}",
                    session.getId(), connectorId, profile.getChargingProfileId());
            logToSession(session, String.format(
                    "SetChargingProfile ACCEPTED - profile #%d", profile.getChargingProfileId()));

            return createResponse(ChargingProfileStatus.ACCEPTED);

        } catch (Exception e) {
            log.error("[{}] SetChargingProfile error: {}", session.getId(), e.getMessage(), e);
            logToSession(session, "SetChargingProfile REJECTED - " + e.getMessage());
            return createResponse(ChargingProfileStatus.REJECTED);
        }
    }

    @SuppressWarnings("unchecked")
    private ChargingProfile parseChargingProfile(Map<String, Object> data) {
        try {
            ChargingProfile.ChargingProfileBuilder builder = ChargingProfile.builder();

            // Champs obligatoires
            Number profileId = (Number) data.get("chargingProfileId");
            Number stackLevel = (Number) data.get("stackLevel");

            if (profileId != null) {
                builder.chargingProfileId(profileId.intValue());
            }
            if (stackLevel != null) {
                builder.stackLevel(stackLevel.intValue());
            }

            // Purpose
            String purpose = (String) data.get("chargingProfilePurpose");
            if (purpose != null) {
                builder.chargingProfilePurpose(
                        ChargingProfile.ChargingProfilePurpose.fromValue(purpose));
            }

            // Kind
            String kind = (String) data.get("chargingProfileKind");
            if (kind != null) {
                builder.chargingProfileKind(
                        ChargingProfile.ChargingProfileKind.fromValue(kind));
            }

            // RecurrencyKind
            String recurrency = (String) data.get("recurrencyKind");
            if (recurrency != null) {
                if ("Daily".equalsIgnoreCase(recurrency)) {
                    builder.recurrencyKind(ChargingProfile.RecurrencyKind.DAILY);
                } else if ("Weekly".equalsIgnoreCase(recurrency)) {
                    builder.recurrencyKind(ChargingProfile.RecurrencyKind.WEEKLY);
                }
            }

            // TransactionId
            Number transactionId = (Number) data.get("transactionId");
            if (transactionId != null) {
                builder.transactionId(transactionId.intValue());
            }

            // ValidFrom/ValidTo - Parser correctement les timestamps UTC
            String validFrom = (String) data.get("validFrom");
            if (validFrom != null) {
                builder.validFrom(parseIsoTimestamp(validFrom));
            }
            String validTo = (String) data.get("validTo");
            if (validTo != null) {
                builder.validTo(parseIsoTimestamp(validTo));
            }

            // ChargingSchedule
            Map<String, Object> scheduleData = (Map<String, Object>) data.get("chargingSchedule");
            if (scheduleData != null) {
                builder.chargingSchedule(parseChargingSchedule(scheduleData));
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse ChargingProfile: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ChargingProfile.ChargingSchedule parseChargingSchedule(Map<String, Object> data) {
        ChargingProfile.ChargingSchedule.ChargingScheduleBuilder builder =
                ChargingProfile.ChargingSchedule.builder();

        // ChargingRateUnit
        String rateUnit = (String) data.get("chargingRateUnit");
        if (rateUnit != null) {
            builder.chargingRateUnit(ChargingProfile.ChargingRateUnit.fromValue(rateUnit));
        }

        // Duration
        Number duration = (Number) data.get("duration");
        if (duration != null) {
            builder.duration(duration.intValue());
        }

        // StartSchedule - Parser correctement les timestamps UTC
        String startSchedule = (String) data.get("startSchedule");
        if (startSchedule != null) {
            builder.startSchedule(parseIsoTimestamp(startSchedule));
        }

        // MinChargingRate
        Number minChargingRate = (Number) data.get("minChargingRate");
        if (minChargingRate != null) {
            builder.minChargingRate(minChargingRate.doubleValue());
        }

        // ChargingSchedulePeriod
        List<Map<String, Object>> periodsData =
                (List<Map<String, Object>>) data.get("chargingSchedulePeriod");
        if (periodsData != null) {
            List<ChargingProfile.ChargingSchedulePeriod> periods = new ArrayList<>();
            for (Map<String, Object> periodData : periodsData) {
                ChargingProfile.ChargingSchedulePeriod.ChargingSchedulePeriodBuilder periodBuilder =
                        ChargingProfile.ChargingSchedulePeriod.builder();

                Number startPeriod = (Number) periodData.get("startPeriod");
                Number limit = (Number) periodData.get("limit");
                Number numberPhases = (Number) periodData.get("numberPhases");

                if (startPeriod != null) periodBuilder.startPeriod(startPeriod.intValue());
                if (limit != null) periodBuilder.limit(limit.doubleValue());
                if (numberPhases != null) periodBuilder.numberPhases(numberPhases.intValue());

                periods.add(periodBuilder.build());
            }
            builder.chargingSchedulePeriod(periods);
        }

        return builder.build();
    }

    /**
     * Parse un timestamp ISO 8601 (UTC ou avec timezone) en LocalDateTime.
     * Convertit correctement les timestamps UTC ("Z") en heure locale du système.
     *
     * @param isoTimestamp Le timestamp ISO 8601 (ex: "2025-12-15T21:41:00.000Z")
     * @return LocalDateTime dans le fuseau horaire local du système
     */
    private LocalDateTime parseIsoTimestamp(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return null;
        }

        try {
            // Si le timestamp contient "Z" (UTC) ou un offset (+/-), parser comme Instant
            if (isoTimestamp.endsWith("Z") || isoTimestamp.contains("+") ||
                    (isoTimestamp.lastIndexOf('-') > 10)) {
                // Parser comme Instant et convertir en LocalDateTime local
                Instant instant = Instant.parse(isoTimestamp);
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            } else {
                // Pas de timezone, parser directement comme LocalDateTime
                return LocalDateTime.parse(isoTimestamp);
            }
        } catch (DateTimeParseException e) {
            // Fallback: essayer de parser sans le "Z"
            try {
                String cleaned = isoTimestamp.replace("Z", "").replace(".000", "");
                return LocalDateTime.parse(cleaned);
            } catch (Exception ex) {
                log.warn("Failed to parse timestamp '{}': {}", isoTimestamp, e.getMessage());
                return null;
            }
        }
    }
}
