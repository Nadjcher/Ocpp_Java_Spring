package com.evse.simulator.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * États possibles d'une session de charge EVSE.
 * Correspond aux états du protocole OCPP 1.6 avec validation des transitions.
 */
public enum SessionState {

    // =========================================================================
    // États de connexion (pas de StatusNotification)
    // =========================================================================

    DISCONNECTED("disconnected", null),
    CONNECTING("connecting", null),
    CONNECTED("connected", null),
    DISCONNECTING("disconnecting", null),

    // =========================================================================
    // États opérationnels (avec StatusNotification OCPP)
    // =========================================================================

    /** BootNotification accepté */
    BOOT_ACCEPTED("booted", "Available"),

    /** Prêt pour nouvelle session */
    AVAILABLE("available", "Available"),

    /** Véhicule garé/configuré (profil sélectionné) - optionnel */
    PARKED("parked", "Available"),

    /** Câble branché */
    PLUGGED("plugged", "Preparing"),

    /** Authorize en cours */
    AUTHORIZING("authorizing", "Preparing"),

    /** Authorize accepté */
    AUTHORIZED("authorized", "Preparing"),

    /** StartTransaction en cours */
    STARTING("starting", "Preparing"),

    /** Transaction active, charge en cours */
    CHARGING("started", "Charging"),

    /** Charge suspendue par l'EVSE */
    SUSPENDED_EVSE("suspended_evse", "SuspendedEVSE"),

    /** Charge suspendue par le véhicule */
    SUSPENDED_EV("suspended_ev", "SuspendedEV"),

    /** StopTransaction en cours */
    STOPPING("stopping", "Charging"),

    /** Transaction terminée, véhicule encore branché */
    FINISHING("stopped", "Finishing"),

    // =========================================================================
    // États spéciaux
    // =========================================================================

    /** Réservé pour un utilisateur */
    RESERVED("reserved", "Reserved"),

    /** Indisponible */
    UNAVAILABLE("unavailable", "Unavailable"),

    /** En erreur */
    FAULTED("error", "Faulted"),

    // =========================================================================
    // États legacy (pour compatibilité)
    // =========================================================================

    PREPARING("preparing", "Preparing"),
    FINISHED("finished", "Finishing"),
    IDLE("closed", null);

    private final String value;
    private final String ocppStatus;

