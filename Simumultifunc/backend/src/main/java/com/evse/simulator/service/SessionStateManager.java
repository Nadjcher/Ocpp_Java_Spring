package com.evse.simulator.service;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gestionnaire des transitions d'état pour les sessions OCPP.
 * Valide les transitions et envoie les StatusNotification automatiquement.
 */
@Service
@Slf4j
public class SessionStateManager {

    private final SimpMessagingTemplate messagingTemplate;

    public SessionStateManager(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Effectue une transition d'état avec validation.
     *
     * @param session  La session à modifier
     * @param newState Le nouvel état cible
     * @return true si la transition a réussi, false sinon
     */
    public boolean transition(Session session, SessionState newState) {
        if (session == null || newState == null) {
            log.error("[ERR] Invalid transition: session or newState is null");
            return false;
        }

        SessionState currentState = session.getState();
        if (currentState == null) {
            currentState = SessionState.DISCONNECTED;
        }

        // Vérifier si la transition est valide
        if (!currentState.canTransitionTo(newState)) {
            log.error("[ERR] Invalid transition: {} → {} for session {} (cpId: {})",
                    currentState.getValue(), newState.getValue(),
                    session.getId(), session.getCpId());
            log.error("   Valid transitions from {}: {}",
                    currentState.getValue(),
                    Arrays.toString(currentState.getValidTransitions()));
            return false;
        }

        // Effectuer la transition
        SessionState previousState = currentState;
        session.setState(newState);
        session.setLastStateChange(LocalDateTime.now());

        log.info("[STATE] {} → {} (session: {}, cpId: {})",
                previousState.getValue(),
                newState.getValue(),
                session.getId(),
                session.getCpId());

        // Broadcaster le changement au frontend via WebSocket
        broadcastStateChange(session, previousState, newState);

        return true;
    }

    /**
     * Force une transition sans validation (pour cas exceptionnels).
     *
     * @param session  La session à modifier
     * @param newState Le nouvel état cible
     * @param reason   Raison de la transition forcée
     */
    public void forceTransition(Session session, SessionState newState, String reason) {
        if (session == null || newState == null) {
            return;
        }

        SessionState previousState = session.getState();
        session.setState(newState);
        session.setLastStateChange(LocalDateTime.now());

        log.warn("[STATE] FORCED {} → {} (session: {}, cpId: {}, reason: {})",
                previousState != null ? previousState.getValue() : "null",
                newState.getValue(),
                session.getId(),
                session.getCpId(),
                reason);

        broadcastStateChange(session, previousState, newState);
    }

    /**
     * Vérifie si une transition est valide sans l'effectuer.
     *
     * @param session    La session
     * @param targetState L'état cible
     * @return true si la transition serait valide
     */
    public boolean canTransition(Session session, SessionState targetState) {
        if (session == null || targetState == null) {
            return false;
        }
        SessionState currentState = session.getState();
        if (currentState == null) {
            return targetState == SessionState.CONNECTED || targetState == SessionState.CONNECTING;
        }
        return currentState.canTransitionTo(targetState);
    }

    /**
     * Détermine si un StatusNotification doit être envoyé pour cette transition.
     *
     * @param from État source
     * @param to   État cible
     * @return true si un StatusNotification est nécessaire
     */
    public boolean shouldSendStatusNotification(SessionState from, SessionState to) {
        if (!to.requiresStatusNotification()) {
            return false;
        }

        String fromOcpp = from != null ? from.getOcppStatus() : null;
        String toOcpp = to.getOcppStatus();

        // Envoyer si le status OCPP change
        return toOcpp != null && !toOcpp.equals(fromOcpp);
    }

    /**
     * Retourne le statut OCPP à envoyer pour une transition.
     *
     * @param newState Le nouvel état
     * @return Le statut OCPP ou null si pas de StatusNotification nécessaire
     */
    public String getOcppStatusForTransition(SessionState newState) {
        return newState != null ? newState.getOcppStatus() : null;
    }

    /**
     * Récupère les actions valides pour une session (pour le frontend).
     *
     * @param session La session
     * @return Liste des actions possibles
     */
    public List<String> getValidActions(Session session) {
        List<String> actions = new ArrayList<>();
        if (session == null) {
            return actions;
        }

        SessionState state = session.getState();
        if (state == null) {
            state = SessionState.DISCONNECTED;
        }

        switch (state) {
            case DISCONNECTED, IDLE:
                actions.add("connect");
                break;
            case CONNECTING:
                actions.add("cancel");
                break;
            case CONNECTED:
                actions.add("boot");
                actions.add("disconnect");
                break;
            case BOOT_ACCEPTED, AVAILABLE:
                actions.add("park");
                actions.add("plug");
                actions.add("disconnect");
                break;
            case PARKED:
                actions.add("plug");
                actions.add("unpark");
                actions.add("disconnect");
                break;
            case PLUGGED:
                actions.add("authorize");
                actions.add("unplug");
                break;
            case AUTHORIZING:
                // En attente de réponse
                break;
            case AUTHORIZED:
                actions.add("startTransaction");
                actions.add("unplug");
                break;
            case STARTING:
                // En attente de réponse
                break;
            case CHARGING:
                actions.add("stopTransaction");
                actions.add("suspend");
                actions.add("sendMeterValues");
                break;
            case SUSPENDED_EVSE, SUSPENDED_EV:
                actions.add("resume");
                actions.add("stopTransaction");
                break;
            case STOPPING:
                // En attente de réponse
                break;
            case FINISHING:
                actions.add("unplug");
                break;
            case FAULTED:
                actions.add("clearFault");
                actions.add("disconnect");
                break;
            case UNAVAILABLE:
                actions.add("setAvailable");
                break;
            case RESERVED:
                actions.add("cancelReservation");
                break;
            default:
                break;
        }

        return actions;
    }

    /**
     * Retourne les transitions valides depuis l'état actuel.
     *
     * @param session La session
     * @return Liste des états cibles possibles
     */
    public List<SessionState> getValidTransitions(Session session) {
        if (session == null || session.getState() == null) {
            return List.of(SessionState.CONNECTING, SessionState.CONNECTED);
        }
        return Arrays.asList(session.getState().getValidTransitions());
    }

    /**
     * Broadcast le changement d'état au frontend.
     */
    private void broadcastStateChange(Session session, SessionState previousState, SessionState newState) {
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend(
                        "/topic/session/" + session.getId() + "/state",
                        java.util.Map.of(
                                "sessionId", session.getId(),
                                "cpId", session.getCpId() != null ? session.getCpId() : "",
                                "previousState", previousState != null ? previousState.getValue() : "null",
                                "newState", newState.getValue(),
                                "ocppStatus", newState.getOcppStatus() != null ? newState.getOcppStatus() : "",
                                "timestamp", LocalDateTime.now().toString(),
                                "validActions", getValidActions(session)
                        )
                );
            }
        } catch (Exception e) {
            log.debug("Could not broadcast state change: {}", e.getMessage());
        }
    }
}
