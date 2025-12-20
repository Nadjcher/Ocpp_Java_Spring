package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to DataTransfer.req.
 */
public enum DataTransferStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    UNKNOWN_MESSAGE_ID("UnknownMessageId"),
    UNKNOWN_VENDOR_ID("UnknownVendorId");

    private final String value;

    DataTransferStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DataTransferStatus fromValue(String value) {
        for (DataTransferStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown DataTransferStatus: " + value);
    }
}
