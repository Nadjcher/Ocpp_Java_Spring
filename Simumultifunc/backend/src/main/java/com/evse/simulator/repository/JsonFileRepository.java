package com.evse.simulator.repository;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.TNRScenario;
import com.evse.simulator.model.VehicleProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository pour la persistance JSON des données.
 * <p>
 * Gère le stockage et la récupération des sessions, véhicules et scénarios TNR
 * dans des fichiers JSON avec sauvegarde automatique.
 * </p>
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class JsonFileRepository {

    private final ObjectMapper objectMapper;

    @Value("${data.path:./data}")
    private String dataPath;

    @Value("${data.vehicles-file:./data/vehicles.json}")
    private String vehiclesFile;

    @Value("${data.sessions-file:./data/sessions.json}")
    private String sessionsFile;

    @Value("${data.tnr-scenarios-file:./data/tnr-scenarios.json}")
    private String tnrScenariosFile;

    // Caches en mémoire
    private final Map<String, Session> sessionsCache = new ConcurrentHashMap<>();
    private final Map<String, VehicleProfile> vehiclesCache = new ConcurrentHashMap<>();
    private final Map<String, TNRScenario> tnrScenariosCache = new ConcurrentHashMap<>();

    // Flags de modification
    private volatile boolean sessionsDirty = false;
    private volatile boolean vehiclesDirty = false;
    private volatile boolean tnrScenariosDirty = false;

    /**
     * Initialise le repository au démarrage.
     */
    @PostConstruct
    public void init() {
        try {
            ensureDataDirectory();
            loadVehicles();
            loadSessions();
            loadTNRScenarios();
            log.info("JsonFileRepository initialized: {} vehicles, {} sessions, {} TNR scenarios",
                    vehiclesCache.size(), sessionsCache.size(), tnrScenariosCache.size());
        } catch (Exception e) {
            log.error("Failed to initialize JsonFileRepository", e);
        }
    }

    /**
     * Crée le répertoire de données s'il n'existe pas.
     */
    private void ensureDataDirectory() throws IOException {
        Path path = Paths.get(dataPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("Created data directory: {}", dataPath);
        }
    }

    // =========================================================================
    // Sessions
    // =========================================================================

    /**
     * Charge les sessions depuis le fichier JSON.
     */
    private void loadSessions() {
        File file = new File(sessionsFile);
        if (file.exists()) {
            try {
                List<Session> sessions = objectMapper.readValue(file,
                        new TypeReference<List<Session>>() {});
                sessions.forEach(s -> sessionsCache.put(s.getId(), s));
                log.info("Loaded {} sessions from {}", sessions.size(), sessionsFile);
            } catch (Exception e) {
                log.error("Failed to load sessions from {}", sessionsFile, e);
            }
        }
    }

    /**
     * Sauvegarde les sessions dans le fichier JSON.
     */
    public synchronized void saveSessions() {
        if (!sessionsDirty) {
            return;
        }
        try {
            List<Session> sessions = new ArrayList<>(sessionsCache.values());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(sessionsFile), sessions);
            sessionsDirty = false;
            log.debug("Saved {} sessions to {}", sessions.size(), sessionsFile);
        } catch (Exception e) {
            log.error("Failed to save sessions to {}", sessionsFile, e);
        }
    }

    /**
     * Récupère toutes les sessions.
     */
    public List<Session> findAllSessions() {
        return new ArrayList<>(sessionsCache.values());
    }

    /**
     * Récupère une session par ID.
     */
    public Optional<Session> findSessionById(String id) {
        return Optional.ofNullable(sessionsCache.get(id));
    }

    /**
     * Sauvegarde une session.
     */
    public Session saveSession(Session session) {
        session.touch();
        sessionsCache.put(session.getId(), session);
        sessionsDirty = true;
        return session;
    }

    /**
     * Supprime une session.
     */
    public void deleteSession(String id) {
        sessionsCache.remove(id);
        sessionsDirty = true;
    }

    /**
     * Compte les sessions.
     */
    public long countSessions() {
        return sessionsCache.size();
    }

    // =========================================================================
    // Vehicles
    // =========================================================================

    /**
     * Charge les véhicules depuis le fichier JSON.
     */
    private void loadVehicles() {
        File file = new File(vehiclesFile);
        if (file.exists()) {
            try {
                List<VehicleProfile> vehicles = objectMapper.readValue(file,
                        new TypeReference<List<VehicleProfile>>() {});
                vehicles.stream()
                        .filter(v -> v != null && v.getId() != null)
                        .forEach(v -> vehiclesCache.put(v.getId(), v));
                log.info("Loaded {} vehicle profiles from {}", vehiclesCache.size(), vehiclesFile);
            } catch (Exception e) {
                log.error("Failed to load vehicles from {}", vehiclesFile, e);
            }
        } else {
            log.warn("Vehicles file not found: {}", vehiclesFile);
        }
    }

    /**
     * Sauvegarde les véhicules dans le fichier JSON.
     */
    public synchronized void saveVehicles() {
        if (!vehiclesDirty) {
            return;
        }
        try {
            List<VehicleProfile> vehicles = new ArrayList<>(vehiclesCache.values());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(vehiclesFile), vehicles);
            vehiclesDirty = false;
            log.debug("Saved {} vehicles to {}", vehicles.size(), vehiclesFile);
        } catch (Exception e) {
            log.error("Failed to save vehicles to {}", vehiclesFile, e);
        }
    }

    /**
     * Récupère tous les véhicules.
     */
    public List<VehicleProfile> findAllVehicles() {
        return new ArrayList<>(vehiclesCache.values());
    }

    /**
     * Récupère un véhicule par ID.
     */
    public Optional<VehicleProfile> findVehicleById(String id) {
        return Optional.ofNullable(vehiclesCache.get(id));
    }

    /**
     * Sauvegarde un véhicule.
     */
    public VehicleProfile saveVehicle(VehicleProfile vehicle) {
        vehiclesCache.put(vehicle.getId(), vehicle);
        vehiclesDirty = true;
        return vehicle;
    }

    /**
     * Supprime un véhicule.
     */
    public void deleteVehicle(String id) {
        vehiclesCache.remove(id);
        vehiclesDirty = true;
    }

    // =========================================================================
    // TNR Scenarios
    // =========================================================================

    /**
     * Charge les scénarios TNR depuis le fichier JSON.
     */
    private void loadTNRScenarios() {
        File file = new File(tnrScenariosFile);
        if (file.exists()) {
            try {
                List<TNRScenario> scenarios = objectMapper.readValue(file,
                        new TypeReference<List<TNRScenario>>() {});
                scenarios.forEach(s -> tnrScenariosCache.put(s.getId(), s));
                log.info("Loaded {} TNR scenarios from {}", scenarios.size(), tnrScenariosFile);
            } catch (Exception e) {
                log.error("Failed to load TNR scenarios from {}", tnrScenariosFile, e);
            }
        }
    }

    /**
     * Sauvegarde les scénarios TNR dans le fichier JSON.
     */
    public synchronized void saveTNRScenarios() {
        if (!tnrScenariosDirty) {
            return;
        }
        try {
            List<TNRScenario> scenarios = new ArrayList<>(tnrScenariosCache.values());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(tnrScenariosFile), scenarios);
            tnrScenariosDirty = false;
            log.debug("Saved {} TNR scenarios to {}", scenarios.size(), tnrScenariosFile);
        } catch (Exception e) {
            log.error("Failed to save TNR scenarios to {}", tnrScenariosFile, e);
        }
    }

    /**
     * Récupère tous les scénarios TNR.
     */
    public List<TNRScenario> findAllTNRScenarios() {
        return new ArrayList<>(tnrScenariosCache.values());
    }

    /**
     * Récupère un scénario TNR par ID.
     */
    public Optional<TNRScenario> findTNRScenarioById(String id) {
        return Optional.ofNullable(tnrScenariosCache.get(id));
    }

    /**
     * Sauvegarde un scénario TNR.
     */
    public TNRScenario saveTNRScenario(TNRScenario scenario) {
        tnrScenariosCache.put(scenario.getId(), scenario);
        tnrScenariosDirty = true;
        return scenario;
    }

    /**
     * Supprime un scénario TNR.
     */
    public void deleteTNRScenario(String id) {
        tnrScenariosCache.remove(id);
        tnrScenariosDirty = true;
    }

    // =========================================================================
    // Auto-save
    // =========================================================================

    /**
     * Sauvegarde automatique périodique.
     */
    @Scheduled(fixedDelayString = "${data.auto-save-interval:30000}")
    public void autoSave() {
        saveSessions();
        saveVehicles();
        saveTNRScenarios();
    }

    /**
     * Force la sauvegarde de toutes les données.
     */
    public void saveAll() {
        sessionsDirty = true;
        vehiclesDirty = true;
        tnrScenariosDirty = true;
        autoSave();
        log.info("Forced save of all data");
    }

    /**
     * Recharge toutes les données depuis les fichiers.
     */
    public void reloadAll() {
        sessionsCache.clear();
        vehiclesCache.clear();
        tnrScenariosCache.clear();
        loadSessions();
        loadVehicles();
        loadTNRScenarios();
        log.info("Reloaded all data from files");
    }
}