    SessionState(String value, String ocppStatus) {
        this.value = value;
        this.ocppStatus = ocppStatus;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Retourne le statut OCPP pour StatusNotification.
     */
    public String getOcppStatus() {
        return ocppStatus;
    }

    /**
     * Vérifie si cet état nécessite l'envoi d'un StatusNotification.
     */
    public boolean requiresStatusNotification() {
        return ocppStatus != null;
    }

    // =========================================================================
    // Validation des transitions
    // =========================================================================

    /**
     * Vérifie si une transition vers l'état cible est valide.
     */
    public boolean canTransitionTo(SessionState target) {
        if (target == null) return false;

        return switch (this) {
            case DISCONNECTED -> target == CONNECTING || target == CONNECTED;
            case CONNECTING -> target == CONNECTED || target == DISCONNECTED;
            case CONNECTED -> target == BOOT_ACCEPTED || target == DISCONNECTED || target == DISCONNECTING;

            case BOOT_ACCEPTED -> target == AVAILABLE || target == PARKED || target == PLUGGED
                               || target == DISCONNECTED || target == DISCONNECTING || target == FAULTED;

            case AVAILABLE -> target == PARKED || target == PLUGGED || target == RESERVED
                           || target == UNAVAILABLE || target == FAULTED
                           || target == DISCONNECTED || target == DISCONNECTING;

            case PARKED -> target == PLUGGED || target == AVAILABLE || target == FAULTED
                        || target == DISCONNECTED || target == DISCONNECTING;

            case PLUGGED -> target == AUTHORIZING || target == AVAILABLE || target == PARKED
                         || target == FAULTED || target == DISCONNECTING;

            case AUTHORIZING -> target == AUTHORIZED || target == PLUGGED || target == FAULTED;

            case AUTHORIZED -> target == STARTING || target == PLUGGED || target == FAULTED;

            case STARTING -> target == CHARGING || target == AUTHORIZED || target == PLUGGED || target == FAULTED;

            case CHARGING -> target == STOPPING || target == SUSPENDED_EVSE || target == SUSPENDED_EV
                          || target == FAULTED;

            case SUSPENDED_EVSE -> target == CHARGING || target == STOPPING || target == FAULTED;
            case SUSPENDED_EV -> target == CHARGING || target == STOPPING || target == FAULTED;

            case STOPPING -> target == FINISHING || target == FAULTED;

            case FINISHING -> target == AVAILABLE || target == PLUGGED || target == PARKED
                           || target == DISCONNECTING;

            case FAULTED -> target == AVAILABLE || target == UNAVAILABLE || target == DISCONNECTED;
            case UNAVAILABLE -> target == AVAILABLE || target == FAULTED;
            case RESERVED -> target == AVAILABLE || target == PLUGGED;

            case DISCONNECTING -> target == DISCONNECTED;

            // États legacy - transitions permissives
            case PREPARING -> target == CHARGING || target == AUTHORIZED || target == PLUGGED;
            case FINISHED -> target == AVAILABLE || target == PLUGGED;
            case IDLE -> target == DISCONNECTED || target == CONNECTING || target == CONNECTED;
        };
    }

    /**
     * Retourne les transitions valides depuis cet état.
     */
    public SessionState[] getValidTransitions() {
        return switch (this) {
            case DISCONNECTED -> new SessionState[]{CONNECTING, CONNECTED};
            case CONNECTING -> new SessionState[]{CONNECTED, DISCONNECTED};
            case CONNECTED -> new SessionState[]{BOOT_ACCEPTED, DISCONNECTED, DISCONNECTING};

            case BOOT_ACCEPTED -> new SessionState[]{AVAILABLE, PARKED, PLUGGED, DISCONNECTED, DISCONNECTING, FAULTED};
            case AVAILABLE -> new SessionState[]{PARKED, PLUGGED, RESERVED, UNAVAILABLE, FAULTED, DISCONNECTED, DISCONNECTING};
            case PARKED -> new SessionState[]{PLUGGED, AVAILABLE, FAULTED, DISCONNECTED, DISCONNECTING};

            case PLUGGED -> new SessionState[]{AUTHORIZING, AVAILABLE, PARKED, FAULTED, DISCONNECTING};
            case AUTHORIZING -> new SessionState[]{AUTHORIZED, PLUGGED, FAULTED};
            case AUTHORIZED -> new SessionState[]{STARTING, PLUGGED, FAULTED};
            case STARTING -> new SessionState[]{CHARGING, AUTHORIZED, PLUGGED, FAULTED};

            case CHARGING -> new SessionState[]{STOPPING, SUSPENDED_EVSE, SUSPENDED_EV, FAULTED};
            case SUSPENDED_EVSE -> new SessionState[]{CHARGING, STOPPING, FAULTED};
            case SUSPENDED_EV -> new SessionState[]{CHARGING, STOPPING, FAULTED};
            case STOPPING -> new SessionState[]{FINISHING, FAULTED};

            case FINISHING -> new SessionState[]{AVAILABLE, PLUGGED, PARKED, DISCONNECTING};

            case FAULTED -> new SessionState[]{AVAILABLE, UNAVAILABLE, DISCONNECTED};
            case UNAVAILABLE -> new SessionState[]{AVAILABLE, FAULTED};
            case RESERVED -> new SessionState[]{AVAILABLE, PLUGGED};

            case DISCONNECTING -> new SessionState[]{DISCONNECTED};

            // États legacy
            case PREPARING -> new SessionState[]{CHARGING, AUTHORIZED, PLUGGED};
            case FINISHED -> new SessionState[]{AVAILABLE, PLUGGED};
            case IDLE -> new SessionState[]{DISCONNECTED, CONNECTING, CONNECTED};
        };
    }

    // =========================================================================
    // Helpers existants (conservés pour compatibilité)
    // =========================================================================

    public boolean canStartCharge() {
        return this == AUTHORIZED;
    }

    public boolean canAuthorize() {
        return this == PLUGGED;
    }

    public boolean canPlug() {
        return this == PARKED || this == BOOT_ACCEPTED || this == AVAILABLE;
    }

    public boolean canPark() {
        return this == BOOT_ACCEPTED || this == AVAILABLE;
    }

    public boolean isCharging() {
        return this == CHARGING || this == SUSPENDED_EVSE || this == SUSPENDED_EV;
    }

    public boolean isConnected() {
        return this != IDLE && this != DISCONNECTED && this != CONNECTING && this != DISCONNECTING;
    }

    public boolean isError() {
        return this == FAULTED || this == UNAVAILABLE;
    }

    public boolean isTransitional() {
        return this == CONNECTING || this == AUTHORIZING || this == STARTING
            || this == STOPPING || this == DISCONNECTING;
    }

    public boolean hasActiveTransaction() {
        return this == CHARGING || this == SUSPENDED_EVSE || this == SUSPENDED_EV
            || this == STOPPING;
    }

    public boolean canStopTransaction() {
        return this == CHARGING || this == SUSPENDED_EVSE || this == SUSPENDED_EV;
    }

    public static SessionState fromValue(String value) {
        if (value == null) return IDLE;
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
