package com.evse.simulator.controller;

import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.Session;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.service.SmartChargingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contrôleur d'analyse et d'optimisation du parc de bornes.
 * Analyse les SCP (Smart Charging Profiles) et la répartition de l'énergie.
 */
@RestController
@RequestMapping("/api/ml")
@Tag(name = "Optimisation Parc", description = "Analyse SCP et optimisation énergétique du parc")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class MLController {

    private final SessionService sessionService;
    private final SmartChargingService smartChargingService;

    @GetMapping("/status")
    @Operation(summary = "Statut du parc")
    public ResponseEntity<Map<String, Object>> getParkStatus() {
        List<Session> sessions = sessionService.getAllSessions();

        long connected = sessions.stream().filter(Session::isConnected).count();
        long charging = sessions.stream().filter(Session::isCharging).count();
        long withScp = sessions.stream()
                .filter(s -> s.getActiveChargingProfile() != null)
                .count();

        double totalCapacity = sessions.stream().mapToDouble(Session::getMaxPowerKw).sum();
        double totalUsed = sessions.stream().mapToDouble(Session::getCurrentPowerKw).sum();

        return ResponseEntity.ok(Map.of(
                "sessionsTotal", sessions.size(),
                "sessionsConnected", connected,
                "sessionsCharging", charging,
                "sessionsWithScp", withScp,
                "totalCapacityKw", round(totalCapacity),
                "totalUsedKw", round(totalUsed),
                "parkUtilization", totalCapacity > 0 ? round(totalUsed / totalCapacity * 100) : 0
        ));
    }

    @PostMapping("/analyze/{sessionId}")
    @Operation(summary = "Analyse une session (SCP et énergie)")
    public ResponseEntity<Map<String, Object>> analyzeSession(@PathVariable String sessionId) {
        try {
            Session session = sessionService.getSession(sessionId);
            Map<String, Object> result = new LinkedHashMap<>();

            // Métriques de base
            result.put("metrics", buildSessionMetrics(session));

            // Analyse SCP
            result.put("scp", analyzeScp(session));

            // Anomalies/alertes
            result.put("anomalies", detectIssues(session));

            // Prédiction simple
            result.put("prediction", buildPrediction(session));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage(), "anomalies", List.of()));
        }
    }

    @GetMapping("/park")
    @Operation(summary = "Analyse complète du parc")
    public ResponseEntity<Map<String, Object>> analyzePark() {
        List<Session> sessions = sessionService.getAllSessions();
        List<Session> chargingSessions = sessions.stream()
                .filter(Session::isCharging)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();

        // Vue globale du parc
        result.put("summary", buildParkSummary(sessions, chargingSessions));

        // Analyse SCP globale
        result.put("scpAnalysis", buildScpAnalysis(chargingSessions));

        // Alertes d'optimisation
        result.put("alerts", buildParkAlerts(chargingSessions));

        // Sessions détaillées
        result.put("sessions", chargingSessions.stream()
                .map(this::buildSessionMetrics)
                .collect(Collectors.toList()));

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // Analyse SCP
    // =========================================================================

    private Map<String, Object> analyzeScp(Session session) {
        Map<String, Object> scp = new LinkedHashMap<>();

        ChargingProfile profile = session.getActiveChargingProfile();
        scp.put("hasActiveProfile", profile != null);

        if (profile != null) {
            scp.put("profileId", profile.getChargingProfileId());
            scp.put("purpose", profile.getChargingProfilePurpose().getValue());
            scp.put("stackLevel", profile.getStackLevel());

            // Limite actuelle du SCP
            double scpLimitKw = profile.getCurrentLimitKw(
                    session.getVoltage(),
                    session.getChargerType().getPhases());

            if (scpLimitKw < Double.MAX_VALUE) {
                scp.put("currentLimitKw", round(scpLimitKw));

                // Écart entre SCP et puissance réelle
                double actualPower = session.getCurrentPowerKw();
                double gap = scpLimitKw - actualPower;
                double gapPercent = scpLimitKw > 0 ? (gap / scpLimitKw) * 100 : 0;

                scp.put("actualPowerKw", round(actualPower));
                scp.put("gapKw", round(gap));
                scp.put("gapPercent", round(gapPercent));
                scp.put("scpUtilization", round(100 - gapPercent));

                // Évaluation
                if (gapPercent > 30) {
                    scp.put("status", "UNDERUSED");
                    scp.put("statusMessage", "SCP sous-utilisé: la borne pourrait charger plus");
                } else if (gapPercent < -5) {
                    scp.put("status", "EXCEEDED");
                    scp.put("statusMessage", "Dépassement du SCP!");
                } else {
                    scp.put("status", "OPTIMAL");
                    scp.put("statusMessage", "Utilisation optimale du SCP");
                }
            } else {
                scp.put("currentLimitKw", "unlimited");
                scp.put("status", "NO_LIMIT");
            }

            // Unité du profil
            if (profile.getChargingSchedule() != null) {
                scp.put("rateUnit", profile.getChargingSchedule().getChargingRateUnit().getValue());
            }
        } else {
            scp.put("status", "NO_PROFILE");
            scp.put("statusMessage", "Pas de SCP actif - charge sans limitation");
        }

        return scp;
    }

    // =========================================================================
    // Métriques session
    // =========================================================================

    private Map<String, Object> buildSessionMetrics(Session session) {
        Map<String, Object> m = new LinkedHashMap<>();

        m.put("sessionId", session.getId());
        m.put("cpId", session.getCpId());
        m.put("state", session.getState().getValue());
        m.put("isCharging", session.isCharging());

        // Puissance
        m.put("powerKw", round(session.getCurrentPowerKw()));
        m.put("maxPowerKw", round(session.getMaxPowerKw()));

        // Efficacité d'utilisation de la capacité
        double efficiency = session.getMaxPowerKw() > 0
                ? (session.getCurrentPowerKw() / session.getMaxPowerKw()) * 100
                : 0;
        m.put("capacityUsage", round(efficiency));

        // SoC
        m.put("soc", round(session.getSoc()));
        m.put("targetSoc", round(session.getTargetSoc()));

        // Énergie
        m.put("energyKwh", round(session.getEnergyDeliveredKwh()));

        // SCP
        ChargingProfile profile = session.getActiveChargingProfile();
        m.put("hasScp", profile != null);
        if (profile != null) {
            double scpLimit = profile.getCurrentLimitKw(
                    session.getVoltage(),
                    session.getChargerType().getPhases());
            m.put("scpLimitKw", scpLimit < Double.MAX_VALUE ? round(scpLimit) : null);
        }

        return m;
    }

    // =========================================================================
    // Analyse parc
    // =========================================================================

    private Map<String, Object> buildParkSummary(List<Session> all, List<Session> charging) {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Compteurs
        summary.put("totalStations", all.size());
        summary.put("charging", charging.size());
        summary.put("available", all.stream().filter(s -> s.getState().getValue().equals("available")).count());

        // Capacité
        double totalCapacity = all.stream().mapToDouble(Session::getMaxPowerKw).sum();
        double usedCapacity = charging.stream().mapToDouble(Session::getCurrentPowerKw).sum();

        summary.put("totalCapacityKw", round(totalCapacity));
        summary.put("usedCapacityKw", round(usedCapacity));
        summary.put("availableCapacityKw", round(totalCapacity - usedCapacity));
        summary.put("utilizationPercent", totalCapacity > 0 ? round(usedCapacity / totalCapacity * 100) : 0);

        // Énergie totale délivrée
        double totalEnergy = all.stream().mapToDouble(Session::getEnergyDeliveredKwh).sum();
        summary.put("totalEnergyKwh", round(totalEnergy));

        return summary;
    }

    private Map<String, Object> buildScpAnalysis(List<Session> charging) {
        Map<String, Object> analysis = new LinkedHashMap<>();

        long withScp = charging.stream()
                .filter(s -> s.getActiveChargingProfile() != null)
                .count();
        long withoutScp = charging.size() - withScp;

        analysis.put("sessionsWithScp", withScp);
        analysis.put("sessionsWithoutScp", withoutScp);
        analysis.put("scpCoverage", charging.size() > 0
                ? round((double) withScp / charging.size() * 100)
                : 0);

        // Analyse de l'utilisation des SCP
        double totalScpLimit = 0;
        double totalActualPower = 0;
        int scpCount = 0;

        for (Session s : charging) {
            ChargingProfile profile = s.getActiveChargingProfile();
            if (profile != null) {
                double limit = profile.getCurrentLimitKw(s.getVoltage(), s.getChargerType().getPhases());
                if (limit < Double.MAX_VALUE) {
                    totalScpLimit += limit;
                    totalActualPower += s.getCurrentPowerKw();
                    scpCount++;
                }
            }
        }

        if (scpCount > 0) {
            analysis.put("totalScpLimitKw", round(totalScpLimit));
            analysis.put("totalActualPowerKw", round(totalActualPower));
            analysis.put("avgScpUtilization", round(totalActualPower / totalScpLimit * 100));
            analysis.put("wastedCapacityKw", round(totalScpLimit - totalActualPower));
        }

        return analysis;
    }

    private List<Map<String, Object>> buildParkAlerts(List<Session> charging) {
        List<Map<String, Object>> alerts = new ArrayList<>();

        for (Session s : charging) {
            ChargingProfile profile = s.getActiveChargingProfile();

            // Alerte: pas de SCP pendant charge
            if (profile == null) {
                alerts.add(createAlert(s.getId(), "NO_SCP",
                        "Charge sans SCP - pas de contrôle de puissance",
                        "MEDIUM"));
                continue;
            }

            double limit = profile.getCurrentLimitKw(s.getVoltage(), s.getChargerType().getPhases());
            if (limit >= Double.MAX_VALUE) continue;

            double actual = s.getCurrentPowerKw();
            double usage = (actual / limit) * 100;

            // Alerte: SCP sous-utilisé
            if (usage < 50 && actual > 0) {
                alerts.add(createAlert(s.getId(), "SCP_UNDERUSED",
                        String.format("SCP utilisé à %.0f%% - capacité gaspillée: %.1f kW",
                                usage, limit - actual),
                        "LOW"));
            }

            // Alerte: dépassement SCP
            if (actual > limit * 1.05) {
                alerts.add(createAlert(s.getId(), "SCP_EXCEEDED",
                        String.format("Dépassement SCP: %.1f kW > %.1f kW limite",
                                actual, limit),
                        "HIGH"));
            }

            // Alerte: puissance nulle avec SCP
            if (s.isCharging() && actual <= 0) {
                alerts.add(createAlert(s.getId(), "ZERO_POWER",
                        "Charge active mais puissance nulle",
                        "HIGH"));
            }
        }

        // Alerte globale: faible utilisation du parc
        double totalCapacity = charging.stream().mapToDouble(Session::getMaxPowerKw).sum();
        double totalUsed = charging.stream().mapToDouble(Session::getCurrentPowerKw).sum();
        if (totalCapacity > 0 && (totalUsed / totalCapacity) < 0.3) {
            alerts.add(createAlert("PARK", "LOW_UTILIZATION",
                    String.format("Utilisation du parc faible: %.0f%% de la capacité",
                            totalUsed / totalCapacity * 100),
                    "LOW"));
        }

        return alerts;
    }

    // =========================================================================
    // Détection issues session
    // =========================================================================

    private List<Map<String, Object>> detectIssues(Session session) {
        List<Map<String, Object>> issues = new ArrayList<>();

        if (!session.isCharging()) {
            return issues;
        }

        ChargingProfile profile = session.getActiveChargingProfile();
        double actual = session.getCurrentPowerKw();

        // Pas de SCP
        if (profile == null) {
            issues.add(createAlert(session.getId(), "NO_SCP",
                    "Pas de profil de charge (SCP) actif",
                    "MEDIUM"));
        } else {
            double limit = profile.getCurrentLimitKw(
                    session.getVoltage(),
                    session.getChargerType().getPhases());

            if (limit < Double.MAX_VALUE) {
                double usage = (actual / limit) * 100;

                if (usage < 50 && actual > 0) {
                    issues.add(createAlert(session.getId(), "SCP_UNDERUSED",
                            String.format("SCP sous-utilisé (%.0f%%) - vérifier le véhicule ou ajuster le SCP", usage),
                            "LOW"));
                }

                if (actual > limit * 1.05) {
                    issues.add(createAlert(session.getId(), "SCP_EXCEEDED",
                            "Dépassement de la limite SCP",
                            "HIGH"));
                }
            }
        }

        // Puissance nulle
        if (actual <= 0) {
            issues.add(createAlert(session.getId(), "ZERO_POWER",
                    "Charge active mais puissance nulle",
                    "HIGH"));
        }

        // Efficacité globale faible
        double efficiency = session.getMaxPowerKw() > 0
                ? (actual / session.getMaxPowerKw()) * 100
                : 0;
        if (efficiency < 30 && actual > 0) {
            issues.add(createAlert(session.getId(), "LOW_EFFICIENCY",
                    String.format("Utilisation capacité borne faible: %.0f%%", efficiency),
                    "LOW"));
        }

        return issues;
    }

    // =========================================================================
    // Prédiction
    // =========================================================================

    private Map<String, Object> buildPrediction(Session session) {
        Map<String, Object> pred = new LinkedHashMap<>();

        pred.put("sessionId", session.getId());
        pred.put("currentSoc", round(session.getSoc()));
        pred.put("targetSoc", round(session.getTargetSoc()));
        pred.put("currentEnergyKwh", round(session.getEnergyDeliveredKwh()));

        double remaining = session.getTargetSoc() - session.getSoc();
        double power = session.getCurrentPowerKw();

        if (remaining > 0 && power > 0) {
            // Estimation basée sur capacité batterie typique
            double batteryKwh = 60.0;
            double remainingEnergy = (remaining / 100.0) * batteryKwh;
            int remainingMinutes = (int) ((remainingEnergy / power) * 60);

            pred.put("remainingEnergyKwh", round(remainingEnergy));
            pred.put("remainingMinutes", remainingMinutes);
        } else {
            pred.put("remainingEnergyKwh", 0);
            pred.put("remainingMinutes", 0);
        }

        return pred;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Map<String, Object> createAlert(String sessionId, String type,
                                             String message, String severity) {
        return Map.of(
                "id", UUID.randomUUID().toString().substring(0, 8),
                "sessionId", sessionId,
                "type", type,
                "description", message,
                "severity", severity,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
