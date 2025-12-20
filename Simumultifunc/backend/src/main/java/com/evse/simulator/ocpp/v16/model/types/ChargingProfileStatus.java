package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to SetChargingProfile.req.
 */
public enum ChargingProfileStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    NOT_SUPPORTED("NotSupported");

    private final String value;

    ChargingProfileStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ChargingProfileStatus fromValue(String value) {
        for (ChargingProfileStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ChargingProfileStatus: " + value);
    }
}
