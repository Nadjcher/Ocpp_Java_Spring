package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status de ClearChargingProfile OCPP 1.6.
 */
public enum ClearChargingProfileStatus {
    ACCEPTED("Accepted"),
    UNKNOWN("Unknown");

    private final String value;

    ClearChargingProfileStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ClearChargingProfileStatus fromValue(String value) {
        for (ClearChargingProfileStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
