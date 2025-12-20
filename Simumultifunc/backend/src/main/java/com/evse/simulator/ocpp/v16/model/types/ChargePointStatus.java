package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status du Charge Point OCPP 1.6.
 */
public enum ChargePointStatus {
    AVAILABLE("Available"),
    PREPARING("Preparing"),
    CHARGING("Charging"),
    SUSPENDED_EVSE("SuspendedEVSE"),
    SUSPENDED_EV("SuspendedEV"),
    FINISHING("Finishing"),
    RESERVED("Reserved"),
    UNAVAILABLE("Unavailable"),
    FAULTED("Faulted");

    private final String value;

    ChargePointStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ChargePointStatus fromValue(String value) {
        for (ChargePointStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Vérifie si le status permet de démarrer une charge.
     */
    public boolean canStartCharging() {
        return this == AVAILABLE || this == PREPARING;
    }

    /**
     * Vérifie si le status indique une charge en cours.
     */
    public boolean isCharging() {
        return this == CHARGING || this == SUSPENDED_EV || this == SUSPENDED_EVSE;
    }
}
