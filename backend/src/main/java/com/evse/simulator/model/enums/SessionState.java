package com.evse.simulator.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * États possibles d'une session de charge EVSE.
 * <p>
 * Correspond aux états du protocole OCPP 1.6 avec extensions pour le simulateur.
 * </p>
 */
public enum SessionState {

    /**
     * Session inactive, aucune connexion WebSocket.
     */
    DISCONNECTED("disconnected"),

    /**
     * Connexion WebSocket en cours d'établissement.
     */
    CONNECTING("connecting"),

    /**
     * Connecté au CSMS, en attente de BootNotification.
     */
    CONNECTED("connected"),

    /**
     * BootNotification accepté, StatusNotification:Available envoyé.
     */
    BOOT_ACCEPTED("booted"),

    /**
     * Véhicule garé/configuré (profil sélectionné).
     */
    PARKED("parked"),

    /**
     * Câble branché, StatusNotification:Preparing envoyé.
     */
    PLUGGED("plugged"),

    /**
     * Authorize envoyé, en attente de réponse.
     */
    AUTHORIZING("authorizing"),

    /**
     * Authorize accepté, prêt à démarrer la transaction.
     */
    AUTHORIZED("authorized"),

    /**
     * StartTransaction envoyé, en attente de réponse.
     */
    STARTING("starting"),

    /**
     * Transaction active, charge en cours.
     */
    CHARGING("started"),

    /**
     * Charge suspendue par l'EVSE.
     */
    SUSPENDED_EVSE("suspended_evse"),

    /**
     * Charge suspendue par le véhicule.
     */
    SUSPENDED_EV("suspended_ev"),

    /**
     * StopTransaction envoyé, en attente de réponse.
     */
    STOPPING("stopping"),

    /**
     * Transaction terminée, StatusNotification:Finishing envoyé.
     */
    FINISHING("stopped"),

    /**
     * Prêt pour nouvelle session (état legacy).
     */
    AVAILABLE("available"),

    /**
     * Préparation (état legacy).
     */
    PREPARING("preparing"),

    /**
     * Charge terminée (état legacy).
     */
    FINISHED("finished"),

    /**
     * Session inactive (état legacy).
     */
    IDLE("closed"),

    /**
     * Réservé pour un utilisateur spécifique.
     */
    RESERVED("reserved"),

    /**
     * Indisponible (maintenance, erreur).
     */
    UNAVAILABLE("unavailable"),

    /**
     * En erreur (panne, défaut).
     */
    FAULTED("error"),

    /**
     * Déconnexion en cours.
     */
    DISCONNECTING("disconnecting");

    private final String value;

    SessionState(String value) {
        this.value = value;
    }

    /**
     * Retourne la valeur JSON de l'état.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Vérifie si la session peut démarrer une charge.
     */
    public boolean canStartCharge() {
        return this == AUTHORIZED;
    }

    /**
     * Vérifie si la session peut s'autoriser.
     */
    public boolean canAuthorize() {
        return this == PLUGGED;
    }

    /**
     * Vérifie si la session peut brancher le câble.
     */
    public boolean canPlug() {
        return this == PARKED || this == BOOT_ACCEPTED;
    }

    /**
     * Vérifie si la session peut garer le véhicule.
     */
    public boolean canPark() {
        return this == BOOT_ACCEPTED;
    }

    /**
     * Vérifie si la session est en cours de charge.
     */
    public boolean isCharging() {
        return this == CHARGING || this == SUSPENDED_EVSE || this == SUSPENDED_EV;
    }

    /**
     * Vérifie si la session est connectée au CSMS.
     */
    public boolean isConnected() {
        return this != IDLE && this != DISCONNECTED && this != CONNECTING && this != DISCONNECTING;
    }

    /**
     * Vérifie si la session est dans un état d'erreur.
     */
    public boolean isError() {
        return this == FAULTED || this == UNAVAILABLE;
    }

    /**
     * Vérifie si c'est un état transitoire (en attente de réponse).
     */
    public boolean isTransitional() {
        return this == CONNECTING || this == AUTHORIZING || this == STARTING || this == STOPPING;
    }

    /**
     * Convertit une chaîne en SessionState.
     */
    public static SessionState fromValue(String value) {
        for (SessionState state : values()) {
            if (state.value.equalsIgnoreCase(value) || state.name().equalsIgnoreCase(value)) {
                return state;
            }
        }
        return IDLE;
    }

    @Override
    public String toString() {
        return value;
    }
}