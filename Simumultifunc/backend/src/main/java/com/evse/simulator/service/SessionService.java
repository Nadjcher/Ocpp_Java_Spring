package com.evse.simulator.service;

import com.evse.simulator.domain.service.BroadcastService;
import com.evse.simulator.exception.SessionNotFoundException;
import com.evse.simulator.model.*;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.repository.DataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service de gestion des sessions de charge.
 * <p>
 * Gère le cycle de vie complet des sessions EVSE :
 * création, mise à jour, connexion, charge, déconnexion.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionService implements com.evse.simulator.domain.service.SessionService {

    private final DataRepository repository;
    private final BroadcastService broadcaster;

    // =========================================================================
    // CRUD Operations
    // =========================================================================

    /**
     * Récupère toutes les sessions.
     *
     * @return liste de toutes les sessions
     */
    public List<Session> getAllSessions() {
        return repository.findAllSessions();
    }

    /**
     * Récupère une session par ID.
     *
     * @param id identifiant de la session
     * @return la session trouvée
     * @throws SessionNotFoundException si la session n'existe pas
     */
    public Session getSession(String id) {
        return repository.findSessionById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
    }

    /**
     * Récupère une session par ID (Optional).
     *
     * @param id identifiant de la session
     * @return Optional contenant la session ou vide
     */
    public Optional<Session> findSession(String id) {
        return repository.findSessionById(id);
    }

    /**
     * Crée une nouvelle session.
     *
     * @param session données de la session
     * @return la session créée
     */
    public Session createSession(Session session) {
        if (session.getId() == null || session.getId().isBlank()) {
            session.setId(UUID.randomUUID().toString());
        }

        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setState(SessionState.DISCONNECTED);
        session.setConnected(false);

        // Valeurs par défaut
        if (session.getChargerType() == null) {
            session.setChargerType(ChargerType.AC_TRI);
        }
        if (session.getConnectorId() <= 0) {
            session.setConnectorId(1);
        }
        if (session.getMaxPowerKw() <= 0) {
            session.setMaxPowerKw(session.getChargerType().getMaxPowerKw());
        }
        if (session.getMaxCurrentA() <= 0) {
            session.setMaxCurrentA(session.getChargerType().getMaxCurrentA());
        }
        if (session.getVoltage() <= 0) {
            session.setVoltage(session.getChargerType().getVoltage());
        }

        Session saved = repository.saveSession(session);
        log.info("Created session: {} - {} ({})", saved.getId(), saved.getTitle(), saved.getCpId());

        broadcaster.broadcastSession(saved);
        return saved;
    }

    /**
     * Met à jour une session existante.
     *
     * @param id identifiant de la session
     * @param updates données de mise à jour
     * @return la session mise à jour
     */
    public Session updateSession(String id, Session updates) {
        Session session = getSession(id);

        // Mise à jour des champs modifiables
        if (updates.getTitle() != null) {
            session.setTitle(updates.getTitle());
        }
        if (updates.getUrl() != null) {
            session.setUrl(updates.getUrl());
        }
        if (updates.getCpId() != null) {
            session.setCpId(updates.getCpId());
        }
        if (updates.getBearerToken() != null) {
            session.setBearerToken(updates.getBearerToken());
        }
        if (updates.getVehicleProfile() != null) {
            session.setVehicleProfile(updates.getVehicleProfile());
        }
        if (updates.getChargerType() != null) {
            session.setChargerType(updates.getChargerType());
        }
        if (updates.getConnectorId() > 0) {
            session.setConnectorId(updates.getConnectorId());
        }
        if (updates.getIdTag() != null) {
            session.setIdTag(updates.getIdTag());
        }
        if (updates.getTargetSoc() > 0) {
            session.setTargetSoc(updates.getTargetSoc());
        }
        if (updates.getMaxPowerKw() > 0) {
            session.setMaxPowerKw(updates.getMaxPowerKw());
        }
        if (updates.getMaxCurrentA() > 0) {
            session.setMaxCurrentA(updates.getMaxCurrentA());
        }
        if (updates.getHeartbeatInterval() > 0) {
            session.setHeartbeatInterval(updates.getHeartbeatInterval());
        }
        if (updates.getMeterValuesInterval() > 0) {
            session.setMeterValuesInterval(updates.getMeterValuesInterval());
        }

        Session saved = repository.saveSession(session);
        log.debug("Updated session: {}", saved.getId());

        broadcaster.broadcastSession(saved);
        return saved;
    }

    /**
     * Supprime une session.
     *
     * @param id identifiant de la session
     */
    public void deleteSession(String id) {
        Session session = getSession(id);

        // Vérifier que la session n'est pas connectée
        if (session.isConnected()) {
            throw new IllegalStateException("Cannot delete connected session. Disconnect first.");
        }

        repository.deleteSession(id);
        log.info("Deleted session: {}", id);
    }

    // =========================================================================
    // State Management
    // =========================================================================

    /**
     * Met à jour l'état d'une session.
     *
     * @param id identifiant de la session
     * @param state nouvel état
     * @return la session mise à jour
     */
    public Session updateState(String id, SessionState state) {
        Session session = getSession(id);
        SessionState previousState = session.getState();
        session.setState(state);

        // Mise à jour des flags associés selon le nouvel état
        switch (state) {
            case CONNECTED, BOOT_ACCEPTED, PARKED, PLUGGED, AUTHORIZED, AVAILABLE -> {
                session.setConnected(true);
            }
            case CHARGING, STARTING -> {
                session.setConnected(true);
                session.setCharging(true);
                if (session.getStartTime() == null) {
                    session.setStartTime(LocalDateTime.now());
                }
            }
            case SUSPENDED_EVSE, SUSPENDED_EV -> {
                session.setConnected(true);
                session.setCharging(false);
            }
            case STOPPING, FINISHING, FINISHED -> {
                session.setConnected(true);
                session.setCharging(false);
                session.setStopTime(LocalDateTime.now());
            }
            case DISCONNECTED, IDLE, DISCONNECTING -> {
                session.setConnected(false);
                session.setCharging(false);
                session.setAuthorized(false);
            }
            default -> {
                // Autres états: ne pas modifier les flags
            }
        }

        Session saved = repository.saveSession(session);

        // Log du changement d'état
        session.addLog(LogEntry.info("STATE",
                String.format("%s → %s", previousState, state)));

        log.debug("Session {} state: {} → {}", id, previousState, state);

        broadcaster.broadcastSession(saved);
        return saved;
    }

    /**
     * Met à jour le flag d'autorisation d'une session.
     *
     * @param id identifiant de la session
     * @param authorized true si autorisé
     * @return la session mise à jour
     */
    public Session setAuthorized(String id, boolean authorized) {
        Session session = getSession(id);
        session.setAuthorized(authorized);
        Session saved = repository.saveSession(session);
        log.debug("Session {} authorized: {}", id, authorized);
        broadcaster.broadcastSession(saved);
        return saved;
    }

    /**
     * Met à jour les données de charge d'une session.
     *
     * @param id identifiant de la session
     * @param soc nouveau SoC
     * @param powerKw nouvelle puissance
     * @param energyKwh énergie délivrée
     */
    public void updateChargingData(String id, double soc, double powerKw, double energyKwh) {
        Session session = getSession(id);

        session.setSoc(soc);
        session.setCurrentPowerKw(powerKw);
        session.setEnergyDeliveredKwh(energyKwh);
        session.setMeterValue((int) (energyKwh * 1000)); // Conversion en Wh

        // Calcul du courant par phase
        if (session.getVoltage() > 0) {
            double voltage = session.getVoltage();
            int phases = session.getChargerType().getPhases();
            double current;

            if (phases > 1) {
                // Triphasé: déterminer si la tension est phase-neutre ou ligne-ligne
                if (voltage < 300) {
                    // Tension phase-neutre (ex: 230V) - I = P / (V × phases)
                    current = (powerKw * 1000) / (voltage * phases);
                } else {
                    // Tension ligne-ligne (ex: 400V) - I = P / (V × √3)
                    current = (powerKw * 1000) / (voltage * Math.sqrt(3));
                }
            } else {
                // Monophasé: I = P / V
                current = (powerKw * 1000) / voltage;
            }
            session.setCurrentA(current);
        }

        // Ajout des points de données
        session.addSocDataPoint(ChartPoint.of(soc));
        session.addPowerDataPoint(ChartPoint.of(powerKw));

        repository.saveSession(session);

        // Diffusion des données de graphique
        broadcaster.broadcastChartData(id, ChartData.builder()
                .sessionId(id)
                .socPoint(ChartPoint.of(soc))
                .powerPoint(ChartPoint.of(powerKw))
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * Ajoute un log à une session.
     *
     * @param id identifiant de la session
     * @param logEntry entrée de log
     */
    public void addLog(String id, LogEntry logEntry) {
        findSession(id).ifPresent(session -> {
            session.addLog(logEntry);
            repository.saveSession(session);
            broadcaster.broadcastLog(id, logEntry);
        });
    }

    /**
     * Ajoute un message OCPP à une session.
     *
     * @param id identifiant de la session
     * @param message message OCPP
     */
    public void addOcppMessage(String id, OCPPMessage message) {
        findSession(id).ifPresent(session -> {
            message.setSessionId(id);
            session.addOcppMessage(message);
            repository.saveSession(session);
            broadcaster.broadcastOcppMessage(id, message);
        });
    }

    // =========================================================================
    // Queries
    // =========================================================================

    /**
     * Récupère les sessions par état.
     *
     * @param state état recherché
     * @return liste des sessions dans cet état
     */
    public List<Session> getSessionsByState(SessionState state) {
        return repository.findAllSessions().stream()
                .filter(s -> s.getState() == state)
                .collect(Collectors.toList());
    }

    /**
     * Récupère les sessions connectées.
     *
     * @return liste des sessions connectées
     */
    public List<Session> getConnectedSessions() {
        return repository.findAllSessions().stream()
                .filter(Session::isConnected)
                .collect(Collectors.toList());
    }

    /**
     * Récupère les sessions en charge.
     *
     * @return liste des sessions en charge
     */
    public List<Session> getChargingSessions() {
        return repository.findAllSessions().stream()
                .filter(Session::isCharging)
                .collect(Collectors.toList());
    }

    /**
     * Compte le nombre de sessions.
     *
     * @return nombre total de sessions
     */
    public long countSessions() {
        return repository.countSessions();
    }

    /**
     * Compte les sessions par état.
     *
     * @return map état → nombre
     */
    public java.util.Map<SessionState, Long> countSessionsByState() {
        return repository.findAllSessions().stream()
                .collect(Collectors.groupingBy(Session::getState, Collectors.counting()));
    }

    // =========================================================================
    // Batch Operations
    // =========================================================================

    /**
     * Crée plusieurs sessions en lot.
     *
     * @param count nombre de sessions à créer
     * @param template modèle de session
     * @return liste des sessions créées
     */
    public List<Session> createBatchSessions(int count, Session template) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    Session session = Session.builder()
                            .title(template.getTitle() + " #" + (i + 1))
                            .url(template.getUrl())
                            .cpId(template.getCpId() + "_" + (i + 1))
                            .bearerToken(template.getBearerToken())
                            .vehicleProfile(template.getVehicleProfile())
                            .chargerType(template.getChargerType())
                            .idTag(template.getIdTag())
                            .soc(template.getSoc())
                            .targetSoc(template.getTargetSoc())
                            .build();
                    return createSession(session);
                })
                .collect(Collectors.toList());
    }

    /**
     * Supprime toutes les sessions déconnectées.
     *
     * @return nombre de sessions supprimées
     */
    public int deleteDisconnectedSessions() {
        List<Session> toDelete = repository.findAllSessions().stream()
                .filter(s -> !s.isConnected())
                .collect(Collectors.toList());

        toDelete.forEach(s -> repository.deleteSession(s.getId()));

        log.info("Deleted {} disconnected sessions", toDelete.size());
        return toDelete.size();
    }

    // =========================================================================
    // Session Persistence & Keepalive
    // =========================================================================

    /**
     * Met à jour le keepalive d'une session.
     * Appelé par le frontend pour maintenir la session active.
     *
     * @param id identifiant de la session
     * @return la session mise à jour
     */
    public Session keepalive(String id) {
        Session session = getSession(id);
        session.updateKeepalive();
        Session saved = repository.saveSession(session);
        log.debug("[KEEPALIVE] Session {} keepalive updated", id);
        return saved;
    }

    /**
     * Marque une session pour arrêt volontaire.
     * Seule méthode qui devrait vraiment fermer une session.
     *
     * @param id identifiant de la session
     * @param reason raison de l'arrêt
     * @return la session mise à jour
     */
    public Session setVoluntaryStop(String id, String reason) {
        Session session = getSession(id);
        session.prepareVoluntaryDisconnect(reason);
        Session saved = repository.saveSession(session);
        log.info("[VOLUNTARY_STOP] Session {} marked for voluntary stop: {}", id, reason);
        broadcaster.broadcastSession(saved);
        return saved;
    }

    /**
     * Annule le flag d'arrêt volontaire pour permettre la reconnexion.
     *
     * @param id identifiant de la session
     * @return la session mise à jour
     */
    public Session clearVoluntaryStop(String id) {
        Session session = getSession(id);
        session.prepareReconnect();
        Session saved = repository.saveSession(session);
        log.info("[RECONNECT_ALLOWED] Session {} cleared for reconnection", id);
        broadcaster.broadcastSession(saved);
        return saved;
    }

    /**
     * Marque une session comme en arrière-plan.
     *
     * @param id identifiant de la session
     * @param backgrounded true si en arrière-plan
     * @return la session mise à jour
     */
    public Session setBackgrounded(String id, boolean backgrounded) {
        Session session = getSession(id);
        session.setBackgrounded(backgrounded);
        session.touch();
        Session saved = repository.saveSession(session);
        log.debug("[VISIBILITY] Session {} backgrounded: {}", id, backgrounded);
        return saved;
    }

    /**
     * Vérifie si une session peut être fermée.
     * Retourne false si la session n'a pas été marquée pour arrêt volontaire.
     *
     * @param id identifiant de la session
     * @return true si la session peut être fermée
     */
    public boolean canCloseSession(String id) {
        return findSession(id)
                .map(Session::isVoluntaryStop)
                .orElse(true); // Si session non trouvée, on peut la "fermer"
    }

    /**
     * Récupère les sessions qui peuvent être reconnectées.
     * Sessions déconnectées sans voluntaryStop.
     *
     * @return liste des sessions reconnectables
     */
    public List<Session> getReconnectableSessions() {
        return repository.findAllSessions().stream()
                .filter(s -> !s.isConnected() && s.canReconnect())
                .collect(Collectors.toList());
    }

    /**
     * Récupère les sessions stales (pas de keepalive depuis longtemps).
     *
     * @return liste des sessions stales
     */
    public List<Session> getStaleSessions() {
        return repository.findAllSessions().stream()
                .filter(Session::isStale)
                .collect(Collectors.toList());
    }

    /**
     * Met à jour la raison de déconnexion.
     *
     * @param id identifiant de la session
     * @param reason raison de la déconnexion
     */
    public void setDisconnectReason(String id, String reason) {
        findSession(id).ifPresent(session -> {
            session.setDisconnectReason(reason);
            session.touch();
            repository.saveSession(session);
            log.info("[DISCONNECT_REASON] Session {}: {}", id, reason);
        });
    }
}