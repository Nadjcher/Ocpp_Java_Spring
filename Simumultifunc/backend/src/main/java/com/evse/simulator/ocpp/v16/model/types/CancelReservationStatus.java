package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to CancelReservation.req.
 */
public enum CancelReservationStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    private final String value;

    CancelReservationStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CancelReservationStatus fromValue(String value) {
        for (CancelReservationStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CancelReservationStatus: " + value);
    }
}
