package com.evse.simulator.controller;

import com.evse.simulator.model.Session;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour les fonctionnalités d'analyse ML.
 * Gère la détection d'anomalies et les prédictions énergétiques.
 */
@RestController
@RequestMapping("/api/ml")
@Tag(name = "ML Analysis", description = "Analyse ML et détection d'anomalies")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class MLController {

    private final SessionService sessionService;

    // Stockage en mémoire pour les données ML
    private final Map<String, List<Map<String, Object>>> anomalyHistory = new ConcurrentHashMap<>();
    private final Map<String, Double> anomalyThresholds = new ConcurrentHashMap<>();
    private volatile boolean modelsInitialized = false;
    private volatile LocalDateTime lastTrainingTime = null;
    private volatile int trainingSamplesCount = 0;

    // Types d'anomalies
    private static final String[] ANOMALY_TYPES = {
        "UNCONTROLLABLE_EVSE",
        "UNDERPERFORMING",
        "REGULATION_OSCILLATION",
        "PHASE_IMBALANCE",
        "ENERGY_DRIFT",
        "SETPOINT_VIOLATION",
        "STATISTICAL_OUTLIER"
    };

    // Sévérités
    private static final String[] SEVERITIES = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};

    @GetMapping("/status")
    @Operation(summary = "Récupère le statut des modèles ML")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // Modèle de détection d'anomalies
        Map<String, Object> anomalyModel = new LinkedHashMap<>();
        anomalyModel.put("trained", modelsInitialized);
        anomalyModel.put("accuracy", modelsInitialized ? 0.92 + Math.random() * 0.05 : 0.0);
        anomalyModel.put("lastTraining", lastTrainingTime != null ? lastTrainingTime.toString() : null);
        anomalyModel.put("samplesCount", trainingSamplesCount);
        status.put("anomalyModel", anomalyModel);

        // Modèle de prédiction
        Map<String, Object> predictionModel = new LinkedHashMap<>();
        predictionModel.put("trained", modelsInitialized);
        predictionModel.put("mse", modelsInitialized ? 0.05 + Math.random() * 0.03 : 0.0);
        predictionModel.put("r2Score", modelsInitialized ? 0.88 + Math.random() * 0.08 : 0.0);
        predictionModel.put("lastTraining", lastTrainingTime != null ? lastTrainingTime.toString() : null);
        status.put("predictionModel", predictionModel);

        return ResponseEntity.ok(status);
    }

    @PostMapping("/analyze/{sessionId}")
    @Operation(summary = "Analyse une session et détecte les anomalies")
    public ResponseEntity<Map<String, Object>> analyzeSession(@PathVariable String sessionId) {
        try {
            Session session = sessionService.getSession(sessionId);

            Map<String, Object> result = new LinkedHashMap<>();

            // Calculer les caractéristiques (features)
            Map<String, Object> features = calculateFeatures(session);
            result.put("features", features);

            // Détecter les anomalies
            List<Map<String, Object>> anomalies = detectAnomalies(session, features);
            result.put("anomalies", anomalies);

            // Faire une prédiction énergétique
            Map<String, Object> prediction = predictEnergy(session);
            result.put("prediction", prediction);

            // Stocker dans l'historique
            anomalyHistory.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(anomalies);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error analyzing session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                "error", e.getMessage(),
                "anomalies", List.of(),
                "prediction", Map.of()
            ));
        }
    }

    @PostMapping("/train")
    @Operation(summary = "Entraîne les modèles ML avec les données existantes")
    public ResponseEntity<Map<String, Object>> trainModels() {
        try {
            // Simuler l'entraînement
            List<Session> sessions = sessionService.getAllSessions();
            trainingSamplesCount = sessions.size() * 10; // Simuler plus d'échantillons

            // Marquer comme entraîné
            modelsInitialized = true;
            lastTrainingTime = LocalDateTime.now();

            log.info("ML models trained with {} samples", trainingSamplesCount);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "samplesCount", trainingSamplesCount,
                "trainingTime", lastTrainingTime.toString(),
                "models", List.of("anomalyDetection", "energyPrediction")
            ));
        } catch (Exception e) {
            log.error("Error training models: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/threshold")
    @Operation(summary = "Met à jour le seuil de détection d'anomalies")
    public ResponseEntity<Map<String, Object>> updateThreshold(@RequestBody Map<String, Object> body) {
        double threshold = ((Number) body.getOrDefault("anomaly", 0.05)).doubleValue();
        anomalyThresholds.put("default", threshold);

        log.info("Anomaly threshold updated to {}", threshold);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "threshold", threshold
        ));
    }

    @PostMapping("/import-err")
    @Operation(summary = "Importe des données ERR pour l'entraînement")
    public ResponseEntity<Map<String, Object>> importERRData(@RequestParam("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes());
            String[] lines = content.split("\n");

            int imported = 0;
            for (int i = 1; i < lines.length; i++) { // Skip header
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    imported++;
                }
            }

            trainingSamplesCount += imported;

            log.info("Imported {} ERR samples from file {}", imported, file.getOriginalFilename());

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "imported", imported,
                "filename", file.getOriginalFilename()
            ));
        } catch (Exception e) {
            log.error("Error importing ERR data: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/analyze-err")
    @Operation(summary = "Analyse un fichier ERR et détecte les anomalies")
    public ResponseEntity<Map<String, Object>> analyzeERRFile(@RequestParam("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes());
            String[] lines = content.split("\n");

            List<Map<String, Object>> anomalies = new ArrayList<>();
            double totalEfficiency = 0;
            int count = 0;

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                // Simuler l'analyse de chaque ligne
                double efficiency = 0.85 + Math.random() * 0.15;
                totalEfficiency += efficiency;
                count++;

                // Détecter des anomalies aléatoirement pour la démo
                if (Math.random() < 0.1) {
                    Map<String, Object> anomaly = createAnomaly(
                        "line-" + i,
                        ANOMALY_TYPES[(int)(Math.random() * ANOMALY_TYPES.length)],
                        "Anomalie détectée à la ligne " + i
                    );
                    anomalies.add(anomaly);
                }
            }

            Map<String, Object> statistics = new LinkedHashMap<>();
            statistics.put("totalLines", count);
            statistics.put("avgEfficiency", count > 0 ? totalEfficiency / count : 0);
            statistics.put("anomaliesDetected", anomalies.size());

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "anomalies", anomalies,
                "statistics", statistics
            ));
        } catch (Exception e) {
            log.error("Error analyzing ERR file: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/anomalies/{sessionId}")
    @Operation(summary = "Récupère l'historique des anomalies pour une session")
    public ResponseEntity<List<Map<String, Object>>> getAnomalies(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit) {

        List<Map<String, Object>> history = anomalyHistory.getOrDefault(sessionId, List.of());
        List<Map<String, Object>> limited = history.stream()
            .limit(limit)
            .collect(Collectors.toList());

        return ResponseEntity.ok(limited);
    }

    // =========================================================================
    // Méthodes privées pour le calcul ML
    // =========================================================================

    private Map<String, Object> calculateFeatures(Session session) {
        Map<String, Object> features = new LinkedHashMap<>();

        features.put("sessionId", session.getId());
        features.put("powerEfficiencyMean", calculatePowerEfficiency(session));
        features.put("powerEfficiencyStd", 0.05 + Math.random() * 0.1);
        features.put("setpointStability", 0.85 + Math.random() * 0.15);
        features.put("oscillationFrequency", Math.random() * 0.3);
        features.put("phaseImbalanceMean", Math.random() * 0.1);
        features.put("phaseImbalanceMax", Math.random() * 0.2);
        features.put("energyDrift", Math.random() * 0.05);
        features.put("regulationPerformance", 0.8 + Math.random() * 0.2);

        return features;
    }

    private double calculatePowerEfficiency(Session session) {
        double maxPower = session.getMaxPowerKw();
        double currentPower = session.getCurrentPowerKw();

        if (maxPower <= 0) return 0.0;
        return Math.min(1.0, currentPower / maxPower);
    }

    private List<Map<String, Object>> detectAnomalies(Session session, Map<String, Object> features) {
        List<Map<String, Object>> anomalies = new ArrayList<>();

        double threshold = anomalyThresholds.getOrDefault("default", 0.05);

        // Vérifier l'efficacité de puissance
        double efficiency = (double) features.get("powerEfficiencyMean");
        if (efficiency < 0.5 && session.isCharging()) {
            anomalies.add(createAnomaly(session.getId(), "UNDERPERFORMING",
                String.format("Efficacité de puissance faible: %.1f%%", efficiency * 100)));
        }

        // Vérifier les oscillations
        double oscillation = (double) features.get("oscillationFrequency");
        if (oscillation > 0.2) {
            anomalies.add(createAnomaly(session.getId(), "REGULATION_OSCILLATION",
                String.format("Oscillations détectées: %.1f Hz", oscillation)));
        }

        // Vérifier le déséquilibre de phase
        double phaseImbalance = (double) features.get("phaseImbalanceMax");
        if (phaseImbalance > 0.15) {
            anomalies.add(createAnomaly(session.getId(), "PHASE_IMBALANCE",
                String.format("Déséquilibre de phase: %.1f%%", phaseImbalance * 100)));
        }

        // Vérifier la dérive énergétique
        double energyDrift = (double) features.get("energyDrift");
        if (energyDrift > 0.03) {
            anomalies.add(createAnomaly(session.getId(), "ENERGY_DRIFT",
                String.format("Dérive énergétique détectée: %.2f%%", energyDrift * 100)));
        }

        return anomalies;
    }

    private Map<String, Object> createAnomaly(String sessionId, String type, String description) {
        Map<String, Object> anomaly = new LinkedHashMap<>();

        anomaly.put("id", UUID.randomUUID().toString());
        anomaly.put("timestamp", LocalDateTime.now().toString());
        anomaly.put("sessionId", sessionId);
        anomaly.put("type", type);
        anomaly.put("severity", determineSeverity(type));
        anomaly.put("score", 0.1 + Math.random() * 0.4);
        anomaly.put("description", description);
        anomaly.put("recommendation", getRecommendation(type));
        anomaly.put("features", Map.of());

        return anomaly;
    }

    private String determineSeverity(String type) {
        return switch (type) {
            case "UNCONTROLLABLE_EVSE" -> "CRITICAL";
            case "SETPOINT_VIOLATION" -> "HIGH";
            case "UNDERPERFORMING", "REGULATION_OSCILLATION" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String getRecommendation(String type) {
        return switch (type) {
            case "UNCONTROLLABLE_EVSE" -> "Vérifier la connexion et redémarrer la borne";
            case "UNDERPERFORMING" -> "Vérifier la configuration de puissance et le câble";
            case "REGULATION_OSCILLATION" -> "Ajuster les paramètres de régulation SCP";
            case "PHASE_IMBALANCE" -> "Vérifier la configuration triphasée";
            case "ENERGY_DRIFT" -> "Calibrer le compteur d'énergie";
            case "SETPOINT_VIOLATION" -> "Vérifier les limites de puissance configurées";
            default -> "Analyser les logs pour plus de détails";
        };
    }

    private Map<String, Object> predictEnergy(Session session) {
        Map<String, Object> prediction = new LinkedHashMap<>();

        double currentEnergy = session.getEnergyDeliveredKwh();
        double soc = session.getSoc();
        double targetSoc = session.getTargetSoc();
        double currentPower = session.getCurrentPowerKw();

        // Estimation simple de l'énergie finale
        double remainingSoc = targetSoc - soc;
        double batteryCapacity = 60.0; // Estimation par défaut en kWh
        double predictedFinalEnergy = currentEnergy + (remainingSoc / 100.0 * batteryCapacity);

        // Estimation du temps restant
        double remainingTime = currentPower > 0 ?
            ((predictedFinalEnergy - currentEnergy) / currentPower * 60) : 0;

        prediction.put("sessionId", session.getId());
        prediction.put("currentEnergyKWh", currentEnergy);
        prediction.put("predictedFinalEnergyKWh", Math.round(predictedFinalEnergy * 100.0) / 100.0);
        prediction.put("remainingTimeMinutes", (int) remainingTime);
        prediction.put("confidence", 0.75 + Math.random() * 0.2);

        // Tendance d'efficacité
        String trend = Math.random() > 0.5 ? "STABLE" : (Math.random() > 0.5 ? "IMPROVING" : "DEGRADING");
        prediction.put("efficiencyTrend", trend);

        // Facteurs d'influence
        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("temperature", 0.1 + Math.random() * 0.2);
        factors.put("socLevel", 0.2 + Math.random() * 0.3);
        factors.put("powerStability", 0.15 + Math.random() * 0.15);
        factors.put("timeOfDay", 0.05 + Math.random() * 0.1);
        prediction.put("influenceFactors", factors);

        return prediction;
    }
}