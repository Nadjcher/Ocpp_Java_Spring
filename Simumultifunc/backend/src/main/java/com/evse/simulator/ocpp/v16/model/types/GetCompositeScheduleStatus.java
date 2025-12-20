package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to GetCompositeSchedule.req.
 */
public enum GetCompositeScheduleStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    private final String value;

    GetCompositeScheduleStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static GetCompositeScheduleStatus fromValue(String value) {
        for (GetCompositeScheduleStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown GetCompositeScheduleStatus: " + value);
    }
}
