package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to ReserveNow.req.
 */
public enum ReservationStatus {
    ACCEPTED("Accepted"),
    FAULTED("Faulted"),
    OCCUPIED("Occupied"),
    REJECTED("Rejected"),
    UNAVAILABLE("Unavailable");

    private final String value;

    ReservationStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ReservationStatus fromValue(String value) {
        for (ReservationStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ReservationStatus: " + value);
    }
}
