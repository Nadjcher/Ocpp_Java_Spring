package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to ChangeAvailability.req.
 */
public enum AvailabilityStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    SCHEDULED("Scheduled");

    private final String value;

    AvailabilityStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AvailabilityStatus fromValue(String value) {
        for (AvailabilityStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AvailabilityStatus: " + value);
    }
}
