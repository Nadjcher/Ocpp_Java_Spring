package com.evse.simulator.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * États des connecteurs OCPP 1.6.
 * <p>
 * Correspond aux valeurs de ChargePointStatus dans StatusNotification.
 * </p>
 */
public enum ConnectorStatus {

    /**
     * Connecteur disponible pour charge.
     */
    AVAILABLE("Available"),

    /**
     * Préparation de la charge (véhicule connecté).
     */
    PREPARING("Preparing"),

    /**
     * Charge en cours.
     */
    CHARGING("Charging"),

    /**
     * Charge suspendue par l'EVSE.
     */
    SUSPENDED_EVSE("SuspendedEVSE"),

    /**
     * Charge suspendue par le véhicule.
     */
    SUSPENDED_EV("SuspendedEV"),

    /**
     * Fin de charge.
     */
    FINISHING("Finishing"),

    /**
     * Connecteur réservé.
     */
    RESERVED("Reserved"),

    /**
     * Connecteur indisponible.
     */
    UNAVAILABLE("Unavailable"),

    /**
     * Connecteur en erreur.
     */
    FAULTED("Faulted");

    private final String value;

    ConnectorStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Convertit une chaîne en ConnectorStatus.
     */
    public static ConnectorStatus fromValue(String value) {
        if (value == null) {
            return AVAILABLE;
        }
        for (ConnectorStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return AVAILABLE;
    }

    /**
     * Convertit un SessionState en ConnectorStatus.
     */
    public static ConnectorStatus fromSessionState(SessionState state) {
        return switch (state) {
            case IDLE, DISCONNECTED, CONNECTING, CONNECTED, BOOT_ACCEPTED, PARKED -> AVAILABLE;
            case AVAILABLE -> AVAILABLE;
            case PREPARING, AUTHORIZING, PLUGGED, AUTHORIZED, STARTING -> PREPARING;
            case CHARGING -> CHARGING;
            case SUSPENDED_EVSE -> SUSPENDED_EVSE;
            case SUSPENDED_EV -> SUSPENDED_EV;
            case STOPPING, FINISHING, FINISHED -> FINISHING;
            case RESERVED -> RESERVED;
            case UNAVAILABLE, DISCONNECTING -> UNAVAILABLE;
            case FAULTED -> FAULTED;
        };
    }

    @Override
    public String toString() {
        return value;
    }
}