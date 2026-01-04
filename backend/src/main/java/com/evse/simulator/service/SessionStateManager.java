package com.evse.simulator.service;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des transitions d'état des sessions.
 * Valide et applique les changements d'état selon les règles métier.
 */
@Service
@Slf4j
public class SessionStateManager {

    /**
     * Transitions valides depuis chaque état.
     * Clé: état source, Valeur: ensemble des états cibles autorisés.
     */
    private static final Map<SessionState, Set<SessionState>> VALID_TRANSITIONS = Map.ofEntries(
            // Déconnecté: peut se connecter
            Map.entry(SessionState.DISCONNECTED, Set.of(
                    SessionState.CONNECTED,
                    SessionState.FAULTED
            )),

            // Connecté: peut recevoir BootNotification
            Map.entry(SessionState.CONNECTED, Set.of(
                    SessionState.BOOT_ACCEPTED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Boot accepté: peut passer en disponible, garé, branché, ou réservé
            Map.entry(SessionState.BOOT_ACCEPTED, Set.of(
                    SessionState.AVAILABLE,
                    SessionState.PARKED,
                    SessionState.PLUGGED,
                    SessionState.RESERVED,
                    SessionState.AUTHORIZING,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Disponible: peut être garé, branché, ou réservé
            Map.entry(SessionState.AVAILABLE, Set.of(
                    SessionState.PARKED,
                    SessionState.PLUGGED,
                    SessionState.RESERVED,
                    SessionState.AUTHORIZING,
                    SessionState.BOOT_ACCEPTED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Garé: peut être branché ou partir
            Map.entry(SessionState.PARKED, Set.of(
                    SessionState.PLUGGED,
                    SessionState.AVAILABLE,
                    SessionState.BOOT_ACCEPTED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Branché: peut s'autoriser, se débrancher
            Map.entry(SessionState.PLUGGED, Set.of(
                    SessionState.AUTHORIZING,
                    SessionState.AUTHORIZED,
                    SessionState.PARKED,
                    SessionState.AVAILABLE,
                    SessionState.BOOT_ACCEPTED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Réservé: peut être branché ou annulé
            Map.entry(SessionState.RESERVED, Set.of(
                    SessionState.PLUGGED,
                    SessionState.AVAILABLE,
                    SessionState.BOOT_ACCEPTED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // En cours d'autorisation: peut être autorisé ou refusé
            Map.entry(SessionState.AUTHORIZING, Set.of(
                    SessionState.AUTHORIZED,
                    SessionState.PLUGGED,
                    SessionState.AVAILABLE,
                    SessionState.BOOT_ACCEPTED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Autorisé: peut démarrer la transaction
            Map.entry(SessionState.AUTHORIZED, Set.of(
                    SessionState.STARTING,
                    SessionState.PLUGGED,
                    SessionState.AVAILABLE,
                    SessionState.BOOT_ACCEPTED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Démarrage en cours: peut passer en charge ou échouer
            Map.entry(SessionState.STARTING, Set.of(
                    SessionState.CHARGING,
                    SessionState.AUTHORIZED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // En charge: peut être suspendu ou arrêté
            Map.entry(SessionState.CHARGING, Set.of(
                    SessionState.SUSPENDED_EV,
                    SessionState.SUSPENDED_EVSE,
                    SessionState.STOPPING,
                    SessionState.FINISHING,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Suspendu par EV: peut reprendre ou s'arrêter
            Map.entry(SessionState.SUSPENDED_EV, Set.of(
                    SessionState.CHARGING,
                    SessionState.STOPPING,
                    SessionState.FINISHING,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Suspendu par EVSE: peut reprendre ou s'arrêter
            Map.entry(SessionState.SUSPENDED_EVSE, Set.of(
                    SessionState.CHARGING,
                    SessionState.STOPPING,
                    SessionState.FINISHING,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Arrêt en cours: passe en finition
            Map.entry(SessionState.STOPPING, Set.of(
                    SessionState.FINISHING,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // Finition: peut être disponible ou débranché
            Map.entry(SessionState.FINISHING, Set.of(
                    SessionState.AVAILABLE,
                    SessionState.PARKED,
                    SessionState.PLUGGED,
                    SessionState.BOOT_ACCEPTED,
                    SessionState.FAULTED,
                    SessionState.DISCONNECTED
            )),

            // En faute: peut récupérer vers n'importe quel état
            Map.entry(SessionState.FAULTED, Set.of(
                    SessionState.AVAILABLE,
                    SessionState.BOOT_ACCEPTED,
                    SessionState.DISCONNECTED
            ))
    );

    /**
     * Cache des états précédents par session (pour rollback si nécessaire).
     */
    private final Map<String, SessionState> previousStates = new ConcurrentHashMap<>();

    /**
     * Tente une transition d'état pour une session.
     *
     * @param session     La session à modifier
     * @param targetState L'état cible souhaité
     * @return true si la transition est valide et appliquée, false sinon
     */
    public boolean transition(Session session, SessionState targetState) {
        if (session == null || targetState == null) {
            log.warn("Cannot transition: session or targetState is null");
            return false;
        }

        SessionState currentState = session.getState();
        if (currentState == null) {
            currentState = SessionState.DISCONNECTED;
        }

        // Vérifier si la transition est valide
        Set<SessionState> validTargets = VALID_TRANSITIONS.get(currentState);
        if (validTargets == null || !validTargets.contains(targetState)) {
            log.warn("Invalid state transition for session {}: {} -> {} (not in valid transitions)",
                    session.getId(), currentState, targetState);
            return false;
        }

        // Sauvegarder l'état précédent
        previousStates.put(session.getId(), currentState);

        // Appliquer la transition
        session.setState(targetState);
        session.setLastStateChange(LocalDateTime.now());

        log.info("Session {} state transition: {} -> {}", session.getId(), currentState, targetState);
        return true;
    }

    /**
     * Force une transition d'état sans validation.
     * À utiliser avec précaution pour les cas spéciaux.
     *
     * @param session     La session à modifier
     * @param targetState L'état cible
     */
    public void forceTransition(Session session, SessionState targetState) {
        if (session == null || targetState == null) {
            return;
        }

        SessionState currentState = session.getState();
        previousStates.put(session.getId(), currentState);
        session.setState(targetState);
        session.setLastStateChange(LocalDateTime.now());

        log.warn("Session {} FORCED state transition: {} -> {}", session.getId(), currentState, targetState);
    }

    /**
     * Annule la dernière transition et revient à l'état précédent.
     *
     * @param session La session à restaurer
     * @return true si le rollback a réussi
     */
    public boolean rollback(Session session) {
        if (session == null) {
            return false;
        }

        SessionState previousState = previousStates.remove(session.getId());
        if (previousState == null) {
            log.warn("No previous state to rollback for session {}", session.getId());
            return false;
        }

        SessionState currentState = session.getState();
        session.setState(previousState);
        session.setLastStateChange(LocalDateTime.now());

        log.info("Session {} state rollback: {} -> {}", session.getId(), currentState, previousState);
        return true;
    }

    /**
     * Vérifie si une transition est valide sans l'appliquer.
     *
     * @param currentState L'état actuel
     * @param targetState  L'état cible
     * @return true si la transition serait valide
     */
    public boolean isValidTransition(SessionState currentState, SessionState targetState) {
        if (currentState == null || targetState == null) {
            return false;
        }

        Set<SessionState> validTargets = VALID_TRANSITIONS.get(currentState);
        return validTargets != null && validTargets.contains(targetState);
    }

    /**
     * Retourne les états cibles valides depuis l'état actuel.
     *
     * @param currentState L'état actuel
     * @return Ensemble des états cibles possibles
     */
    public Set<SessionState> getValidTargets(SessionState currentState) {
        return VALID_TRANSITIONS.getOrDefault(currentState, Set.of());
    }

    /**
     * Nettoie le cache des états précédents pour une session supprimée.
     *
     * @param sessionId ID de la session
     */
    public void cleanup(String sessionId) {
        previousStates.remove(sessionId);
    }
}
