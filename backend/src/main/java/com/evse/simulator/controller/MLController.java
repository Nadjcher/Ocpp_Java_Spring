package com.evse.simulator.controller;

import com.evse.simulator.model.Session;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.websocket.MLWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour l'analyse des sessions de charge.
 * Version simplifiée - basée sur des données réelles uniquement.
 */
@RestController
@RequestMapping("/api/ml")
@Tag(name = "Analyse", description = "Analyse des sessions et détection d'anomalies")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class MLController {

    private final SessionService sessionService;
    private final MLWebSocketHandler mlWebSocketHandler;

    // Historique des anomalies par session
    private final Map<String, List<Map<String, Object>>> anomalyHistory = new ConcurrentHashMap<>();

    // Seuils configurables
    private double efficiencyThreshold = 0.5;  // Seuil d'efficacité (50%)
    private double socDriftThreshold = 5.0;    // Dérive SoC max en %

    @GetMapping("/status")
    @Operation(summary = "Statut de l'analyse")
    public ResponseEntity<Map<String, Object>> getStatus() {
        List<Session> sessions = sessionService.getAllSessions();
        long charging = sessions.stream().filter(Session::isCharging).count();
        long connected = sessions.stream().filter(Session::isConnected).count();

        return ResponseEntity.ok(Map.of(
            "ready", true,
            "sessionsTotal", sessions.size(),
            "sessionsConnected", connected,
            "sessionsCharging", charging,
            "anomaliesTracked", anomalyHistory.values().stream().mapToInt(List::size).sum()
        ));
    }

    @PostMapping("/analyze/{sessionId}")
    @Operation(summary = "Analyse une session")
    public ResponseEntity<Map<String, Object>> analyzeSession(@PathVariable String sessionId) {
        try {
            Session session = sessionService.getSession(sessionId);
            Map<String, Object> result = new LinkedHashMap<>();

            // Métriques calculées à partir de données réelles
            Map<String, Object> metrics = calculateMetrics(session);
            result.put("metrics", metrics);

            // Détecter les anomalies
            List<Map<String, Object>> anomalies = detectAnomalies(session, metrics);
            result.put("anomalies", anomalies);

            // Prédiction simple
            Map<String, Object> prediction = predictCharge(session);
            result.put("prediction", prediction);

            // Stocker et diffuser les anomalies
            if (!anomalies.isEmpty()) {
                anomalyHistory.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(anomalies);
                anomalies.forEach(mlWebSocketHandler::broadcastAnomaly);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur analyse session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.ok(Map.of("error", e.getMessage(), "anomalies", List.of()));
        }
    }

    @PostMapping("/threshold")
    @Operation(summary = "Configure les seuils de détection")
    public ResponseEntity<Map<String, Object>> updateThresholds(@RequestBody Map<String, Object> body) {
        if (body.containsKey("efficiency")) {
            efficiencyThreshold = ((Number) body.get("efficiency")).doubleValue();
        }
        if (body.containsKey("socDrift")) {
            socDriftThreshold = ((Number) body.get("socDrift")).doubleValue();
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "efficiencyThreshold", efficiencyThreshold,
            "socDriftThreshold", socDriftThreshold
        ));
    }

    @GetMapping("/anomalies/{sessionId}")
    @Operation(summary = "Historique des anomalies d'une session")
    public ResponseEntity<List<Map<String, Object>>> getAnomalies(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "20") int limit) {

        List<Map<String, Object>> history = anomalyHistory.getOrDefault(sessionId, List.of());
        return ResponseEntity.ok(history.stream().limit(limit).collect(Collectors.toList()));
    }

    @DeleteMapping("/anomalies")
    @Operation(summary = "Efface l'historique des anomalies")
    public ResponseEntity<Map<String, Object>> clearAnomalies() {
        int count = anomalyHistory.values().stream().mapToInt(List::size).sum();
        anomalyHistory.clear();
        return ResponseEntity.ok(Map.of("cleared", count));
    }

    // =========================================================================
    // Calculs basés sur données réelles
    // =========================================================================

    private Map<String, Object> calculateMetrics(Session session) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // Données de base
        metrics.put("sessionId", session.getId());
        metrics.put("state", session.getState().getValue());
        metrics.put("isCharging", session.isCharging());

        // Puissance
        double maxPower = session.getMaxPowerKw();
        double currentPower = session.getCurrentPowerKw();
        double efficiency = maxPower > 0 ? currentPower / maxPower : 0;
        metrics.put("powerKw", round(currentPower, 2));
        metrics.put("maxPowerKw", round(maxPower, 2));
        metrics.put("efficiency", round(efficiency * 100, 1)); // En %

        // Énergie
        metrics.put("energyKwh", round(session.getEnergyDeliveredKwh(), 2));

        // SoC
        metrics.put("soc", round(session.getSoc(), 1));
        metrics.put("targetSoc", round(session.getTargetSoc(), 1));
        metrics.put("socRemaining", round(session.getTargetSoc() - session.getSoc(), 1));

        // Durée
        if (session.getStartTime() != null) {
            long minutes = Duration.between(session.getStartTime(), LocalDateTime.now()).toMinutes();
            metrics.put("durationMinutes", minutes);
        }

        return metrics;
    }

    private List<Map<String, Object>> detectAnomalies(Session session, Map<String, Object> metrics) {
        List<Map<String, Object>> anomalies = new ArrayList<>();

        // 1. Efficacité faible pendant charge active
        if (session.isCharging()) {
            double efficiency = ((Number) metrics.get("efficiency")).doubleValue() / 100.0;
            if (efficiency < efficiencyThreshold && efficiency > 0) {
                anomalies.add(createAnomaly(
                    session.getId(),
                    "UNDERPERFORMING",
                    "MEDIUM",
                    String.format("Efficacité faible: %.0f%% (seuil: %.0f%%)",
                        efficiency * 100, efficiencyThreshold * 100),
                    "Vérifier le câble de charge et la configuration de puissance"
                ));
            }
        }

        // 2. Puissance nulle pendant charge
        if (session.isCharging() && session.getCurrentPowerKw() <= 0) {
            anomalies.add(createAnomaly(
                session.getId(),
                "NO_POWER",
                "HIGH",
                "Charge active mais puissance nulle",
                "Vérifier la connexion et l'état de la borne"
            ));
        }

        // 3. SoC qui n'évolue pas (charge bloquée)
        if (session.isCharging() && session.getStartTime() != null) {
            long minutes = Duration.between(session.getStartTime(), LocalDateTime.now()).toMinutes();
            if (minutes > 5 && session.getSoc() <= session.getInitialSoc() + 1) {
                anomalies.add(createAnomaly(
                    session.getId(),
                    "CHARGE_STUCK",
                    "HIGH",
                    String.format("SoC n'a pas évolué depuis %d min (%.1f%% → %.1f%%)",
                        minutes, session.getInitialSoc(), session.getSoc()),
                    "Vérifier le véhicule et la borne"
                ));
            }
        }

        // 4. Dépassement du SoC cible
        if (session.getSoc() > session.getTargetSoc() + socDriftThreshold) {
            anomalies.add(createAnomaly(
                session.getId(),
                "SOC_OVERSHOOT",
                "LOW",
                String.format("SoC dépasse la cible: %.1f%% > %.1f%%",
                    session.getSoc(), session.getTargetSoc()),
                "Vérifier la configuration du SoC cible"
            ));
        }

        return anomalies;
    }

    private Map<String, Object> createAnomaly(String sessionId, String type, String severity,
                                               String description, String recommendation) {
        Map<String, Object> anomaly = new LinkedHashMap<>();
        anomaly.put("id", UUID.randomUUID().toString().substring(0, 8));
        anomaly.put("timestamp", LocalDateTime.now().toString());
        anomaly.put("sessionId", sessionId);
        anomaly.put("type", type);
        anomaly.put("severity", severity);
        anomaly.put("description", description);
        anomaly.put("recommendation", recommendation);
        return anomaly;
    }

    private Map<String, Object> predictCharge(Session session) {
        Map<String, Object> prediction = new LinkedHashMap<>();

        prediction.put("sessionId", session.getId());
        prediction.put("currentSoc", round(session.getSoc(), 1));
        prediction.put("targetSoc", round(session.getTargetSoc(), 1));
        prediction.put("currentEnergyKwh", round(session.getEnergyDeliveredKwh(), 2));

        double remainingSoc = session.getTargetSoc() - session.getSoc();
        double currentPower = session.getCurrentPowerKw();

        if (remainingSoc > 0 && currentPower > 0) {
            // Estimation simple basée sur capacité batterie moyenne (60 kWh)
            double batteryCapacity = 60.0;
            double remainingEnergy = (remainingSoc / 100.0) * batteryCapacity;
            double remainingMinutes = (remainingEnergy / currentPower) * 60;

            prediction.put("remainingEnergyKwh", round(remainingEnergy, 2));
            prediction.put("remainingMinutes", (int) remainingMinutes);
            prediction.put("estimatedEndTime", LocalDateTime.now().plusMinutes((long) remainingMinutes).toString());
        } else {
            prediction.put("remainingEnergyKwh", 0);
            prediction.put("remainingMinutes", 0);
            prediction.put("estimatedEndTime", null);
        }

        return prediction;
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
